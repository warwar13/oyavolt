package com.warwar13.gentai.ui.main

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRangeTest {
    @Test fun `history presets start at calendar boundaries in reporting zone`() {
        val zone = ZoneId.of("Australia/Sydney")
        val now = LocalDate.of(2026, 10, 19).atTime(16, 30).atZone(zone).toInstant().toEpochMilli()
        val today = LocalDate.of(2026, 10, 19)
        assertEquals(today.atStartOfDay(zone).toInstant().toEpochMilli(), HistoryRange.DAY.window(now, zone).startEpochMs)
        assertEquals(today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli(), HistoryRange.WEEK.window(now, zone).startEpochMs)
        assertEquals(today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli(), HistoryRange.MONTH.window(now, zone).startEpochMs)
        HistoryRange.entries.forEach { range ->
            val window = range.window(now, zone)
            assertEquals(now, window.endEpochMs)
            assertTrue((window.endEpochMs - window.startEpochMs + 1) / window.bucketMs <= 720)
        }
    }

    @Test fun `today respects daylight saving boundary`() {
        val zone = ZoneId.of("Australia/Sydney")
        val date = LocalDate.of(2026, 10, 4)
        val now = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val window = HistoryRange.DAY.window(now, zone)
        assertEquals(date.atStartOfDay(zone).toInstant().toEpochMilli(), window.startEpochMs)
        assertEquals(23 * 60 * 60 * 1000L, now - window.startEpochMs + 1)
    }

    @Test fun `signed graph omits mixed direction and connection gap buckets`() {
        assertTrue(historyBucketAllowed(0, 1, 3, 0, HistoryDirectionFilter.CHARGING))
        assertTrue(historyBucketAllowed(0, 1, 0, 3, HistoryDirectionFilter.DISCHARGING))
        assertFalse(historyBucketAllowed(0, 1, 3, 3, HistoryDirectionFilter.ALL))
        assertFalse(historyBucketAllowed(1, 1, 3, 0, HistoryDirectionFilter.ALL))
        assertFalse(historyBucketAllowed(0, 2, 3, 0, HistoryDirectionFilter.ALL))
        assertFalse(historyBucketAllowed(0, 1, 0, 3, HistoryDirectionFilter.CHARGING))
    }


    @Test fun `selected picker dates cover complete local calendar days`() {
        val zone = ZoneId.of("Australia/Sydney")
        val pickerStart = LocalDate.of(2026, 9, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerEnd = LocalDate.of(2026, 9, 3).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val window = selectedHistoryDatesWindow(pickerStart, pickerEnd, zone)

        assertEquals(LocalDate.of(2026, 9, 1).atStartOfDay(zone).toInstant().toEpochMilli(), window.startEpochMs)
        assertEquals(LocalDate.of(2026, 9, 4).atStartOfDay(zone).toInstant().toEpochMilli() - 1, window.endEpochMs)
        assertTrue((window.endEpochMs - window.startEpochMs + 1) / window.bucketMs <= 720)
    }
}
