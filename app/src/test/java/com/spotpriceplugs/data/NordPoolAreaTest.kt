package com.spotpriceplugs.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NordPoolAreaTest {
    @Test fun germanyUsesApiCode() = assertEquals("GER", NordPool.apiArea("DE"))

    @Test fun othersUnchanged() {
        for (a in listOf("LV", "SE3", "NO1", "AT", "BE", "NL", "FR", "PL")) {
            assertEquals(a, NordPool.apiArea(a))
        }
    }
}
