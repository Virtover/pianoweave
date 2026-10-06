package com.lumenchord.pianoweave.auth

import android.accounts.Account
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class GoogleAuthUser(
    val email: String,
    val token: String, // Google OAuth ACCESS token (sent to the server as the Bearer token)
    val displayName: String? = null
)

/**
 * Thrown when Google needs the user to grant consent (UI required), so a silent
 * refresh is impossible. [pendingIntent] can be launched by an Activity if you want
 * to resolve it; otherwise fall back to signIn().
 */
class UserInteractionRequiredException(val pendingIntent: PendingIntent?) :
    Exception("Google authorization needs user interaction.")

object GoogleAuthManager {

    private const val PREFS_NAME = "piano_weave_prefs"
    private const val KEY_TOKEN = "google_token"
    private const val KEY_USER_EMAIL = "google_user_email"

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    fun getSavedToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TOKEN, null)
    }

    fun getSavedEmail(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_EMAIL, null)
    }

    fun saveAuthData(context: Context, token: String?, email: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_EMAIL, email)
            .apply()
    }

    fun clearAuthData(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_EMAIL)
            .apply()
    }

    fun getAuthHeader(token: String?): String? {
        return if (!token.isNullOrBlank()) "Bearer $token" else null
    }

    private suspend fun <T> awaitTask(task: Task<T>): T = suspendCancellableCoroutine { cont ->
        task.addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
            .addOnCanceledListener { cont.cancel() }
    }

    /**
     * Gets a fresh access token from AuthorizationClient. Returns immediately with no UI
     * if the user already granted the scopes; throws UserInteractionRequiredException otherwise.
     */
    private suspend fun fetchAccessToken(context: Context, email: String?): String {
        val client = Identity.getAuthorizationClient(context)

        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(Scope(Scopes.OPEN_ID), Scope(Scopes.EMAIL), Scope(Scopes.PROFILE))
            )
        if (!email.isNullOrBlank()) {
            builder.setAccount(Account(email, "com.google")) // pin to the known account
        }

        val result = awaitTask(
            Identity.getAuthorizationClient(context).authorize(builder.build())
        )
        if (result.hasResolution()) {
            throw UserInteractionRequiredException(result.pendingIntent)
        }
        return result.accessToken ?: throw IllegalStateException("No access token returned.")
    }

    // webClientId is unused now (AuthorizationClient identifies the app by package + SHA-1),
    // but the signature is kept so call sites don't change.
    suspend fun silentRefresh(
        context: Context,
        webClientId: String
    ): Result<GoogleAuthUser> = withContext(Dispatchers.Main) {
        val savedEmail = getSavedEmail(context)
            ?: return@withContext Result.failure(IllegalStateException("No previously signed-in user."))

        try {
            val accessToken = fetchAccessToken(context, savedEmail)
            val user = GoogleAuthUser(
                email = savedEmail,
                token = accessToken,
                displayName = null
            )

            saveAuthData(context, user.token, user.email)

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signIn(
        context: Context,
        webClientId: String,
        filterByAuthorizedAccounts: Boolean = false
    ): Result<GoogleAuthUser> = withContext(Dispatchers.Main) {
        if (webClientId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Google Web Client ID is not configured."))
        }

        val activity = context.findActivity() ?: context
        val credentialManager = CredentialManager.create(context)

        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(filterByAuthorizedAccounts)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                context = activity,
                request = request
            )

            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
            val email = googleIdTokenCredential.email ?: googleIdTokenCredential.uniqueId

            // Credential Manager only proves who the user is. The server wants an access token.
            val accessToken = fetchAccessToken(context, email)

            val user = GoogleAuthUser(
                email = email,
                token = accessToken,
                displayName = googleIdTokenCredential.displayName
            )

            saveAuthData(context, user.token, user.email)
            Result.success(user)
        } catch (e: GetCredentialCancellationException) {
            Result.failure(e)
        } catch (e: GetCredentialException) {
            // If filterByAuthorizedAccounts was true and failed, retry with false
            if (filterByAuthorizedAccounts) {
                return@withContext signIn(context, webClientId, filterByAuthorizedAccounts = false)
            }
            val msg = e.message ?: ""
            if (msg.contains("28444") || msg.contains("Developer console", ignoreCase = true)) {
                Result.failure(
                    IllegalStateException(
                        "Google Cloud Console setup error [28444]: Ensure the Web Client ID is correct and the SHA-1 signing fingerprint for ${context.packageName} is added in Google Cloud Console.",
                        e
                    )
                )
            } else {
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}