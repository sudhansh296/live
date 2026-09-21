package com.coderlobby.hivo.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coderlobby.hivo.Config
import com.coderlobby.hivo.data.ApiException
import com.coderlobby.hivo.data.AppContainer
import com.coderlobby.hivo.data.UserDto
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class AuthViewModel(private val app: AppContainer) : ViewModel() {
    private val api = app.api

    val countries: List<Country> get() = app.countries

    private val _session = MutableStateFlow<Session>(Session.Loading)
    val session: StateFlow<Session> = _session.asStateFlow()

    private val _ui = MutableStateFlow(AuthUi(country = app.defaultCountry))
    val ui: StateFlow<AuthUi> = _ui.asStateFlow()

    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var e164: String? = null
    private var timerJob: Job? = null

    init {
        api.onSessionExpired = {
            _session.value = Session.SignedOut
            resetFlow()
        }
        viewModelScope.launch { restoreSession() }
    }

    // ── Session ───────────────────────────────────────────────────────────────

    private suspend fun restoreSession() {
        _session.value = try {
            api.restoreSession()?.let { Session.SignedIn(it, isNewUser = false) } ?: Session.SignedOut
        } catch (e: ApiException) {
            // Offline at start-up: show the welcome screen; the saved session is kept for the next start.
            Session.SignedOut
        }
    }

    /** Called when the server sent a newer copy of the signed-in user (for example after the profile was saved). */
    fun onUserUpdated(user: UserDto) {
        _session.update { current -> if (current is Session.SignedIn) current.copy(user = user) else current }
    }

    fun signOut() {
        viewModelScope.launch {
            api.logout()
            runCatching { FirebaseAuth.getInstance().signOut() }
            resetFlow()
            _session.value = Session.SignedOut
        }
    }

    private fun resetFlow() {
        timerJob?.cancel()
        verificationId = null
        resendToken = null
        e164 = null
        _ui.update { AuthUi(country = it.country) }
    }

    // ── Navigation inside the login flow ──────────────────────────────────────

    fun goToPhone() = _ui.update { it.copy(step = Step.Phone, error = null) }

    fun back() = _ui.update {
        when (it.step) {
            Step.Otp -> it.copy(step = Step.Phone, error = null, otp = "", otpWrong = false)
            Step.Phone -> it.copy(step = Step.Welcome, error = null)
            Step.Welcome -> it
        }
    }

    fun onCountryPicked(country: Country) = _ui.update { it.copy(country = country, error = null) }

    fun onPhoneChanged(text: String) =
        _ui.update { it.copy(phoneInput = text.filter(Char::isDigit).take(15), error = null) }

    fun onOtpChanged(text: String) =
        _ui.update { it.copy(otp = text.filter(Char::isDigit).take(Config.OTP_LENGTH), otpWrong = false, error = null) }

    fun onTermsChanged(accepted: Boolean) = _ui.update {
        it.copy(termsAccepted = accepted, termsHintCount = if (accepted) 0 else it.termsHintCount, error = null)
    }

    /** A sign-in button was tapped while the box is unticked: show the hint instead of signing in. */
    fun onTermsRequired() = _ui.update { it.copy(termsHintCount = it.termsHintCount + 1, error = null) }

    // ── Google ────────────────────────────────────────────────────────────────

    fun signInWithGoogle(activity: Activity) {
        if (_ui.value.busy || !_ui.value.termsAccepted) return
        val webClientId = webClientId(activity)
        if (!firebaseReady(activity) || webClientId == null) {
            fail(AuthError.NotConfigured)
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try {
                val option = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val credential = CredentialManager.create(activity).getCredential(activity, request).credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val google = GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseUser = FirebaseAuth.getInstance()
                        .signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null))
                        .await().user ?: error("Firebase returned no user")
                    finishLogin(firebaseUser)
                } else {
                    fail(AuthError.Generic)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: GetCredentialCancellationException) {
                _ui.update { it.copy(busy = false) } // the user closed the account picker: not an error
            } catch (e: NoCredentialException) {
                fail(AuthError.NoGoogleAccount)
            } catch (e: Exception) {
                fail(map(e))
            }
        }
    }

    // ── Phone ─────────────────────────────────────────────────────────────────

    fun sendCode(activity: Activity) {
        val state = _ui.value
        if (state.busy || !state.termsAccepted) return
        if (!firebaseReady(activity)) {
            fail(AuthError.NotConfigured)
            return
        }
        val number = try {
            app.phoneUtil.parse(state.phoneInput, state.country.region).takeIf { app.phoneUtil.isValidNumber(it) }
        } catch (e: Exception) {
            null
        }
        if (number == null) {
            fail(AuthError.InvalidPhone)
            return
        }
        e164 = app.phoneUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.E164)
        _ui.update {
            it.copy(
                busy = true,
                error = null,
                phoneDisplay = maskPhone(number.countryCode, number.nationalNumber.toString()),
            )
        }
        startVerification(activity, resend = null)
    }

    fun resendCode(activity: Activity) {
        val state = _ui.value
        if (state.busy || state.resendSeconds > 0) return
        _ui.update { it.copy(busy = true, error = null, otp = "", otpWrong = false, otpAttemptsLeft = Config.OTP_MAX_ATTEMPTS) }
        startVerification(activity, resend = resendToken)
    }

    fun verifyOtp() {
        val state = _ui.value
        val id = verificationId ?: return
        if (state.busy || state.otp.length != Config.OTP_LENGTH || state.otpAttemptsLeft <= 0) return
        signInWithPhoneCredential(PhoneAuthProvider.getCredential(id, state.otp))
    }

    private fun startVerification(activity: Activity, resend: PhoneAuthProvider.ForceResendingToken?) {
        val phone = e164 ?: return
        val builder = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(phoneCallbacks)
        if (resend != null) builder.setForceResendingToken(resend)
        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    private val phoneCallbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        // Instant verification or SMS auto-retrieval: no need for the user to type the code.
        override fun onVerificationCompleted(credential: PhoneAuthCredential) = signInWithPhoneCredential(credential)

        override fun onVerificationFailed(e: FirebaseException) {
            fail(if (e is FirebaseAuthInvalidCredentialsException) AuthError.InvalidPhone else map(e))
        }

        override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
            verificationId = id
            resendToken = token
            _ui.update { it.copy(step = Step.Otp, busy = false, error = null, otp = "", otpWrong = false) }
            startResendTimer()
        }
    }

    private fun startResendTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            for (seconds in Config.RESEND_SECONDS downTo 1) {
                _ui.update { it.copy(resendSeconds = seconds) }
                delay(1000)
            }
            _ui.update { it.copy(resendSeconds = 0) }
        }
    }

    private fun signInWithPhoneCredential(credential: PhoneAuthCredential) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try {
                val user = FirebaseAuth.getInstance().signInWithCredential(credential).await().user
                    ?: error("Firebase returned no user")
                finishLogin(user)
            } catch (e: CancellationException) {
                throw e
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                if (e.errorCode == "ERROR_SESSION_EXPIRED") {
                    fail(AuthError.CodeExpired)
                } else {
                    _ui.update { it.copy(busy = false, otpWrong = true, otpAttemptsLeft = it.otpAttemptsLeft - 1) }
                }
            } catch (e: Exception) {
                fail(map(e))
            }
        }
    }

    // ── Shared ────────────────────────────────────────────────────────────────

    // Trades the fresh Firebase sign-in for our own session. The server verifies the token; the app never decides who is who.
    private suspend fun finishLogin(user: FirebaseUser) {
        try {
            val idToken = user.getIdToken(false).await().token ?: error("Firebase returned no token")
            val result = api.loginWithFirebase(idToken, Config.TERMS_VERSION, adultConfirmed = _ui.value.termsAccepted)
            timerJob?.cancel()
            _ui.update { AuthUi(country = it.country) }
            _session.value = Session.SignedIn(result.user, result.isNewUser)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching { FirebaseAuth.getInstance().signOut() }
            fail(map(e))
        }
    }

    private fun fail(error: AuthError) = _ui.update { it.copy(busy = false, error = error) }

    private fun map(e: Throwable): AuthError = when {
        e is ApiException -> when {
            e.status == 0 -> AuthError.Network
            e.status == 429 -> AuthError.TooMany
            e.status == 403 -> AuthError.Suspended
            e.status >= 500 -> AuthError.Server
            else -> AuthError.Generic
        }
        e is FirebaseNetworkException -> AuthError.Network
        e is FirebaseTooManyRequestsException -> AuthError.TooMany
        else -> AuthError.Generic
    }

    private fun firebaseReady(context: Context) = FirebaseApp.getApps(context).isNotEmpty()

    // Generated by the google-services plugin from google-services.json; absent until that file is added.
    private fun webClientId(context: Context): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id)
    }
}
