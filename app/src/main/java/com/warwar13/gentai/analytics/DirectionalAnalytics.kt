package com.warwar13.gentai.analytics

import kotlin.math.abs

/** Direction used by the user-facing flow analytics. */
enum class FlowDirection { CHARGING, DISCHARGING }

data class DirectionalChartPoint(
    val timestampMs: Long,
    val watts: Double,
    val amps: Double,
    val socPct: Int,
    val gapBefore: Boolean = false,
)

data class DirectionalSegment(
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long,
    val energyWh: Double,
    val averagePowerW: Double,
    val peakPowerW: Double,
)

data class DirectionalInsights(
    val direction: FlowDirection,
    val energyWh: Double = 0.0,
    val energyAh: Double = 0.0,
    val durationMs: Long = 0,
    val observedMs: Long = 0,
    val missingMs: Long = 0,
    val coveragePct: Double = 0.0,
    val sampleCount: Int = 0,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val startSocPct: Int? = null,
    val endSocPct: Int? = null,
    val averagePowerW: Double = 0.0,
    val peakPowerW: Double = 0.0,
    val averageCurrentA: Double = 0.0,
    val peakCurrentA: Double = 0.0,
    val segments: List<DirectionalSegment> = emptyList(),
    val chartPoints: List<DirectionalChartPoint> = emptyList(),
) {
    val hasData: Boolean get() = sampleCount > 0 || durationMs > 0
}

data class DirectionalDayInsights(
    val charging: DirectionalInsights = DirectionalInsights(FlowDirection.CHARGING),
    val discharging: DirectionalInsights = DirectionalInsights(FlowDirection.DISCHARGING),
    val idleMs: Long = 0,
    val observedMs: Long = 0,
    val missingMs: Long = 0,
    val sampleCount: Int = 0,
) {
    val coveragePct: Double
        get() = if (observedMs + missingMs == 0L) 0.0 else observedMs * 100.0 / (observedMs + missingMs)

    fun forDirection(direction: FlowDirection): DirectionalInsights =
        if (direction == FlowDirection.CHARGING) charging else discharging
}

/**
 * Classifies raw telemetry on read, so old mixed connection recordings are useful
 * without rewriting their source rows or pretending they were typed recordings.
 */
object DirectionalAnalytics {

    fun analyse(samples: List<AnalyticsSample>): DirectionalDayInsights {
        val ordered = samples.sortedBy { it.timestampMs }
        if (ordered.isEmpty()) return DirectionalDayInsights()

        val accumulators = FlowDirection.entries.associateWith { Accumulator(it) }
        var idleMs = 0L
        var observedMs = 0L
        var missingMs = 0L

        for (index in 1 until ordered.size) {
            val previous = ordered[index - 1]
            val current = ordered[index]
            val elapsedMs = current.timestampMs - previous.timestampMs
            if (elapsedMs <= 0L) continue
            if (current.gapBefore || elapsedMs > TelemetryAnalytics.MAX_INTEGRATION_GAP_MS) {
                missingMs += elapsedMs
                accumulators.values.forEach { it.breakSegment() }
                continue
            }

            observedMs += elapsedMs
            when (TelemetryAnalytics.modeFor((previous.currentA + current.currentA) / 2.0)) {
                PowerMode.CHARGING -> {
                    accumulators.getValue(FlowDirection.DISCHARGING).breakSegment()
                    accumulators.getValue(FlowDirection.CHARGING).addInterval(previous, current, elapsedMs)
                }
                PowerMode.DISCHARGING -> {
                    accumulators.getValue(FlowDirection.CHARGING).breakSegment()
                    accumulators.getValue(FlowDirection.DISCHARGING).addInterval(previous, current, elapsedMs)
                }
                PowerMode.IDLE, PowerMode.DISCONNECTED -> {
                    accumulators.values.forEach { it.breakSegment() }
                    idleMs += elapsedMs
                }
            }
        }

        val coverage = if (observedMs + missingMs == 0L) 0.0 else observedMs * 100.0 / (observedMs + missingMs)
        return DirectionalDayInsights(
            charging = accumulators.getValue(FlowDirection.CHARGING).finish(ordered, missingMs, coverage),
            discharging = accumulators.getValue(FlowDirection.DISCHARGING).finish(ordered, missingMs, coverage),
            idleMs = idleMs,
            observedMs = observedMs,
            missingMs = missingMs,
            sampleCount = ordered.size,
        )
    }

    private class Accumulator(private val direction: FlowDirection) {
        private var energyWh = 0.0
        private var energyAh = 0.0
        private var durationMs = 0L
        private var currentWeighted = 0.0
        private val samples = mutableListOf<AnalyticsSample>()
        private val segments = mutableListOf<DirectionalSegment>()
        private var segmentStart: AnalyticsSample? = null
        private var segmentEnergyWh = 0.0
        private var segmentDurationMs = 0L
        private var segmentPowers = mutableListOf<Double>()

