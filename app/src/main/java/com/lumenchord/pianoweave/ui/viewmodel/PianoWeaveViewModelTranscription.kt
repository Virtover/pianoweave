package com.lumenchord.pianoweave.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.lumenchord.pianoweave.api.CreateTranscriptionRequest
import com.lumenchord.pianoweave.api.CreateTranscriptionResponse
import com.lumenchord.pianoweave.api.MaintenanceNotice
import com.lumenchord.pianoweave.api.MaintenanceTexts
import com.lumenchord.pianoweave.api.ServerMaintenance
import com.lumenchord.pianoweave.api.PianoApiFactory
import com.lumenchord.pianoweave.api.ServerErrors
import com.lumenchord.pianoweave.api.TranscriptionMode
import com.lumenchord.pianoweave.api.libraryKey
import com.lumenchord.pianoweave.util.AppPrefs
import com.lumenchord.pianoweave.api.retryWhenBusy
import com.google.gson.Gson
import com.lumenchord.pianoweave.cloud.CloudAccountCache
import com.lumenchord.pianoweave.cloud.GoogleDriveManager
import com.lumenchord.pianoweave.midi.MidiStorage
import com.lumenchord.pianoweave.midi.StoredMidi
import com.lumenchord.pianoweave.worker.ActiveJobStore
import com.lumenchord.pianoweave.worker.PendingSubmissionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

// --- Cost estimate ---

internal fun PianoWeaveViewModel.calculateCostForUrlImpl(url: String) {
    costJob?.cancel()
    val stableUrl = normalizeUrlImpl(url)
    val context = appContext
    // Signed out: a billed server tells the cost only after sign-in, which recalculates it
    if (stableUrl.isBlank() || !isBilledServer || !isGoogleSignedIn || context == null) {
        estimatedCostCredits = null
        isCalculatingCost = false
        return
    }
    isCalculatingCost = true
    costJob = viewModelScope.launch {
        try {
            estimatedCostCredits = fetchBillingCost(context, sourceUrl = stableUrl)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            estimatedCostCredits = null
            // Links are off for a while: say so now, rather than after the user taps Transcribe
            if (ServerErrors.errorCode(e) == ServerErrors.SOURCE_TEMPORARILY_UNAVAILABLE) {
                val message = linksOffMessage(e)
                showTranscriptionError(message, offerUpload = true)
                costCheckError = message
            }
        } finally {
            isCalculatingCost = false
        }
    }
}
/**
 * The server's cost in minutes of a link or of a file's length. Signed in only: a billed server answers
 * 401 without the Google token. While it is busy checking other videos it is asked again after a moment.
 */
internal suspend fun PianoWeaveViewModel.fetchBillingCost(
    context: Context,
    sourceUrl: String? = null,
    durationSeconds: Float? = null
): Int {
    val url = activeServerUrl
    return retryWhenBusy {
        executeServerCallImpl(context, url) { authHeader ->
            PianoApiFactory.getApi(url).getBillingCost(sourceUrl = sourceUrl, durationSeconds = durationSeconds, authHeader = authHeader)
        }
    }.costMinutes
}
internal fun PianoWeaveViewModel.updateUrlImpl(url: String) {
    if (isLoading) return
    val isNewLink = url != videoUrl
    videoUrl = url
    // Links are refused per video site: a link to another site may well work, so the refusal goes with
    // the link it was about. An error from a submit stays until it is dismissed.
    if (isNewLink && costCheckError != null) {
        if (transcriptionError == costCheckError) clearTranscriptionErrorImpl()
        costCheckError = null
    }
    if (url.isBlank()) {
        status = "Paste a video link above to begin."
        progress = 0f
        estimatedCostCredits = null
    } else if (!status.startsWith("Error") && !status.startsWith("Failed")) {
        status = "Ready to convert."
        calculateCostForUrlImpl(url)
    }
}
// --- Start ---

/** The mode chosen last time, if this server still offers it; the choice dialog starts on it. */
internal fun PianoWeaveViewModel.lastChosenModeImpl(context: Context): TranscriptionMode {
    if (!supportsArrangement) return TranscriptionMode.Transcription
    return TranscriptionMode.fromApiValue(AppPrefs.get(context).getString(AppPrefs.LAST_TRANSCRIPTION_MODE, null))
}

