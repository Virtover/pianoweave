package com.lumenchord.pianoweave.ui.viewmodel

import android.content.Context
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.viewModelScope
import com.lumenchord.pianoweave.auth.GoogleAuthManager
import kotlinx.coroutines.launch
import retrofit2.HttpException
import android.content.Intent
import android.provider.Settings
import androidx.credentials.exceptions.GetCredentialCancellationException

private var pendingAction: (() -> Unit)? = null

internal fun openAddGoogleAccountImpl(context: Context) {
    val addAccount = Intent(Settings.ACTION_ADD_ACCOUNT).apply {
        putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(addAccount)
    } catch (e: Exception) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_SYNC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
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

                pendingAction?.invoke()
                pendingAction = null
                onResult?.invoke(true)
            },
            onFailure = { err ->
                pendingAction = null
                isGoogleAuthLoading = false

                when (err) {
                    is NoCredentialException -> {
                        // no Google account on the device
                        googleAuthError = null
                        showNoGoogleAccountDialog = true
                        onResult?.invoke(false)
                    }
                    is GetCredentialCancellationException -> {
                        signInCancelEvent++
                    }
                    else -> {
                        googleAuthError = err.localizedMessage ?: "Google sign-in failed."
                        onResult?.invoke(false)
                    }
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

internal fun PianoWeaveViewModel.withEnsuredAuthImpl(context: Context, forceAuthWhenNonBilledServer: Boolean = false, action: () -> Unit) {
    if (isGoogleSignedIn || (!isBilledServer && !forceAuthWhenNonBilledServer)) {
        action()
    } else {
        pendingAction = action
        signInWithGoogle(context)
    }
}

internal suspend fun PianoWeaveViewModel.ensureGoogleAuthTokenImpl(context: Context, forceAuthWhenNonBilledServer: Boolean = false): String? {
    if (googleToken.isNotBlank()) return googleToken
    val clientId = activeGoogleClientId
    if (clientId.isBlank()) return null

    if (!isBilledServer && !forceAuthWhenNonBilledServer) return null

    val result = GoogleAuthManager.signIn(context, clientId, filterByAuthorizedAccounts = true)
    return result.getOrNull()?.let { user ->
        googleToken = user.token
        googleUserEmail = user.email
        user.token
    }
}

internal suspend fun <T> PianoWeaveViewModel.executeWithAuthRetryImpl(
    context: Context,
    forceAuthWhenNonBilledServer: Boolean = false,
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
