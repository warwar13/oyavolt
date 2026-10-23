package com.warwar13.gentai.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowRecordingAnalyticsTest {
    @Test fun classifiesChargingDischargingAndIdle() {
        assertEquals(FlowRecordingMode.CHARGING, FlowRecordingAnalytics.modeFor(0.16))
        assertEquals(FlowRecordingMode.DISCHARGING, FlowRecordingAnalytics.modeFor(-0.16))
        assertEquals(null, FlowRecordingAnalytics.modeFor(0.15))
    }

    @Test fun integratesSignedFlowAndLeavesGapOut() {
        val samples = listOf(
            FlowSample(0, 50.0, 10.0, 500.0, 50),
            FlowSample(10_000, 50.0, 10.0, 500.0, 51),
            FlowSample(60_000, 50.0, 10.0, 500.0, 52, gapFromPreviousMs = 50_000),
        )
        val totals = FlowRecordingAnalytics.summarize(samples)
        assertEquals(1.3889, totals.chargedWh, 0.001)
        assertEquals(0.0, totals.dischargedWh, 0.001)
        assertEquals(50_000L, totals.missingMs)
        assertTrue(totals.coveragePct < 30.0)
    }
}
