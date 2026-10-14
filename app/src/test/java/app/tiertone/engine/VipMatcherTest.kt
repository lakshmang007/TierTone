package app.tiertone.engine

import app.tiertone.core.Vip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VipMatcherTest {
    private val mom = Vip(id = "1", name = "Mum ❤️", phones = listOf("+91 98765 43210"), lookupKey = "0r12-ABC")
    private val dev = Vip(id = "2", name = "Dev Patel", aliases = listOf("Devu"))
    private val vips = listOf(mom, dev)

    @Test fun matchesNameIgnoringCaseSpacesAndEmoji() {
        assertEquals(mom, VipMatcher.match(vips, "mum", emptyList()))
        assertEquals(mom, VipMatcher.match(vips, "  MUM ❤️ ", emptyList()))
        assertEquals(dev, VipMatcher.match(vips, "dev   patel", emptyList()))
    }

    @Test fun matchesAliases() {
        assertEquals(dev, VipMatcher.match(vips, "Devu", emptyList()))
    }

    @Test fun doesNotMatchPartialNames() {
        assertNull(VipMatcher.match(vips, "Dev", emptyList()))
        assertNull(VipMatcher.match(vips, "Mummy", emptyList()))
        assertNull(VipMatcher.match(vips, null, emptyList()))
        assertNull(VipMatcher.match(emptyList(), "Mum", emptyList()))
    }

    @Test fun matchesTelUriWithDifferentFormatting() {
        assertEquals(mom, VipMatcher.match(vips, "Someone else", listOf("tel:09876543210")))
        assertEquals(mom, VipMatcher.match(vips, null, listOf("tel:+919876543210")))
    }

    @Test fun matchesContactLookupUri() {
        assertEquals(mom, VipMatcher.match(vips, null, listOf("content://com.android.contacts/contacts/lookup/0r12-ABC/42")))
        assertNull(VipMatcher.match(vips, null, listOf("content://com.android.contacts/contacts/lookup/OTHER/42")))
    }

    @Test fun matchesSenderShownAsPhoneNumber() {
        assertEquals(mom, VipMatcher.match(vips, "+91 98765 43210", emptyList()))
        assertEquals(mom, VipMatcher.match(vips, "98765-43210", emptyList()))
    }

    @Test fun phoneComparison() {
        assertTrue(VipMatcher.samePhone("+1 (415) 555-0100", "4155550100"))
        assertFalse(VipMatcher.samePhone("4155550100", "4155550101"))
        assertTrue(VipMatcher.samePhone("12345", "12345"))
        assertFalse(VipMatcher.samePhone("1234", "1234"))
        assertFalse(VipMatcher.samePhone("123456", "9123456"))
    }

    @Test fun symbolOnlyNamesStillMatchExactly() {
        val heart = Vip(id = "3", name = "❤️")
        assertEquals(heart, VipMatcher.match(listOf(heart), "❤️", emptyList()))
    }

    @Test fun titleCleanup() {
        assertEquals("Mom", Titles.senderFromTitle("Mom (3 messages)"))
        assertEquals("Mom", Titles.senderFromTitle("Mom @ Family"))
        assertEquals("Mom", Titles.senderFromTitle("  Mom "))
        assertNull(Titles.senderFromTitle(""))
    }
}
