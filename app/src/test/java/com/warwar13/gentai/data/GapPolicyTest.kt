package com.warwar13.gentai.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GapPolicyTest {
    @Test fun `only intervals exceeding thirty seconds are marked gaps`() {
        assertNull((30_000L).takeIf { it > GentaiTelemetryRepository.GAP_THRESHOLD_MS })
        assertEquals(30_001L, (30_001L).takeIf { it > GentaiTelemetryRepository.GAP_THRESHOLD_MS })
    }
}
