package com.coderlobby.hivo.auth

data class Country(val region: String, val dialCode: Int, val name: String) {
    /** Flag emoji built from the two-letter region code (no image files needed). */
    val flag: String
        get() = region.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}
