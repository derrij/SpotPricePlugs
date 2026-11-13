package com.spotpriceplugs.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class TasmotaTimeTest {
    private val expected = LocalDateTime.parse("2026-11-05T09:10:11")

    @Test fun isoFormat() = assertEquals(expected, Tasmota.parseDeviceTime("2026-11-05T09:10:11"))

    @Test fun isoWithExtras() {
        assertEquals(expected, Tasmota.parseDeviceTime("2026-11-05T09:10:11Z"))
        assertEquals(expected, Tasmota.parseDeviceTime("2026-11-05T09:10:11.123"))
        assertEquals(expected, Tasmota.parseDeviceTime("2026-11-05T09:10:11+01:00"))
    }

    @Test fun legacyFormat() {
        assertEquals(expected, Tasmota.parseDeviceTime("Thu Nov 05 09:10:11 2026"))
        assertEquals(expected, Tasmota.parseDeviceTime("Thu Nov  5 09:10:11 2026"))
    }

    @Test fun unsetClockStillParses() =
        assertEquals(1970, Tasmota.parseDeviceTime("1970-01-01T00:00:42")?.year)

    @Test fun garbage() {
        assertNull(Tasmota.parseDeviceTime(""))
        assertNull(Tasmota.parseDeviceTime("soon"))
    }
}
