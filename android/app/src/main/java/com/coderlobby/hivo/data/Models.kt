package com.coderlobby.hivo.data

import kotlinx.serialization.Serializable

@Serializable
data class FirebaseLoginRequest(val idToken: String, val termsVersion: String, val adultConfirmed: Boolean)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class UserDto(
    val id: String,
    val phoneMasked: String? = null,
    val email: String? = null,
    val emailVerified: Boolean = false,
    val profileCompleted: Boolean = false,
    val displayName: String? = null,
    val username: String? = null,
    /** Path on our server, for example /media/avatars/<random>.webp. */
    val avatarUrl: String? = null,
    val countryCode: String? = null,
    /** True when the server refused this account for being under 18. */
    val ageRestricted: Boolean = false,
)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val expiresIn: Int,
    val refreshToken: String,
    val user: UserDto,
    val isNewUser: Boolean = false,
)

@Serializable
data class MeResponse(val user: UserDto)

@Serializable
data class ProfileRequest(
    val displayName: String,
    val username: String,
    /** 'YYYY-MM-DD' */
    val birthDate: String,
    /** Two-letter country code. */
    val countryCode: String,
)

@Serializable
data class UsernameCheck(val available: Boolean, val reason: String? = null)

@Serializable
data class ErrorBody(val error: String? = null, val field: String? = null)
