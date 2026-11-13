package com.lumenchord.pianoweave.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Fixed public URLs used by the app. Server-specific links live in config.txt (see AppConfig). */
object AppLinks {
    private const val DOCS_BASE_URL = "https://virtover.github.io/pianoweave/"

    const val PRIVACY_POLICY = "${DOCS_BASE_URL}privacy_policy.html"
    const val TERMS_OF_SERVICE = "${DOCS_BASE_URL}terms_of_service.html"
    const val TERMS_VERSIONS = "${DOCS_BASE_URL}terms_versions.json"
    const val ACCOUNT_DELETION = "${DOCS_BASE_URL}delete_account.html"
    const val SUGGESTIONS_AND_BUG_REPORTS = "https://pianoweave.com/tickets#bug"
    const val CUSTOM_SERVER_SETUP = "https://github.com/Virtover/piano-transcription-server"

    /** One version of the Terms (its effective date), in Polish or English. */
    fun termsVersion(version: String, polish: Boolean): String =
        "${DOCS_BASE_URL}terms/$version${if (polish) "_pl" else ""}.html"

    /** One version of the Privacy Policy (its effective date); it is in English only. */
    fun privacyVersion(version: String): String = "${DOCS_BASE_URL}privacy/$version.html"

    /** The next announced Terms (or the current ones if none is), for links that have no real version to name. */
    fun termsUpcoming(polish: Boolean): String = "${DOCS_BASE_URL}terms_of_service_upcoming${if (polish) "_pl" else ""}.html"

    const val PRIVACY_POLICY_UPCOMING = "${DOCS_BASE_URL}privacy_policy_upcoming.html"
}

/** Opens [url] in the browser; returns false if no app can handle it. */
fun Context.openUrl(url: String): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
