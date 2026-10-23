package com.warwar13.gentai.ui.main

import com.warwar13.gentai.analytics.AnalyticsSample
import com.warwar13.gentai.analytics.ReadableTrend
import com.warwar13.gentai.analytics.SolarChartBar
import com.warwar13.gentai.analytics.TelemetryAnalytics
import com.warwar13.gentai.analytics.TrendBucket
import com.warwar13.gentai.analytics.TrendReading
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

internal enum class AnalyticsMetric(val label: String, val unit: String) {
    ENERGY("Energy", "Wh"), POWER("Power", "W"), SOC("SOC", "%"), CURRENT("Current", "A"),
}
internal enum class AnalyticsFilter(val label: String) { BOTH("Both"), CHARGING("Charging"), DISCHARGING("Discharging") }
internal enum class AnalyticsWindow(val label: String) { RECORDED("Recorded span"), DAY("Full day"), SIX_HOURS("Last 6h"), HOUR("Last 1h") }
internal enum class EnergySlotState { MEASURED, MISSING, OUTSIDE_TRACKING }
internal fun energySlotState(bar: SolarChartBar): EnergySlotState = when {
    !bar.inScope -> EnergySlotState.OUTSIDE_TRACKING
    bar.observedMs > 0 -> EnergySlotState.MEASURED
    else -> EnergySlotState.MISSING
}

internal fun analyticsEnergy(wh: Double): String = when {
    abs(wh) >= 1_000 -> String.format(Locale.getDefault(), "%.2f kWh", wh / 1_000)
    abs(wh) >= 10 -> String.format(Locale.getDefault(), "%.0f Wh", wh)
    abs(wh) >= 1 -> String.format(Locale.getDefault(), "%.1f Wh", wh)
    else -> String.format(Locale.getDefault(), "%.2f Wh", wh)
}

internal fun analyticsNiceMaximum(value: Double): Double {
    if (value <= 0 || !value.isFinite()) return 1.0
    val magnitude = 10.0.pow(floor(log10(value)))
    val step = magnitude / 2
    return ceil(value / step) * step
}

internal fun analyticsWindow(samples: List<AnalyticsSample>, day: DayWindow, nowMs: Long, window: AnalyticsWindow): DayWindow {
    if (window == AnalyticsWindow.DAY || samples.isEmpty()) return day
    if (window == AnalyticsWindow.RECORDED) {
        val first = samples.first().timestampMs
        val last = samples.last().timestampMs
        val pad = maxOf(30_000L, (last - first) / 40)
        return DayWindow(maxOf(day.startEpochMs, first - pad), minOf(day.endEpochMs, last + pad))
    }
    val end = minOf(day.endEpochMs, nowMs)
    val width = if (window == AnalyticsWindow.HOUR) 3_600_000L else 6 * 3_600_000L
    return DayWindow(maxOf(day.startEpochMs, end - width), maxOf(day.startEpochMs + 1, end))
}

internal fun analyticsTrend(samples: List<AnalyticsSample>, metric: AnalyticsMetric, filter: AnalyticsFilter, window: DayWindow): List<TrendBucket> {
    val readings = mutableListOf<TrendReading>()
    var omitted = false
    samples.filter { it.timestampMs in window.startEpochMs..window.endEpochMs }.forEach { sample ->
        val matches = metric == AnalyticsMetric.SOC || when (filter) {
            AnalyticsFilter.BOTH -> true
            AnalyticsFilter.CHARGING -> sample.currentA > TelemetryAnalytics.MODE_THRESHOLD_A
            AnalyticsFilter.DISCHARGING -> sample.currentA < -TelemetryAnalytics.MODE_THRESHOLD_A
        }
        if (!matches) { omitted = true; return@forEach }
        readings += TrendReading(
            sample.timestampMs,
            when (metric) {
                AnalyticsMetric.SOC -> sample.socPct.toDouble()
                AnalyticsMetric.CURRENT -> sample.currentA
                else -> sample.powerW
            },
            gapBefore = omitted || sample.gapBefore,
        )
        omitted = false
    }
    return ReadableTrend.reduce(readings, window.startEpochMs, window.endEpochMs, targetBuckets = 180, preserveSignChanges = metric != AnalyticsMetric.SOC)
}
