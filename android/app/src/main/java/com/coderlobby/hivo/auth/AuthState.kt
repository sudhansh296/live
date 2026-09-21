package com.coderlobby.hivo.auth

import com.coderlobby.hivo.Config
import com.coderlobby.hivo.data.UserDto

enum class Step { Welcome, Phone, Otp }

/** Errors are codes, not text, so the UI can show them in the user's language. */
enum class AuthError { Network, InvalidPhone, TooMany, NotConfigured, NoGoogleAccount, CodeExpired, Suspended, Server, Generic }

sealed interface Session {
    data object Loading : Session
    data object SignedOut : Session
    data class SignedIn(val user: UserDto, val isNewUser: Boolean) : Session
}

data class AuthUi(
    val country: Country,
    /** The mandatory welcome-screen checkbox: 18+ and Terms/Privacy. Never pre-ticked. */
    val termsAccepted: Boolean = false,
    /** How many times the user tapped a sign-in button without ticking the box (drives the shake + red hint). */
    val termsHintCount: Int = 0,
    val step: Step = Step.Welcome,
    val busy: Boolean = false,
    val error: AuthError? = null,
    val phoneInput: String = "",
    val phoneDisplay: String = "",
    val otp: String = "",
    val otpWrong: Boolean = false,
    val otpAttemptsLeft: Int = Config.OTP_MAX_ATTEMPTS,
    val resendSeconds: Int = 0,
)
