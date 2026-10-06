package com.lumenchord.pianoweave.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.viewModelScope
import com.lumenchord.pianoweave.api.PianoApiFactory
import com.lumenchord.pianoweave.api.VerifyPurchaseRequest
import com.lumenchord.pianoweave.api.config.AppConfig
import com.lumenchord.pianoweave.auth.GoogleAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val prefs = context.getSharedPreferences("piano_weave_prefs", Context.MODE_PRIVATE)
    var id = prefs.getString("user_id", null)
    if (id.isNullOrBlank()) {
        id = UUID.randomUUID().toString()
        prefs.edit().putString("user_id", id).apply()
    }
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

internal fun PianoWeaveViewModel.checkServerHealthAndInfoImpl(context: Context? = null) {
    val url = activeServerUrl
    viewModelScope.launch {
        serverStatus = ServerStatus.CHECKING
        serverStatus = testServerConnectionImpl(url)
        if (serverStatus == ServerStatus.ONLINE && context != null) {
            try {
                val api = PianoApiFactory.getApi(url)
                val info = api.getServerInfo()
                billingProvider = info.billingProvider ?: "none"
                serverOffers = info.offers ?: emptyList()
                cleanupIntervalSeconds = info.cleanupIntervalSeconds
                freeMinutes = info.freeMinutes ?: 0

                supportMeLink = try {
                    AppConfig.initialize(context)
                    AppConfig.getConfig().supportMeLink
                } catch (_: Exception) {
                    null
                }

                if (isBilledServer) {
                    val appClientId = activeGoogleClientId

                    val token = ensureGoogleAuthTokenImpl(context)
                    if (token.isNullOrBlank() && appClientId.isNotBlank()) {
                        signInWithGoogleImpl(context, null)
                    } else {
                        refreshUserBalanceImpl(context)
                    }
                }
            } catch (_: Exception) {
                billingProvider = "none"
            }
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
            val balance = executeWithAuthRetryImpl(context) { authHeader ->
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

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun PianoWeaveViewModel.loadPreferencesImpl(context: Context) {
    val prefs = context.getSharedPreferences("piano_weave_prefs", Context.MODE_PRIVATE)
    getOrCreateUserIdImpl(context)
    isStrikeOverlayEnabled = prefs.getBoolean("is_strike_overlay_enabled", true)
    selectedThemeId = prefs.getString("selected_theme_id", "gold") ?: "gold"
    topBarSpeed = prefs.getFloat("top_bar_speed", 1.0f)

    googleToken = GoogleAuthManager.getSavedToken(context) ?: ""
    googleUserEmail = GoogleAuthManager.getSavedEmail(context) ?: ""
    customGoogleClientId = prefs.getString("custom_google_client_id", "") ?: ""

    defaultServerUrl = try {
        AppConfig.initialize(context)
        AppConfig.getConfig().baseUrl
    } catch (_: Exception) {
        ""
    }
    isCustomServer = prefs.getBoolean("use_custom_server", false)
    customServerUrl = prefs.getString("custom_server_url", "") ?: ""

    if (context.findActivity() != null) {
        if (requireGoogleAccount && !isGoogleSignedIn && activeGoogleClientId.isNotBlank()) {
            viewModelScope.launch {
                ensureGoogleAuthTokenImpl(context)
                if (!isGoogleSignedIn) {
                    signInWithGoogleImpl(context, null)
                }
            }
        }

        checkServerHealthAndInfoImpl(context)
    }
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

    val prefs = context.getSharedPreferences("piano_weave_prefs", Context.MODE_PRIVATE)
    prefs.edit()
        .putBoolean("use_custom_server", useCustom)
        .putString("custom_server_url", formattedUrl)
        .putString("custom_google_client_id", customGoogleClientId)
        .apply()

    checkServerHealthAndInfoImpl(context)
}

internal suspend fun PianoWeaveViewModel.verifyGooglePlayPurchaseImpl(
    context: Context,
    productId: String,
    purchaseToken: String
): Boolean = withContext(Dispatchers.IO) {
    try {
        val uId = getOrCreateUserIdImpl(context)
        val resp = executeWithAuthRetryImpl(context) { authHeader ->
            val api = PianoApiFactory.getApi(activeServerUrl)
            api.verifyPurchase(
                authHeader = authHeader,
                userId = if (authHeader == null) uId else null,
                request = VerifyPurchaseRequest(productId, purchaseToken)
            )
        }
        withContext(Dispatchers.Main) {
            userCredits = resp.minutes
        }
        true
    } catch (_: Exception) {
        false
    }
}
