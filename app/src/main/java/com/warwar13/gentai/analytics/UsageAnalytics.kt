package com.warwar13.gentai.analytics

import kotlin.math.abs
import kotlin.math.ceil

data class UsageInsights(
    val totals: EnergyTotals = EnergyTotals(),
    val sampleCount: Int = 0,
    val startSocPct: Int? = null,
    val endSocPct: Int? = null,
    val averageDischargePowerW: Double = 0.0,
    val averageChargePowerW: Double = 0.0,
    val peakDischargePowerW: Double = 0.0,
    val peakDischargeAtMs: Long? = null,
    val medianDischargePowerW: Double = 0.0,
    val percentile95DischargePowerW: Double = 0.0,
    val averageDischargeCurrentA: Double = 0.0,
    val minimumVoltageV: Double? = null,
    val maximumTemperatureC: Int? = null,
    val maximumCellDeltaV: Double? = null,
    val dischargingMs: Long = 0,
    val chargingMs: Long = 0,
    val idleMs: Long = 0,
    val lightLoadMs: Long = 0,
    val mediumLoadMs: Long = 0,
    val heavyLoadMs: Long = 0,
    val usagePeriods: List<UsagePeriod> = emptyList(),
)

data class UsagePeriod(
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long,
    val energyWh: Double,
    val averagePowerW: Double,
    val peakPowerW: Double,
)

object UsageAnalytics {
    const val LIGHT_LOAD_MAX_W = 100.0
    const val MEDIUM_LOAD_MAX_W = 300.0

    fun analyse(samples: List<AnalyticsSample>): UsageInsights {
        val ordered = samples.sortedBy { it.timestampMs }
        if (ordered.isEmpty()) return UsageInsights()
        val totals = TelemetryAnalytics.totals(ordered)
        var dischargingMs = 0L
        var chargingMs = 0L
        var idleMs = 0L
        var lightMs = 0L
        var mediumMs = 0L
        var heavyMs = 0L

        for (index in 1 until ordered.size) {
            val previous = ordered[index - 1]
            val current = ordered[index]
            val elapsedMs = current.timestampMs - previous.timestampMs
            if (elapsedMs <= 0 || elapsedMs > TelemetryAnalytics.MAX_INTEGRATION_GAP_MS) continue
            val averageCurrent = (previous.currentA + current.currentA) / 2.0
            when (TelemetryAnalytics.modeFor(averageCurrent)) {
                PowerMode.CHARGING -> chargingMs += elapsedMs
                PowerMode.DISCHARGING -> {
                    dischargingMs += elapsedMs
                    val loadW = (abs(previous.powerW) + abs(current.powerW)) / 2.0
                    when {
                        loadW < LIGHT_LOAD_MAX_W -> lightMs += elapsedMs
                        loadW < MEDIUM_LOAD_MAX_W -> mediumMs += elapsedMs
                        else -> heavyMs += elapsedMs
                    }
                }
                PowerMode.IDLE -> idleMs += elapsedMs
                PowerMode.DISCONNECTED -> Unit
            }
        }

        val peak = ordered.filter { it.powerW < 0 }.maxByOrNull { -it.powerW }
        val dischargeLoads = ordered.filter { TelemetryAnalytics.modeFor(it.currentA) == PowerMode.DISCHARGING }.map { -it.powerW }.sorted()
        val usagePeriods = TelemetryAnalytics.sessions(ordered)
            .filter { it.mode == PowerMode.DISCHARGING && it.totals.observedMs > 0 }
            .map { UsagePeriod(it.startMs, it.endMs, it.totals.observedMs, it.totals.dischargedWh, it.averageAbsolutePowerW, it.peakAbsolutePowerW) }
        return UsageInsights(
            totals = totals,
            sampleCount = ordered.size,
            startSocPct = ordered.first().socPct,
            endSocPct = ordered.last().socPct,
            averageDischargePowerW = averagePower(totals.dischargedWh, dischargingMs),
            averageChargePowerW = averagePower(totals.chargedWh, chargingMs),
            peakDischargePowerW = peak?.let { -it.powerW } ?: 0.0,
            peakDischargeAtMs = peak?.timestampMs,
            medianDischargePowerW = percentile(dischargeLoads, 0.50),
            percentile95DischargePowerW = percentile(dischargeLoads, 0.95),
            averageDischargeCurrentA = averagePower(totals.dischargedAh, dischargingMs),
            minimumVoltageV = ordered.minOfOrNull { it.voltageV },
            maximumTemperatureC = ordered.mapNotNull { it.temperatureMaxC }.maxOrNull(),
            maximumCellDeltaV = ordered.mapNotNull { it.cellDeltaV }.maxOrNull(),
            dischargingMs = dischargingMs,
            chargingMs = chargingMs,
            idleMs = idleMs,
            lightLoadMs = lightMs,
            mediumLoadMs = mediumMs,
            heavyLoadMs = heavyMs,
            usagePeriods = usagePeriods,
        )
    }

    private fun averagePower(energyWh: Double, durationMs: Long): Double =
        if (durationMs > 0) energyWh / (durationMs / 3_600_000.0) else 0.0

    private fun percentile(sortedValues: List<Double>, fraction: Double): Double {
        if (sortedValues.isEmpty()) return 0.0
        val index = (ceil(sortedValues.size * fraction).toInt() - 1).coerceIn(0, sortedValues.lastIndex)
        return sortedValues[index]
    }
}
