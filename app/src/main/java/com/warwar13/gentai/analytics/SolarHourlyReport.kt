package com.warwar13.gentai.analytics

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Display buckets only. Uses the same interval integration and gap rules as daily rollups. */
object SolarHourlyReport {
    fun make(readings: List<SolarReading>, date: LocalDate, settings: EnergySettings, nowMs: Long): List<SolarChartBar> {
        val dayStart = date.atStartOfDay(settings.zone)
        val dayEnd = date.plusDays(1).atStartOfDay(settings.zone)
        val startMs = dayStart.toInstant().toEpochMilli()
        val endMs = minOf(dayEnd.toInstant().toEpochMilli(), nowMs)
        val effectiveStart = maxOf(startMs, settings.trackingStartMs)
        val hours = sortedMapOf<Long, SolarEnergyAmount>()
        readings.sortedBy { it.timestampMs }.zipWithNext().forEach { (a, b) ->
            if (b.timestampMs <= nowMs) {
                SolarEnergyAnalytics.hourlyParts(a, b, settings.trackingStartMs, settings.zone).forEach { part ->
                    if (part.startMs in startMs until endMs) {
                        val hour = Instant.ofEpochMilli(part.startMs).atZone(settings.zone).truncatedTo(ChronoUnit.HOURS).toInstant().toEpochMilli()
                        hours[hour] = (hours[hour] ?: SolarEnergyAmount()) + part.amount
                    }
                }
            }
        }
        val slots = generateSequence(dayStart) { it.plusHours(1) }.takeWhile { it < dayEnd }.toList()
        val clock = DateTimeFormatter.ofPattern("HH:mm")
        val repeated = slots.groupingBy { it.format(clock) }.eachCount()
        return slots.map { hour ->
            val start = hour.toInstant().toEpochMilli()
            val end = hour.plusHours(1).toInstant().toEpochMilli()
            val amount = hours[start] ?: SolarEnergyAmount()
            val expected = (minOf(end, endMs) - maxOf(start, effectiveStart)).coerceAtLeast(0)
            val label = hour.format(clock).let { if (repeated[it]!! > 1) "$it ${hour.offset}" else it }
            SolarChartBar(label, amount.usedWh, amount.chargedWh, amount.observedMs, expected > 0, start, end, expected)
        }
    }
}
