package com.spotpriceplugs.net

import org.junit.Assert.assertEquals
import org.junit.Test

class TasmotaJsonTest {
    private val q4 = "\"\"\"\""

    @Test fun emptySsid2FromOpenBeken() {
        val raw = "{\"SSId1\":\".\",\"SSId2\":$q4,\"TelePeriod\":300}"
        assertEquals(
            "{\"SSId1\":\".\",\"SSId2\":\"\",\"TelePeriod\":300}",
            Tasmota.repairJson(raw)
        )
    }

    @Test fun validJsonUntouched() {
        val raw = """{"a":"","b":"x\"y","c":["",""]}"""
        assertEquals(raw, Tasmota.repairJson(raw))
    }
}
