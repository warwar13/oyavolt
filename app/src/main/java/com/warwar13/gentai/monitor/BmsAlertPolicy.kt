package com.warwar13.gentai.monitor

import com.warwar13.gentai.analytics.TelemetryAnalytics
import kotlin.math.roundToInt

/** Alerts that come directly from decoded BMS protection/status warnings. */
sealed interface BmsAlert {
    data class ProtectionWarnings(val warnings: List<String>) : BmsAlert
    @Deprecated("Local low-SOC reminders are no longer user alerts")
    data class LowSoc(val socPct: Int) : BmsAlert
    @Deprecated("Local full-charge alarms are no longer user alerts")
    data class FullCharge(val voltageV: Double, val socPct: Int, val isReminder: Boolean) : BmsAlert
    @Deprecated("Local cell-risk alerts are no longer user alerts")
    data class ChargingCellRisk(val highCellNumber: Int, val highCellVoltageV: Double, val deltaV: Double, val isReminder: Boolean) : BmsAlert
}

enum class ChargingCellSeverity { GREEN, YELLOW, RED }

data class ChargingCellStatus(
    val severity: ChargingCellSeverity,
    val highCellNumber: Int,
    val highCellVoltageV: Double,
    val deltaV: Double,
)

@Deprecated("Local cell-alert persistence is no longer used")
data class PersistedCellAlertState(
    val active: Boolean = false,
    val episodeRecorded: Boolean = false,
    val lastNotificationAtMs: Long? = null,
)

@Deprecated("Local full-charge persistence is no longer used")
data class PersistedFullChargeAlertState(
    val active: Boolean = false,
    val episodeRecorded: Boolean = false,
    val lastNotificationAtMs: Long? = null,
)

/**
 * Cell colors remain useful for live telemetry, but this policy never creates an alert.
 * Only the BMS warning payload can produce a user notification or Alert history row.
 */
object ChargingCellPolicy {
    const val CHARGING_THRESHOLD_A = 0.15
    const val YELLOW_CELL_V = 3.55
    const val RED_CELL_V = 3.60
    const val YELLOW_DELTA_V = 0.050
    const val RED_DELTA_V = 0.070

    fun isCharging(currentA: Double): Boolean = currentA > CHARGING_THRESHOLD_A

    fun status(cellsV: List<Double>): ChargingCellStatus? {
        val cells = cellsV.withIndex().filter { it.value.isFinite() && it.value > 0.0 }
        if (cells.size < 2) return null
        val lowest = cells.minBy { it.value }
        val highest = cells.maxBy { it.value }
        val deltaV = highest.value - lowest.value
        val deltaMv = (deltaV * 1_000).roundToInt()
        val severity = when {
            highest.value >= RED_CELL_V || deltaMv >= (RED_DELTA_V * 1_000).roundToInt() -> ChargingCellSeverity.RED
            highest.value >= YELLOW_CELL_V || deltaMv >= (YELLOW_DELTA_V * 1_000).roundToInt() -> ChargingCellSeverity.YELLOW
            else -> ChargingCellSeverity.GREEN
        }
        return ChargingCellStatus(severity, highest.index + 1, highest.value, deltaV)
    }
}

/** De-duplicates BMS warnings while allowing a resolved warning to alert again later. */
class BmsAlertTracker {
    private var activeWarnings = emptySet<String>()

    @Synchronized
    fun update(
        socPct: Int,
        warnings: List<String>?,
        currentA: Double = 0.0,
        cellsV: List<Double> = emptyList(),
        nowEpochMs: Long = System.currentTimeMillis(),
        voltageV: Double = Double.NaN,
    ): List<BmsAlert> {
        // These telemetry parameters are intentionally accepted for call-site compatibility.
        // They must never become locally generated alert sources.
        @Suppress("UNUSED_VARIABLE")
        val ignored = socPct + currentA.toInt() + cellsV.size + nowEpochMs.toInt() + voltageV.toInt()
        if (warnings == null) return emptyList()
        val current = warnings.filter { it.isNotBlank() }.toSet()
        val newlyActive = current - activeWarnings
        activeWarnings = current
        return newlyActive.takeIf { it.isNotEmpty() }
            ?.let { listOf(BmsAlert.ProtectionWarnings(it.sorted())) }
            ?: emptyList()
    }
}

data class SocThresholdAlert(
    val thresholdPct: Int,
    val observedSocPct: Int,
)

/**
 * Emits low-SOC reminders only for a real discharging stretch. Each threshold
 * re-arms after the battery rises above its threshold by the configured margin.
 */
class SocThresholdAlertTracker {
    private val thresholds = intArrayOf(25, 20)
    private val armed = thresholds.associateWith { true }.toMutableMap()
    private var previousDischargingSoc: Int? = null

    @Synchronized
    fun update(socPct: Int, currentA: Double): List<SocThresholdAlert> {
        val discharging = currentA < -TelemetryAnalytics.MODE_THRESHOLD_A
        if (!discharging) {
            previousDischargingSoc = null
            thresholds.forEach { threshold ->
                if (socPct >= threshold + REARM_MARGIN_PCT) armed[threshold] = true
            }
            return emptyList()
        }

        val previous = previousDischargingSoc
        val alerts = thresholds.asSequence().mapNotNull { threshold ->
            if (socPct >= threshold + REARM_MARGIN_PCT) armed[threshold] = true
            val crossed = armed.getValue(threshold) &&
                socPct <= threshold &&
                (previous == null || previous > threshold)
            if (!crossed) return@mapNotNull null
            armed[threshold] = false
            SocThresholdAlert(threshold, socPct)
        }
        previousDischargingSoc = socPct
        return alerts.toList()
    }

    companion object {
        const val REARM_MARGIN_PCT = 3
    }
}