internal fun PianoWeaveViewModel.startTranscriptionImpl(context: Context, mode: TranscriptionMode) {
    if (isLoading) return
    if (isRecording) return
    val isFileSource = transcriptionSource != TranscriptionSource.Link
    val file = if (isFileSource && supportsFileUpload) sourceMediaFile else null
    val stableUrl = normalizeUrlImpl(videoUrl)
    if (file == null && (isFileSource || stableUrl.isBlank())) return

    if (file == null) videoUrl = stableUrl
    transcriptionError = null
    // A server that dropped arrangements since the dialog opened gets a transcription it can make
    transcriptionMode = if (supportsArrangement) mode else TranscriptionMode.Transcription
    if (supportsArrangement) {
        AppPrefs.get(context).edit().putString(AppPrefs.LAST_TRANSCRIPTION_MODE, mode.apiValue).apply()
    }

    // Client-side pre-check only; the server enforces the balance.
    val cost = estimatedCostCredits
    val credits = userCredits
    if (isBilledServer && cost != null && credits != null && credits < cost) {
        showNotEnoughCreditsDialog = true
        return
    }

    if (file != null) proceedStartUpload(context, file) else proceedStartTranscription(context)
}
internal fun PianoWeaveViewModel.proceedStartTranscription(context: Context) {
    val stableUrl = normalizeUrlImpl(videoUrl)
    if (stableUrl.isBlank() || isLoading) return
    val mode = transcriptionMode
    // The song's key in the library: an arrangement of a link is kept apart from its transcription
    val songKey = libraryKey(stableUrl, mode)

    transcriptionJob = viewModelScope.launch {
        isLoading = true
        readySong = null
        progress = 0f
        status = "Checking local cache..."

        try {
            // Songs already in the local or cloud library never reach the (billed) server.
            val storedFile = MidiStorage.find(context, songKey)
            if (storedFile != null) {
                progress = 1f
                status = "Loaded from local library cache!"
                selectLocalSong(context, storedFile)
                isLoading = false
                return@launch
            }

            status = "Checking cloud cache..."
            val cloudSong = findInCloudLibrary(context, songKey)
            if (cloudSong != null) {
                progress = 1f
                status = "Loaded from cloud library!"
                selectLocalSong(context, cloudSong.file)
                isLoading = false
                return@launch
            }

            status = "Submitting request to server..."
            val submitted = submitTranscription(context, stableUrl, mode)
            val jobId = submitted.job_id
            submitted.minutes?.let { userCredits = it }
            currentJobId = jobId
            ActiveJobStore.save(context, jobId, songKey)
            enqueueTranscriptionWorker(context, jobId, songKey)
            status = "Job successfully queued..."

            pollTranscriptionJob(context, jobId, songKey)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            failTranscriptionStart(context, e)
        }
    }
}
/** The balance can't cover a transcription; found by the app, where the server's 402 never came through. */
internal class InsufficientBalanceException : Exception("You don't have enough credits for this transcription.")
/** Shows why a transcription could not be submitted, preferring the server's own explanation. */
internal fun PianoWeaveViewModel.failTranscriptionStart(context: Context, e: Exception) {
    val errorCode = ServerErrors.errorCode(e)
    val errMsg = when {
        errorCode == ServerErrors.SOURCE_TEMPORARILY_UNAVAILABLE -> linksOffMessage(e)
        errorCode == ServerErrors.ARRANGEMENT_UNAVAILABLE -> {
            // 409: off on this server, so the choice goes away; 503: only no room for one right now
            if ((e as? HttpException)?.code() == 409) {
                supportsArrangement = false
                "This server no longer makes piano arrangements."
            } else {
                "No room for a piano arrangement right now. Try again later."
            }
        }
        e is InsufficientBalanceException || ServerErrors.isPaymentRequired(e) -> {
            // Offer to buy more, with the balance as the server now has it (it may be owed minutes)
            if (isBilledServer) {
                refreshUserBalanceImpl(context)
                showNotEnoughCreditsDialog = true
            }
            (e as? HttpException)?.let { serverErrorDetail(context, it) } ?: InsufficientBalanceException().message!!
        }
        ServerErrors.isAlreadyProcessed(e) ->
            "This was already transcribed, but the result has expired on the server. Tap Transcribe to start it again."
        // Too many in progress: the server says how many it allows. Not retried, the user decides when.
        ServerErrors.isTooManyRequests(e) ->
            serverErrorDetail(context, e as HttpException)
                ?: "You have too many transcriptions in progress. Wait for one to finish, then try again."
        PianoApiFactory.isServerPasswordRequired(e) -> {
            serverStatus = ServerStatus.PASSWORD_REQUIRED
            if (isUsingDefaultServer) "The server turned this app version away. Updating the app should fix it."
            else "This server needs a password. Enter it in Server Settings."
        }
        e is HttpException && e.code() == 401 && !isUsingDefaultServer ->
            "This server requires Google sign-in. Your Google account is only shared with the official server."
        e is HttpException -> serverErrorDetail(context, e) ?: "Server error (HTTP ${e.code()})"
        else -> connectionMessage(e) ?: e.localizedMessage ?: "Connection error"
    }
    showTranscriptionError(errMsg, offerUpload = ServerErrors.uploadMayWork(errorCode))
    ActiveJobStore.clear(context)
    currentJobId = null
    isLoading = false
}
/**
 * FastAPI's `{"detail": "..."}` message, if the error body has one. A refusal because of a planned
 * restart or shutdown (`{"detail": {"message": ..., "maintenance": {...}}}`) gets its own text.
 */
