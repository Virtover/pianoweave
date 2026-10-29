package com.lumenchord.pianoweave.ui.viewmodel

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.lumenchord.pianoweave.api.ConnectionErrors
import com.lumenchord.pianoweave.api.MaintenanceNotice
import com.lumenchord.pianoweave.api.PianoApiFactory
import com.lumenchord.pianoweave.api.ServerMaintenance
import com.lumenchord.pianoweave.api.VerifyPurchaseResponse
import com.lumenchord.pianoweave.api.config.AppConfig
import com.lumenchord.pianoweave.auth.GoogleAuthManager
import com.lumenchord.pianoweave.billing.PendingPurchase
import com.lumenchord.pianoweave.billing.PendingPurchaseStore
import com.lumenchord.pianoweave.billing.isVerificationRetryable
import com.lumenchord.pianoweave.billing.verify
import com.lumenchord.pianoweave.ui.theme.AppThemeManager
import com.lumenchord.pianoweave.midi.MidiInputManager
import com.lumenchord.pianoweave.util.AppPrefs
import com.lumenchord.pianoweave.util.Connectivity
import com.lumenchord.pianoweave.util.SecretStore
import com.lumenchord.pianoweave.util.findActivity
import com.lumenchord.pianoweave.worker.PurchaseVerificationWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.UUID

/** Servers from before `result_retention_seconds` kept finished jobs for a fixed hour. */
private const val LEGACY_RESULT_RETENTION_SECONDS = 3600L

internal fun PianoWeaveViewModel.formatRetentionTimeImpl(seconds: Long? = resultRetentionSeconds): String {
    val s = seconds ?: LEGACY_RESULT_RETENTION_SECONDS
    val days = s / 86400
    val hours = s / 3600
    val mins = s / 60
    return when {
        days >= 1 && s % 86400 == 0L -> "$days day${if (days > 1) "s" else ""}"
        hours >= 1 -> "$hours hour${if (hours > 1) "s" else ""}"
        mins >= 1 -> "$mins minute${if (mins > 1) "s" else ""}"
        else -> "$s seconds"
    }
}

internal fun PianoWeaveViewModel.getOrCreateUserIdImpl(context: Context): String {
    if (userId.isNotBlank()) return userId
    val prefs = AppPrefs.get(context)
    val id = prefs.getString(AppPrefs.USER_ID, null).takeUnless { it.isNullOrBlank() }
        ?: UUID.randomUUID().toString().also { prefs.edit().putString(AppPrefs.USER_ID, it).apply() }
    userId = id
    return id
}

