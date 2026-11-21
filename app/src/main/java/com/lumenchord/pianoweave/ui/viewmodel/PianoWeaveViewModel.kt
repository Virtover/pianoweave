package com.lumenchord.pianoweave.ui.viewmodel

import androidx.compose.runtime.mutableIntStateOf
import com.lumenchord.pianoweave.api.Clip
import com.lumenchord.pianoweave.api.TranscriptionMode
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import com.lumenchord.pianoweave.api.PianoApiFactory
import com.lumenchord.pianoweave.audio.VoiceListeningMode
import com.lumenchord.pianoweave.audio.VoiceModelError
import com.lumenchord.pianoweave.api.ServerOffer
import com.lumenchord.pianoweave.api.ServerSubscriptionPlan
import com.lumenchord.pianoweave.api.ActiveSubscription
import com.lumenchord.pianoweave.api.VideoLimit
import com.lumenchord.pianoweave.api.effectiveMaxVideoMinutes
import com.lumenchord.pianoweave.api.DeveloperJobDetails
import com.lumenchord.pianoweave.api.DeveloperServerState
import com.lumenchord.pianoweave.api.config.AppConfig
import com.lumenchord.pianoweave.cloud.CloudMidi
import com.lumenchord.pianoweave.midi.ExportState
import com.lumenchord.pianoweave.midi.StoredMidi
import com.lumenchord.pianoweave.ui.theme.AppThemeManager
import com.lumenchord.pianoweave.ui.screens.learn.ShopTab
import com.lumenchord.pianoweave.util.AccountProgress
import com.lumenchord.pianoweave.util.AppPrefs
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
    /**
     * The error message (as in [transcriptionError]) for which uploading the file instead is offered, because
     * the server could not read the link. Matched by text, so a later, different error never shows the offer.
     */
    var uploadOfferedFor by mutableStateOf<String?>(null)
        internal set
    /** The error the cost check of a pasted link showed, which a different link replaces (see updateUrlImpl). */
    internal var costCheckError: String? = null

    var isCancelling by mutableStateOf(false)
        internal set

    var selectedThemeId by mutableStateOf(AppThemeManager.defaultTheme.id)
        internal set
    /** The light themes were found (hold Lamplight in the theme picker): Settings offers Night / Day. */
    var isLightThemeUnlocked by mutableStateOf(false)
        internal set
    /** Debug builds: progress kept apart from the Google account (see AccountProgress). */
    var isIgnoringAccountProgress by mutableStateOf(false)
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
    /** Piano roll display: names on notes and keys, the song map, and which keys are shown (see PianoWeaveViewModelDisplay). */
    var isNoteNamesEnabled by mutableStateOf(true)
        internal set
    var isSongMapEnabled by mutableStateOf(false)
        internal set
    var keyboardRange by mutableStateOf(KeyboardRange.All)
        internal set
    var customRangeLow by mutableIntStateOf(33)
        internal set
    var customRangeHigh by mutableIntStateOf(96)
        internal set
    /** Debug builds: the Learn header's pill forced to credits or support (see Settings → About). */
    var headerPillOverride by mutableStateOf(HeaderPillOverride.Auto)
        internal set
    var isKeyboardSoundEnabled by mutableStateOf(true)
        internal set
    var isMidiOutputEnabled by mutableStateOf(true)
        internal set
    var isMetronomeEnabled by mutableStateOf(false)
        internal set
    var isCountInEnabled by mutableStateOf(false)
        internal set
    var isMetronomeLeadInSilent by mutableStateOf(false)
        internal set

    var isLoopingEnabled by mutableStateOf(false)
    var loopStartMs by mutableLongStateOf(0L)
    var loopEndMs by mutableLongStateOf(0L)
    var transposeOffset by mutableIntStateOf(0)

    // --- Voice Commands (piano roll) ---
    var isVoiceCommandsEnabled by mutableStateOf(false)
        internal set
    var isVoiceCommandsPillEnabled by mutableStateOf(true)
        internal set
    var voiceModelState by mutableStateOf(VoiceModelState.MISSING)
        internal set
    var voiceModelProgress by mutableFloatStateOf(0f)
        internal set
    /** Why the last model download failed (set while [voiceModelState] is FAILED). */
    var voiceModelError by mutableStateOf<VoiceModelError?>(null)
        internal set
    var voiceListeningMode by mutableStateOf(VoiceListeningMode.WORDS)
    /** Mic processing for voice commands (a debug setting, to compare). */
    var voiceMicMode by mutableStateOf(com.lumenchord.pianoweave.audio.VoiceMicMode.CALL)
        internal set
    internal var voiceModelJob: Job? = null

    // --- Server Settings & Billing State ---
    var defaultServerUrl by mutableStateOf("")
        internal set
    var customServerUrl by mutableStateOf("")
        internal set
    var customServerPassword by mutableStateOf("")
        internal set
    var isCustomServer by mutableStateOf(false)
        internal set
    var serverStatus by mutableStateOf(ServerStatus.UNKNOWN)
        internal set

    var userId by mutableStateOf("")
        internal set

    var billingProvider by mutableStateOf("none")
        internal set

    /**
     * Billing needs the user's Google identity, which is only ever shared with the official
     * server, so custom servers are always used anonymously and unbilled.
     */
    val isBilledServer: Boolean
        get() = isUsingDefaultServer && billingProvider.equals("google_play", ignoreCase = true)

    private var userCreditsState by mutableStateOf<Int?>(null)

    /**
     * The whole balance (free + purchased), as shown in the credits pill. Many server answers carry only
     * this total, so a drop is taken from the free [freeCredits] first, the way the server spends them; the next
     * balance download then sets the exact split.
     */
    var userCredits: Int?
        get() = userCreditsState
        internal set(value) {
            val old = userCreditsState
            val free = freeCredits
            if (value == null) {
                freeCredits = null
                // The balance is unknown again (signed out, another server): so is the account's role and plan
                isDeveloper = false
                activeSubscription = null
                isUnlimitedPlan = false
                isUnlimitedHeavyUse = false
                userVideoLimit = null
                billingAccountId = null
            } else if (old != null && free != null && value < old) {
                freeCredits = (free - (old - value)).coerceAtLeast(0)
            }
            userCreditsState = value
        }

    /** Free credits (renewed each period, spent first) left of [userCredits]; null when unknown or the server has none. */
    var freeCredits by mutableStateOf<Int?>(null)
        internal set

    /** Purchased credits, which never expire: the rest of [userCredits]. Negative while a refund is owed. */
    val paidCredits: Int?
        get() {
            val total = userCredits ?: return null
            return total - (freeCredits ?: return null)
        }

    var freeMinutes by mutableIntStateOf(0)
        internal set

    /** How often free credits are renewed, as the server's setting says it ("30d"); null if it doesn't say. */
    var freeMinutesPeriod by mutableStateOf<String?>(null)
        internal set

    var freeMinutesSecondsUntilNextGrant by mutableStateOf<Long?>(null)
        internal set

    var freeMinutesNextGrantAt by mutableStateOf<Long?>(null)
        internal set

    // --- Developer account (an administrator's choice on the server, see PianoWeaveViewModelDeveloper) ---
    /** The signed-in account is a developer's on the selected server: unlimited credits and the developer extras. */
    var isDeveloper by mutableStateOf(false)
        internal set

    /** Whether the developer background is drawn behind the app; a developer can turn it off in Settings. */
    var isDeveloperBackgroundEnabled by mutableStateOf(true)
        internal set

    var showDeveloperPanel by mutableStateOf(false)
        internal set

    var developerServerState by mutableStateOf<DeveloperServerState?>(null)
        internal set

    var developerServerError by mutableStateOf<String?>(null)
        internal set

    var isLoadingDeveloperServerState by mutableStateOf(false)
        internal set

    /** The latest job this run followed, with what the server reported about it. */
    var developerLastJob by mutableStateOf<DeveloperJobSnapshot?>(null)
        internal set

    var serverOffers by mutableStateOf<List<ServerOffer>>(emptyList())
        internal set

    // --- Subscriptions (see PianoWeaveViewModelSubscriptions) ---
    /** The server's plans, lowest level first; empty on older servers and when billing is off. */
    var subscriptionPlans by mutableStateOf<List<ServerSubscriptionPlan>>(emptyList())
        internal set

    /** The account's plan, as the last balance said; null without one. */
    var activeSubscription by mutableStateOf<ActiveSubscription?>(null)
        internal set

    /** An unlimited plan runs: jobs aren't charged, so the app skips its balance checks. */
    var isUnlimitedPlan by mutableStateOf(false)
        internal set

    /** An unlimited account had a busy day: its jobs run one at a time for now. */
    var isUnlimitedHeavyUse by mutableStateOf(false)
        internal set

    /** The server's id for the signed-in account (balance `user_id`); subscriptions are bound to its hash. */
    var billingAccountId by mutableStateOf<String?>(null)
        internal set

    /**
     * The last server-info could not be refreshed (the server restarting, a proxy error): what it said before is
     * still shown, and it is asked again soon, on resume and when the shop opens.
     */
    var isServerInfoStale by mutableStateOf(false)
        internal set

    /** The server whose server-info was last read successfully: its billing state is kept through failures. */
    internal var serverInfoUrl = ""
    internal var serverInfoRetryJob: Job? = null

    /** When the app last refreshed the server state on returning to the foreground (elapsedRealtime). */
    internal var lastResumeRefreshAt = 0L

    /** Credit-pack tokens being verified now, and when each was last verified (elapsedRealtime). */
    internal val packVerificationsInFlight = mutableSetOf<String>()
    internal val packVerifiedAt = mutableMapOf<String, Long>()

    /** Purchase tokens whose "we'll confirm it later" note was shown: said once, not on every recovery. */
    internal val purchaseNoticesShown = mutableSetOf<String>()

    /** A billing problem to tell the user plainly, e.g. a subscription another account owns. */
    var billingNotice by mutableStateOf<String?>(null)
        internal set

    /** Subscription purchase tokens being verified now, so a token found twice at once is sent once. */
    internal val subscriptionVerificationsInFlight = mutableSetOf<String>()

    /** When each subscription token was last verified (elapsedRealtime), so reopening the shop doesn't resend it. */
    internal val subscriptionVerifiedAt = mutableMapOf<String, Long>()

    /** The account whose subscriptions were recovered this run after its first balance; the shop recovers them again. */
    internal var subscriptionsRecoveredFor: String? = null

    /** The server never charges this account's jobs: a developer's, or an unlimited plan's. */
    val hasUnlimitedCredits: Boolean get() = isDeveloper || isUnlimitedPlan

    var resultRetentionSeconds by mutableStateOf<Long?>(null)
        internal set

    var estimatedCostCredits by mutableStateOf<Int?>(null)
        internal set

    var isCalculatingCost by mutableStateOf(false)
        internal set

    // --- File upload ---
    var transcriptionSource by mutableStateOf(TranscriptionSource.Link)
        internal set

    /** Picked audio/video file to transcribe instead of a link. */
    var selectedMediaFile by mutableStateOf<SelectedMediaFile?>(null)
        internal set

    var isUploading by mutableStateOf(false)
        internal set

    internal var activeUpload: MediaUploadBody? = null

    // --- Recording ---
    var isRecording by mutableStateOf(false)
        internal set

    var recordingElapsedMs by mutableLongStateOf(0L)
        internal set

    /** Microphone level, 0..1, for the meter while recording. */
    var recordingLevel by mutableFloatStateOf(0f)
        internal set

    /** A finished microphone recording, transcribed like a picked file. */
    var recordedMediaFile by mutableStateOf<SelectedMediaFile?>(null)
        internal set

    internal var activeRecorder: MediaRecorder? = null
    internal var recordingFile: java.io.File? = null
    internal var recordingTickerJob: Job? = null

    /** From server-info; null when the server cannot transcribe uploaded files. */
    var maxUploadSizeMb by mutableStateOf<Float?>(null)
        internal set

    /** The server's general limit, from server-info. */
    var serverMaxVideoLengthMinutes by mutableStateOf<Float?>(null)
        internal set

    /** This user's limit from the balance (a plan may allow longer videos); null when not known. */
    var userVideoLimit by mutableStateOf<VideoLimit?>(null)
        internal set

    /** The longest video to accept before sending: the user's limit, else the server's general one; null = none. */
    val maxVideoLengthMinutes: Float?
        get() = effectiveMaxVideoMinutes(userVideoLimit, serverMaxVideoLengthMinutes)

    val supportsFileUpload: Boolean
        get() = maxUploadSizeMb != null

    /** From server-info: the server can make piano arrangements, so Transcribe offers the choice. */
    var supportsArrangement by mutableStateOf(false)
        internal set

    /** The server [supportsArrangement] was read from. */
    internal var arrangementInfoServerUrl = ""

    /** From server-info: a submission may ask for only a part of its song, so the card offers Trim. */
    var supportsTrimming by mutableStateOf(false)
        internal set

    /** The part chosen with Trim and the source it was chosen for; see [activeClip]. */
    internal var trimChoice by mutableStateOf<TrimChoice?>(null)

    /** Lengths of links as the server read them (for the cost or for Trim), by link. */
    internal var linkDurations by mutableStateOf<Map<String, Float>>(emptyMap())

    /** The Trim dialog is open; kept here, as the mode choice is, so a rotation doesn't close it. */
    var showTrimDialog by mutableStateOf(false)

    /** Parts already in the library that the part about to be sent overlaps; the warning shows while set. */
    var overlapWarning by mutableStateOf<OverlapWarning?>(null)

    /** Trim waits for the server to read the link's length. */
    var isReadingTrimLength by mutableStateOf(false)
        internal set

    /** Why the link's length couldn't be read for Trim; null otherwise. */
    var trimLengthError by mutableStateOf<String?>(null)
        internal set

    /**
     * The Transcribe flow's open dialogs: the mode choice, and the Terms with the mode waiting on them. Kept
     * here rather than in the card, which portrait and landscape build in different places, so a rotation
     * doesn't close them. [modeChoice] is the option selected in the open choice, null while it is closed.
     */
    var modeChoice by mutableStateOf<TranscriptionMode?>(null)
    var termsPendingMode by mutableStateOf<TranscriptionMode?>(null)

    /** What the current (or last) job makes; set when it starts or is resumed. */
    var transcriptionMode by mutableStateOf(TranscriptionMode.Transcription)
        internal set

    var supportMeLink by mutableStateOf<String?>(null)
        internal set

    /**
     * Whether the official server charges for transcriptions: its last known answer, kept across runs, so it still
     * holds while that server can't be reached; null only until it has answered once.
     */
    var isOfficialServerBilled by mutableStateOf<Boolean?>(null)
        internal set

    /** Whether the official server has been asked about billing in this run (its answer can change). */
    internal var hasCheckedOfficialBillingThisRun = false

    /** Takes and keeps a definite answer from the official server about billing. */
    internal fun rememberOfficialServerBilled(billed: Boolean) {
        isOfficialServerBilled = billed
        hasCheckedOfficialBillingThisRun = true
        appContext?.let { AppPrefs.get(it).edit().putBoolean(AppPrefs.OFFICIAL_SERVER_BILLED, billed).apply() }
    }

    /** Donations only make sense while the official server is free, whichever server is selected. */
    val canShowSupport: Boolean
        get() = isOfficialServerBilled == false && !supportMeLink.isNullOrBlank()

    val discordLink: String?
        get() = AppConfig.getConfigOrNull()?.discordLink

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
    /** Action deferred until an in-progress Google sign-in succeeds (see [withEnsuredSignIn]). */
    internal var pendingSignInAction: (() -> Unit)? = null
    var showNoGoogleAccountDialog by mutableStateOf(false)
    var showBilledServerGoogleAccountDialog by mutableStateOf(false)
    var savedUnauthenticatedServerUrl by mutableStateOf("")
    var isInitialConnection by mutableStateOf(true)
    /** Why the server couldn't be used when the app opened; shows [com.lumenchord.pianoweave.ui.components.ServerConnectionDialog]. */
    var startupConnectionProblem by mutableStateOf<ServerConnectionProblem?>(null)
    var showServerSettingsDialog by mutableStateOf(false)
    var showWelcomeTutorial by mutableStateOf(false)
    /** The "How it works" path on the Learn tab; stays until the walkthrough has been finished once. */
    var showHowItWorksHint by mutableStateOf(false)
    /** [loadPreferences] already ran for this ViewModel; MainActivity skips it when only the Activity was recreated. */
    var preferencesLoaded = false

    // First open shows one prompt at a time: the permission request, then the sign-in offer
    /** The startup permission request was launched; kept here so a recreated Activity doesn't launch it again. */
    internal var startupPermissionsRequested = false
    /** The startup permission request was answered (or nothing needed asking); later first-open prompts wait for it. */
    var startupPermissionsAnswered by mutableStateOf(false)
    /** Sign-in is to be offered on the first connection to a billed server, once the earlier prompts are done. */
    var pendingBilledServerSignInOffer by mutableStateOf(false)

    // --- Cloud Storage & Sync State ---
    var isCloudSyncEnabled by mutableStateOf(false)
    var hasCloudSyncError by mutableStateOf(false)
    var isCloudSyncing by mutableStateOf(false)
    var isRefreshingCloud by mutableStateOf(false)
    var cloudSyncProgressText by mutableStateOf("")
    var cloudSyncError by mutableStateOf<String?>(null)
    var showCloudSyncErrorDialog by mutableStateOf(false)
    var showTurnOnCloudSyncConfirmDialog by mutableStateOf(false)
    var showTurnOffCloudSyncConfirmDialog by mutableStateOf(false)

    // Legacy fields kept for compatibility where needed
    var cloudSongs by mutableStateOf<List<CloudMidi>>(emptyList())
    var isCloudLoading by mutableStateOf(false)
    var cloudError by mutableStateOf<String?>(null)
    var showUploadDialog by mutableStateOf(false)
    var isUploadingToCloud by mutableStateOf(false)
    var uploadProgressText by mutableStateOf("")
    var uploadErrorOccured by mutableStateOf(false)
    var selectedStorageViewName by mutableStateOf<String?>(null)

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

    var signInCancelEvent by mutableIntStateOf(0)
        internal set

    val exportStates = mutableStateMapOf<String, ExportState>()
    var exportMessage by mutableStateOf<String?>(null)
        internal set

    // Dialog flags
    var showShopDialog by mutableStateOf(false)
    /** Kept for the lifetime of this view model so the shop tab survives Activity/Dialog recreation. */
    var shopSelectedTab by mutableStateOf<ShopTab?>(null)
        internal set
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
        get() = PianoApiFactory.isOfficialServer(activeServerUrl)

    internal var balanceTickerJob: Job? = null
    internal var costJob: Job? = null
    internal var autoReconnectJob: Job? = null
    internal var serverCheckJob: Job? = null
    internal var maintenanceWatchJob: Job? = null
    internal var announcementsJob: Job? = null
    internal var announcementsJobServers: List<String>? = null
    /** The servers last asked for announcements, and when (elapsed realtime). */
    internal var lastAnnouncementsFetch: Pair<List<String>, Long>? = null
    internal var appContext: Context? = null

    var importError by mutableStateOf<String?>(null)
        internal set

    // --- Delegated Domain Operations ---
    fun openAddGoogleAccount(context: Context) = openAddGoogleAccountImpl(context)
    fun signInWithGoogle(context: Context, isAutoSignIn: Boolean = false, onResult: ((Boolean) -> Unit)? = null) = signInWithGoogleImpl(context, isAutoSignIn, onResult)
    fun signOutGoogle(context: Context) = signOutGoogleImpl(context)
    fun offerBilledServerSignIn(context: Context) = offerBilledServerSignInImpl(context)
    fun dismissBilledServerGoogleAccountDialog() { showBilledServerGoogleAccountDialog = false }
    fun markServerUnauthenticated(context: Context, url: String) {
        savedUnauthenticatedServerUrl = url
        AppPrefs.get(context).edit().putString(AppPrefs.LAST_BILLED_SERVER_UNAUTHENTICATED, url).apply()
    }
    fun markServerAuthenticated(context: Context) {
        savedUnauthenticatedServerUrl = ""
        AppPrefs.get(context).edit().remove(AppPrefs.LAST_BILLED_SERVER_UNAUTHENTICATED).apply()
    }
    fun withEnsuredSignIn(context: Context, action: () -> Unit) = withEnsuredSignInImpl(context, action)
    suspend fun ensureGoogleAuthToken(context: Context): String? = ensureGoogleAuthTokenImpl(context)
    suspend fun <T> executeServerCall(context: Context, apiCall: suspend (authHeader: String?) -> T): T = executeServerCallImpl(context, apiCall = apiCall)

    fun calculateCostForUrl(url: String) = calculateCostForUrlImpl(url)
    fun updateUrl(url: String) = updateUrlImpl(url)
    fun resumeActiveJobIfAny(context: Context) = resumeActiveJobIfAnyImpl(context)
    fun cancelTranscription(context: Context) = cancelTranscriptionImpl(context)
    fun startTranscription(
        context: Context,
        mode: TranscriptionMode = TranscriptionMode.Transcription,
        overlapConfirmed: Boolean = false
    ) = startTranscriptionImpl(context, mode, overlapConfirmed)
    fun lastChosenMode(context: Context): TranscriptionMode = lastChosenModeImpl(context)
    fun openTrim(context: Context) = openTrimImpl(context)
    fun applyTrim(clip: Clip?) = applyTrimImpl(clip)
    fun selectMediaFile(context: Context, uri: Uri) = selectMediaFileImpl(context, uri)
    fun clearSelectedMediaFile() = clearSelectedMediaFileImpl()
    fun selectTranscriptionSource(source: TranscriptionSource) = selectTranscriptionSourceImpl(source)
    fun startRecording(context: Context) = startRecordingImpl(context)
    fun stopRecording() = stopRecordingImpl()
    fun discardRecording() = discardRecordingImpl()
    fun normalizeUrl(url: String): String = normalizeUrlImpl(url)

    fun formatRetentionTime(seconds: Long? = resultRetentionSeconds): String = formatRetentionTimeImpl(seconds)
    fun getOrCreateUserId(context: Context): String = getOrCreateUserIdImpl(context)
    suspend fun testServerConnection(url: String): ServerStatus = testServerConnectionImpl(url)
    fun checkServerHealthAndInfo(context: Context? = null) = checkServerHealthAndInfoImpl(context)
    fun startAutoReconnect(context: Context? = null) = startAutoReconnectImpl(context)
    fun refreshAnnouncements(context: Context, force: Boolean = false) = refreshAnnouncementsImpl(context, force)
    fun refreshUserBalance(context: Context) = refreshUserBalanceImpl(context)
    fun loadPreferences(context: Context) = loadPreferencesImpl(context)
    fun updateServerSettings(context: Context, useCustom: Boolean, customUrl: String, customClientId: String = customGoogleClientId) = updateServerSettingsImpl(context, useCustom, customUrl, customClientId)
    fun updateServerPassword(context: Context, password: String) = updateServerPasswordImpl(context, password)
    suspend fun verifyGooglePlayPurchase(context: Context, productId: String, purchaseToken: String, fromRecovery: Boolean = false): PackVerification =
        verifyGooglePlayPurchaseImpl(context, productId, purchaseToken, fromRecovery)
    suspend fun verifyGooglePlaySubscription(
        context: Context, productId: String, purchaseToken: String, fromRecovery: Boolean = false, recheck: Boolean = false
    ) = verifyGooglePlaySubscriptionImpl(context, productId, purchaseToken, fromRecovery, recheck)
    fun dismissBillingNotice() { billingNotice = null }
    fun refreshServerStateOnResume(context: Context) = refreshServerStateOnResumeImpl(context)

    fun setSelectedTheme(context: Context, themeId: String) = setSelectedThemeImpl(context, themeId)
    fun unlockLightTheme(context: Context): Boolean = unlockLightThemeImpl(context)
    fun resetLightThemeUnlock(context: Context) = resetLightThemeUnlockImpl(context)
    fun resetVerseOfLight(context: Context) = resetVerseOfLightImpl(context)
    fun setLightTheme(context: Context, light: Boolean) = setLightThemeImpl(context, light)
    fun setIgnoringAccountProgress(context: Context, ignore: Boolean) = setIgnoringAccountProgressImpl(context, ignore)
    /** Rereads progress changed outside the ViewModel (see AccountProgress.revision). */
    fun reloadProgress(context: Context) = reloadProgressImpl(context)
    fun pushAccountProgress(context: Context) = pushAccountProgressImpl(context)
    fun clearTranscriptionError() = clearTranscriptionErrorImpl()
    fun uploadInsteadOfLink() = uploadInsteadOfLinkImpl()
    fun setTopBarSpeed(context: Context, speed: Float) = setTopBarSpeedImpl(context, speed)
    fun selectPlaybackSpeed(context: Context, speed: Float) = selectPlaybackSpeedImpl(context, speed)
    fun setStrikeOverlayEnabled(context: Context, enabled: Boolean) = setStrikeOverlayEnabledImpl(context, enabled)
    fun setNoteNamesEnabled(context: Context, enabled: Boolean) = setNoteNamesEnabledImpl(context, enabled)
    fun setSongMapEnabled(context: Context, enabled: Boolean) = setSongMapEnabledImpl(context, enabled)
    fun setKeyboardRange(context: Context, range: KeyboardRange) = setKeyboardRangeImpl(context, range)
    fun setCustomRange(context: Context, low: Int, high: Int) = setCustomRangeImpl(context, low, high)
    fun cycleHeaderPill(context: Context) = cycleHeaderPillImpl(context)
    fun setKeyboardSoundEnabled(context: Context, enabled: Boolean) = setKeyboardSoundEnabledImpl(context, enabled)
    fun setMidiOutputEnabled(context: Context, enabled: Boolean) = setMidiOutputEnabledImpl(context, enabled)
    fun setMetronomeEnabled(context: Context, enabled: Boolean) = setMetronomeEnabledImpl(context, enabled)
    fun setCountInEnabled(context: Context, enabled: Boolean) = setCountInEnabledImpl(context, enabled)
    fun setMetronomeLeadInSilent(context: Context, silent: Boolean) = setMetronomeLeadInSilentImpl(context, silent)
    fun setVoiceCommandsEnabled(context: Context, enabled: Boolean) = setVoiceCommandsEnabledImpl(context, enabled)
    fun setVoiceCommandsPillEnabled(context: Context, enabled: Boolean) = setVoiceCommandsPillEnabledImpl(context, enabled)
    fun downloadVoiceModel(context: Context) = downloadVoiceModelImpl(context)
    fun setVoiceListeningMode(context: Context, mode: VoiceListeningMode) = setVoiceListeningModeImpl(context, mode)
    fun setVoiceMicMode(context: Context, mode: com.lumenchord.pianoweave.audio.VoiceMicMode) = setVoiceMicModeImpl(context, mode)
    fun openPracticeSession(context: Context, song: StoredMidi) = openPracticeSessionImpl(context, song)
    fun openShop(context: Context) = openShopImpl(context)
    fun dismissShop() {
        showShopDialog = false
        shopSelectedTab = null
    }
    fun openSupport() { showSupportDialog = true }
    fun dismissSupport() { showSupportDialog = false }
    fun dismissNotEnoughCredits() { showNotEnoughCreditsDialog = false }
    fun openDeveloperPanel() { showDeveloperPanel = true }
    fun dismissDeveloperPanel() { showDeveloperPanel = false }
    fun refreshDeveloperServerState(context: Context) = refreshDeveloperServerStateImpl(context)
    fun setDeveloperBackgroundEnabled(context: Context, enabled: Boolean) = setDeveloperBackgroundEnabledImpl(context, enabled)
    fun loadSongs(context: Context) = loadSongsImpl(context)
    fun deleteSong(context: Context, song: StoredMidi) = deleteSongImpl(context, song)
    fun clearImportError() = clearImportErrorImpl()
    fun importMidiFile(context: Context, uri: Uri) = importMidiFileImpl(context, uri)
    fun exportLocalSong(context: Context, song: StoredMidi) = exportLocalSongImpl(context, song)
    fun updateSongDetails(context: Context, song: StoredMidi, title: String, artist: String) =
        updateSongDetailsImpl(context, song, title, artist)

    fun enableCloudSync(context: Context) = enableCloudSyncImpl(context)
    fun disableCloudSync(context: Context) = disableCloudSyncImpl(context)
    fun refreshCloudSync(context: Context) = refreshCloudSyncImpl(context)
    fun catchUpCloudBackup(context: Context) = catchUpCloudBackupImpl(context)

    /** When [catchUpCloudBackup] last started a sync (elapsedRealtime), 0 if never. */
    internal var lastCloudCatchUpAt = 0L
    fun requestTurnOnCloudSync(context: Context) {
        if (!isGoogleSignedIn) {
            signInWithGoogle(context) { success ->
                if (success) {
                    showTurnOnCloudSyncConfirmDialog = true
                }
            }
        } else {
            showTurnOnCloudSyncConfirmDialog = true
        }
    }
    fun requestTurnOffCloudSync(context: Context) {
        showTurnOffCloudSyncConfirmDialog = true
    }

    fun hasShownCloudBackupDialog(context: Context): Boolean =
        AppPrefs.get(context).getBoolean(AppPrefs.HAS_SHOWN_CLOUD_BACKUP_DIALOG, false)

    fun markCloudBackupDialogShown(context: Context) {
        AppPrefs.get(context).edit().putBoolean(AppPrefs.HAS_SHOWN_CLOUD_BACKUP_DIALOG, true).apply()
    }

    fun hasSeenPianoRollTutorial(context: Context): Boolean =
        AccountProgress.getFlag(context, AppPrefs.HAS_SEEN_PIANO_ROLL_TUTORIAL)

    fun markPianoRollTutorialSeen(context: Context) = setProgressFlag(context, AppPrefs.HAS_SEEN_PIANO_ROLL_TUTORIAL, true)

    /**
     * Offers the walkthrough with the "How it works" path until it has been finished once. It isn't shown on
     * its own at start: most people want to transcribe first, and would only click through it.
     */
    fun loadHowItWorksHint(context: Context) {
        showHowItWorksHint = !AccountProgress.getFlag(context, AppPrefs.HAS_SEEN_WELCOME_TUTORIAL)
    }

    fun openWelcomeTutorial() {
        showWelcomeTutorial = true
    }

    /**
     * Only a finished walkthrough retires the Learn tab's path. Skipping early may be a slip after an
     * accidental tap, so the path stays; Settings keeps the walkthrough reachable either way.
     */
    fun dismissWelcomeTutorial(context: Context, finished: Boolean) {
        showWelcomeTutorial = false
        if (finished) setHowItWorksHintShown(context, false)
    }

    /** Hides or (on Undo) brings back the Learn tab's "How it works" path without going through the walkthrough. */
    fun setHowItWorksHintShown(context: Context, shown: Boolean) {
        showHowItWorksHint = shown
        setProgressFlag(context, AppPrefs.HAS_SEEN_WELCOME_TUTORIAL, !shown)
    }

    override fun onCleared() {
        activeRecorder?.let { recorder ->
            runCatching { recorder.stop() }
            recorder.release()
        }
        activeRecorder = null
        recordingFile?.delete()
    }
}

internal fun PianoWeaveViewModel.saveCloudSyncState(context: Context, enabled: Boolean, hasError: Boolean, errorMsg: String?) {
    isCloudSyncEnabled = enabled
    hasCloudSyncError = hasError
    cloudSyncError = errorMsg
    val editor = AppPrefs.get(context).edit()
        .putBoolean(AppPrefs.IS_CLOUD_SYNC_ENABLED, enabled)
        .putBoolean(AppPrefs.HAS_CLOUD_SYNC_ERROR, hasError)

    if (errorMsg != null) {
        editor.putString(AppPrefs.CLOUD_SYNC_ERROR, errorMsg)
    } else {
        editor.remove(AppPrefs.CLOUD_SYNC_ERROR)
    }
    editor.apply()
}
