package com.warwar13.gentai.analytics

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SolarEnergyAnalyticsTest {
    private val zone = ZoneId.of("Australia/Sydney")

    @Test fun `discharge energy uses signed battery watts and actual time`() {
        val parts = SolarEnergyAnalytics.dailyParts(SolarReading(0, -120.0, 1), SolarReading(5_000, -120.0, 1), 0, zone)
        assertEquals(120.0 * 5.0 / 3_600.0, parts.single().amount.usedWh, 1e-9)
        assertEquals(0.0, parts.single().amount.chargedWh, 0.0)
        assertEquals(5_000, parts.single().amount.observedMs)
        assertEquals(5_000, parts.single().amount.dischargingMs)
        assertEquals(0, parts.single().amount.chargingMs)
    }

    @Test fun `charging is separate and gaps never produce energy`() {
        val charge = SolarEnergyAnalytics.dailyParts(SolarReading(0, 120.0, 1), SolarReading(5_000, 120.0, 1), 0, zone)
        assertTrue(charge.single().amount.chargedWh > 0)
        assertEquals(0.0, charge.single().amount.usedWh, 0.0)
        assertEquals(5_000, charge.single().amount.chargingMs)
        assertTrue(SolarEnergyAnalytics.dailyParts(SolarReading(0, -120.0, 1), SolarReading(60_000, -120.0, 1), 0, zone).isEmpty())
        assertTrue(SolarEnergyAnalytics.dailyParts(SolarReading(0, -120.0, 1), SolarReading(10_000, -120.0, 1, disconnectBefore = true), 0, zone).isEmpty())
        assertTrue(SolarEnergyAnalytics.dailyParts(SolarReading(0, -120.0, 1), SolarReading(5_000, -120.0, 2), 0, zone).isEmpty())
    }

    @Test fun `crossing local midnight preserves energy and splits dates`() {
        val midnight = LocalDate.of(2026, 10, 15).atStartOfDay(zone).toInstant().toEpochMilli()
        val parts = SolarEnergyAnalytics.dailyParts(SolarReading(midnight - 5_000, -100.0, 1), SolarReading(midnight + 5_000, -100.0, 1), 0, zone)
        assertEquals(2, parts.size)
        assertEquals(10_000, parts.sumOf { it.amount.observedMs })
        assertEquals(100.0 * 10.0 / 3_600.0, parts.sumOf { it.amount.usedWh }, 1e-9)
        assertEquals(parts[0].amount.usedWh, parts[1].amount.usedWh, 1e-9)
    }

    @Test fun `tracking start clips first interval without fabricating earlier energy`() {
        val parts = SolarEnergyAnalytics.dailyParts(SolarReading(0, -100.0, 1), SolarReading(10_000, -100.0, 1), 4_000, zone)
        assertEquals(6_000, parts.single().amount.observedMs)
        assertEquals(100.0 * 6.0 / 3_600.0, parts.single().amount.usedWh, 1e-9)
    }

    @Test fun `direction change splits observed duration`() {
        val parts = SolarEnergyAnalytics.dailyParts(SolarReading(0, 100.0, 1), SolarReading(10_000, -100.0, 1), 0, zone)
        assertEquals(5_000, parts.single().amount.chargingMs)
        assertEquals(5_000, parts.single().amount.dischargingMs)
        assertEquals(10_000, parts.single().amount.observedMs)
    }

    @Test fun `direction change at midnight assigns duration to the correct day`() {
        val midnight = LocalDate.of(2026, 10, 15).atStartOfDay(zone).toInstant().toEpochMilli()
        val parts = SolarEnergyAnalytics.dailyParts(SolarReading(midnight - 5_000, 100.0, 1), SolarReading(midnight + 5_000, -100.0, 1), 0, zone)
        assertEquals(5_000, parts[0].amount.chargingMs)
        assertEquals(0, parts[0].amount.dischargingMs)
        assertEquals(0, parts[1].amount.chargingMs)
        assertEquals(5_000, parts[1].amount.dischargingMs)
    }

    @Test fun `spring daylight saving day is twenty three hours`() {
        val date = LocalDate.of(2026, 10, 4)
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val report = SolarReports.make(emptyList(), EnergySettings(start, zone.id, "AUD", 0.30, 1000.0), SolarPeriod.DAY, date, end)
        assertEquals(23L * 3_600_000L, report.expectedMs)
        assertEquals(0.0, report.coveragePct, 0.0)
        assertTrue(!report.hasData)
    }

    @Test fun `autumn daylight saving day is twenty five hours`() {
        val date = LocalDate.of(2026, 4, 5)
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val report = SolarReports.make(emptyList(), EnergySettings(start, zone.id, "AUD", 0.30, 1000.0), SolarPeriod.DAY, date, end)
        assertEquals(25L * 3_600_000L, report.expectedMs)
    }
}
