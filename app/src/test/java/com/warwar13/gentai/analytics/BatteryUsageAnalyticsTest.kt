package com.warwar13.gentai.analytics

import com.warwar13.gentai.ui.main.batteryUsageProjectionDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryUsageAnalyticsTest {
    @Test fun `latest near-full episode selects its highest and latest peak`() {
        val tracker = NearFullPeakTracker(sample(50_000, -2.0, 95).copy(id = 5))
        assertTrue(tracker.addOlder(sample(40_000, -2.0, 98).copy(id = 4)))
        assertTrue(tracker.addOlder(sample(30_000, 0.0, 100).copy(id = 3)))
        assertTrue(tracker.addOlder(sample(20_000, 2.0, 100).copy(id = 2)))
        assertFalse(tracker.addOlder(sample(10_000, 2.0, 94).copy(id = 1)))
        assertEquals(3L, tracker.peak.id)
        assertEquals(100, tracker.peak.socPct)
        assertTrue(tracker.complete)
    }

    @Test fun `near-full episode does not cross a missing-data gap`() {
        val tracker = NearFullPeakTracker(sample(50_000, -2.0, 95, gap = 60_000).copy(id = 5))
        assertFalse(tracker.addOlder(sample(0, 0.0, 100).copy(id = 4)))
        assertEquals(5L, tracker.peak.id)
    }

    @Test fun `irregular discharge intervals integrate signed Ah and Wh`() {
        val result = BatteryUsageAnalytics.analyse(listOf(
            sample(0, -10.0, 100),
            sample(10_000, -10.0, 99),
            sample(25_000, -10.0, 98),
        ))!!
        assertEquals(10.0 * 25.0 / 3_600.0, result.dischargedAh, 1e-9)
        assertEquals(100.0 * 25.0 / 3_600.0, result.dischargedWh, 1e-9)
        assertEquals(25_000L, result.observedMs)
        assertEquals(0L, result.missingMs)
    }

    @Test fun `charging contributes no discharged energy`() {
        val result = BatteryUsageAnalytics.analyse(listOf(sample(0, 5.0, 95), sample(10_000, 5.0, 96)))!!
        assertEquals(0.0, result.dischargedAh, 0.0)
        assertEquals(0.0, result.dischargedWh, 0.0)
        assertEquals(0L, result.dischargingMs)
    }

    @Test fun `missing BLE interval is a graph break and is never integrated`() {
        val result = BatteryUsageAnalytics.analyse(listOf(sample(0, -10.0, 100), sample(60_000, -10.0, 99, gap = 60_000)))!!
        assertEquals(0.0, result.dischargedWh, 0.0)
        assertEquals(60_000L, result.missingMs)
        assertEquals(0.0, result.coveragePct, 0.0)
        assertEquals(listOf(0, 1), result.points.map { it.segment })
    }
    @Test fun `short marked idle interval is also a graph break`() {
        val result = BatteryUsageAnalytics.analyse(listOf(sample(0, -10.0, 100), sample(5_000, -10.0, 99, gap = 5_000)))!!
        assertEquals(0.0, result.dischargedWh, 0.0)
        assertEquals(5_000L, result.missingMs)
        assertEquals(listOf(0, 1), result.points.map { it.segment })
    }

    @Test fun `completed cycle stops projecting at twenty percent`() {
        val result = BatteryUsageAnalytics.analyse(listOf(sample(0, -10.0, 100), sample(10_000, -10.0, 20)))!!
        assertTrue(result.completed)
        assertEquals(20, result.endSocPct)
        assertNull(result.projectedActiveMsTo20)
    }

    @Test fun `projection starts after ten active minutes without requiring a five percent drop`() {
        val samples = (0..20).map { index ->
            sample(index * 30_000L, -10.0, 100 - index / 20)
        }
        val result = BatteryUsageAnalytics.analyse(samples)!!
        assertFalse(result.completed)
        assertEquals(99, result.endSocPct)
        assertEquals(28_440_000L, result.projectedActiveMsTo20)
        assertEquals(100.0, result.coveragePct, 0.0)
    }

    @Test fun `projection waits for ten valid discharge minutes and ignores gaps`() {
        val short = (0 until 20).map { index -> sample(index * 30_000L, -10.0, 100) }
        assertNull(BatteryUsageAnalytics.analyse(short)!!.projectedActiveMsTo20)

        val withGap = listOf(
            sample(0, -10.0, 100),
            sample(10 * 60_000L, -10.0, 99, gap = 10 * 60_000L),
        )
        assertNull(BatteryUsageAnalytics.analyse(withGap)!!.projectedActiveMsTo20)
    }

    @Test fun `projection display explains readiness instead of staying on learning`() {
        val fiveMinutes = (0..10).map { index -> sample(index * 30_000L, -10.0, 100) }
        val display = batteryUsageProjectionDisplay(BatteryUsageAnalytics.analyse(fiveMinutes)!!)
        assertEquals("5m more", display.value)
        assertTrue(display.hint!!.contains("10 minutes"))

        val charging = batteryUsageProjectionDisplay(BatteryUsageAnalytics.analyse(listOf(sample(0, 5.0, 100), sample(30_000, 5.0, 100)))!!)
        assertEquals("Waiting", charging.value)
        assertTrue(charging.hint!!.contains("discharging"))
    }

    @Test fun `long cycle projects while chart stays bounded`() {
        val samples = (0 until 1_000).map { index -> sample(index * 5_000L, -2.0, 100 - index / 500) }
        val result = BatteryUsageAnalytics.analyse(samples, maxChartPoints = 60)!!
        assertTrue(result.projectedActiveMsTo20 != null)
        assertTrue(result.points.size <= 61)
    }

    private fun sample(time: Long, currentA: Double, soc: Int, gap: Long? = null) = BatteryUsageSample(
        timestampMs = time,
        voltageV = 10.0,
        currentA = currentA,
        socPct = soc,
        remainingAh = soc.toDouble(),
        fullAh = 100.0,
        gapFromPreviousMs = gap,
    )
}
