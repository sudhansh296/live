package com.coderlobby.hivo.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coderlobby.hivo.auth.Country
import com.coderlobby.hivo.data.ApiClient
import com.coderlobby.hivo.data.ApiException
import com.coderlobby.hivo.data.ProfileRequest
import com.coderlobby.hivo.data.UserDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class UsernameStatus { Empty, TooShort, BadShape, Checking, Available, Taken, Reserved, Unreachable }

/** Errors are codes, not text, so the screen can show them in the user's language. */
enum class ProfileError { Network, Server, Generic, TooMany, DisplayName, BirthDate, Country, PhotoTooLarge, PhotoInvalid, PhotoUnsupported, NoCamera }

data class ProfileSetupState(
    val country: Country,
    val displayName: String = "",
    val username: String = "",
    val usernameStatus: UsernameStatus = UsernameStatus.Empty,
    /** Date picker value: UTC midnight in milliseconds. */
    val birthDateMillis: Long? = null,
    /** Preview of the uploaded photo. */
    val avatar: Bitmap? = null,
    val avatarBusy: Boolean = false,
    /** A picture the user just chose, waiting on the crop screen. */
    val cropSource: Bitmap? = null,
    val submitting: Boolean = false,
    val error: ProfileError? = null,
    /** The server refused this account for being under 18. */
    val ageBlocked: Boolean = false,
) {
    val canSubmit: Boolean
        get() = ProfileRules.displayNameOk(ProfileRules.cleanDisplayName(displayName)) &&
            (usernameStatus == UsernameStatus.Available || usernameStatus == UsernameStatus.Unreachable) &&
            birthDateMillis != null &&
            !avatarBusy &&
            !submitting
}