private fun PianoWeaveViewModel.serverErrorDetail(context: Context, e: HttpException): String? = try {
    val body = ServerErrors.errorBody(e)
    val notice = ServerErrors.detail(body)
        ?.takeIf { it.isJsonObject }
        ?.asJsonObject?.get("maintenance")
        ?.let { Gson().fromJson(it, MaintenanceNotice::class.java) }
        ?.takeIf { it.isValid }
    if (notice != null) {
        ServerMaintenance.onNotice(activeServerUrl, notice)
        val retryAfter = ServerErrors.retryAfterSeconds(e.response()?.headers())
        MaintenanceTexts.refusal(notice, System.currentTimeMillis() / 1000, retryAfter) { formatClockTime(context, it) }
    } else {
        ServerErrors.detailMessage(body)
    }
} catch (_: Exception) {
    null
}
/** Shows [message] as the transcription error, with the offer to upload the file if [offerUpload] and possible. */
internal fun PianoWeaveViewModel.showTranscriptionError(message: String, offerUpload: Boolean) {
    transcriptionError = message
    uploadOfferedFor = message.takeIf { offerUpload && canOfferUpload() }
    status = "Error: $message"
}
/** A link was refused, and this server takes uploads instead. */
internal fun PianoWeaveViewModel.canOfferUpload(): Boolean =
    supportsFileUpload && transcriptionSource == TranscriptionSource.Link
/**
 * Links to a video site are off on the server after the site refused it or stopped answering. The server's
 * message names the site ("Links to youtube.com cannot be read at the moment; try again later, ..."); its
 * "later" becomes the time Retry-After gives.
 */
internal fun linksOffMessage(e: Throwable): String {
    val http = e as? HttpException
    val seconds = http?.let { ServerErrors.retryAfterSeconds(it.response()?.headers()) }
    val serverMessage = http?.let { ServerErrors.detailMessage(ServerErrors.errorBody(it)) }
    if (serverMessage != null) {
        val timed = if (seconds != null) serverMessage.replace(TRY_AGAIN_LATER, "try again in ${formatWait(seconds)}") else serverMessage
        return if (timed.endsWith(".")) timed else "$timed."
    }
    val wait = seconds?.let { " in ${formatWait(it)}" } ?: " later"
    return "Video links can't be read at the moment. Try again$wait, or upload the file instead."
}
private val TRY_AGAIN_LATER = Regex("try again later", RegexOption.IGNORE_CASE)
/** A wait in words: "a minute", "25 minutes", "about 3 hours". */
internal fun formatWait(seconds: Long): String {
    val minutes = (seconds + 59) / 60
    val hours = (seconds + 1800) / 3600
    return when {
        minutes <= 1 -> "a minute"
        minutes < 60 -> "$minutes minutes"
        hours <= 1 -> "about an hour"
        else -> "about $hours hours"
    }
}
/** Unix seconds as a clock time in the user's format, e.g. "15:30" or "3:30 PM". */
internal fun formatClockTime(context: Context, epochSec: Long): String =
    android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(epochSec * 1000))
/**
 * Downloads [stableUrl]'s song into the library if the cloud library (as of the last sync) has it, linked
 * to that cloud copy. Only with sync on and signed in: otherwise the list may be another account's, and
 * getting a token could interrupt the transcription with a sign-in prompt.
 */
