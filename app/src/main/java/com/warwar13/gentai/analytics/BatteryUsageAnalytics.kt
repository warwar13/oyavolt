package com.warwar13.gentai.analytics

import kotlin.math.max

data class BatteryUsageSample(
    val id: Long = 0,
    val timestampMs: Long,
    val voltageV: Double,
    val currentA: Double,
    val socPct: Int,
    val remainingAh: Double,
    val fullAh: Double,
    val gapFromPreviousMs: Long? = null,
) {
    val powerW: Double get() = voltageV * currentA
}

data class BatteryUsagePoint(val timestampMs: Long, val socPct: Double, val segment: Int)

data class BatteryUsageCycle(
    val startMs: Long,
    val endMs: Long,
    val startSocPct: Int,
    val endSocPct: Int,
    val completed: Boolean,
    val elapsedMs: Long,
    val dischargingMs: Long,
    val observedMs: Long,
    val missingMs: Long,
    val dischargedAh: Double,
    val dischargedWh: Double,
    val averageDischargePowerW: Double,
    val peakDischargePowerW: Double,
    val coveragePct: Double,
    val projectedActiveMsTo20: Long?,
    val sampleCount: Int,
    val points: List<BatteryUsagePoint>,
)

class NearFullPeakTracker(latestNearFull: BatteryUsageSample) {
    var peak: BatteryUsageSample = latestNearFull
        private set
    var complete: Boolean = false
        private set
    private var newer = latestNearFull

    /** Accepts samples in reverse chronological order and stops at the edge of the near-full episode. */
    fun addOlder(sample: BatteryUsageSample): Boolean {
        if (newer.gapFromPreviousMs != null || sample.socPct < BatteryUsageAccumulator.NEAR_FULL_SOC) {
            complete = true
            return false
        }
        if (sample.socPct > peak.socPct) peak = sample
        newer = sample
        return true
    }
}

class BatteryUsageAccumulator(
    private val expectedStartMs: Long,
    private val expectedEndMs: Long,
    private val maxChartPoints: Int = 360,
) {
    private val bucketMs = max(1L, ((expectedEndMs - expectedStartMs).coerceAtLeast(1L) + maxChartPoints - 1) / maxChartPoints)
    private val points = mutableListOf<BatteryUsagePoint>()
    private var previous: BatteryUsageSample? = null
    private var first: BatteryUsageSample? = null
    private var last: BatteryUsageSample? = null
    private var observedMs = 0L
    private var missingMs = 0L
    private var dischargingMs = 0L
    private var dischargedAh = 0.0
    private var dischargedWh = 0.0
    private var peakDischargePowerW = 0.0
    private var sampleCount = 0
    private var segment = 0
    private var activeBucket = Long.MIN_VALUE
    private var activeBucketSegment = 0
    private var bucketSocTotal = 0.0
    private var bucketSamples = 0
    private var bucketLastTimestamp = 0L

    fun add(sample: BatteryUsageSample) {
        val prior = previous
        if (prior != null) {
            val durationMs = sample.timestampMs - prior.timestampMs
            val isGap = durationMs > TelemetryAnalytics.MAX_INTEGRATION_GAP_MS || sample.gapFromPreviousMs != null
            if (durationMs > 0L) {
                if (isGap) {
                    missingMs += durationMs
                    flushBucket()
                    segment++
                } else {
                    observedMs += durationMs
                    integrate(prior, sample, durationMs)
                }
            }
        }

        val bucket = (sample.timestampMs - expectedStartMs).coerceAtLeast(0L) / bucketMs
        if (activeBucket != Long.MIN_VALUE && (bucket != activeBucket || activeBucketSegment != segment)) flushBucket()
        if (activeBucket == Long.MIN_VALUE) {
            activeBucket = bucket
            activeBucketSegment = segment
        }
        bucketSocTotal += sample.socPct
        bucketSamples++
        bucketLastTimestamp = sample.timestampMs
        peakDischargePowerW = max(peakDischargePowerW, (-sample.powerW).coerceAtLeast(0.0))
        first = first ?: sample
        last = sample
        previous = sample
        sampleCount++
    }

    fun finish(): BatteryUsageCycle? {
        flushBucket()
        val start = first ?: return null
        val end = last ?: return null
        val activeHours = dischargingMs / 3_600_000.0
        val averagePower = if (activeHours > 0.0) dischargedWh / activeHours else 0.0
        val averageCurrent = if (activeHours > 0.0) dischargedAh / activeHours else 0.0
        val projected = if (
            end.socPct > TARGET_SOC && dischargingMs >= MIN_DISCHARGE_MS_FOR_ESTIMATE &&
            averageCurrent > TelemetryAnalytics.MODE_THRESHOLD_A &&
            end.fullAh > 0.0
        ) {
            val usableAh = end.fullAh * (end.socPct - TARGET_SOC) / 100.0
            (usableAh / averageCurrent * 3_600_000.0).toLong().coerceAtLeast(0L)
        } else null
        val totalTime = observedMs + missingMs
        return BatteryUsageCycle(
            startMs = start.timestampMs,
            endMs = end.timestampMs,
            startSocPct = start.socPct,
            endSocPct = end.socPct,
            completed = end.socPct <= TARGET_SOC,
            elapsedMs = (end.timestampMs - start.timestampMs).coerceAtLeast(0L),
            dischargingMs = dischargingMs,
            observedMs = observedMs,
            missingMs = missingMs,
            dischargedAh = dischargedAh,
            dischargedWh = dischargedWh,
            averageDischargePowerW = averagePower,
            peakDischargePowerW = peakDischargePowerW,
            coveragePct = if (totalTime > 0L) observedMs * 100.0 / totalTime else 0.0,
            projectedActiveMsTo20 = projected,
            sampleCount = sampleCount,
            points = points.toList(),
        )
    }

    private fun integrate(a: BatteryUsageSample, b: BatteryUsageSample, durationMs: Long) {
        val hours = durationMs / 3_600_000.0
        val dischargeA = ((-a.currentA).coerceAtLeast(0.0) + (-b.currentA).coerceAtLeast(0.0)) / 2.0
        val dischargeW = ((-a.powerW).coerceAtLeast(0.0) + (-b.powerW).coerceAtLeast(0.0)) / 2.0
        dischargedAh += dischargeA * hours
        dischargedWh += dischargeW * hours
        if ((a.currentA + b.currentA) / 2.0 < -TelemetryAnalytics.MODE_THRESHOLD_A) dischargingMs += durationMs
    }

    private fun flushBucket() {
        if (bucketSamples > 0) {
            points += BatteryUsagePoint(bucketLastTimestamp, bucketSocTotal / bucketSamples, activeBucketSegment)
        }
        activeBucket = Long.MIN_VALUE
        bucketSocTotal = 0.0
        bucketSamples = 0
    }

    companion object {
        const val TARGET_SOC = 20
        const val NEAR_FULL_SOC = 95
        const val MIN_DISCHARGE_MS_FOR_ESTIMATE = 10 * 60 * 1000L
    }
}

object BatteryUsageAnalytics {
    fun analyse(samples: List<BatteryUsageSample>, maxChartPoints: Int = 360): BatteryUsageCycle? {
        if (samples.isEmpty()) return null
        val ordered = samples.sortedWith(compareBy(BatteryUsageSample::timestampMs, BatteryUsageSample::id))
        return BatteryUsageAccumulator(ordered.first().timestampMs, ordered.last().timestampMs, maxChartPoints).also { accumulator ->
            ordered.forEach(accumulator::add)
        }.finish()
    }
}
