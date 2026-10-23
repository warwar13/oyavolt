package com.warwar13.gentai.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadableTrendTest {
    @Test fun `noisy five-second samples become a small readable trend`() {
        val samples = (0 until 1_440).map { index ->
            TrendReading(index * 5_000L, if (index % 2 == 0) 100.0 else 140.0)
        }
        val result = ReadableTrend.reduce(samples, 0, samples.last().timestampMs)
        assertTrue(result.size in 40..50)
        assertEquals(samples.size, result.sumOf { it.sampleCount })
        assertTrue(result.all { it.average in 115.0..125.0 })
    }

    @Test fun `brief dip stays in range while sustained drop changes line`() {
        val samples = (0 until 120).map { index ->
            val watts = when {
                index == 12 -> 8.0
                index in 60..89 -> 40.0
                else -> 100.0
            }
            TrendReading(index * 5_000L, watts)
        }
        val result = ReadableTrend.reduce(samples, 0, samples.last().timestampMs)
        assertTrue(result.any { it.minimum == 8.0 && it.average > 50.0 })
        assertTrue(result.any { it.average == 40.0 })
        assertEquals(8.0, result.minOf { it.minimum }, 0.0)
    }

    @Test fun `marked gap and long missing period never join buckets`() {
        val samples = listOf(
            TrendReading(0, 100.0), TrendReading(5_000, 100.0),
            TrendReading(10_000, 90.0, gapBefore = true), TrendReading(15_000, 90.0),
            TrendReading(90_000, 80.0),
        )
        val result = ReadableTrend.reduce(samples, 0, 90_000)
        assertEquals(5, result.size)
        assertFalse(result[1].gapBefore)
        assertTrue(result[2].gapBefore)
        assertTrue(result[4].gapBefore)
    }

    @Test fun `source summary ranges and counts survive display aggregation`() {
        val result = ReadableTrend.reduce(
            listOf(
                TrendReading(0, 120.0, minimum = 10.0, maximum = 180.0, sampleCount = 10, exact = false),
                TrendReading(10_000, 100.0, minimum = 60.0, maximum = 120.0, sampleCount = 30, exact = false),
            ), 0, 600_000, maxContinuousGapMs = null,
        )
        assertEquals(1, result.size)
        assertEquals(105.0, result.single().average, 0.001)
        assertEquals(10.0, result.single().minimum, 0.0)
        assertEquals(180.0, result.single().maximum, 0.0)
        assertEquals(40, result.single().sampleCount)
        assertEquals(null, result.single().nearestExact(0))
    }

    @Test fun `a gap inside one display interval still splits the line`() {
        val result = ReadableTrend.reduce(
            listOf(
                TrendReading(0, 100.0),
                TrendReading(5_000, 100.0),
                TrendReading(10_000, 30.0, gapBefore = true),
                TrendReading(15_000, 30.0),
            ), 0, 600_000,
        )
        assertEquals(2, result.size)
        assertEquals(100.0, result[0].average, 0.0)
        assertEquals(30.0, result[1].average, 0.0)
        assertTrue(result[1].gapBefore)
    }

    @Test fun `long seven-hour run stays near the target point count`() {
        val samples = (0..5_040).map { TrendReading(it * 5_000L, 80.0) }
        val result = ReadableTrend.reduce(samples, 0, samples.last().timestampMs)
        assertTrue(result.size in 45..49)
        assertEquals(samples.size, result.sumOf { it.sampleCount })
        assertEquals(80.0, result.last().nearestExact(result.last().endMs)?.value ?: 0.0, 0.0)
    }

    @Test fun `day viewport can retain reference style detail`() {
        val dayMs = 24 * 60 * 60 * 1000L
        val samples = (0 until 2_880).map { index -> TrendReading(index * 30_000L, index.toDouble()) }
        val result = ReadableTrend.reduce(samples, 0, dayMs - 1, targetBuckets = 144)
        assertTrue(result.size in 140..144)
        assertEquals(samples.size, result.sumOf { it.sampleCount })
    }
}
