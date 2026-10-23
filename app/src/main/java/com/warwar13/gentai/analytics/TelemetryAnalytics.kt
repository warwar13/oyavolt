package com.warwar13.gentai.analytics

import kotlin.math.abs

enum class PowerMode { CHARGING, DISCHARGING, IDLE, DISCONNECTED }
data class AnalyticsSample(val timestampMs: Long, val voltageV: Double, val currentA: Double, val socPct: Int, val temperatureMaxC: Int? = null, val cellDeltaV: Double? = null, val gapBefore: Boolean = false) {
    val powerW: Double get() = voltageV * currentA
}
data class EnergyTotals(val chargedAh: Double = 0.0, val dischargedAh: Double = 0.0, val chargedWh: Double = 0.0, val dischargedWh: Double = 0.0, val observedMs: Long = 0, val missingMs: Long = 0) {
    val coveragePct: Double get() = if (observedMs + missingMs == 0L) 0.0 else observedMs * 100.0 / (observedMs + missingMs)
}
data class SessionSummary(val mode: PowerMode, val startMs: Long, val endMs: Long, val startSocPct: Int?, val endSocPct: Int?, val totals: EnergyTotals, val sampleCount: Int, val averageAbsolutePowerW: Double, val peakAbsolutePowerW: Double)
data class DailySummary(val dayStartMs: Long, val totals: EnergyTotals, val sampleCount: Int)

object TelemetryAnalytics {
    const val MODE_THRESHOLD_A = 0.15
    const val MAX_INTEGRATION_GAP_MS = 30_000L
    fun modeFor(currentA: Double): PowerMode = when { currentA > MODE_THRESHOLD_A -> PowerMode.CHARGING; currentA < -MODE_THRESHOLD_A -> PowerMode.DISCHARGING; else -> PowerMode.IDLE }

    fun totals(samples: List<AnalyticsSample>): EnergyTotals {
        val ordered = samples.sortedBy { it.timestampMs }; var result = EnergyTotals()
        for (i in 1 until ordered.size) result += interval(ordered[i - 1], ordered[i])
        return result
    }
    fun sessions(samples: List<AnalyticsSample>): List<SessionSummary> {
        val ordered = samples.sortedBy { it.timestampMs }; if (ordered.isEmpty()) return emptyList()
        val result = mutableListOf<SessionSummary>(); var start = ordered.first(); var mode = modeFor(start.currentA); var members = mutableListOf(start)
        fun finish() { result += summarize(mode, start, members.last(), members) }
        for (i in 1 until ordered.size) {
            val previous = ordered[i - 1]; val current = ordered[i]; val gap = current.gapBefore || current.timestampMs - previous.timestampMs > MAX_INTEGRATION_GAP_MS
            if (gap) {
                finish()
                result += SessionSummary(PowerMode.DISCONNECTED, previous.timestampMs, current.timestampMs, previous.socPct, current.socPct, EnergyTotals(missingMs = current.timestampMs - previous.timestampMs), 0, 0.0, 0.0)
                start = current; mode = modeFor(current.currentA); members = mutableListOf(current)
            } else if (modeFor(current.currentA) != mode) {
                finish(); start = current; mode = modeFor(current.currentA); members = mutableListOf(current)
            } else members += current
        }
        finish(); return result
    }
    fun daily(samples: List<AnalyticsSample>): List<DailySummary> = samples.sortedBy { it.timestampMs }.groupBy { utcDayStart(it.timestampMs) }.map { (day, values) -> DailySummary(day, totals(values), values.size) }.sortedBy { it.dayStartMs }

    private fun summarize(mode: PowerMode, start: AnalyticsSample, end: AnalyticsSample, values: List<AnalyticsSample>): SessionSummary {
        val totals = totals(values)
        val averagePower = if (totals.observedMs > 0) (totals.chargedWh + totals.dischargedWh) / (totals.observedMs / 3_600_000.0) else abs(values.first().powerW)
        return SessionSummary(mode, start.timestampMs, end.timestampMs, start.socPct, end.socPct, totals, values.size, averagePower, values.maxOf { abs(it.powerW) })
    }
    private fun interval(a: AnalyticsSample, b: AnalyticsSample): EnergyTotals {
        val ms = b.timestampMs - a.timestampMs
        if (ms <= 0) return EnergyTotals()
        if (ms > MAX_INTEGRATION_GAP_MS || b.gapBefore) return EnergyTotals(missingMs = ms)
        val hours = ms / 3_600_000.0
        val chargeA = (a.currentA.coerceAtLeast(0.0) + b.currentA.coerceAtLeast(0.0)) / 2
        val dischargeA = ((-a.currentA).coerceAtLeast(0.0) + (-b.currentA).coerceAtLeast(0.0)) / 2
        val chargeW = (a.powerW.coerceAtLeast(0.0) + b.powerW.coerceAtLeast(0.0)) / 2
        val dischargeW = ((-a.powerW).coerceAtLeast(0.0) + (-b.powerW).coerceAtLeast(0.0)) / 2
        return EnergyTotals(chargeA * hours, dischargeA * hours, chargeW * hours, dischargeW * hours, ms, 0)
    }
    private operator fun EnergyTotals.plus(other: EnergyTotals) = EnergyTotals(chargedAh + other.chargedAh, dischargedAh + other.dischargedAh, chargedWh + other.chargedWh, dischargedWh + other.dischargedWh, observedMs + other.observedMs, missingMs + other.missingMs)
    private fun utcDayStart(ms: Long): Long = Math.floorDiv(ms, 86_400_000L) * 86_400_000L
}
