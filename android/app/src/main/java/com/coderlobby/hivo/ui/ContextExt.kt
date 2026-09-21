package com.coderlobby.hivo.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Compose gives us a Context that may be wrapped; Firebase and Credential Manager need the real Activity. */
fun Context.findActivity(): Activity {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    error("No Activity found in this Context")
}
