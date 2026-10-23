package com.warwar13.gentai.monitor

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

data class BatteryTimeEstimate(val target: String, val hours: Double)

fun estimateBatteryTime(socPct: Int, remainingAh: Double, fullAh: Double, currentA: Double): BatteryTimeEstimate? {
    if (abs(currentA) <= 0.15) return null
    val estimatedFullAh = when {
        fullAh > 0.0 -> fullAh
        socPct > 0 && remainingAh > 0.0 -> remainingAh * 100.0 / socPct
        else -> return null
    }
    val hours = if (currentA > 0) {
        (estimatedFullAh - remainingAh).coerceAtLeast(0.0) / currentA
    } else {
        (remainingAh - estimatedFullAh * 0.20).coerceAtLeast(0.0) / -currentA
    }
    if (!hours.isFinite() || hours > 10_000) return null
    return BatteryTimeEstimate(if (currentA > 0) "Full" else "20%", hours)
}

fun formatBatteryEstimate(estimate: BatteryTimeEstimate): String {
    if (estimate.hours == 0.0) return if (estimate.target == "20%") "At or below 20%" else "Full"
    val totalMinutes = (estimate.hours * 60).toLong().coerceAtLeast(1)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val duration = if (hours > 0) "$hours hrs $minutes mins" else "$minutes mins"
    return "${estimate.target} in ~$duration"
}

fun formatBatteryEstimateClock(
    estimate: BatteryTimeEstimate,
    fromEpochMs: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String? {
    if (estimate.hours <= 0.0 || !estimate.hours.isFinite()) return null
    val targetEpochMs = fromEpochMs + (estimate.hours * 3_600_000.0).roundToLong()
    return Instant.ofEpochMilli(targetEpochMs).atZone(zoneId).format(BATTERY_TIME_FORMATTER)
}

private val BATTERY_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

fun livePowerWatts(voltageV: Double, currentA: Double): Double = abs(voltageV * currentA)
