package com.warwar13.gentai.analytics

import com.warwar13.gentai.data.SolarDailyRollupEntity
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SolarReportsTest {
    private val zone = ZoneId.of("Australia/Sydney")
    private val day = LocalDate.of(2026, 10, 15)
    private val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
    private val settings = EnergySettings(start, zone.id, "AUD", 0.30, 1_000.0)

    @Test fun `periods sum recorded battery energy once`() {
        val rows = listOf(
            SolarDailyRollupEntity("2026-10-15", 1_500.0, 2_000.0, 10 * 3_600_000L, 4 * 3_600_000L, 6 * 3_600_000L),
            SolarDailyRollupEntity("2026-10-16", 1_000.0, 3_000.0, 12 * 3_600_000L, 5 * 3_600_000L, 7 * 3_600_000L),
        )
        val now = day.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val today = SolarReports.make(rows, settings, SolarPeriod.DAY, day, now)
        val month = SolarReports.make(rows, settings, SolarPeriod.MONTH, day, now)
        assertEquals(2_000.0, today.amount.usedWh, 0.0)
        assertEquals(5_000.0, month.amount.usedWh, 0.0)
        assertEquals(2_500.0, month.amount.chargedWh, 0.0)
        assertEquals(31, month.bars.size)
        assertEquals(2, month.bars.count { it.inScope })
        assertEquals("1", month.bars.first().label)
        assertEquals("31", month.bars.last().label)
        assertEquals(9 * 3_600_000L, month.amount.chargingMs)
        assertEquals(13 * 3_600_000L, month.amount.dischargingMs)
        assertEquals(1.50, settings.savingsFor(month.amount.usedWh)!!, 1e-9)
        assertEquals(0.75, settings.potentialSolarValueFor(month.amount.chargedWh)!!, 1e-9)
        assertEquals(0.15, settings.paybackPercent(month.amount.usedWh)!!, 1e-9)
        assertEquals(-99.85, settings.netRoiPercent(month.amount.usedWh)!!, 1e-9)
    }

    @Test fun `year report keeps all calendar month slots`() {
        val now = day.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val report = SolarReports.make(emptyList(), settings, SolarPeriod.YEAR, day, now)
        assertEquals(12, report.bars.size)
        assertEquals("JAN", report.bars.first().label)
        assertEquals("DEC", report.bars.last().label)
    }

    @Test fun `no recordings are not presented as measured zero use`() {
        val report = SolarReports.make(emptyList(), settings, SolarPeriod.DAY, day, start + 3_600_000)
        assertFalse(report.hasData)
        assertEquals(0.0, report.coveragePct, 0.0)
        assertEquals(3_600_000, report.missingMs)
        val idle = SolarReports.make(listOf(SolarDailyRollupEntity("2026-10-15", 0.0, 0.0, 3_600_000)), settings, SolarPeriod.DAY, day, start + 3_600_000)
        assertTrue(idle.hasData)
        assertEquals(100.0, idle.coveragePct, 0.0)
    }

    @Test fun `missing tariff or cost leaves financial figures unavailable`() {
        val withoutTariff = settings.copy(tariffPerKwh = null)
        assertEquals(null, withoutTariff.savingsFor(1_000.0))
        assertEquals(null, withoutTariff.potentialSolarValueFor(1_000.0))
        assertEquals(null, withoutTariff.paybackPercent(1_000.0))
        assertEquals(null, settings.copy(systemCost = null).netRoiPercent(1_000.0))
    }
}
