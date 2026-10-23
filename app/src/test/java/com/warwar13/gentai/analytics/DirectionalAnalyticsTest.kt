package com.warwar13.gentai.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectionalAnalyticsTest {
    @Test fun `mixed raw recording is separated by direction`() {
        val result = DirectionalAnalytics.analyse(listOf(
            sample(0, 5.0),
            sample(10_000, 5.0),
            sample(20_000, -5.0),
            sample(30_000, -5.0),
            sample(90_000, -5.0),
        ))

        assertEquals(10_000L, result.charging.durationMs)
        assertEquals(10_000L, result.discharging.durationMs)
        assertEquals(10_000L, result.idleMs)
        assertEquals(60_000L, result.missingMs)
        assertEquals(0.138888, result.charging.energyWh, 0.000001)
        assertEquals(0.138888, result.discharging.energyWh, 0.000001)
        assertEquals(1, result.charging.segments.size)
        assertEquals(3, result.discharging.chartPoints.size)
        assertTrue(result.discharging.chartPoints.last().gapBefore)
    }

    @Test fun `opposite and idle samples break directional chart lines`() {
        val result = DirectionalAnalytics.analyse(listOf(
            sample(0, -2.0),
            sample(5_000, -2.0),
            sample(10_000, 0.0),
            sample(15_000, 0.0),
            sample(20_000, -2.0),
            sample(25_000, -2.0),
        ))

        assertEquals(2, result.discharging.segments.size)
        assertTrue(result.discharging.chartPoints[1].gapBefore.not())
        assertTrue(result.discharging.chartPoints[2].gapBefore)
    }
    @Test fun `short marked idle gap splits discharge graph and energy`() {
        val result = DirectionalAnalytics.analyse(listOf(sample(0, -2.0), sample(5_000, -2.0), sample(15_000, -2.0).copy(gapBefore = true), sample(20_000, -2.0)))
        assertEquals(10_000L, result.discharging.durationMs)
        assertEquals(10_000L, result.missingMs)
        assertEquals(2, result.discharging.segments.size)
        assertTrue(result.discharging.chartPoints.any { it.timestampMs == 15_000L && it.gapBefore })
    }

    private fun sample(timestampMs: Long, currentA: Double) = AnalyticsSample(
        timestampMs = timestampMs,
        voltageV = 10.0,
        currentA = currentA,
        socPct = 50,
    )
}
