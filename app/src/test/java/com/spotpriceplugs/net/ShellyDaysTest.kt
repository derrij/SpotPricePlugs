package com.spotpriceplugs.net

import org.junit.Assert.assertEquals
import org.junit.Test

/** Weekday masks are Tasmota-style: seven characters, index 0 = Sunday. */
class ShellyDaysTest {
    @Test fun gen1CountsFromMonday() {
        assertEquals("6", Shelly.gen1Days("1000000")) // Sunday
        assertEquals("0", Shelly.gen1Days("0100000")) // Monday
        assertEquals("01234", Shelly.gen1Days("0111110")) // working days
        assertEquals("0123456", Shelly.gen1Days("1111111"))
        assertEquals("", Shelly.gen1Days("0000000"))
    }

    @Test fun gen2UsesCronNames() {
        assertEquals("SUN", Shelly.cronDays("1000000"))
        assertEquals("THU,FRI", Shelly.cronDays("0000110"))
        assertEquals("SUN,MON,TUE,WED,THU,FRI,SAT", Shelly.cronDays("1111111"))
        assertEquals("", Shelly.cronDays("0000000"))
    }
}