class ProfileSetupViewModel(
    private val api: ApiClient,
    val countries: List<Country>,
    defaultCountry: Country,
    initialUser: UserDto,
    private val onProfileSaved: (UserDto) -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileSetupState(country = defaultCountry))
    val state: StateFlow<ProfileSetupState> = _state.asStateFlow()

    private var checkJob: Job? = null

    init {
        // A photo uploaded earlier (before the app was closed) is fetched again so the screen can show it.
        initialUser.avatarUrl?.let { path ->
            viewModelScope.launch {
                val bytes = api.fetchPublicFile(path) ?: return@launch
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@launch
                _state.update { it.copy(avatar = bitmap) }
            }
        }
    }

    // ── Form fields ───────────────────────────────────────────────────────────

    fun onNameChanged(raw: String) =
        _state.update { it.copy(displayName = ProfileRules.limitDisplayName(raw), error = null) }

    fun onUsernameChanged(raw: String) {
        val name = ProfileRules.cleanUsernameInput(raw)
        checkJob?.cancel()
        val status = when {
            name.isEmpty() -> UsernameStatus.Empty
            name.length < ProfileRules.USERNAME_MIN -> UsernameStatus.TooShort
            !ProfileRules.usernameShapeOk(name) -> UsernameStatus.BadShape
            else -> UsernameStatus.Checking
        }
        _state.update { it.copy(username = name, usernameStatus = status, error = null) }
        if (status == UsernameStatus.Checking) checkJob = viewModelScope.launch { checkAfterPause(name) }
    }

    // Waits until the user stops typing, so the server is not asked after every single letter.
    private suspend fun checkAfterPause(name: String) {
        delay(CHECK_PAUSE_MS)
        val result = try {
            api.checkUsername(name)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        _state.update { s ->
            if (s.username != name) {
                s // the user kept typing: this answer is out of date
            } else {
                s.copy(
                    usernameStatus = when {
                        result == null -> UsernameStatus.Unreachable
                        result.available -> UsernameStatus.Available
                        result.reason == "taken" -> UsernameStatus.Taken
                        result.reason == "reserved" -> UsernameStatus.Reserved
                        else -> UsernameStatus.BadShape
                    },
                )
            }
        }
    }

    fun onBirthDatePicked(utcMillis: Long) = _state.update { it.copy(birthDateMillis = utcMillis, error = null) }

    fun onCountryPicked(country: Country) = _state.update { it.copy(country = country, error = null) }

    // ── Photo ─────────────────────────────────────────────────────────────────

    /** A photo was picked or taken: load it and open the crop screen. Nothing is uploaded yet. */
    fun onPhotoChosen(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(avatarBusy = true, error = null) }
            val picture = ImageUtils.loadUpright(context, uri)
            if (picture == null) {
                fail(ProfileError.PhotoInvalid)
            } else {
                _state.update { it.copy(avatarBusy = false, cropSource = picture) }
            }
        }
    }

    fun onCropCancelled() = _state.update { it.copy(cropSource = null) }

    /** The user finished moving and zooming: cut out the chosen square and upload it. */
    fun onCropConfirmed(square: CropMath.Square) {
        val source = _state.value.cropSource ?: return
        viewModelScope.launch {
            _state.update { it.copy(cropSource = null, avatarBusy = true, error = null) }
            val jpeg = ImageUtils.cropToJpeg(source, square)
            if (jpeg == null) {
                fail(ProfileError.PhotoInvalid)
            } else {
                upload(jpeg)
            }
        }
    }

    private suspend fun upload(jpeg: ByteArray) {
        try {
            api.uploadAvatar(jpeg)
            val preview = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            _state.update { it.copy(avatarBusy = false, avatar = preview) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            fail(
                when {
                    e.status == 0 -> ProfileError.Network
                    e.status == 413 || e.code == "file_too_large" -> ProfileError.PhotoTooLarge
                    e.code == "image_unsupported" -> ProfileError.PhotoUnsupported
                    e.code == "image_invalid" -> ProfileError.PhotoInvalid
                    e.status == 429 -> ProfileError.TooMany
                    e.status >= 500 -> ProfileError.Server
                    else -> ProfileError.Generic
                },
            )
        }
    }

    fun onCameraMissing() = fail(ProfileError.NoCamera)

    fun onPhotoRemoved() {
        viewModelScope.launch {
            _state.update { it.copy(avatarBusy = true, error = null) }
            try {
                api.deleteAvatar()
                _state.update { it.copy(avatarBusy = false, avatar = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                fail(if (e.status == 0) ProfileError.Network else ProfileError.Generic)
            }
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    fun submit() {
        val s = _state.value
        val birthMillis = s.birthDateMillis
        if (!s.canSubmit || birthMillis == null) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            try {
                val user = api.saveProfile(
                    ProfileRequest(
                        displayName = ProfileRules.cleanDisplayName(s.displayName),
                        username = s.username,
                        birthDate = ProfileRules.isoDate(birthMillis),
                        countryCode = s.country.region,
                    ),
                )
                onProfileSaved(user) // the app moves on to the next screen
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                handleSaveError(e)
            }
        }
    }

    private suspend fun handleSaveError(e: ApiException) {
        when {
            // The server decides who is old enough. This account is now locked for good.
            e.code == "age_restricted" -> _state.update { it.copy(submitting = false, ageBlocked = true) }
            e.code == "profile_already_completed" -> try {
                onProfileSaved(api.me())
            } catch (_: ApiException) {
                fail(ProfileError.Generic)
            }
            e.code == "username_taken" -> setUsernameStatus(UsernameStatus.Taken)
            e.code == "username_reserved" -> setUsernameStatus(UsernameStatus.Reserved)
            e.code == "username_invalid" -> setUsernameStatus(UsernameStatus.BadShape)
            e.code == "display_name_invalid" -> fail(ProfileError.DisplayName)
            e.code == "birth_date_invalid" -> fail(ProfileError.BirthDate)
            e.code == "country_invalid" -> fail(ProfileError.Country)
            e.status == 0 -> fail(ProfileError.Network)
            e.status == 429 -> fail(ProfileError.TooMany)
            e.status >= 500 -> fail(ProfileError.Server)
            else -> fail(ProfileError.Generic)
        }
    }

    private fun setUsernameStatus(status: UsernameStatus) =
        _state.update { it.copy(submitting = false, usernameStatus = status) }

    private fun fail(error: ProfileError) =
        _state.update { it.copy(submitting = false, avatarBusy = false, error = error) }

    private companion object {
        const val CHECK_PAUSE_MS = 450L
    }
}
