package com.spotpriceplugs.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenBekenTest {
    @Test fun dayFlagsStartWithSunday() {
        assertEquals(0x01, OpenBeken.dayFlags("1000000")) // Sunday
        assertEquals(0x02, OpenBeken.dayFlags("0100000")) // Monday
        assertEquals(0x40, OpenBeken.dayFlags("0000001")) // Saturday
        assertEquals(0x3e, OpenBeken.dayFlags("0111110")) // working days
        assertEquals(0x7f, OpenBeken.dayFlags("1111111"))
        assertEquals(0, OpenBeken.dayFlags("0000000"))
    }

    @Test fun rebootAfterWriteLosesSchedule() {
        val now = 1_000_000_000L
        // Written an hour ago, up for a day: still there.
        assertFalse(OpenBeken.bootedAfter(86_400, now - 3_600_000, now))
        // Written an hour ago, up for 35 seconds: gone.
        assertTrue(OpenBeken.bootedAfter(35, now - 3_600_000, now))
        // Never written by this version of the app.
        assertTrue(OpenBeken.bootedAfter(86_400, 0L, now))
    }

    @Test fun offsetAsTheFirmwareTakesIt() {
        assertEquals("2:00", OpenBeken.offsetText(7200))
        assertEquals("0:00", OpenBeken.offsetText(0))
        assertEquals("5:30", OpenBeken.offsetText(19800))
        assertEquals("-3:30", OpenBeken.offsetText(-12600))
    }

    private val block = "${OpenBeken.BEGIN}\r\nold line\r\n${OpenBeken.END}\r\n"

    @Test fun autoexecCreatedFromNothing() {
        assertEquals(
            "${OpenBeken.BEGIN}\r\na\r\nb\r\n${OpenBeken.END}\r\n",
            OpenBeken.mergeAutoexec("", listOf("a", "b"))
        )
    }

    @Test fun autoexecKeepsTheUsersLines() {
        val merged = OpenBeken.mergeAutoexec("startDriver ntp\n$block" + "setChannel 5 1\n", listOf("a"))
        assertEquals(
            "startDriver ntp\r\nsetChannel 5 1\r\n${OpenBeken.BEGIN}\r\na\r\n${OpenBeken.END}\r\n",
            merged
        )
        // Writing the same lines again changes nothing, so the flash is not touched.
        assertEquals(merged, OpenBeken.mergeAutoexec(merged, listOf("a")))
    }

    @Test fun autoexecBlockRemovedOnPause() {
        assertEquals("", OpenBeken.mergeAutoexec(block, emptyList()))
        assertEquals("mine\r\n", OpenBeken.mergeAutoexec("mine\r\n$block", emptyList()))
        assertEquals("", OpenBeken.mergeAutoexec("", emptyList()))
    }

    @Test fun recognisedByFirmwareVersion() {
        assertTrue(Tasmota.isOpenBeken("OpenBL602_1.18.247", "obk"))
        assertTrue(Tasmota.isOpenBeken("OpenBK7231N_1.17.551", ""))
        assertFalse(Tasmota.isOpenBeken("13.2.0(tasmota)", "2.7.4.9(2.7.4)"))
        assertFalse(Tasmota.isOpenBeken("", ""))
    }
}