        fun addInterval(previous: AnalyticsSample, current: AnalyticsSample, elapsedMs: Long) {
            val currentMode = TelemetryAnalytics.modeFor((previous.currentA + current.currentA) / 2.0)
            val expectedMode = if (direction == FlowDirection.CHARGING) PowerMode.CHARGING else PowerMode.DISCHARGING
            if (currentMode != expectedMode) {
                finishSegment()
                return
            }
            if (segmentStart == null) segmentStart = previous
            if (samples.lastOrNull()?.timestampMs != previous.timestampMs) samples += previous
            samples += current

            val hours = elapsedMs / 3_600_000.0
            val avgCurrent = abs((previous.currentA + current.currentA) / 2.0)
            val avgPower = abs((previous.powerW + current.powerW) / 2.0)
            energyAh += avgCurrent * hours
            energyWh += avgPower * hours
            durationMs += elapsedMs
            currentWeighted += avgCurrent * elapsedMs
            segmentEnergyWh += avgPower * hours
            segmentDurationMs += elapsedMs
            segmentPowers += avgPower
        }

        fun finish(ordered: List<AnalyticsSample>, missingMs: Long, coverage: Double): DirectionalInsights {
            finishSegment()
            val expectedMode = if (direction == FlowDirection.CHARGING) PowerMode.CHARGING else PowerMode.DISCHARGING
            val directionSamples = ordered.mapIndexedNotNull { index, sample ->
                if (TelemetryAnalytics.modeFor(sample.currentA) == expectedMode) IndexedValue(index, sample) else null
            }
            val chartPoints = chartPoints(ordered, directionSamples)
            val first = directionSamples.firstOrNull()?.value
            val last = directionSamples.lastOrNull()?.value
            val peakCurrent = directionSamples.maxOfOrNull { abs(it.value.currentA) } ?: 0.0
            val averageCurrent = if (durationMs > 0) currentWeighted / durationMs else 0.0
            return DirectionalInsights(
                direction = direction,
                energyWh = energyWh,
                energyAh = energyAh,
                durationMs = durationMs,
                observedMs = durationMs,
                missingMs = missingMs,
                coveragePct = coverage,
                sampleCount = directionSamples.size,
                startMs = first?.timestampMs,
                endMs = last?.timestampMs,
                startSocPct = first?.socPct,
                endSocPct = last?.socPct,
                averagePowerW = if (durationMs > 0) energyWh / (durationMs / 3_600_000.0) else 0.0,
                peakPowerW = directionSamples.maxOfOrNull { abs(it.value.powerW) } ?: 0.0,
                averageCurrentA = averageCurrent,
                peakCurrentA = peakCurrent,
                segments = segments.toList(),
                chartPoints = chartPoints,
            )
        }

        private fun finishSegment() {
            val start = segmentStart ?: return
            val end = samples.lastOrNull() ?: start
            if (segmentDurationMs > 0L) {
                segments += DirectionalSegment(
                    startMs = start.timestampMs,
                    endMs = end.timestampMs,
                    durationMs = segmentDurationMs,
                    energyWh = segmentEnergyWh,
                    averagePowerW = segmentEnergyWh / (segmentDurationMs / 3_600_000.0),
                    peakPowerW = segmentPowers.maxOrNull() ?: 0.0,
                )
            }
            segmentStart = null
            segmentEnergyWh = 0.0
            segmentDurationMs = 0L
            segmentPowers = mutableListOf()
        }

        fun breakSegment() = finishSegment()

        private fun chartPoints(ordered: List<AnalyticsSample>, directionSamples: List<IndexedValue<AnalyticsSample>>): List<DirectionalChartPoint> {
            if (directionSamples.isEmpty()) return emptyList()
            val raw = directionSamples.map { indexed ->
                val sample = indexed.value
                val previous = ordered.getOrNull(indexed.index - 1)
                val expectedMode = if (direction == FlowDirection.CHARGING) PowerMode.CHARGING else PowerMode.DISCHARGING
                DirectionalChartPoint(
                    timestampMs = sample.timestampMs,
                    watts = abs(sample.powerW),
                    amps = abs(sample.currentA),
                    socPct = sample.socPct,
                    gapBefore = previous != null && (sample.gapBefore || sample.timestampMs - previous.timestampMs > TelemetryAnalytics.MAX_INTEGRATION_GAP_MS || TelemetryAnalytics.modeFor(previous.currentA) != expectedMode),
                )
            }
            return raw
        }
    }
}
