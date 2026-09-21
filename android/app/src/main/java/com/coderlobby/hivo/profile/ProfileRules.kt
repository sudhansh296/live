package com.coderlobby.hivo.profile

import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Client-side copies of the server's profile rules, used ONLY to give instant feedback while typing.
 * The server checks everything again and always has the final say (including the 18+ rule, which is
 * deliberately NOT checked here so an under-18 attempt always reaches the server and gets locked).
 */
object ProfileRules {
    const val USERNAME_MIN = 3
    const val USERNAME_MAX = 20
    const val NAME_MIN = 2
    const val NAME_MAX = 30

    private val USERNAME_SHAPE = Regex("^[a-z0-9][a-z0-9._]{1,18}[a-z0-9]$")

    // Control characters, zero-width characters and text-direction overrides.
    private val HIDDEN_CHARS = Regex("[\\u0000-\\u001F\\u007F-\\u009F\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]")

    /** Lowercases and drops anything a username cannot contain, as the user types. */
    fun cleanUsernameInput(raw: String): String =
        raw.lowercase(Locale.ROOT)
            .filter { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' }
            .take(USERNAME_MAX)

    fun usernameShapeOk(name: String): Boolean = USERNAME_SHAPE.matches(name) && !name.contains("..")

    /** Same cleaning the server does: composed form, no hidden characters, single spaces, trimmed. */
    fun cleanDisplayName(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFC).replace(HIDDEN_CHARS, "").replace(Regex("\\s+"), " ").trim()

    /** Length in characters (an emoji counts as one), like the server. */
    fun displayNameLength(name: String): Int = name.codePointCount(0, name.length)

    fun displayNameOk(cleaned: String): Boolean = displayNameLength(cleaned) in NAME_MIN..NAME_MAX

    /** Stops typing at the limit without cutting an emoji in half. */
    fun limitDisplayName(raw: String): String {
        if (displayNameLength(raw) <= NAME_MAX) return raw
        return raw.substring(0, raw.offsetByCodePoints(0, NAME_MAX))
    }

    /** 'YYYY-MM-DD' for the server, from the date picker's UTC-midnight milliseconds. */
    fun isoDate(utcMillis: Long): String = format("yyyy-MM-dd", utcMillis)

    /** 'DD / MM / YYYY' for the screen. */
    fun shownDate(utcMillis: Long): String = format("dd / MM / yyyy", utcMillis)

    private fun format(pattern: String, utcMillis: Long): String =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(utcMillis)
}
