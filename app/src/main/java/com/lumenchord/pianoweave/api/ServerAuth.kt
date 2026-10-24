package com.lumenchord.pianoweave.api

import android.content.Context
import com.lumenchord.pianoweave.auth.GoogleAuthManager

/**
 * Runs a transcription-server call. Only the official server gets the user's Google token
 * (with a silent refresh when it is refused for the token); custom servers always get a null auth header, so callers
 * fall back to the anonymous X-User-Id identity.
 */
suspend fun <T> executeServerCall(
    context: Context,
    serverUrl: String,
    initialAuthHeader: String? = null,
    apiCall: suspend (authHeader: String?) -> T
): T =
    if (PianoApiFactory.isOfficialServer(serverUrl)) {
        GoogleAuthManager.executeWithAuthRetry(context, initialAuthHeader, apiCall)
    } else {
        apiCall(null)
    }
