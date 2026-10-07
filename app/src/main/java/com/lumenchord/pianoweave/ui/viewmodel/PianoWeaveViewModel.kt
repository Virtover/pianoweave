package com.lumenchord.pianoweave.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import com.lumenchord.pianoweave.api.ServerOffer
import com.lumenchord.pianoweave.api.config.AppConfig
import com.lumenchord.pianoweave.cloud.CloudMidi
import com.lumenchord.pianoweave.midi.StoredMidi
import kotlinx.coroutines.Job

class PianoWeaveViewModel : ViewModel() {

    // --- Conversion State ---
    var videoUrl by mutableStateOf("")
        internal set

    var status by mutableStateOf("Paste a video link above to begin.")
        internal set

    var progress by mutableFloatStateOf(0f)
        internal set

    var isLoading by mutableStateOf(false)
        internal set

    var songs by mutableStateOf<List<StoredMidi>>(emptyList())
        internal set

    var currentJobId by mutableStateOf<String?>(null)
        internal set

    internal var transcriptionJob: Job? = null

    var readySong by mutableStateOf<StoredMidi?>(null)
    var activePracticeSong by mutableStateOf<StoredMidi?>(null)
    internal var lastPlayedSongPath: String? = null

    var transcriptionError by mutableStateOf<String?>(null)
        internal set

    var isCancelling by mutableStateOf(false)
        internal set

    var selectedThemeId by mutableStateOf("gold")
        internal set

    // --- Playback State (Orientation Survival) ---
    var isPlaying by mutableStateOf(false)
    var playheadMs by mutableLongStateOf(0L)
    var speedMultiplier by mutableFloatStateOf(1.0f)
    var topBarSpeed by mutableFloatStateOf(1.0f)
        internal set

    var isWaitModeEnabled by mutableStateOf(false)
    var isStrikeOverlayEnabled by mutableStateOf(true)
        internal set

    var isLoopingEnabled by mutableStateOf(false)
    var loopStartMs by mutableLongStateOf(0L)
    var loopEndMs by mutableLongStateOf(0L)
    var transposeOffset by mutableIntStateOf(0)

    // --- Server Settings & Billing State ---
    var defaultServerUrl by mutableStateOf("")
        internal set
    var customServerUrl by mutableStateOf("")
        internal set
    var isCustomServer by mutableStateOf(false)
        internal set
    var serverStatus by mutableStateOf(ServerStatus.UNKNOWN)
        internal set

    var userId by mutableStateOf("")
        internal set

    var billingProvider by mutableStateOf("none")
        internal set

    val isBilledServer: Boolean
        get() = billingProvider.equals("google_play", ignoreCase = true)

    var userCredits by mutableIntStateOf(0)
        internal set

    var freeMinutes by mutableIntStateOf(0)
        internal set

    var freeMinutesSecondsUntilNextGrant by mutableStateOf<Long?>(null)
        internal set

    var freeMinutesNextGrantAt by mutableStateOf<Long?>(null)
        internal set

    var serverOffers by mutableStateOf<List<ServerOffer>>(emptyList())
        internal set

    var cleanupIntervalSeconds by mutableStateOf<Long?>(null)
        internal set

    var estimatedCostCredits by mutableStateOf<Int?>(null)
        internal set

    var isCalculatingCost by mutableStateOf(false)
        internal set

    var supportMeLink by mutableStateOf<String?>(null)
        internal set

    // --- Google OAuth State ---
    var serverGoogleClientId by mutableStateOf("")
        internal set
    var customGoogleClientId by mutableStateOf("")
        internal set
    var googleUserEmail by mutableStateOf("")
        internal set
    var googleToken by mutableStateOf("")
        internal set
    var isGoogleAuthLoading by mutableStateOf(false)
        internal set
    var googleAuthError by mutableStateOf<String?>(null)
        internal set

    // --- Cloud Storage State ---
    var cloudSongs by mutableStateOf<List<CloudMidi>>(emptyList())
    var isCloudLoading by mutableStateOf(false)
    var cloudError by mutableStateOf<String?>(null)
    var showUploadDialog by mutableStateOf(false)
    var isUploadingToCloud by mutableStateOf(false)
    var uploadProgressText by mutableStateOf("")
    var selectedStorageTab by mutableStateOf(StorageTab.MY_LIBRARY)
    var selectedStorageViewName by mutableStateOf<String?>(null)

    val requireGoogleAccount: Boolean
        get() = try { AppConfig.getConfig().requireGoogleAccount } catch (_: Exception) { false }

    val appWebClientId: String
        get() = try { AppConfig.getConfig().webClientId ?: "" } catch (_: Exception) { "" }

    val activeGoogleClientId: String
        get() {
            if (customGoogleClientId.isNotBlank()) return customGoogleClientId
            if (appWebClientId.isNotBlank()) return appWebClientId
            return serverGoogleClientId
        }

    val isGoogleSignedIn: Boolean
        get() = googleToken.isNotBlank()

