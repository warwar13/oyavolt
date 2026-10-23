package com.warwar13.gentai.analytics

import kotlin.math.abs

enum class FlowRecordingMode { CHARGING, DISCHARGING }

data class FlowSample(
    val timestampMs: Long,
    val voltageV: Double,
    val currentA: Double,
    val powerW: Double,
    val socPct: Int,
    val gapFromPreviousMs: Long? = null,
)

data class FlowRecordingTotals(
    val chargedAh: Double = 0.0,
    val dischargedAh: Double = 0.0,
    val chargedWh: Double = 0.0,
    val dischargedWh: Double = 0.0,
    val observedMs: Long = 0,
    val missingMs: Long = 0,
    val sampleCount: Int = 0,
    val averagePowerW: Double = 0.0,
    val peakPowerW: Double = 0.0,
    val startSocPct: Int? = null,
    val endSocPct: Int? = null,
) {
    val coveragePct: Double get() = if (observedMs + missingMs == 0L) 0.0 else observedMs * 100.0 / (observedMs + missingMs)
}

object FlowRecordingAnalytics {
    const val MODE_THRESHOLD_A = 0.15
    const val MAX_INTEGRATION_GAP_MS = 30_000L

    fun modeFor(currentA: Double): FlowRecordingMode? = when {
        currentA > MODE_THRESHOLD_A -> FlowRecordingMode.CHARGING
        currentA < -MODE_THRESHOLD_A -> FlowRecordingMode.DISCHARGING
        else -> null
    }

    fun summarize(samples: List<FlowSample>): FlowRecordingTotals {
        val ordered = samples.sortedBy { it.timestampMs }
        if (ordered.isEmpty()) return FlowRecordingTotals()
        var chargedAh = 0.0
        var dischargedAh = 0.0
        var chargedWh = 0.0
        var dischargedWh = 0.0
        var observed = 0L
        var missing = 0L
        for (i in 1 until ordered.size) {
            val a = ordered[i - 1]
            val b = ordered[i]
            val dt = b.timestampMs - a.timestampMs
            if (dt <= 0) continue
            if (dt > MAX_INTEGRATION_GAP_MS || b.gapFromPreviousMs != null) {
                missing += dt
                continue
            }
            val hours = dt / 3_600_000.0
            chargedAh += ((a.currentA.coerceAtLeast(0.0) + b.currentA.coerceAtLeast(0.0)) / 2.0) * hours
            dischargedAh += (((-a.currentA).coerceAtLeast(0.0) + (-b.currentA).coerceAtLeast(0.0)) / 2.0) * hours
            chargedWh += ((a.powerW.coerceAtLeast(0.0) + b.powerW.coerceAtLeast(0.0)) / 2.0) * hours
            dischargedWh += (((-a.powerW).coerceAtLeast(0.0) + (-b.powerW).coerceAtLeast(0.0)) / 2.0) * hours
            observed += dt
        }
        val peak = ordered.maxOfOrNull { abs(it.powerW) } ?: 0.0
        val average = if (observed > 0) (chargedWh + dischargedWh) / (observed / 3_600_000.0) else abs(ordered.first().powerW)
        return FlowRecordingTotals(
            chargedAh = chargedAh,
            dischargedAh = dischargedAh,
            chargedWh = chargedWh,
            dischargedWh = dischargedWh,
            observedMs = observed,
            missingMs = missing,
            sampleCount = ordered.size,
            averagePowerW = average,
            peakPowerW = peak,
            startSocPct = ordered.first().socPct,
            endSocPct = ordered.last().socPct,
        )
    }
}
