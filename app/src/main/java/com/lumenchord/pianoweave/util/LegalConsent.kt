package com.lumenchord.pianoweave.util

import android.content.Context
import java.time.LocalDate

/** Whether the user has agreed to the current Terms of Service, which is asked before their first transcription. */
object LegalConsent {
    /** The Terms' effective date. Changing it asks everyone to agree again before their next transcription. */
    const val TERMS_VERSION = "2026-10-16"

    fun hasAcceptedTerms(context: Context): Boolean =
        AppPrefs.get(context).getString(AppPrefs.ACCEPTED_TERMS_VERSION, null) == TERMS_VERSION

    fun acceptTerms(context: Context) {
        AppPrefs.get(context).edit().putString(AppPrefs.ACCEPTED_TERMS_VERSION, TERMS_VERSION).apply()
    }

    /**
     * Effective date of the next Terms, announced in the App ahead of time (the Terms promise 14 days' notice).
     * Null when no change is coming. When it takes effect, move it to [TERMS_VERSION] and set this back to null.
     */
    val UPCOMING_TERMS_VERSION: String? = "2026-11-20"

    /** True once per upcoming version, until it takes effect; after that the consent dialog asks again. */
    fun shouldShowTermsChangeNotice(context: Context): Boolean {
        val upcoming = UPCOMING_TERMS_VERSION ?: return false
        if (!LocalDate.now().isBefore(LocalDate.parse(upcoming))) return false
        return AppPrefs.get(context).getString(AppPrefs.SEEN_TERMS_CHANGE_NOTICE, null) != upcoming
    }

    fun markTermsChangeNoticeSeen(context: Context) {
        val upcoming = UPCOMING_TERMS_VERSION ?: return
        AppPrefs.get(context).edit().putString(AppPrefs.SEEN_TERMS_CHANGE_NOTICE, upcoming).apply()
    }
}
