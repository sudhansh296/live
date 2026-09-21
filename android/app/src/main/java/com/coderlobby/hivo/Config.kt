package com.coderlobby.hivo

object Config {
    // Version of the Terms/Privacy text the welcome screen asks the user to accept. Sent to the server at login.
    // Bumped to 2026-09.2 when the mandatory "18+ and Terms" checkbox replaced the old notice line.
    const val TERMS_VERSION = "2026-09.2"

    const val OTP_LENGTH = 6
    const val OTP_MAX_ATTEMPTS = 5
    const val RESEND_SECONDS = 30
}
