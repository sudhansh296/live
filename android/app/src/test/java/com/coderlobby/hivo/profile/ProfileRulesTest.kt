package com.coderlobby.hivo.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRulesTest {
    @Test
    fun usernameInputIsLowercasedAndCleaned() {
        assertEquals("sudha_live", ProfileRules.cleanUsernameInput("Sudha_Live"))
        assertEquals("abc.def", ProfileRules.cleanUsernameInput(" A b c.d!e@f "))
        assertEquals(20, ProfileRules.cleanUsernameInput("x".repeat(40)).length)
    }

    @Test
    fun usernameShapeMatchesTheServerRules() {
        for (good in listOf("abc", "a1b", "sudha_live", "a.b.c", "x".repeat(20))) {
            assertTrue("$good should be fine", ProfileRules.usernameShapeOk(good))
        }
        for (bad in listOf("ab", "", ".abc", "abc.", "_abc", "abc_", "a..b", "has space", "x".repeat(21), "CAPS")) {
            assertFalse("$bad should be refused", ProfileRules.usernameShapeOk(bad))
        }
    }

    @Test
    fun displayNameIsCleanedLikeTheServer() {
        assertEquals("Sudha Kumar", ProfileRules.cleanDisplayName("  Sudha\n   Kumar  "))
        assertEquals("", ProfileRules.cleanDisplayName("​​"))
    }

    @Test
    fun displayNameLengthCountsCharactersNotBytes() {
        val fire = "🔥"
        assertEquals(1, ProfileRules.displayNameLength(fire))
        assertFalse(ProfileRules.displayNameOk(fire))
        assertTrue(ProfileRules.displayNameOk(fire + fire))
        assertTrue(ProfileRules.displayNameOk("Ab"))
        assertFalse(ProfileRules.displayNameOk("A"))
        assertFalse(ProfileRules.displayNameOk("x".repeat(31)))
    }

    @Test
    fun typingStopsAtThirtyCharactersWithoutCuttingAnEmoji() {
        val fire = "🔥"
        val limited = ProfileRules.limitDisplayName(fire.repeat(40))
        assertEquals(30, ProfileRules.displayNameLength(limited))
        assertEquals(fire.repeat(30), limited)
    }

    @Test
    fun datesAreFormattedInUtcForTheServerAndForTheScreen() {
        val millis = 989625600000L // 2001-05-12T00:00:00Z
        assertEquals("2001-05-12", ProfileRules.isoDate(millis))
        assertEquals("12 / 05 / 2001", ProfileRules.shownDate(millis))
    }
}
