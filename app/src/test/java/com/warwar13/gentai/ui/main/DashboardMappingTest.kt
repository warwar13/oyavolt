package com.warwar13.gentai.ui.main

import com.warwar13.gentai.data.TelemetrySampleEntity
import com.warwar13.gentai.analytics.TrendBucket
import com.warwar13.gentai.monitor.MonitorState
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardMappingTest {
    @Test fun `soc gauge severity follows charge thresholds`() {
        assertEquals(GaugeSeverity.CRITICAL, socGaugeSeverity(20))
        assertEquals(GaugeSeverity.WARNING, socGaugeSeverity(21))
        assertEquals(GaugeSeverity.WARNING, socGaugeSeverity(40))
        assertEquals(GaugeSeverity.GOOD, socGaugeSeverity(41))
    }

    @Test fun `gauge progress clamps and rejects invalid ranges`() {
        assertEquals(0f, normalizeGaugeValue(5.0, 10.0, 20.0), 0f)
        assertEquals(0.5f, normalizeGaugeValue(15.0, 10.0, 20.0), 0f)
        assertEquals(1f, normalizeGaugeValue(25.0, 10.0, 20.0), 0f)
        assertEquals(0f, normalizeGaugeValue(Double.NaN, 10.0, 20.0), 0f)
        assertEquals(0f, normalizeGaugeValue(10.0, 20.0, 20.0), 0f)
    }

    @Test fun `dashboard mapping preserves signed current mode and cell values`() {
        val sample = TelemetrySampleEntity(timestampEpochMs = 1, connectionSessionId = 1, voltageV = 12.8, currentA = -4.5, powerW = -57.6, socPct = 50, sohPct = 99, remainingAh = 50.0, fullAh = 100.0, cycleCount = 1, cellVoltagesV = "3.20,3.21", cellMinV = 3.2, cellMaxV = 3.21, cellDeltaV = .01, probeTemperaturesC = "20,21", ambientTemperatureC = 19, mosTemperatureC = 25)
        assertEquals("discharging", sample.displayMode())
        assertEquals(listOf(3.2, 3.21), sample.cells())
        assertEquals(listOf(20, 21), sample.probes())
    }

    @Test fun `dashboard mode follows signed current`() {
        val base = TelemetrySampleEntity(timestampEpochMs = 1, connectionSessionId = 1, voltageV = 12.8, currentA = 0.0, powerW = 0.0, socPct = 50, sohPct = 99, remainingAh = 50.0, fullAh = 100.0, cycleCount = 1, cellVoltagesV = "", cellMinV = null, cellMaxV = null, cellDeltaV = null, probeTemperaturesC = "", ambientTemperatureC = 20, mosTemperatureC = 20)
        assertEquals("charging", base.copy(currentA = 1.0, powerW = 12.8).displayMode())
        assertEquals("discharging", base.copy(currentA = -1.0, powerW = -12.8).displayMode())
        assertEquals("idle", base.copy(currentA = 0.1, powerW = 1.28).displayMode())
    }

    @Test fun `cell colors follow charging safety thresholds`() {
        assertEquals(
            listOf(CellBalanceState.BALANCED, CellBalanceState.BALANCED, CellBalanceState.BALANCED, CellBalanceState.BALANCED),
            cellBalanceStates(listOf(3.287, 3.288, 3.289, 3.286)),
        )
        assertEquals(
            listOf(CellBalanceState.BALANCED, CellBalanceState.BALANCED, CellBalanceState.BALANCED, CellBalanceState.DIFFERENT),
            cellBalanceStates(listOf(3.54, 3.54, 3.54, 3.55)),
        )
        assertEquals(
            listOf(CellBalanceState.BALANCED, CellBalanceState.BALANCED, CellBalanceState.BALANCED, CellBalanceState.DIFFERENT),
            cellBalanceStates(listOf(3.49, 3.49, 3.49, 3.54)),
        )
        assertEquals(CellBalanceState.ALERT, cellBalanceStates(listOf(3.47, 3.47, 3.47, 3.54)).last())
        assertEquals(CellBalanceState.ALERT, cellBalanceStates(listOf(3.61, 3.54, 3.54, 3.54)).first())
    }
    @Test fun `fresh telemetry can recover connected presentation after activity state reset`() {
        val now = 100_000L
        assertEquals(
            true,
            isEffectivelyConnected(
                connection = MonitorState.Idle,
                monitoringRequested = true,
                latestTimestampEpochMs = now - 5_000L,
                nowEpochMs = now,
            ),
        )
    }

    @Test fun `stale telemetry never pretends monitoring is connected`() {
        val now = 100_000L
        assertEquals(
            false,
            isEffectivelyConnected(
                connection = MonitorState.Idle,
                monitoringRequested = true,
                latestTimestampEpochMs = now - LIVE_TELEMETRY_FRESH_MS - 1,
                nowEpochMs = now,
            ),
        )
    }

    @Test fun `reconnecting state is not hidden by a recent sample`() {
        val now = 100_000L
        assertEquals(
            false,
            isEffectivelyConnected(
                connection = MonitorState.Reconnecting(5_000L, "lost"),
                monitoringRequested = true,
                latestTimestampEpochMs = now - 1_000L,
                nowEpochMs = now,
            ),
        )
    }

    @Test fun `home flow animates only for fresh live directional readings`() {
        val now = 100_000L
        assertEquals(true, shouldAnimateHomeFlow("charging", true, now - 5_000L, now))
        assertEquals(true, shouldAnimateHomeFlow("discharging", true, now - LIVE_TELEMETRY_FRESH_MS, now))
        assertEquals(false, shouldAnimateHomeFlow("idle", true, now - 1_000L, now))
        assertEquals(false, shouldAnimateHomeFlow("charging", false, now - 1_000L, now))
        assertEquals(false, shouldAnimateHomeFlow("discharging", true, now - LIVE_TELEMETRY_FRESH_MS - 1L, now))
    }

    @Test fun `home presentation never shows an active path for reconnecting or stale readings`() {
        val now = 100_000L
        assertEquals(HomeFlowPresentation.CHARGING, homeFlowPresentation("charging", true, false, now - 5_000L, now))
        assertEquals(HomeFlowPresentation.DISCHARGING, homeFlowPresentation("discharging", true, false, now - 5_000L, now))
        assertEquals(HomeFlowPresentation.IDLE, homeFlowPresentation("idle", true, false, now - 5_000L, now))
        assertEquals(HomeFlowPresentation.RECONNECTING, homeFlowPresentation("discharging", false, true, now - 5_000L, now))
        assertEquals(HomeFlowPresentation.LAST_RECORDED, homeFlowPresentation("charging", true, false, now - 21_000L, now))
        assertEquals(HomeFlowPresentation.LAST_RECORDED, homeFlowPresentation("charging", false, false, now - 5_000L, now))
    }

    @Test fun `new sample stays live before freshness clock catches up`() {
        val previousClockTick = 100_000L
        val newSampleTimestamp = previousClockTick + 5_000L
        assertEquals(HomeFlowPresentation.CHARGING, homeFlowPresentation("charging", true, false, newSampleTimestamp, previousClockTick))
        assertEquals(HomeFlowPresentation.DISCHARGING, homeFlowPresentation("discharging", true, false, newSampleTimestamp, previousClockTick))
        assertEquals(HomeFlowPresentation.IDLE, homeFlowPresentation("idle", true, false, newSampleTimestamp, previousClockTick))
        assertEquals(HomeFlowPresentation.LAST_RECORDED, homeFlowPresentation("charging", false, false, newSampleTimestamp, previousClockTick))
    }

    @Test fun `fixed cell deviation scale leaves small healthy differences near center`() {
        val average = listOf(3.310, 3.310, 3.311, 3.308).average()
        assertEquals(true, cellOffsetFraction(3.308, average) > -0.1f)
        assertEquals(true, cellOffsetFraction(3.311, average) < 0.1f)
        assertEquals(1f, cellOffsetFraction(3.40, 3.30), 0f)
    }

    @Test fun `soc trend uses a readable local range`() {
        val bounds = trendBounds(
            listOf(
                TrendBucket(0, 1, 0, 83.0, 83.0, 84.0, 1, false, emptyList()),
                TrendBucket(1, 2, 1, 99.0, 98.0, 100.0, 1, false, emptyList()),
            ),
            TrendScale.SOC,
        )
        assertEquals(80.0, bounds.minimum, 0.0)
        assertEquals(100.0, bounds.maximum, 0.0)
    }

}