    // Dialog flags
    var showShopDialog by mutableStateOf(false)
    var showSupportDialog by mutableStateOf(false)
    var showNotEnoughCreditsDialog by mutableStateOf(false)

    val activeServerUrl: String
        get() {
            if (isCustomServer && customServerUrl.isNotBlank()) {
                return if (customServerUrl.endsWith("/")) customServerUrl else "$customServerUrl/"
            }
            return if (defaultServerUrl.isNotBlank()) {
                if (defaultServerUrl.endsWith("/")) defaultServerUrl else "$defaultServerUrl/"
            } else {
                "http://localhost:8000/"
            }
        }

    val isUsingDefaultServer: Boolean
        get() = !isCustomServer || customServerUrl.isBlank() || activeServerUrl == (if (defaultServerUrl.endsWith("/")) defaultServerUrl else "$defaultServerUrl/")

    internal var balanceTickerJob: Job? = null
    internal var costJob: Job? = null

    var importError by mutableStateOf<String?>(null)
        internal set

    // --- Delegated Domain Operations ---
    fun signInWithGoogle(context: Context, onResult: ((Boolean) -> Unit)? = null) = signInWithGoogleImpl(context, onResult)
    fun signOutGoogle(context: Context) = signOutGoogleImpl(context)
    suspend fun ensureGoogleAuthToken(context: Context): String? = ensureGoogleAuthTokenImpl(context)
    suspend fun <T> executeWithAuthRetry(context: Context, apiCall: suspend (authHeader: String?) -> T): T = executeWithAuthRetryImpl(context, apiCall)

    fun calculateCostForUrl(url: String) = calculateCostForUrlImpl(url)
    fun updateUrl(url: String) = updateUrlImpl(url)
    fun resumeActiveJobIfAny(context: Context) = resumeActiveJobIfAnyImpl(context)
    fun cancelTranscription(context: Context) = cancelTranscriptionImpl(context)
    fun startTranscription(context: Context) = startTranscriptionImpl(context)
    fun normalizeUrl(url: String): String = normalizeUrlImpl(url)

    fun formatRetentionTime(seconds: Long? = cleanupIntervalSeconds): String = formatRetentionTimeImpl(seconds)
    fun getOrCreateUserId(context: Context): String = getOrCreateUserIdImpl(context)
    suspend fun testServerConnection(url: String): ServerStatus = testServerConnectionImpl(url)
    fun checkServerHealthAndInfo(context: Context? = null) = checkServerHealthAndInfoImpl(context)
    fun refreshUserBalance(context: Context) = refreshUserBalanceImpl(context)
    fun loadPreferences(context: Context) = loadPreferencesImpl(context)
    fun updateServerSettings(context: Context, useCustom: Boolean, customUrl: String, customClientId: String = customGoogleClientId) = updateServerSettingsImpl(context, useCustom, customUrl, customClientId)
    suspend fun verifyGooglePlayPurchase(context: Context, productId: String, purchaseToken: String): Boolean = verifyGooglePlayPurchaseImpl(context, productId, purchaseToken)

    fun setSelectedTheme(context: Context, themeId: String) = setSelectedThemeImpl(context, themeId)
    fun clearTranscriptionError() = clearTranscriptionErrorImpl()
    fun setTopBarSpeed(context: Context, speed: Float) = setTopBarSpeedImpl(context, speed)
    fun setStrikeOverlayEnabled(context: Context, enabled: Boolean) = setStrikeOverlayEnabledImpl(context, enabled)
    fun openPracticeSession(context: Context, song: StoredMidi) = openPracticeSessionImpl(context, song)
    fun openShop() { showShopDialog = true }
    fun dismissShop() { showShopDialog = false }
    fun openSupport() { showSupportDialog = true }
    fun dismissSupport() { showSupportDialog = false }
    fun dismissNotEnoughCredits() { showNotEnoughCreditsDialog = false }
    fun loadSongs(context: Context) = loadSongsImpl(context)
    fun deleteSong(context: Context, song: StoredMidi) = deleteSongImpl(context, song)
    fun clearImportError() = clearImportErrorImpl()
    fun importMidiFile(context: Context, uri: Uri) = importMidiFileImpl(context, uri)

    fun loadCloudSongs(context: Context) = loadCloudSongsImpl(context)
    fun uploadLocalSongsToCloud(context: Context) = uploadLocalSongsToCloudImpl(context)
    fun deleteCloudSong(context: Context, cloudMidi: CloudMidi) = deleteCloudSongImpl(context, cloudMidi)
    fun playCloudSong(context: Context, cloudMidi: CloudMidi) = playCloudSongImpl(context, cloudMidi)

    fun hasShownCloudBackupDialog(context: Context): Boolean {
        val prefs = context.getSharedPreferences("piano_weave_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("has_shown_cloud_backup_dialog", false)
    }

    fun markCloudBackupDialogShown(context: Context) {
        val prefs = context.getSharedPreferences("piano_weave_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("has_shown_cloud_backup_dialog", true).apply()
    }
}
