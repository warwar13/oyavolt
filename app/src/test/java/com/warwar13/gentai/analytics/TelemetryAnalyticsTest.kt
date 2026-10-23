package com.warwar13.gentai.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryAnalyticsTest {
    @Test fun `positive current charges and negative current discharges`() {
        assertEquals(PowerMode.CHARGING, TelemetryAnalytics.modeFor(0.16)); assertEquals(PowerMode.DISCHARGING, TelemetryAnalytics.modeFor(-0.16)); assertEquals(PowerMode.IDLE, TelemetryAnalytics.modeFor(0.15))
    }
    @Test fun `integrates irregular intervals with signed trapezoids`() {
        val totals = TelemetryAnalytics.totals(listOf(s(0, -10.0), s(10_000, -20.0), s(30_000, 10.0)))
        assertEquals(0.0972222, totals.dischargedAh, 0.000001); assertEquals(0.0277778, totals.chargedAh, 0.000001)
        assertEquals(1.244444, totals.dischargedWh, 0.00001); assertEquals(0.355556, totals.chargedWh, 0.00001)
    }
    @Test fun `does not integrate missing data gaps`() {
        val totals = TelemetryAnalytics.totals(listOf(s(0, -10.0), s(60_000, -10.0)))
        assertEquals(0.0, totals.dischargedAh, 0.0); assertEquals(60_000L, totals.missingMs); assertEquals(0.0, totals.coveragePct, 0.0)
    }
    @Test fun `short marked idle gap is never integrated`() {
        val samples = listOf(s(0, -10.0), s(5_000, -10.0), s(15_000, -10.0).copy(gapBefore = true))
        val totals = TelemetryAnalytics.totals(samples)
        assertEquals(5_000L, totals.observedMs)
        assertEquals(10_000L, totals.missingMs)
        assertEquals(12.8 * 10.0 * 5.0 / 3_600.0, totals.dischargedWh, 1e-9)
        assertEquals(3, TelemetryAnalytics.sessions(samples).size)
    }
    @Test fun `creates charging discharging idle and disconnected sessions`() {
        val sessions = TelemetryAnalytics.sessions(listOf(s(0, -5.0), s(5_000, -5.0), s(10_000, 0.0), s(15_000, 5.0), s(60_000, 5.0)))
        assertEquals(listOf(PowerMode.DISCHARGING, PowerMode.IDLE, PowerMode.CHARGING, PowerMode.DISCONNECTED, PowerMode.CHARGING), sessions.map { it.mode })
        assertTrue(sessions.single { it.mode == PowerMode.DISCONNECTED }.totals.missingMs > 0)
    }
    @Test fun `daily totals report valid interval coverage`() {
        val daily = TelemetryAnalytics.daily(listOf(s(0, -10.0), s(5_000, -10.0))).single()
        assertEquals(100.0, daily.totals.coveragePct, 0.0); assertEquals(2, daily.sampleCount)
    }
    private fun s(time: Long, current: Double) = AnalyticsSample(time, 12.8, current, 50)
}
