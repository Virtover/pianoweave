package com.lumenchord.pianoweave.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Base64
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom

data class GoogleAuthUser(
    val email: String,
    val idToken: String,
    val displayName: String? = null
)

object GoogleAuthManager {

    private const val PREFS_NAME = "piano_weave_prefs"
    private const val KEY_ID_TOKEN = "google_id_token"
    private const val KEY_USER_EMAIL = "google_user_email"

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    fun getSavedIdToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ID_TOKEN, null)
    }

    fun getSavedEmail(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_EMAIL, null)
    }

    fun saveAuthData(context: Context, idToken: String?, email: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_ID_TOKEN, idToken)
            .putString(KEY_USER_EMAIL, email)
            .apply()
    }

    fun clearAuthData(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_ID_TOKEN)
            .remove(KEY_USER_EMAIL)
            .apply()
    }

    fun getAuthHeader(token: String?): String? {
        return if (!token.isNullOrBlank()) "Bearer $token" else null
    }

    private fun generateSecureRandomNonce(byteLength: Int = 32): String {
        val bytes = ByteArray(byteLength)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
        )
    }

    suspend fun silentRefresh(
        context: Context,
        webClientId: String
    ): Result<GoogleAuthUser> = withContext(Dispatchers.Main) {
        if (webClientId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Google Web Client ID is not configured."))
        }
        Log.d("SilentRefresh", "context: $context")
        val activity = context.findActivity()
        if (activity == null) {
            Log.d("SilentRefresh", "Activity context is required for Google authentication.")
            return@withContext Result.failure(IllegalStateException("Activity context is required for Google authentication."))
        }
        val credentialManager = CredentialManager.create(context)

        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .setNonce(generateSecureRandomNonce())
                .build()
            Log.d("SilentRefresh", "googleIdOption: $googleIdOption")
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            Log.d("SilentRefresh", "request: $request")
            val result = credentialManager.getCredential(
                context = activity,
                request = request
            )
            Log.d("SilentRefresh", "result: $result")
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
            Log.d("SilentRefresh", "googleIdTokenCredential: $googleIdTokenCredential")
            val user = GoogleAuthUser(
                email = googleIdTokenCredential.id,
                idToken = googleIdTokenCredential.idToken,
                displayName = googleIdTokenCredential.displayName
            )
            Log.d("SilentRefresh", "user: $user")
            saveAuthData(context, user.idToken, user.email)
            Log.d("SilentRefresh", "Result.success(user)")
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
            val user = GoogleAuthUser(
                email = googleIdTokenCredential.id,
                idToken = googleIdTokenCredential.idToken,
                displayName = googleIdTokenCredential.displayName
            )

            saveAuthData(context, user.idToken, user.email)
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
