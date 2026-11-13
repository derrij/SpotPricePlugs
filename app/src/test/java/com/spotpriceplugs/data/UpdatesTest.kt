package com.spotpriceplugs.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test fun numericNotLexicalOrder() {
        assertTrue(Updates.isNewer("1.10", "1.9"))
        assertFalse(Updates.isNewer("1.9", "1.10"))
        assertTrue(Updates.isNewer("2.0", "1.99.9"))
    }

    @Test fun prefixAndMissingPartsDoNotMatter() {
        assertFalse(Updates.isNewer("v1.3", "1.3"))
        assertFalse(Updates.isNewer("1.3", "1.3.0"))
        assertTrue(Updates.isNewer("V1.3.1", "1.3"))
    }

    @Test fun suffixesAreIgnored() {
        assertFalse(Updates.isNewer("1.3-beta", "1.3"))
        assertTrue(Updates.isNewer("1.4-rc1", "1.3"))
        assertFalse(Updates.isNewer("", "1.0"))
    }
}
