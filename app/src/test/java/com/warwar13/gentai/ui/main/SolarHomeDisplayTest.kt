package com.warwar13.gentai.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SolarHomeDisplayTest {
    @Test fun `energy display separates missing measurement from measured zero`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("N/A", homeKwh(0.0, false))
            assertEquals("0.000 kWh", homeKwh(0.0, true))
            assertEquals("<0.001 kWh", homeKwh(0.1, true))
            assertEquals("0.189 kWh", homeKwh(189.0, true))
            assertEquals("12.35 kWh", homeKwh(12_345.0, true))
            assertEquals("123.5 kWh", homeKwh(123_456.0, true))
        } finally {
            Locale.setDefault(original)
        }
    }
}
