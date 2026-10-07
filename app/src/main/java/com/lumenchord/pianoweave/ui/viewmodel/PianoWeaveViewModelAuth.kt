package com.lumenchord.pianoweave.ui.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.lumenchord.pianoweave.auth.GoogleAuthManager
import kotlinx.coroutines.launch
import retrofit2.HttpException

internal fun PianoWeaveViewModel.signInWithGoogleImpl(context: Context, onResult: ((Boolean) -> Unit)?) {
    if (isLoading) {
        googleAuthError = "Cannot switch account during active transcription."
        onResult?.invoke(false)
        return
    }

    val clientId = activeGoogleClientId
    if (clientId.isBlank()) {
        googleAuthError = "Google WEB_CLIENT_ID is not configured in Piano Weave config."
        onResult?.invoke(false)
        return
    }

    viewModelScope.launch {
        isGoogleAuthLoading = true
        googleAuthError = null
        val result = GoogleAuthManager.signIn(context, clientId, filterByAuthorizedAccounts = false)
        result.fold(
            onSuccess = { user ->
                googleToken = user.token
                googleUserEmail = user.email
                googleAuthError = null
                isGoogleAuthLoading = false
                if (isBilledServer) {
                    refreshUserBalance(context)
                }

                // Load cloud songs for the signed-in account
                loadCloudSongs(context)

                // Requirement: on sign in cloud backup dialog should appear only if it never before appeared after sign in to any account
                if (songs.isNotEmpty() && !hasShownCloudBackupDialog(context)) {
                    showUploadDialog = true
                    markCloudBackupDialogShown(context)
                }

                onResult?.invoke(true)
            },
            onFailure = { err ->
                isGoogleAuthLoading = false
                val msg = err.localizedMessage ?: "Google account selection cancelled."
                googleAuthError = msg
                onResult?.invoke(false)

                if ((isBilledServer || requireGoogleAccount) && !isGoogleSignedIn) {
                    signInWithGoogleImpl(context, onResult)
                }
            }
        )
    }
}

internal fun PianoWeaveViewModel.signOutGoogleImpl(context: Context) {
    if (isLoading) return
    GoogleAuthManager.clearAuthData(context)
    googleToken = ""
    googleUserEmail = ""
    googleAuthError = null
    if (isBilledServer) {
        userCredits = 0
    }
}

internal suspend fun PianoWeaveViewModel.ensureGoogleAuthTokenImpl(context: Context): String? {
    if (googleToken.isNotBlank()) return googleToken
    val clientId = activeGoogleClientId
    if (clientId.isBlank()) return null

    if (!isBilledServer && !requireGoogleAccount) {
        return null
    }

    val result = GoogleAuthManager.signIn(context, clientId, filterByAuthorizedAccounts = true)
    return result.getOrNull()?.let { user ->
        googleToken = user.token
        googleUserEmail = user.email
        user.token
    }
}

internal suspend fun <T> PianoWeaveViewModel.executeWithAuthRetryImpl(
    context: Context,
    apiCall: suspend (authHeader: String?) -> T
): T {
    val clientId = activeGoogleClientId
    val token = ensureGoogleAuthTokenImpl(context)
    var authHeader = GoogleAuthManager.getAuthHeader(token)

    try {
        return apiCall(authHeader)
    } catch (e: HttpException) {
        if (e.code() == 401 || e.code() == 503) {
            if (clientId.isNotBlank()) {
                val refreshResult = GoogleAuthManager.silentRefresh(context, clientId)
                if (refreshResult.isSuccess) {
                    val user = refreshResult.getOrNull()
                    if (user != null) {
                        googleToken = user.token
                        googleUserEmail = user.email
                        authHeader = GoogleAuthManager.getAuthHeader(user.token)
                        return apiCall(authHeader)
                    }
                }
            }
        }
        throw e
    }
}
