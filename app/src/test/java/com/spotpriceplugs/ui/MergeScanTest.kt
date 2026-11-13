package com.spotpriceplugs.ui

import com.spotpriceplugs.model.Plug
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeScanTest {
    private fun plug(ip: String, mac: String, name: String = "Plug", hours: Double = 4.0) =
        Plug(ip = ip, name = name, region = "LV", hours = hours, limit = 0.2, mac = mac)

    private fun merge(saved: List<Plug>, found: List<Plug>): Triple<List<Plug>, Map<String, String>, Set<String>> {
        val moves = LinkedHashMap<String, String>()
        val dropped = HashSet<String>()
        return Triple(mergeScan(saved, found, moves, dropped), moves, dropped)
    }

    @Test fun plugKeepsItsSettingsAtANewAddress() {
        val saved = listOf(plug("10.0.0.5", "AA0000000001", hours = 7.0))
        val (out, moves, dropped) = merge(saved, listOf(plug("10.0.0.9", "AA0000000001", name = "Boiler")))
        assertEquals("10.0.0.9", out.single().ip)
        assertEquals(7.0, out.single().hours, 0.0) // the user's value, not the scan's
        assertEquals("Boiler", out.single().name) // the device's current name
        assertEquals(mapOf("10.0.0.5" to "10.0.0.9"), moves)
        assertTrue(dropped.isEmpty())
    }

    @Test fun plugSavedWithoutMacIsMatchedByAddressAndLearnsIt() {
        val (out, moves, _) = merge(listOf(plug("10.0.0.5", "", hours = 7.0)), listOf(plug("10.0.0.5", "AA0000000001")))
        assertEquals("AA0000000001", out.single().mac)
        assertEquals(7.0, out.single().hours, 0.0)
        assertTrue(moves.isEmpty())
    }

    @Test fun anotherDeviceAtTheOldAddressReplacesTheUnreachablePlug() {
        val (out, moves, dropped) = merge(
            listOf(plug("10.0.0.5", "AA0000000001", hours = 7.0)),
            listOf(plug("10.0.0.5", "BB0000000002"))
        )
        assertEquals("BB0000000002", out.single().mac)
        assertEquals(4.0, out.single().hours, 0.0) // a new entry, not the old plug's settings
        assertTrue(moves.isEmpty())
        assertEquals(setOf("10.0.0.5"), dropped)
    }

    @Test fun twoPlugsSwapAddresses() {
        val saved = listOf(plug("10.0.0.5", "AA0000000001", hours = 1.0), plug("10.0.0.6", "BB0000000002", hours = 2.0))
        val found = listOf(plug("10.0.0.5", "BB0000000002"), plug("10.0.0.6", "AA0000000001"))
        val (out, moves, dropped) = merge(saved, found)
        assertEquals(listOf("10.0.0.5", "10.0.0.6"), out.map { it.ip })
        assertEquals(listOf(2.0, 1.0), out.map { it.hours })
        assertEquals(mapOf("10.0.0.5" to "10.0.0.6", "10.0.0.6" to "10.0.0.5"), moves)
        assertTrue(dropped.isEmpty())
    }

    @Test fun offlinePlugStaysAndNewOnesAreAdded() {
        val saved = listOf(plug("10.0.0.5", "AA0000000001"))
        val (out, _, dropped) = merge(saved, listOf(plug("10.0.0.20", "CC0000000003"), plug("10.0.0.7", "BB0000000002")))
        assertEquals(listOf("10.0.0.5", "10.0.0.7", "10.0.0.20"), out.map { it.ip })
        assertTrue(dropped.isEmpty())
    }
}
