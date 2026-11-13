package com.spotpriceplugs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VatTest {

    @Test
    fun autoPicksCountryRateFromArea() {
        assertEquals(21.0, Vat.effective("LV", Vat.AUTO), 0.0)
        assertEquals(25.0, Vat.effective("NO1", Vat.AUTO), 0.0)
        assertEquals(25.0, Vat.effective("se3", Vat.AUTO), 0.0)
        assertEquals(25.5, Vat.effective("FI", Vat.AUTO), 0.0)
    }

    @Test
    fun explicitValueWinsIncludingZero() {
        assertEquals(0.0, Vat.effective("NO4", 0.0), 0.0)
        assertEquals(12.5, Vat.effective("LV", 12.5), 0.0)
    }

    @Test
    fun unknownAreaWithoutValueMeansNoVat() {
        assertNull(Vat.forRegion("XX"))
        assertEquals(0.0, Vat.effective("XX", Vat.AUTO), 0.0)
    }
}
