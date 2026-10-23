package com.warwar13.gentai.analytics

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import com.warwar13.gentai.ui.main.DayWindow
import com.warwar13.gentai.ui.main.batteryUsageAsOf
import com.warwar13.gentai.ui.main.dayWindow

class UsageAnalyticsTest {
    @Test fun `daily load insights use elapsed time and exclude gaps`() {
        val samples = listOf(
            AnalyticsSample(0, 12.0, -10.0, 80, 25, 0.010),
            AnalyticsSample(10_000, 12.0, -10.0, 79, 30, 0.020),
            AnalyticsSample(70_000, 12.0, -20.0, 78, 35, 0.030),
        )
        val result = UsageAnalytics.analyse(samples)
        assertEquals(120.0, result.averageDischargePowerW, 0.0001)
        assertEquals(240.0, result.peakDischargePowerW, 0.0)
        assertEquals(120.0, result.medianDischargePowerW, 0.0)
        assertEquals(240.0, result.percentile95DischargePowerW, 0.0)
        assertEquals(10.0, result.averageDischargeCurrentA, 0.0001)
        assertEquals(10_000L, result.dischargingMs)
        assertEquals(10_000L, result.mediumLoadMs)
        assertEquals(60_000L, result.totals.missingMs)
        assertEquals(35, result.maximumTemperatureC)
        assertEquals(0.03, result.maximumCellDeltaV!!, 0.0)
        assertEquals(1, result.usagePeriods.size)
    }

    @Test fun `local analytics day follows daylight saving boundaries`() {
        val window = LocalDate.of(2026, 10, 4).dayWindow(ZoneId.of("Australia/Sydney"))
        assertEquals(23 * 3_600_000L, window.endEpochMs - window.startEpochMs + 1)
    }

    @Test fun `historical battery usage is bounded by selected day end`() {
        assertEquals(199L, batteryUsageAsOf(DayWindow(100L, 199L), nowEpochMs = 250L))
    }

    @Test fun `current battery usage is bounded by current time`() {
        assertEquals(150L, batteryUsageAsOf(DayWindow(100L, 199L), nowEpochMs = 150L))
    }

    @Test fun `charging is separate from household load`() {
        val result = UsageAnalytics.analyse(listOf(
            AnalyticsSample(0, 13.0, 5.0, 50),
            AnalyticsSample(5_000, 13.0, 5.0, 51),
        ))
        assertEquals(0.0, result.totals.dischargedWh, 0.0)
        assertEquals(65.0, result.averageChargePowerW, 0.0001)
        assertEquals(5_000L, result.chargingMs)
    }
}
