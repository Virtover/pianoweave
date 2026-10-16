package com.lumenchord.pianoweave.util

import android.content.Context

/** Whether the user has agreed to the current Terms of Service, which is asked before their first transcription. */
object LegalConsent {
    /** The Terms' effective date. Changing it asks everyone to agree again before their next transcription. */
    const val TERMS_VERSION = "2026-10-16"

    fun hasAcceptedTerms(context: Context): Boolean =
        AppPrefs.get(context).getString(AppPrefs.ACCEPTED_TERMS_VERSION, null) == TERMS_VERSION

    fun acceptTerms(context: Context) {
        AppPrefs.get(context).edit().putString(AppPrefs.ACCEPTED_TERMS_VERSION, TERMS_VERSION).apply()
    }
}
