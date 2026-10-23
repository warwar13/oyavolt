package com.warwar13.gentai.analytics

import com.warwar13.gentai.ui.main.EnergySlotState
import com.warwar13.gentai.ui.main.energySlotState
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class SolarHourlyReportTest {
    private val zone = ZoneId.of("Asia/Manila")
    private val date = LocalDate.of(2026, 10, 19)
    private val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    private val settings = EnergySettings(start, zone.id, "PHP", 13.0, null)

    @Test fun `hour sums equal daily integration with partial tracking and direction changes`() {
        val config = settings.copy(trackingStartMs = start + 2_000)
        val points = listOf(
            SolarReading(start, 100.0, 1), SolarReading(start + 5_000, 100.0, 1),
            SolarReading(start + 10_000, -100.0, 1), SolarReading(start + 15_000, -200.0, 1),
            SolarReading(start + 3_600_000 - 5_000, -100.0, 1), SolarReading(start + 3_600_000 + 5_000, -100.0, 1),
        )
        val hourly = SolarHourlyReport.make(points, date, config, start + 2 * 3_600_000)
        val daily = points.zipWithNext().flatMap { (a, b) -> SolarEnergyAnalytics.dailyParts(a, b, config.trackingStartMs, zone) }
        assertEquals(24, hourly.size)
        assertEquals(daily.sumOf { it.amount.chargedWh }, hourly.sumOf { it.chargedWh }, 1e-9)
        assertEquals(daily.sumOf { it.amount.usedWh }, hourly.sumOf { it.usedWh }, 1e-9)
        assertEquals(daily.sumOf { it.amount.observedMs }, hourly.sumOf { it.observedMs })
        assertEquals(3_598_000L, hourly.first().expectedMs)
        assertTrue(hourly.drop(2).all { !it.inScope })
    }

    @Test fun `midnight interval belongs to each matching local day without double counting`() {
        val midnight = start + 24 * 3_600_000
        val points = listOf(SolarReading(midnight - 5_000, -360.0, 1), SolarReading(midnight + 5_000, -360.0, 1))
        val before = SolarHourlyReport.make(points, date, settings, midnight + 10_000)
        val after = SolarHourlyReport.make(points, date.plusDays(1), settings, midnight + 10_000)
        assertEquals(0.5, before.sumOf { it.usedWh }, 1e-9)
        assertEquals(0.5, after.sumOf { it.usedWh }, 1e-9)
        assertEquals(5_000L, before.sumOf { it.observedMs })
        assertEquals(5_000L, after.sumOf { it.observedMs })
    }

    @Test fun `measured idle is zero while missing and future intervals remain distinct`() {
        val points = listOf(SolarReading(start, 0.0, 1), SolarReading(start + 5_000, 0.0, 1))
        val bars = SolarHourlyReport.make(points, date, settings, start + 2 * 3_600_000)
        assertEquals(EnergySlotState.MEASURED, energySlotState(bars[0]))
        assertEquals(0.0, bars[0].chargedWh + bars[0].usedWh, 0.0)
        assertEquals(EnergySlotState.MISSING, energySlotState(bars[1]))
        assertEquals(EnergySlotState.OUTSIDE_TRACKING, energySlotState(bars[2]))
    }

    @Test fun `disconnects and new sessions do not invent recorded energy`() {
        val points = listOf(SolarReading(start, 300.0, 1), SolarReading(start + 5_000, 300.0, 2), SolarReading(start + 10_000, 300.0, 2, true))
        val bars = SolarHourlyReport.make(points, date, settings, start + 60_000)
        assertEquals(0.0, bars.sumOf { it.chargedWh }, 0.0)
        assertEquals(0L, bars.sumOf { it.observedMs })
    }

    @Test fun `calendar hours follow reporting zone and retain repeated DST hours`() {
        val sydney = ZoneId.of("Australia/Sydney")
        listOf(LocalDate.of(2026, 10, 4) to 23, LocalDate.of(2026, 4, 5) to 25).forEach { (day, count) ->
            val beginning = day.atStartOfDay(sydney).toInstant().toEpochMilli()
            val end = day.plusDays(1).atStartOfDay(sydney).toInstant().toEpochMilli()
            val bars = SolarHourlyReport.make(emptyList(), day, settings.copy(trackingStartMs = beginning, reportingZoneId = sydney.id), end)
            assertEquals(count, bars.size)
            assertEquals(end - beginning, bars.sumOf { it.expectedMs })
            assertEquals(count, bars.map { it.startEpochMs }.distinct().size)
            assertEquals(count, bars.map { it.label }.distinct().size)
        }
    }
}
