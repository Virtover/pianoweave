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
    const val ACCOUNT_DELETION = "${DOCS_BASE_URL}delete_account.html"
    const val SUGGESTIONS_AND_BUG_REPORTS = "https://pianoweave.com/tickets#bug"
    const val CUSTOM_SERVER_SETUP = "https://github.com/Virtover/piano-transcription-server"
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
