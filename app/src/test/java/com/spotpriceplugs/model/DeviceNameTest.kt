package com.spotpriceplugs.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceNameTest {
    private data class Parsed(val region: String?, val hours: Double?, val watts: Double?, val limit: Double?)

    private fun parse(name: String) = Parsed(
        DeviceName.region(name), DeviceName.hours(name), DeviceName.watts(name), DeviceName.limit(name)
    )

    @Test fun shortForm() =
        assertEquals(Parsed("DE", 4.0, 2000.0, 0.20), parse("Boiler DE 4h 2000W max0.20"))

    @Test fun legacyUnderscoreForm() =
        assertEquals(Parsed("LV", 4.0, 1500.0, 0.20), parse("Scooter_LV_4_hours_1500W_on_limit_0.20"))

    @Test fun legacyWithoutWatts() =
        assertEquals(Parsed("LV", 4.0, null, 0.99), parse("Scooter_LV_4_hours_on_limit_0.99"))

    @Test fun anyOrderAndCase() =
        assertEquals(Parsed("SE3", 2.5, 1500.0, 0.15), parse("max 0,15 heater 1.5kW se3 2.5H"))

    @Test fun thresholdAndLessThan() {
        assertEquals(0.3, DeviceName.limit("Pump EE 3h threshold_0.30"))
        assertEquals(0.25, DeviceName.limit("Pump EE <0.25"))
        assertEquals(0.25, DeviceName.limit("Pump EE ≤0,25"))
    }

    @Test fun russianHours() = assertEquals(6.0, DeviceName.hours("Бойлер LV 6ч"))

    @Test fun hyphens() =
        assertEquals(Parsed("NO1", 8.0, 500.0, 1.0), parse("Car-NO1-8h-500W-max1"))

    @Test fun wordsDoNotLeak() {
        // "2 heaters" is not hours, "Latvia" is not an area, "Slim" is not a price cap.
        assertNull(DeviceName.hours("2 heaters"))
        assertNull(DeviceName.region("Latvia plug"))
        assertNull(DeviceName.limit("Slim 2"))
        assertNull(DeviceName.watts("Wall"))
    }

    @Test fun lowerCaseWordsAreNotAreas() {
        assertEquals("LV", DeviceName.region("Pump at garage LV 4h"))
        assertNull(DeviceName.region("Pump at garage 4h"))
        assertNull(DeviceName.region("Heater be quiet"))
        assertNull(DeviceName.region("Lampe de salon"))
        assertNull(DeviceName.region("Pool pl fr nl de"))
        assertEquals("Pump at garage", DeviceName.baseName("Pump at garage LV 4h"))
    }

    @Test fun upperCaseAreasStillWork() {
        assertEquals("DE", DeviceName.region("Scooter_DE_4_hours_limit_0.20"))
        assertEquals("DE", DeviceName.region("TeslaCharger DE 4h 3000W max0.20"))
        assertEquals("AT", DeviceName.region("Boiler AT 3h"))
        // Codes with a digit cannot be words, so any case is accepted.
        assertEquals("NO2", DeviceName.region("pump no2 3h"))
        assertEquals("DK1", DeviceName.region("pump-dk1-3h"))
    }

    @Test fun pricesAreNotHours()= assertNull(DeviceName.hours("Boiler DE max0.20"))

    @Test fun baseNameStripsParameters() {
        assertEquals("Scooter", DeviceName.baseName("Scooter_LV_4_hours_on_limit_0.99"))
        assertEquals("ScooterCharger", DeviceName.baseName("ScooterCharger DE 4h 55W max0.99"))
        assertEquals("Hot water tank", DeviceName.baseName("Hot water tank SE3 2.5h 1.5kW <0.15"))
        assertEquals("", DeviceName.baseName("DE 4h"))
    }

    @Test fun composeShortForm() {
        assertEquals("Boiler DE 4h 2000W max0.20", DeviceName.compose("Boiler", "de", 4.0, 2000.0, 0.2))
        assertEquals("Plug LV 2.5h max0.15", DeviceName.compose("", "LV", 2.5, 0.0, 0.15))
    }

    @Test fun composedNameParsesBack() {
        val name = DeviceName.compose("Scooter", "NO1", 0.75, 55.0, 0.99)
        assertEquals(Parsed("NO1", 0.75, 55.0, 0.99), parse(name))
        assertEquals("Scooter", DeviceName.baseName(name))
    }

    @Test fun displayNameHidesParameters() {
        fun plug(name: String) = Plug("10.0.2.2", name, "", 4.0, 0.2)
        assertEquals("Scooter", plug("Scooter_LV_4_hours_on_limit_0.99").displayName)
        assertEquals("TeslaCharger", plug("TeslaCharger DE 4h 3000W max0.20").displayName)
        // Only parameters in the name: show the IP instead of an empty title.
        assertEquals("10.0.2.2", plug("DE 4h max0.20").displayName)
    }

    @Test fun macIsNormalised() {
        assertEquals("AABBCCDDEEFF", DeviceName.normalizeMac("aa:bb:cc:dd:ee:ff"))
        assertEquals("AABBCCDDEEFF", DeviceName.normalizeMac("AABBCCDDEEFF"))
        assertEquals("", DeviceName.normalizeMac(""))
        assertEquals("", DeviceName.normalizeMac(null))
        assertEquals("", DeviceName.normalizeMac("not-a-mac-at-all"))
    }

    @Test fun onlyNamedPlugsAreControlledFromTheStart() {
        assertEquals(true, DeviceName.parse("10.0.0.5", "Boiler DE 4h", 4.0, 0.2).managed)
        assertEquals(true, DeviceName.parse("10.0.0.5", "Heater 1500W", 4.0, 0.2).managed)
        assertEquals(false, DeviceName.parse("10.0.0.6", "Tasmota", 4.0, 0.2).managed)
        assertEquals(false, DeviceName.parse("10.0.0.7", "shellyplugsg3-8cbfea9d1c40", 4.0, 0.2).managed)
        assertEquals(false, DeviceName.parse("10.0.0.8", "Kitchen lamp", 4.0, 0.2).managed)
    }
}
