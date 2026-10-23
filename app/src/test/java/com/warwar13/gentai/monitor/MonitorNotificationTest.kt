package com.warwar13.gentai.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MonitorNotificationTest {
    @Test fun `connected notification does not expose live SOC or wattage`() {
        val text = MonitorState.Connected(
            voltageV = 14.2,
            currentA = -7.0,
            socPct = 24,
            livePowerW = 99.4,
        ).notificationText()

        assertEquals("Monitoring battery", text)
        assertFalse(text.contains("%"))
        assertFalse(text.contains("W"))
    }

    @Test fun `connection and failure states remain visible`() {
        assertEquals("Connecting to battery", MonitorState.Connecting.notificationText())
        assertEquals("Bluetooth is off; waiting to reconnect", MonitorState.BluetoothUnavailable.notificationText())
        assertEquals("BMS unavailable", MonitorState.Failed("BMS unavailable").notificationText())
    }

    @Test fun `connected notification text is stable across live readings`() {
        val first = MonitorState.Connected(14.2, -7.0, socPct = 40, livePowerW = 99.4).notificationText()
        val second = MonitorState.Connected(14.2, -8.0, socPct = 39, livePowerW = 113.6).notificationText()
        assertEquals(first, second)
    }
}
