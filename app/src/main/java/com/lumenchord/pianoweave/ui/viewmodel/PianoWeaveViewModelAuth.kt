package com.lumenchord.pianoweave.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.viewModelScope
import com.lumenchord.pianoweave.api.executeServerCall
import com.lumenchord.pianoweave.auth.GoogleAuthManager
import com.lumenchord.pianoweave.util.AppPrefs
import kotlinx.coroutines.launch

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

internal fun PianoWeaveViewModel.signInWithGoogleImpl(context: Context, isAutoSignIn: Boolean, onResult: ((Boolean) -> Unit)?) {
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

    val previousEmail = googleUserEmail
    val wasCloudSyncEnabled = isCloudSyncEnabled

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

                val switchedAccount = previousEmail.isNotBlank() && !previousEmail.equals(user.email, ignoreCase = true)
                if (switchedAccount) {
                    if (wasCloudSyncEnabled) {
                        // Sync on: the library is replaced by the new account's cloud library (see adoptCloudAccount)
                        refreshCloudSyncImpl(context, showModalProgress = true)
                    } else {
                        // Sync off: keep it off; turning it on later uploads the current library to the new account
                        saveCloudSyncState(context, enabled = false, hasError = false, errorMsg = null)
                    }
                }

                markServerAuthenticated(context)

                if (isBilledServer) {
                    refreshUserBalance(context)
                    // A billed server tells the cost only to signed-in users
                    recalculateCostImpl()
                }

                pendingSignInAction?.invoke()
                pendingSignInAction = null
                onResult?.invoke(true)
            },
            onFailure = { err ->
                pendingSignInAction = null
                isGoogleAuthLoading = false

                if (isBilledServer) {
                    markServerUnauthenticated(context, activeServerUrl)
                }

                when (err) {
                    is NoCredentialException -> {
                        // no Google account on the device
                        googleAuthError = null
                        showNoGoogleAccountDialog = true
                        onResult?.invoke(false)
                    }
                    is GetCredentialCancellationException -> {
                        signInCancelEvent++
                        if (isAutoSignIn && isBilledServer) {
                            showBilledServerGoogleAccountDialog = true
                        }
                        onResult?.invoke(false)
                    }
                    else -> {
                        googleAuthError = err.localizedMessage ?: "Google sign-in failed."
                        if (isAutoSignIn && isBilledServer) {
                            showBilledServerGoogleAccountDialog = true
                        }
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
    isCloudSyncEnabled = false
    AppPrefs.get(context).edit().putBoolean(AppPrefs.IS_CLOUD_SYNC_ENABLED, false).apply()
    if (isBilledServer) {
        userCredits = 0
        costJob?.cancel()
        estimatedCostCredits = null
        isCalculatingCost = false
        markServerUnauthenticated(context, activeServerUrl)
    }
}

internal fun PianoWeaveViewModel.withEnsuredSignInImpl(context: Context, action: () -> Unit) {
    if (isGoogleSignedIn) {
        action()
    } else {
        pendingSignInAction = action
        signInWithGoogle(context)
    }
}

internal suspend fun PianoWeaveViewModel.ensureGoogleAuthTokenImpl(context: Context): String? {
    if (googleToken.isNotBlank()) return googleToken
    val clientId = activeGoogleClientId
    if (clientId.isBlank()) return null

    val result = GoogleAuthManager.signIn(context, clientId, filterByAuthorizedAccounts = true)
    return result.getOrNull()?.let { user ->
        googleToken = user.token
        googleUserEmail = user.email
        user.token
    }
}

/** Transcription-server call; see [executeServerCall]. Also picks up a token refreshed during the call. */
internal suspend fun <T> PianoWeaveViewModel.executeServerCallImpl(
    context: Context,
    serverUrl: String = activeServerUrl,
    apiCall: suspend (authHeader: String?) -> T
): T {
    val result = executeServerCall(
        context = context,
        serverUrl = serverUrl,
        initialAuthHeader = GoogleAuthManager.getAuthHeader(googleToken),
        apiCall = apiCall
    )
    val freshToken = GoogleAuthManager.getSavedToken(context)
    if (!freshToken.isNullOrBlank() && freshToken != googleToken) {
        googleToken = freshToken
        googleUserEmail = GoogleAuthManager.getSavedEmail(context) ?: googleUserEmail
    }
    return result
}
