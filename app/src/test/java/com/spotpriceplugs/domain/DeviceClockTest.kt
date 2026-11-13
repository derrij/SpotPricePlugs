package com.spotpriceplugs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DeviceClockTest {

    @Test fun rotateMovesDays() {
        assertEquals("0100000", DeviceClock.rotate("1000000", 1))
        assertEquals("0000001", DeviceClock.rotate("1000000", -1))
        assertEquals("1000001", DeviceClock.rotate("1100000", -1))
        assertEquals("1111101", DeviceClock.rotate("1111101", 7))
        assertEquals("1111111", DeviceClock.rotate("1111111", 3))
    }

    @Test fun offsetFromLocalAndUtc() {
        val utc = Instant.parse("2026-11-05T09:10:11Z")
        assertEquals(7200, DeviceClock.offsetSeconds(LocalDateTime.parse("2026-11-05T11:10:11"), utc))
        assertEquals(3600, DeviceClock.offsetSeconds(LocalDateTime.parse("2026-11-05T10:10:40"), utc))
        assertEquals(0, DeviceClock.offsetSeconds(LocalDateTime.parse("2026-11-05T09:12:00"), utc))
        assertEquals(-5 * 3600, DeviceClock.offsetSeconds(LocalDateTime.parse("2026-11-05T04:10:11"), utc))
    }

    @Test fun unsetClockIsRejected() =
        assertNull(DeviceClock.offsetSeconds(LocalDateTime.parse("1970-01-01T00:01:00"), Instant.parse("1970-01-01T00:01:00Z")))

    @Test fun atConvertsToDeviceTime() {
        // Riga in winter is UTC+2; a Tasmota on the default Timezone 1 is UTC+1.
        val t = ZonedDateTime.of(2026, 11, 5, 14, 30, 0, 0, ZoneId.of("Europe/Riga"))
        val at = DeviceClock.at(t, "0000100", 3600)
        assertEquals("13:30", at.hhmm)
        assertEquals("0000100", at.mask)
    }

    @Test fun atMovesMaskAcrossMidnight() {
        // 00:30 Friday in Riga is 23:30 Thursday on a UTC+1 plug.
        val t = ZonedDateTime.of(2026, 11, 6, 0, 30, 0, 0, ZoneId.of("Europe/Riga"))
        val at = DeviceClock.at(t, "0000010", 3600)
        assertEquals("23:30", at.hhmm)
        assertEquals("0000100", at.mask)
    }

    @Test fun plugInThePhonesZoneFollowsItsClockChange() {
        val riga = ZoneId.of("Europe/Riga")
        // Measured on Saturday 28 March 2026 (UTC+2); clocks go forward that night.
        val measured = Instant.parse("2026-03-28T12:00:00Z")
        val zone = DeviceClock.zoneOf(7200, riga, measured)
        assertEquals(true, zone.followsPhone)
        // 05:00 on Sunday is already summer time: the plug's clock will say 05:00 too.
        val t = ZonedDateTime.of(2026, 3, 29, 5, 0, 0, 0, riga)
        assertEquals("05:00", DeviceClock.at(t, "1000000", zone).hhmm)
        // A fixed-offset plug stays on UTC+2 and needs 04:00.
        val fixed = DeviceClock.zoneOf(7200, riga, measured, observesDst = false)
        assertEquals(false, fixed.followsPhone)
        assertEquals("04:00", DeviceClock.at(t, "1000000", fixed).hhmm)
    }

    @Test fun plugInAnotherZoneKeepsItsOffset() {
        val riga = ZoneId.of("Europe/Riga")
        val zone = DeviceClock.zoneOf(3600, riga, Instant.parse("2026-11-05T09:00:00Z"))
        assertEquals(false, zone.followsPhone)
        val t = ZonedDateTime.of(2026, 11, 5, 14, 30, 0, 0, riga)
        assertEquals("13:30", DeviceClock.at(t, "0000100", zone).hhmm)
        assertEquals(false, DeviceClock.zoneOf(null, riga, Instant.parse("2026-11-05T09:00:00Z")).followsPhone)
    }

    @Test fun unknownOffsetKeepsPhoneTime() {
        val t = ZonedDateTime.of(2026, 11, 5, 7, 5, 0, 0, ZoneId.of("Europe/Riga"))
        val at = DeviceClock.at(t, "1111111", null)
        assertEquals("07:05", at.hhmm)
        assertEquals("0705", at.hhmmCompact)
    }
}
