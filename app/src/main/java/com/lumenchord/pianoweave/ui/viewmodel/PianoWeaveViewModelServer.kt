package com.lumenchord.pianoweave.ui.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.lumenchord.pianoweave.api.PianoApiFactory
import com.lumenchord.pianoweave.api.VerifyPurchaseResponse
import com.lumenchord.pianoweave.api.config.AppConfig
import com.lumenchord.pianoweave.auth.GoogleAuthManager
import com.lumenchord.pianoweave.billing.PendingPurchase
import com.lumenchord.pianoweave.billing.PendingPurchaseStore
import com.lumenchord.pianoweave.billing.verify
import com.lumenchord.pianoweave.util.AppPrefs
import com.lumenchord.pianoweave.util.findActivity
import com.lumenchord.pianoweave.worker.PurchaseVerificationWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.UUID

internal fun PianoWeaveViewModel.formatRetentionTimeImpl(seconds: Long? = cleanupIntervalSeconds): String {
    val s = seconds ?: 86400L
    val hours = s / 3600
    val mins = s / 60
    return when {
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

internal suspend fun PianoWeaveViewModel.testServerConnectionImpl(url: String): ServerStatus = withContext(Dispatchers.IO) {
    if (url.isBlank()) return@withContext ServerStatus.OFFLINE
    try {
        val api = PianoApiFactory.getApi(url)
        val response = api.checkHealth()
        if (response.code() > 0) {
            ServerStatus.ONLINE
        } else {
            ServerStatus.OFFLINE
        }
    } catch (_: Exception) {
        ServerStatus.OFFLINE
    }
}

internal fun PianoWeaveViewModel.startAutoReconnectImpl(context: Context? = null) {
    if (context != null) {
        appContext = context.applicationContext
    }
    if (autoReconnectJob?.isActive == true) return
    autoReconnectJob = viewModelScope.launch {
        while (serverStatus == ServerStatus.OFFLINE) {
            delay(10000L)
            if (serverStatus != ServerStatus.OFFLINE) break
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
    viewModelScope.launch {
        serverStatus = ServerStatus.CHECKING
        serverStatus = testServerConnectionImpl(url)
        val targetContext = context ?: appContext
        if (serverStatus == ServerStatus.ONLINE) {
            autoReconnectJob?.cancel()
            autoReconnectJob = null
            if (targetContext != null) {
                try {
                    val api = PianoApiFactory.getApi(url)
                    val info = api.getServerInfo()
                    billingProvider = info.billingProvider ?: "none"
                    serverOffers = info.offers ?: emptyList()
                    cleanupIntervalSeconds = info.cleanupIntervalSeconds
                    maxUploadSizeMb = info.maxUploadSizeMb
                    maxVideoLengthMinutes = info.maxVideoLengthMinutes
                    freeMinutes = info.freeMinutes ?: 0

                    supportMeLink = try {
                        AppConfig.initialize(targetContext)
                        AppConfig.getConfig().supportMeLink
                    } catch (_: Exception) {
                        null
                    }

                    if (isBilledServer) {
                        if (isGoogleSignedIn) {
                            refreshUserBalanceImpl(targetContext)
                        } else if (isUserAction) {
                            signInWithGoogleImpl(targetContext, isAutoSignIn = true, onResult = null)
                        } else {
                            refreshUserBalanceImpl(targetContext)
                        }
                    }
                } catch (_: Exception) {
                    billingProvider = "none"
                    maxUploadSizeMb = null
                }
            }
        } else if (serverStatus == ServerStatus.OFFLINE) {
            startAutoReconnectImpl(targetContext)
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
    val url = activeServerUrl
    val uId = getOrCreateUserIdImpl(context)
    viewModelScope.launch {
        try {
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
        } catch (_: Exception) {}
    }
}

internal fun PianoWeaveViewModel.loadPreferencesImpl(context: Context) {
    val prefs = AppPrefs.get(context)
    getOrCreateUserIdImpl(context)
    isStrikeOverlayEnabled = prefs.getBoolean(AppPrefs.IS_STRIKE_OVERLAY_ENABLED, true)
    selectedThemeId = prefs.getString(AppPrefs.SELECTED_THEME_ID, "gold") ?: "gold"
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
    isCustomServer = prefs.getBoolean(AppPrefs.USE_CUSTOM_SERVER, false)
    customServerUrl = prefs.getString(AppPrefs.CUSTOM_SERVER_URL, "") ?: ""

    // Schedule background retries for any pending purchase verifications across app restarts
    PurchaseVerificationWorker.scheduleAllPending(context)

    if (context.findActivity() != null) {
        checkServerHealthAndInfoImpl(context, isUserAction = false)
    }
}

internal fun PianoWeaveViewModel.openShopImpl(context: Context) {
    withEnsuredSignInImpl(context) { showShopDialog = true }
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
        // Received an HTTP status code response from server (4xx/5xx). That counts as an answer from server!
        PendingPurchaseStore.removePendingPurchase(context, purchaseToken)
        true
    } catch (_: Exception) {
        // Network error / server unreachable (no answer from server).
        // The purchase stays saved in PendingPurchaseStore and PurchaseVerificationWorker will retry in the background.
        false
    }
}
