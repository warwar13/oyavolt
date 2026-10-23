package com.warwar13.gentai.monitor

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveBatteryStatsTest {
    @Test fun `discharge estimates time to twenty percent`() {
        val estimate = estimateBatteryTime(80, 80.0, 100.0, -10.0)!!
        assertEquals("20%", estimate.target)
        assertEquals(6.0, estimate.hours, 0.0001)
    }

    @Test fun `charge estimates time to full`() {
        val estimate = estimateBatteryTime(50, 50.0, 100.0, 10.0)!!
        assertEquals("Full", estimate.target)
        assertEquals(5.0, estimate.hours, 0.0001)
    }

    @Test fun `idle current has no unstable estimate`() {
        assertNull(estimateBatteryTime(50, 50.0, 100.0, 0.1))
    }

    @Test fun `estimate includes compact local target time`() {
        val start = Instant.parse("2026-09-16T12:00:00Z").toEpochMilli()
        assertEquals(
            "2:30 PM",
            formatBatteryEstimateClock(BatteryTimeEstimate("20%", 2.5), start, ZoneId.of("UTC")),
        )
    }

    @Test fun `completed estimate omits redundant clock time`() {
        assertNull(formatBatteryEstimateClock(BatteryTimeEstimate("Full", 0.0), 0L, ZoneId.of("UTC")))
    }

    @Test fun `live power is absolute for discharge and charge`() {
        assertEquals(208.0, livePowerWatts(13.0, -16.0), 0.0001)
        assertEquals(208.0, livePowerWatts(13.0, 16.0), 0.0001)
    }

    @Test fun `live power uses latest sample without averaging`() {
        assertEquals(108.0, livePowerWatts(12.0, -9.0), 0.0001)
        assertEquals(208.0, livePowerWatts(13.0, -16.0), 0.0001)
    }
}
