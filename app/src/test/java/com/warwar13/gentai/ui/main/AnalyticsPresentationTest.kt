package com.warwar13.gentai.ui.main

import com.warwar13.gentai.analytics.AnalyticsSample
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class AnalyticsPresentationTest {
    private fun point(time: Long, current: Double, gap: Boolean = false) = AnalyticsSample(time, 12.0, current, 60, gapBefore = gap)

    @Test fun `energy display preserves useful small totals`() {
        assertTrue(analyticsEnergy(332.0).contains("332"))
        assertTrue(analyticsEnergy(332.0).endsWith("Wh"))
        assertTrue(analyticsEnergy(1234.0).endsWith("kWh"))
        assertTrue(analyticsEnergy(-50.0).startsWith("-50"))
    }

    @Test fun `signed flow is not cancelled by display bucket averaging`() {
        val result = analyticsTrend(listOf(point(0, 10.0), point(5_000, 10.0), point(10_000, -10.0), point(15_000, -10.0)), AnalyticsMetric.POWER, AnalyticsFilter.BOTH, DayWindow(0, 600_000))
        assertEquals(2, result.size)
        assertEquals(120.0, result[0].average, 0.0)
        assertEquals(-120.0, result[1].average, 0.0)
        assertFalse(result[1].gapBefore)
    }

    @Test fun `filter does not join discharging through excluded charging`() {
        val result = analyticsTrend(listOf(point(0, -5.0), point(5_000, 5.0), point(10_000, -5.0)), AnalyticsMetric.CURRENT, AnalyticsFilter.DISCHARGING, DayWindow(0, 600_000))
        assertEquals(2, result.size)
        assertTrue(result.last().gapBefore)
    }

    @Test fun `SOC includes idle and both directions independent of flow filter`() {
        val result = analyticsTrend(listOf(point(0, 2.0), point(5_000, 0.0), point(10_000, -2.0)), AnalyticsMetric.SOC, AnalyticsFilter.CHARGING, DayWindow(0, 600_000))
        assertEquals(3, result.sumOf { it.sampleCount })
        assertEquals(60.0, result.single().average, 0.0)
    }

    @Test fun `explicit gaps and long missing periods stay broken`() {
        val result = analyticsTrend(listOf(point(0, -2.0), point(5_000, -2.0, true), point(70_000, -2.0)), AnalyticsMetric.POWER, AnalyticsFilter.BOTH, DayWindow(0, 600_000))
        assertEquals(3, result.size)
        assertTrue(result.drop(1).all { it.gapBefore })
    }

    @Test fun `mixed power chart centers a labelled zero for a clear direction comparison`() {
        val result = analyticsTrend(listOf(point(0, 10.0), point(5_000, -20.0)), AnalyticsMetric.POWER, AnalyticsFilter.BOTH, DayWindow(0, 600_000))
        val bounds = trendBounds(result, TrendScale.SIGNED_ZERO)
        assertEquals(-bounds.minimum, bounds.maximum, 0.0)
        assertEquals(0.0, bounds.minimum + (bounds.maximum - bounds.minimum) / 2, 0.0)
    }

    @Test fun `recorded span zoom excludes empty time and stays within local calendar day`() {
        val day = LocalDate.of(2026, 10, 19).dayWindow(ZoneId.of("Asia/Manila"))
        val samples = listOf(point(day.startEpochMs + 3_600_000, 2.0), point(day.startEpochMs + 7_200_000, -2.0))
        val range = analyticsWindow(samples, day, day.endEpochMs, AnalyticsWindow.RECORDED)
        assertTrue(range.startEpochMs > day.startEpochMs)
        assertTrue(range.startEpochMs < samples.first().timestampMs)
        assertTrue(range.endEpochMs > samples.last().timestampMs)
        assertEquals(day, analyticsWindow(samples, day, day.endEpochMs, AnalyticsWindow.DAY))
    }
}
