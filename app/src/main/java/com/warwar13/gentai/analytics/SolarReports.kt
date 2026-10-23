package com.warwar13.gentai.analytics

import com.warwar13.gentai.data.SolarDailyRollupEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

enum class SolarPeriod { DAY, MONTH, YEAR, ALL_TIME }

data class SolarChartBar(
    val label: String,
    val usedWh: Double,
    val chargedWh: Double,
    val observedMs: Long = 0,
    val inScope: Boolean = true,
    val startEpochMs: Long? = null,
    val endEpochMs: Long? = null,
    val expectedMs: Long = 0,
)

data class SolarReport(
    val period: SolarPeriod,
    val anchor: LocalDate,
    val amount: SolarEnergyAmount,
    val expectedMs: Long,
    val bars: List<SolarChartBar>,
) {
    val coveragePct: Double get() = if (expectedMs > 0) amount.observedMs * 100.0 / expectedMs else 0.0
    val hasData: Boolean get() = amount.observedMs > 0
    val missingMs: Long get() = (expectedMs - amount.observedMs).coerceAtLeast(0)
}

object SolarReports {
    fun make(
        days: List<SolarDailyRollupEntity>,
        settings: EnergySettings,
        period: SolarPeriod,
        anchor: LocalDate,
        nowMs: Long,
        hourly: List<SolarChartBar> = emptyList(),
    ): SolarReport {
        val start = when (period) {
            SolarPeriod.DAY -> anchor.atStartOfDay(settings.zone).toInstant().toEpochMilli()
            SolarPeriod.MONTH -> anchor.withDayOfMonth(1).atStartOfDay(settings.zone).toInstant().toEpochMilli()
            SolarPeriod.YEAR -> anchor.withDayOfYear(1).atStartOfDay(settings.zone).toInstant().toEpochMilli()
            SolarPeriod.ALL_TIME -> settings.trackingStartMs
        }
        val endExclusive = when (period) {
            SolarPeriod.DAY -> anchor.plusDays(1)
            SolarPeriod.MONTH -> anchor.withDayOfMonth(1).plusMonths(1)
            SolarPeriod.YEAR -> anchor.withDayOfYear(1).plusYears(1)
            SolarPeriod.ALL_TIME -> Instant.ofEpochMilli(nowMs).atZone(settings.zone).toLocalDate().plusDays(1)
        }.atStartOfDay(settings.zone).toInstant().toEpochMilli()
        val effectiveStart = maxOf(start, settings.trackingStartMs)
        val effectiveEnd = minOf(endExclusive, nowMs)
        val expected = (effectiveEnd - effectiveStart).coerceAtLeast(0)
        val firstDate = Instant.ofEpochMilli(effectiveStart).atZone(settings.zone).toLocalDate()
        val lastDate = if (effectiveEnd > effectiveStart) Instant.ofEpochMilli(effectiveEnd - 1).atZone(settings.zone).toLocalDate() else firstDate.minusDays(1)
        val selected = days.filter {
            val date = LocalDate.parse(it.localDate)
            date >= firstDate && date <= lastDate
        }
        val amount = selected.fold(SolarEnergyAmount()) { total, day ->
            total + SolarEnergyAmount(day.chargedWh, day.usedWh, day.observedMs, day.chargingMs, day.dischargingMs)
        }
        val byDate = selected.associateBy { LocalDate.parse(it.localDate) }
        val bars = when (period) {
            SolarPeriod.DAY -> hourly
            SolarPeriod.MONTH -> {
                val calendarStart = anchor.withDayOfMonth(1)
                val calendarEnd = calendarStart.plusMonths(1).minusDays(1)
                generateSequence(calendarStart) { it.plusDays(1) }.takeWhile { it <= calendarEnd }.map { date ->
                    val slotStart = date.atStartOfDay(settings.zone).toInstant().toEpochMilli()
                    val slotEnd = date.plusDays(1).atStartOfDay(settings.zone).toInstant().toEpochMilli()
                    val inScope = slotEnd > effectiveStart && slotStart < effectiveEnd
                    val row = byDate[date]
                    SolarChartBar(date.dayOfMonth.toString(), row?.usedWh ?: 0.0, row?.chargedWh ?: 0.0, row?.observedMs ?: 0, inScope, slotStart, slotEnd, (minOf(slotEnd, effectiveEnd) - maxOf(slotStart, effectiveStart)).coerceAtLeast(0))
                }.toList()
            }
            SolarPeriod.YEAR -> (1..12).map { monthNumber ->
                val month = YearMonth.of(anchor.year, monthNumber)
                val slotStart = month.atDay(1).atStartOfDay(settings.zone).toInstant().toEpochMilli()
                val slotEnd = month.plusMonths(1).atDay(1).atStartOfDay(settings.zone).toInstant().toEpochMilli()
                val inScope = slotEnd > effectiveStart && slotStart < effectiveEnd
                val rows = selected.filter { it.localDate.startsWith(month.toString()) }
                SolarChartBar(month.month.name.take(3), rows.sumOf { it.usedWh }, rows.sumOf { it.chargedWh }, rows.sumOf { it.observedMs }, inScope, slotStart, slotEnd, (minOf(slotEnd, effectiveEnd) - maxOf(slotStart, effectiveStart)).coerceAtLeast(0))
            }
            SolarPeriod.ALL_TIME -> if (lastDate < firstDate) emptyList() else (firstDate.year..lastDate.year).map { year ->
                val rows = selected.filter { it.localDate.startsWith("$year-") }
                val slotStart = LocalDate.of(year, 1, 1).atStartOfDay(settings.zone).toInstant().toEpochMilli()
                val slotEnd = LocalDate.of(year + 1, 1, 1).atStartOfDay(settings.zone).toInstant().toEpochMilli()
                SolarChartBar(year.toString(), rows.sumOf { it.usedWh }, rows.sumOf { it.chargedWh }, rows.sumOf { it.observedMs }, true, slotStart, slotEnd, (minOf(slotEnd, effectiveEnd) - maxOf(slotStart, effectiveStart)).coerceAtLeast(0))
            }
        }
        return SolarReport(period, anchor, amount, expected, bars)
    }
}
