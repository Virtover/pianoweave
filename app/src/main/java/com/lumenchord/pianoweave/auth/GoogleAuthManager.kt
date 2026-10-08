package com.lumenchord.pianoweave.auth

import android.accounts.Account
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import com.lumenchord.pianoweave.api.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import retrofit2.HttpException
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
        if (token.isNullOrBlank()) return null
        return if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
    }

    fun getActiveClientId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val customClientId = prefs.getString("custom_google_client_id", "") ?: ""
        if (customClientId.isNotBlank()) return customClientId

        return try {
            AppConfig.initialize(context)
            AppConfig.getConfig().webClientId ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun <T> executeWithAuthRetry(
        context: Context,
        initialAuthHeader: String? = null,
        apiCall: suspend (authHeader: String?) -> T
    ): T {
        var authHeader = if (!initialAuthHeader.isNullOrBlank()) {
            getAuthHeader(initialAuthHeader)
        } else {
            getAuthHeader(getSavedToken(context))
        }

        try {
            return apiCall(authHeader)
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 503) {
                val clientId = getActiveClientId(context)
                if (clientId.isNotBlank()) {
                    if (authHeader.isNullOrBlank()) signIn(context, clientId)
                    val refreshResult = silentRefresh(context, clientId)
                    if (refreshResult.isSuccess) {
                        val user = refreshResult.getOrNull()
                        if (user != null) {
                            authHeader = getAuthHeader(user.token)
                            return apiCall(authHeader)
                        }
                    }
                }
            }
            throw e
        }
    }

    private suspend fun <T> awaitTask(task: Task<T>): T = suspendCancellableCoroutine { cont ->
        task.addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
            .addOnCanceledListener { cont.cancel() }
    }

    private suspend fun resolvePendingIntent(
        activity: Activity,
        pendingIntent: PendingIntent
    ): String = suspendCancellableCoroutine { cont ->
        if (activity !is ComponentActivity) {
            cont.resumeWithException(UserInteractionRequiredException(pendingIntent))
            return@suspendCancellableCoroutine
        }

        val registry = activity.activityResultRegistry
        val key = "google_auth_resolution_${System.currentTimeMillis()}"

        var launcher: ActivityResultLauncher<IntentSenderRequest>? = null

        launcher = registry.register(
            key,
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            try {
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    val authResult = Identity.getAuthorizationClient(activity)
                        .getAuthorizationResultFromIntent(result.data)
                    val token = authResult.accessToken
                    if (!token.isNullOrBlank()) {
                        cont.resume(token)
                    } else {
                        cont.resumeWithException(IllegalStateException("No access token returned after resolution."))
                    }
                } else {
                    cont.resumeWithException(IllegalStateException("Google authorization was cancelled or denied."))
                }
            } catch (e: Exception) {
                cont.resumeWithException(e)
            } finally {
                launcher?.unregister()
            }
        }

        try {
            val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            launcher.launch(request)
        } catch (e: Exception) {
            launcher.unregister()
            cont.resumeWithException(e)
        }
    }

    /**
     * Gets a fresh access token from AuthorizationClient. Returns immediately with no UI
     * if the user already granted the scopes; resolves pending consent resolution otherwise.
     */
    private suspend fun fetchAccessToken(context: Context, email: String?): String {
        val client = Identity.getAuthorizationClient(context)

        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope(Scopes.OPEN_ID),
                    Scope(Scopes.EMAIL),
                    Scope(Scopes.PROFILE),
                    Scope(Scopes.DRIVE_APPFOLDER)
                )
            )
        if (!email.isNullOrBlank()) {
            builder.setAccount(Account(email, "com.google")) // pin to the known account
        }

        val result = awaitTask(client.authorize(builder.build()))

        if (result.hasResolution()) {
            val activity = context.findActivity()
            val pendingIntent = result.pendingIntent
            if (activity != null && pendingIntent != null) {
                return resolvePendingIntent(activity, pendingIntent)
            } else {
                throw UserInteractionRequiredException(pendingIntent)
            }
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