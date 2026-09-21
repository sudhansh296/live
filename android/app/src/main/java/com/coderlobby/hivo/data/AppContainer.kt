package com.coderlobby.hivo.data

import android.content.Context
import com.coderlobby.hivo.BuildConfig
import com.coderlobby.hivo.auth.Country
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import java.util.Locale

/** Holds the app-wide singletons (created once in HivoApp). */
class AppContainer(context: Context) {
    val store = SecureStore(context)
    val api = ApiClient(BuildConfig.API_BASE_URL, store)
    val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.createInstance(context)

    /** Every country libphonenumber knows, sorted by name. Built into the app: it is not sensitive data. */
    val countries: List<Country> by lazy {
        phoneUtil.supportedRegions
            .filter { it.length == 2 }
            .map { region ->
                Country(
                    region = region,
                    dialCode = phoneUtil.getCountryCodeForRegion(region),
                    name = Locale.Builder().setRegion(region).build().getDisplayCountry(Locale.getDefault()),
                )
            }
            .filter { it.name.isNotBlank() }
            .sortedBy { it.name }
    }

    val defaultCountry: Country
        get() = countries.firstOrNull { it.region == Locale.getDefault().country }
            ?: countries.first { it.region == "IN" }
}
