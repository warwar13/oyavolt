package com.warwar13.gentai.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BmsAlertPolicyTest {
    @Test fun `only newly active BMS protection warnings alert`() {
        val tracker = BmsAlertTracker()
        assertEquals(
            listOf(BmsAlert.ProtectionWarnings(listOf("Cell undervoltage protection"))),
            tracker.update(80, listOf("Cell undervoltage protection")),
        )
        assertTrue(tracker.update(80, listOf("Cell undervoltage protection")).isEmpty())
        assertEquals(
            listOf(BmsAlert.ProtectionWarnings(listOf("Charge overcurrent protection"))),
            tracker.update(80, listOf("Cell undervoltage protection", "Charge overcurrent protection")),
        )
    }

    @Test fun `missing BMS status does not create a local alert`() {
        val tracker = BmsAlertTracker()
        tracker.update(80, listOf("Pack overvoltage protection"))
        assertTrue(tracker.update(80, null, currentA = 5.0, cellsV = listOf(3.4, 3.7), voltageV = 14.3).isEmpty())
        assertTrue(tracker.update(80, listOf("Pack overvoltage protection")).isEmpty())
        tracker.update(80, emptyList())
        assertEquals(1, tracker.update(80, listOf("Pack overvoltage protection")).size)
    }

    @Test fun `local SOC full charge and cell thresholds never create alerts`() {
        val tracker = BmsAlertTracker()
        val redCells = listOf(3.50, 3.50, 3.50, 3.61)
        repeat(3) {
            assertTrue(tracker.update(20, emptyList(), currentA = 2.0, cellsV = redCells, nowEpochMs = it * 5_000L, voltageV = 14.2).isEmpty())
        }
    }

    @Test fun `charging cell status remains a display measurement only`() {
        assertEquals(ChargingCellSeverity.GREEN, ChargingCellPolicy.status(listOf(3.50, 3.51, 3.52, 3.54))?.severity)
        assertEquals(ChargingCellSeverity.YELLOW, ChargingCellPolicy.status(listOf(3.54, 3.54, 3.54, 3.55))?.severity)
        assertEquals(ChargingCellSeverity.RED, ChargingCellPolicy.status(listOf(3.54, 3.54, 3.54, 3.60))?.severity)
    }

    @Test fun `BMS warning conversion keeps exact source text`() {
        val events = listOf(BmsAlert.ProtectionWarnings(listOf("Pack overvoltage protection")))
            .toHistoryEvents(socPct = 84, nowEpochMs = 1234)
        assertEquals(1, events.size)
        assertEquals("BMS_PROTECTION", events.single().type)
        assertEquals("Pack overvoltage protection", events.single().message)
        assertEquals(84, events.single().socPct)
    }

    @Test fun `local alert event types are not converted to history`() {
        val events = listOf(
            BmsAlert.LowSoc(20),
            BmsAlert.FullCharge(14.2, 100, false),
            BmsAlert.ChargingCellRisk(4, 3.61, 0.08, false),
        ).toHistoryEvents(socPct = 100, nowEpochMs = 1234)
        assertTrue(events.isEmpty())
    }

    @Test fun `SOC alerts fire only while discharging and only once per crossing`() {
        val tracker = SocThresholdAlertTracker()

        assertTrue(tracker.update(26, currentA = 2.0).isEmpty())
        assertEquals(listOf(SocThresholdAlert(25, 25)), tracker.update(25, currentA = -2.0))
        assertTrue(tracker.update(24, currentA = -2.0).isEmpty())
        assertEquals(listOf(SocThresholdAlert(20, 20)), tracker.update(20, currentA = -2.0))
        assertTrue(tracker.update(19, currentA = -2.0).isEmpty())
    }

    @Test fun `a jump below both thresholds emits both warnings`() {
        val tracker = SocThresholdAlertTracker()

        assertEquals(
            listOf(SocThresholdAlert(25, 19), SocThresholdAlert(20, 19)),
            tracker.update(19, currentA = -2.0),
        )
    }

    @Test fun `SOC threshold re-arms only after rising three points above it`() {
        val tracker = SocThresholdAlertTracker()

        assertEquals(listOf(SocThresholdAlert(25, 25)), tracker.update(25, currentA = -2.0))
        assertTrue(tracker.update(27, currentA = -2.0).isEmpty())
        assertTrue(tracker.update(24, currentA = -2.0).isEmpty())
        assertTrue(tracker.update(28, currentA = 1.0).isEmpty())
        assertEquals(listOf(SocThresholdAlert(25, 25)), tracker.update(25, currentA = -2.0))
    }

    @Test fun `idle or charging at low SOC does not consume the next discharging warning`() {
        val tracker = SocThresholdAlertTracker()

        assertTrue(tracker.update(20, currentA = 0.0).isEmpty())
        assertEquals(listOf(SocThresholdAlert(25, 20), SocThresholdAlert(20, 20)), tracker.update(20, currentA = -2.0))
    }
}