/** Checks [url]'s /health; [onError] receives the failure when no answer came at all. */
internal suspend fun PianoWeaveViewModel.testServerConnectionImpl(
    url: String,
    onError: (Exception) -> Unit = {}
): ServerStatus = withContext(Dispatchers.IO) {
    if (url.isBlank()) return@withContext ServerStatus.OFFLINE
    try {
        val api = PianoApiFactory.getApi(url)
        val response = api.checkHealth()
        when {
            // The saved password, if any, went along: this means it's missing or wrong
            PianoApiFactory.isServerPasswordRequired(response.code(), response.headers()) -> ServerStatus.PASSWORD_REQUIRED
            // Only the API's own /health answer counts: a proxy in front of a down server still answers
            // (Cloudflare Tunnel with 502/530), and so does any other site at a mistyped address (404)
            response.isSuccessful -> ServerStatus.ONLINE
            else -> ServerStatus.OFFLINE
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        onError(e)
        if (!isOnline()) ServerStatus.NO_INTERNET else ServerStatus.OFFLINE
    }
}

/** What the startup connection dialog should explain for [status], or null when the server is usable. */
private fun PianoWeaveViewModel.connectionProblemFor(status: ServerStatus, error: Exception?): ServerConnectionProblem? {
    val notice = ServerMaintenance.notice
    return when (status) {
        ServerStatus.ONLINE, ServerStatus.CHECKING, ServerStatus.UNKNOWN -> null
        ServerStatus.PASSWORD_REQUIRED -> ServerConnectionProblem.PasswordRequired
        ServerStatus.NO_INTERNET -> ServerConnectionProblem.NoInternet
        ServerStatus.OFFLINE ->
            if (notice != null && notice.hasStarted(System.currentTimeMillis() / 1000)) {
                ServerConnectionProblem.Maintenance(notice)
            } else {
                ServerConnectionProblem.Unreachable(error?.let { connectionMessage(it) })
            }
    }
}

private fun PianoWeaveViewModel.isOnline(): Boolean = appContext?.let(Connectivity::isOnline) ?: true

/**
 * [ConnectionErrors.message] for a request that got no answer: from the selected server, or from [target]
 * (such as "Google Drive"). Null if [e] is not a connection failure.
 */
internal fun PianoWeaveViewModel.connectionMessage(e: Throwable, target: String? = null): String? =
    if (target != null) ConnectionErrors.message(e, isOnline(), target)
    else ConnectionErrors.message(
        e,
        isOnline(),
        target = if (isUsingDefaultServer) "the server" else "this server",
        isCustomServer = !isUsingDefaultServer
    )

private val ServerStatus.isUnreachable get() = this == ServerStatus.OFFLINE || this == ServerStatus.NO_INTERNET

internal fun PianoWeaveViewModel.startAutoReconnectImpl(context: Context? = null) {
    if (context != null) {
        appContext = context.applicationContext
    }
    if (autoReconnectJob?.isActive == true) return
    autoReconnectJob = viewModelScope.launch {
        while (serverStatus.isUnreachable) {
            delay(reconnectIntervalMs(ServerMaintenance.notice))
            if (!serverStatus.isUnreachable) break
            val targetContext = appContext ?: context
            checkServerHealthAndInfoImpl(targetContext, isUserAction = false, fromAutoReconnect = true)
        }
    }
}

internal fun PianoWeaveViewModel.checkServerHealthAndInfoImpl(
    context: Context? = null,
    isUserAction: Boolean = false,
    fromAutoReconnect: Boolean = false
) {
    if (context != null) {
        appContext = context.applicationContext
    }
    if (!fromAutoReconnect) {
        autoReconnectJob?.cancel()
        autoReconnectJob = null
    }

    val url = activeServerUrl
    ServerMaintenance.watch(url)
    startMaintenanceWatch()
    // Only the newest check counts: typing a server URL checks on every key, and an older check
    // answering last would otherwise show the status of a half-typed address
    serverCheckJob?.cancel()
    // The check MainActivity runs on opening; the earlier one from loadPreferences has no Activity
    val isStartupCheck = isInitialConnection && context is Activity
    serverCheckJob = viewModelScope.launch {
        serverStatus = ServerStatus.CHECKING
        var connectError: Exception? = null
        serverStatus = testServerConnectionImpl(url) { connectError = it }
        val targetContext = context ?: appContext
        if (serverStatus == ServerStatus.ONLINE) {
            autoReconnectJob?.cancel()
            autoReconnectJob = null
            if (targetContext != null) {
                try {
                    val api = PianoApiFactory.getApi(url)
                    val info = api.getServerInfo()
                    ServerMaintenance.onNotice(url, info.maintenance)
                    billingProvider = info.billingProvider ?: "none"
                    serverOffers = info.offers ?: emptyList()
                    resultRetentionSeconds = info.resultRetentionSeconds
                    maxUploadSizeMb = info.maxUploadSizeMb
                    maxVideoLengthMinutes = info.maxVideoLengthMinutes
                    freeMinutes = info.freeMinutes ?: 0
                    if (PianoApiFactory.isOfficialServer(url)) isOfficialServerBilled = isBilledServer


                    if (isBilledServer) {
                        if (isGoogleSignedIn) {
                            refreshUserBalanceImpl(targetContext)
                            // Billing is known only now; a link or file chosen before has no cost yet
                            if (estimatedCostCredits == null) recalculateCostImpl()
                        } else if (isUserAction) {
                            signInWithGoogleImpl(targetContext, isAutoSignIn = true, onResult = null)
                        } else if (context is Activity &&
                            !AppPrefs.get(context).getBoolean(AppPrefs.HAS_PROMPTED_BILLED_SERVER_SIGN_IN, false)
                        ) {
                            // First ever connection to a billed server: offer sign-in once. Needs an
                            // Activity for the account picker, so background reconnects wait for the next check.
                            AppPrefs.get(context).edit()
                                .putBoolean(AppPrefs.HAS_PROMPTED_BILLED_SERVER_SIGN_IN, true).apply()
                            signInWithGoogleImpl(context, isAutoSignIn = true, onResult = null)
                        } else {
                            refreshUserBalanceImpl(targetContext)
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    billingProvider = "none"
                    maxUploadSizeMb = null
                    if (PianoApiFactory.isServerPasswordRequired(e)) serverStatus = ServerStatus.PASSWORD_REQUIRED
                }
            }
        } else if (serverStatus.isUnreachable) {
            startAutoReconnectImpl(targetContext)
        }
        // Explained once on opening; while that dialog is up, later checks (auto-reconnect, "Try again")
        // keep it current and close it once the server is back
        if (isStartupCheck || startupConnectionProblem != null) {
            isInitialConnection = false
            startupConnectionProblem = connectionProblemFor(serverStatus, connectError)
        }
    }
    refreshOfficialServerBilling()
}

/**
 * How often to look for the server while it cannot be reached. After an announced shutdown it stays
 * offline for a while, so it is checked only now and then.
 */
private fun reconnectIntervalMs(notice: MaintenanceNotice?): Long {
    val nowSec = System.currentTimeMillis() / 1000
    return if (notice != null && notice.isShutdown && notice.hasStarted(nowSec)) 60_000L else 10_000L
}

/**
 * While a restart or shutdown is announced, asks the server now and then, so the notice follows a
 * reschedule or cancellation and disappears once the server is back. Nothing runs while none is pending.
 */
private fun PianoWeaveViewModel.startMaintenanceWatch() {
    if (maintenanceWatchJob?.isActive == true) return
    maintenanceWatchJob = viewModelScope.launch {
        while (true) {
            val notice = ServerMaintenance.notice ?: snapshotFlow { ServerMaintenance.notice }.filterNotNull().first()
            val nowSec = System.currentTimeMillis() / 1000
            delay(
                when {
                    !notice.hasStarted(nowSec) -> 30_000L
                    notice.isShutdown -> 60_000L
                    else -> 10_000L
                }
            )
            val url = activeServerUrl
            try {
                val info = PianoApiFactory.getApi(url).getServerInfo()
                ServerMaintenance.onNotice(url, info.maintenance)
                // Back after the restart or shutdown: reload everything that failed meanwhile
                if (serverStatus != ServerStatus.ONLINE) checkServerHealthAndInfoImpl(appContext)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }
}

/** Asks the official server whether it bills, also while a custom server is selected (see [PianoWeaveViewModel.canShowSupport]). */
private fun PianoWeaveViewModel.refreshOfficialServerBilling() {
    // With the official server selected, its server-info above already answers this.
    if (isOfficialServerBilled != null || isUsingDefaultServer) return
    val officialUrl = defaultServerUrl.takeIf { it.isNotBlank() } ?: return
    viewModelScope.launch {
        isOfficialServerBilled = try {
            PianoApiFactory.getApi(officialUrl).getServerInfo()
                .billingProvider.equals("google_play", ignoreCase = true)
        } catch (_: Exception) {
            null
        }
    }
}

internal fun PianoWeaveViewModel.startBalanceTicker(context: Context) {
    balanceTickerJob?.cancel()
    balanceTickerJob = viewModelScope.launch {
        while (isBilledServer) {
            val nextGrant = freeMinutesNextGrantAt
            if (nextGrant != null && nextGrant > 0) {
                val nowSec = System.currentTimeMillis() / 1000
                val remaining = (nextGrant - nowSec).coerceAtLeast(0L)
                freeMinutesSecondsUntilNextGrant = remaining
                if (remaining <= 0L) {
                    refreshUserBalanceImpl(context)
                    delay(10000)
                    continue
                }
            }
            delay(1000)
        }
    }
}

internal fun PianoWeaveViewModel.refreshUserBalanceImpl(context: Context) {
    if (!isBilledServer) return
    viewModelScope.launch { loadUserBalance(context) }
}

/** Asks the server for the balance and shows it; returns it, or null if the server couldn't be asked. */
internal suspend fun PianoWeaveViewModel.loadUserBalance(context: Context): Int? {
    if (!isBilledServer) return null
    val url = activeServerUrl
    val uId = getOrCreateUserIdImpl(context)
    return try {
        val balance = executeServerCallImpl(context, url) { authHeader ->
            val api = PianoApiFactory.getApi(url)
            api.getUserBalance(
                authHeader = authHeader,
                userId = if (authHeader == null) uId else null
            )
        }
        userCredits = balance.minutes
        freeMinutesSecondsUntilNextGrant = balance.freeMinutesSecondsUntilNextGrant
        freeMinutesNextGrantAt = balance.freeMinutesNextGrantAt
        startBalanceTicker(context)
        balance.minutes
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }
}

internal fun PianoWeaveViewModel.loadPreferencesImpl(context: Context) {
    val prefs = AppPrefs.get(context)
    getOrCreateUserIdImpl(context)
    isStrikeOverlayEnabled = prefs.getBoolean(AppPrefs.IS_STRIKE_OVERLAY_ENABLED, true)
    isKeyboardSoundEnabled = prefs.getBoolean(AppPrefs.IS_KEYBOARD_SOUND_ENABLED, true)
    MidiInputManager.isSoundEnabled = isKeyboardSoundEnabled
    // A theme merged into another resolves to its replacement, so the picker shows it as selected
    selectedThemeId = AppThemeManager.resolveId(prefs.getString(AppPrefs.SELECTED_THEME_ID, null)) ?: AppThemeManager.defaultTheme.id
    topBarSpeed = prefs.getFloat(AppPrefs.TOP_BAR_SPEED, 1.0f)
    isCloudSyncEnabled = prefs.getBoolean(AppPrefs.IS_CLOUD_SYNC_ENABLED, false)
    hasCloudSyncError = prefs.getBoolean(AppPrefs.HAS_CLOUD_SYNC_ERROR, false)
    cloudSyncError = prefs.getString(AppPrefs.CLOUD_SYNC_ERROR, null)

    googleToken = GoogleAuthManager.getSavedToken(context) ?: ""
    googleUserEmail = GoogleAuthManager.getSavedEmail(context) ?: ""
    customGoogleClientId = prefs.getString(AppPrefs.CUSTOM_GOOGLE_CLIENT_ID, "") ?: ""
    savedUnauthenticatedServerUrl = prefs.getString(AppPrefs.LAST_BILLED_SERVER_UNAUTHENTICATED, "") ?: ""
    isInitialConnection = true

    defaultServerUrl = try {
        AppConfig.initialize(context)
        AppConfig.getConfig().baseUrl
    } catch (_: Exception) {
        ""
    }
    supportMeLink = AppConfig.getConfigOrNull()?.supportMeLink
    isCustomServer = prefs.getBoolean(AppPrefs.USE_CUSTOM_SERVER, false)
    customServerUrl = prefs.getString(AppPrefs.CUSTOM_SERVER_URL, "") ?: ""
    customServerPassword = SecretStore.get(context, AppPrefs.CUSTOM_SERVER_PASSWORD).orEmpty()

    // Schedule background retries for any pending purchase verifications across app restarts
    PurchaseVerificationWorker.scheduleAllPending(context)

    if (context.findActivity() != null) {
        checkServerHealthAndInfoImpl(context, isUserAction = false)
    }
}

internal fun PianoWeaveViewModel.openShopImpl(context: Context) {
    withEnsuredSignInImpl(context) { showShopDialog = true }
}

/** Saves the custom server's password (blank removes it) and checks the server again with it. */
internal fun PianoWeaveViewModel.updateServerPasswordImpl(context: Context, password: String) {
    if (isLoading) return
    customServerPassword = password
    SecretStore.put(context, AppPrefs.CUSTOM_SERVER_PASSWORD, password)
    if (isCustomServer) checkServerHealthAndInfoImpl(context, isUserAction = true)
}

internal fun PianoWeaveViewModel.updateServerSettingsImpl(context: Context, useCustom: Boolean, customUrl: String, customClientId: String = customGoogleClientId) {
    if (isLoading) return
    var formattedUrl = customUrl.trim()
    if (formattedUrl.isNotEmpty()) {
        if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
            formattedUrl = "http://$formattedUrl"
        }
        if (!formattedUrl.endsWith("/")) {
            formattedUrl = "$formattedUrl/"
        }
    }
    isCustomServer = useCustom
    customServerUrl = formattedUrl
    customGoogleClientId = customClientId.trim()

    showShopDialog = false
    userCredits = 0
    serverOffers = emptyList()
    billingProvider = "none"

    AppPrefs.get(context).edit()
        .putBoolean(AppPrefs.USE_CUSTOM_SERVER, useCustom)
        .putString(AppPrefs.CUSTOM_SERVER_URL, formattedUrl)
        .putString(AppPrefs.CUSTOM_GOOGLE_CLIENT_ID, customGoogleClientId)
        .apply()

    checkServerHealthAndInfoImpl(context, isUserAction = true)
}

internal suspend fun PianoWeaveViewModel.verifyGooglePlayPurchaseImpl(
    context: Context,
    productId: String,
    purchaseToken: String
): Boolean = withContext(Dispatchers.IO) {
    val purchase = PendingPurchase(
        productId = productId,
        purchaseToken = purchaseToken,
        serverUrl = activeServerUrl,
        userId = getOrCreateUserIdImpl(context)
    )

    // Persist first so PurchaseVerificationWorker keeps retrying if the server can't answer now
    PurchaseVerificationWorker.scheduleVerification(context, purchase)

    suspend fun callVerify(): VerifyPurchaseResponse = executeServerCallImpl(context, purchase.serverUrl) { authHeader ->
        PianoApiFactory.getApi(purchase.serverUrl).verify(purchase, authHeader)
    }

    try {
        var resp = callVerify()
        // Server answered! Remove from pending store.
        PendingPurchaseStore.removePendingPurchase(context, purchaseToken)

        withContext(Dispatchers.Main) { userCredits = resp.minutes }
        var retries = 0
        while (!resp.consumed && retries < 2) {
            retries++
            delay(1500L * retries)
            try {
                resp = callVerify()
                withContext(Dispatchers.Main) { userCredits = resp.minutes }
            } catch (_: Exception) { }
        }
        true
    } catch (e: HttpException) {
        // No answer about the purchase yet: keep it pending, PurchaseVerificationWorker retries in the background
        if (isVerificationRetryable(e)) return@withContext false
        // Any other HTTP status code from the server counts as an answer.
        PendingPurchaseStore.removePendingPurchase(context, purchaseToken)
        true
    } catch (_: Exception) {
        // Network error / server unreachable (no answer from server).
        // The purchase stays saved in PendingPurchaseStore and PurchaseVerificationWorker will retry in the background.
        false
    }
}
