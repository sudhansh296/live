package com.coderlobby.hivo

import android.app.Application
import com.coderlobby.hivo.data.AppContainer
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

class HivoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Opt-in, debug builds only (see app/build.gradle). Real phone numbers are unaffected.
        if (BuildConfig.DISABLE_APP_VERIFICATION && FirebaseApp.getApps(this).isNotEmpty()) {
            FirebaseAuth.getInstance().firebaseAuthSettings.setAppVerificationDisabledForTesting(true)
        }
    }
}
