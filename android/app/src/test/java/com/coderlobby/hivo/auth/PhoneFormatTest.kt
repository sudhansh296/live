package com.coderlobby.hivo.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneFormatTest {
    @Test
    fun masksMiddleDigitsOfAnIndianNumber() {
        assertEquals("+91 98•••••210", maskPhone(91, "9876543210"))
    }

    @Test
    fun ignoresNonDigits() {
        assertEquals("+1 41•••••789", maskPhone(1, "(415) 555-0789"))
    }

    @Test
    fun leavesVeryShortNumbersReadable() {
        assertEquals("+44 12345", maskPhone(44, "12345"))
    }

    @Test
    fun neverRevealsTheWholeNumber() {
        val masked = maskPhone(91, "9876543210")
        assertEquals(false, masked.contains("9876543210"))
    }
}