private suspend fun PianoWeaveViewModel.findInCloudLibrary(context: Context, songKey: String): StoredMidi? {
    if (!isCloudSyncEnabled || !isGoogleSignedIn) return null
    val cloudList = cloudSongs.ifEmpty { withContext(Dispatchers.IO) { CloudAccountCache.loadCloudSongs(context) } }
    val match = cloudList.firstOrNull { it.videoUrl == songKey } ?: return null

    val token = ensureGoogleAuthToken(context)
    if (token.isNullOrBlank()) return null
    // Gone since the last sync (e.g. deleted on another device): null, and the server transcribes it
    val stored = GoogleDriveManager.downloadMidiFile(context, match, token).getOrNull() ?: return null
    withContext(Dispatchers.IO) { MidiStorage.updateCloudFileId(context, stored, match.id) }
    return stored.copy(cloudFileId = match.id)
}
/**
 * Creates the server job; the response has its id and the balance after the charge. Billed servers require
 * a signed-in user. A server busy checking other videos is asked again after the delay it gives.
 */
private suspend fun PianoWeaveViewModel.submitTranscription(
    context: Context,
    stableUrl: String,
    mode: TranscriptionMode
): CreateTranscriptionResponse {
    val uId = getOrCreateUserId(context)
    val serverUrl = activeServerUrl
    // One key per submission: every retry below, and a tap after a lost answer, finds the same job. The
    // mode is part of it, or arranging a link just transcribed would be answered with that transcription.
    val key = PendingSubmissionStore.keyFor(context, "link|$serverUrl|$stableUrl${submissionModeTag(mode)}")
    return settlingSubmission(context, key) {
        retryWhenBusy(attempts = ServerErrors.SUBMIT_ATTEMPTS, retryDelayMs = ServerErrors::submitRetryDelayMs) {
            executeServerCallImpl(context) { authHeader ->
                if (isBilledServer && authHeader == null) {
                    throw IllegalAccessException("Google authentication required.")
                }
                PianoApiFactory.getApi(serverUrl).createTranscription(
                    request = CreateTranscriptionRequest(
                        source_url = stableUrl,
                        mode = mode.apiValue.takeIf { mode != TranscriptionMode.Transcription },
                        style = mode.style
                    ),
                    authHeader = authHeader,
                    userId = if (!isBilledServer) uId else null,
                    idempotencyKey = key
                )
            }
        }
    }
}
/** Added to a submission's source for [PendingSubmissionStore]; empty for a transcription, as before modes. */
internal fun submissionModeTag(mode: TranscriptionMode): String =
    if (mode == TranscriptionMode.Transcription) "" else "|${mode.apiValue}"

/**
 * Runs a submission sent with [key] and drops the key once the server has answered it for good: with the
 * job, or with a refusal. After anything else the job may exist, so the key stays for the next try.
 */
internal suspend fun <T> settlingSubmission(context: Context, key: String, submit: suspend () -> T): T =
    try {
        submit().also { PendingSubmissionStore.settle(context, key) }
    } catch (e: Exception) {
        if (e !is CancellationException && ServerErrors.isSubmissionSettled(e)) PendingSubmissionStore.settle(context, key)
        throw e
    }
// --- URL normalization ---

internal fun PianoWeaveViewModel.normalizeUrlImpl(url: String): String {
    var trimmed = url.trim()
    if (trimmed.isBlank()) return trimmed
    val ytDomain = "y" + "o" + "u" + "t" + "u" + "b" + "e.com"
    val ytShort = "y" + "o" + "u" + "t" + "u.be"
    val lowercase = trimmed.lowercase()
    if (!lowercase.startsWith("http://") && !lowercase.startsWith("https://")) {
        if (lowercase.contains(ytDomain) || lowercase.contains(ytShort)) trimmed = "https://$trimmed"
    }
    try {
        val uri = Uri.parse(trimmed)
        val host = uri.host?.lowercase() ?: ""
        if (host.contains(ytDomain)) {
            val videoId = uri.getQueryParameter("v")
            if (!videoId.isNullOrBlank()) return "https://www.$ytDomain/watch?v=$videoId"
        } else if (host.contains(ytShort)) {
            val videoId = uri.path?.trim('/')?.split('?')?.firstOrNull()?.split('&')?.firstOrNull()
            if (!videoId.isNullOrBlank()) return "https://$ytShort/$videoId"
        }
    } catch (_: Exception) {}
    return trimmed
}
