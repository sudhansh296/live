package com.coderlobby.hivo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coderlobby.hivo.auth.AuthViewModel
import com.coderlobby.hivo.auth.Session
import com.coderlobby.hivo.auth.Step
import com.coderlobby.hivo.data.AppContainer
import com.coderlobby.hivo.data.UserDto
import com.coderlobby.hivo.profile.ProfileSetupViewModel
import com.coderlobby.hivo.ui.auth.LoadingScreen
import com.coderlobby.hivo.ui.auth.OtpScreen
import com.coderlobby.hivo.ui.auth.PhoneScreen
import com.coderlobby.hivo.ui.auth.SignedInScreen
import com.coderlobby.hivo.ui.auth.WelcomeScreen
import com.coderlobby.hivo.ui.profile.AgeBlockedScreen
import com.coderlobby.hivo.ui.profile.ProfileSetupScreen

@Composable
fun AppRoot(vm: AuthViewModel, container: AppContainer) {
    val session by vm.session.collectAsStateWithLifecycle()
    when (val current = session) {
        Session.Loading -> LoadingScreen()
        // The server decides which of these a signed-in user sees: refused for age, still needs a profile, or ready.
        is Session.SignedIn -> when {
            current.user.ageRestricted -> AgeBlockedScreen(onSignOut = vm::signOut)
            !current.user.profileCompleted -> ProfileSetup(current.user, vm, container)
            else -> SignedInScreen(current, onSignOut = vm::signOut)
        }
        Session.SignedOut -> AuthFlow(vm)
    }
}

@Composable
private fun ProfileSetup(user: UserDto, vm: AuthViewModel, container: AppContainer) {
    val profileVm = viewModel<ProfileSetupViewModel>(
        key = "profile-${user.id}",
        factory = viewModelFactory {
            initializer {
                ProfileSetupViewModel(
                    api = container.api,
                    countries = container.countries,
                    defaultCountry = container.defaultCountry,
                    initialUser = user,
                    onProfileSaved = vm::onUserUpdated,
                )
            }
        },
    )
    ProfileSetupScreen(profileVm, onSignOut = vm::signOut)
}

@Composable
private fun AuthFlow(vm: AuthViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()

    BackHandler(enabled = ui.step != Step.Welcome) { vm.back() }

    Crossfade(targetState = ui.step, label = "auth-step") { step ->
        when (step) {
            Step.Welcome -> WelcomeScreen(
                busy = ui.busy,
                error = ui.error,
                termsAccepted = ui.termsAccepted,
                termsHintCount = ui.termsHintCount,
                onTermsChange = vm::onTermsChanged,
                onTermsRequired = vm::onTermsRequired,
                onGoogle = { vm.signInWithGoogle(activity) },
                onPhone = vm::goToPhone,
            )
            Step.Phone -> PhoneScreen(
                ui = ui,
                countries = vm.countries,
                onBack = vm::back,
                onPhoneChange = vm::onPhoneChanged,
                onCountryPick = vm::onCountryPicked,
                onSend = { vm.sendCode(activity) },
            )
            Step.Otp -> OtpScreen(
                ui = ui,
                onBack = vm::back,
                onOtpChange = vm::onOtpChanged,
                onVerify = vm::verifyOtp,
                onResend = { vm.resendCode(activity) },
            )
        }
    }
}
