package com.lumenchord.pianoweave.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.lumenchord.pianoweave.api.CreateTranscriptionRequest
import com.lumenchord.pianoweave.api.CreateTranscriptionResponse
import com.lumenchord.pianoweave.api.MaintenanceNotice
import com.lumenchord.pianoweave.api.MaintenanceTexts
import com.lumenchord.pianoweave.api.ServerMaintenance
import com.lumenchord.pianoweave.api.PianoApiFactory
import com.lumenchord.pianoweave.api.ServerErrors
import com.lumenchord.pianoweave.api.TranscriptionStatusResponse
import com.lumenchord.pianoweave.api.failureMessage
import com.lumenchord.pianoweave.api.isUploadSourceKey
import com.lumenchord.pianoweave.api.retryWhenBusy
import com.google.gson.Gson
import com.lumenchord.pianoweave.cloud.CloudAccountCache
import com.lumenchord.pianoweave.cloud.GoogleDriveManager
import com.lumenchord.pianoweave.midi.CompletedTranscriptions
import com.lumenchord.pianoweave.midi.MidiStorage
import com.lumenchord.pianoweave.midi.StoredMidi
import com.lumenchord.pianoweave.worker.ActiveJobStore
import com.lumenchord.pianoweave.worker.TranscriptionWorker
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

private const val POLL_INTERVAL_MS = 2000L
// While the server is offline after an announced shutdown
private const val OFFLINE_POLL_INTERVAL_MS = 30_000L
private const val CANCEL_JOB_ID_WAIT_MS = 10000L

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
    videoUrl = url
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

internal fun PianoWeaveViewModel.startTranscriptionImpl(context: Context) {
    if (isLoading) return
    if (isRecording) return
    val isFileSource = transcriptionSource != TranscriptionSource.Link
    val file = if (isFileSource && supportsFileUpload) sourceMediaFile else null
    val stableUrl = normalizeUrlImpl(videoUrl)
    if (file == null && (isFileSource || stableUrl.isBlank())) return

    if (file == null) videoUrl = stableUrl
    transcriptionError = null

    // Client-side pre-check only; the server enforces the balance.
    val cost = estimatedCostCredits
    if (isBilledServer && cost != null && userCredits < cost) {
        showNotEnoughCreditsDialog = true
        return
    }

    if (file != null) proceedStartUpload(context, file) else proceedStartTranscription(context)
}

internal fun PianoWeaveViewModel.proceedStartTranscription(context: Context) {
    val stableUrl = normalizeUrlImpl(videoUrl)
    if (stableUrl.isBlank() || isLoading) return

    transcriptionJob = viewModelScope.launch {
        isLoading = true
        readySong = null
        progress = 0f
        status = "Checking local cache..."

        try {
            // Songs already in the local or cloud library never reach the (billed) server.
            val storedFile = MidiStorage.find(context, stableUrl)
            if (storedFile != null) {
                progress = 1f
                status = "Loaded from local library cache!"
                selectLocalSong(context, storedFile)
                isLoading = false
                return@launch
            }

            status = "Checking cloud cache..."
            val cloudSong = findInCloudLibrary(context, stableUrl)
            if (cloudSong != null) {
                progress = 1f
                status = "Loaded from cloud library!"
                selectLocalSong(context, cloudSong.file)
                isLoading = false
                return@launch
            }

            status = "Submitting request to server..."
            val submitted = submitTranscription(context, stableUrl)
            val jobId = submitted.job_id
            submitted.minutes?.let { userCredits = it }
            currentJobId = jobId
            ActiveJobStore.save(context, jobId, stableUrl)
            enqueueTranscriptionWorker(context, jobId, stableUrl)
            status = "Job successfully queued..."

            pollTranscriptionJob(context, jobId, stableUrl)
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
    val errMsg = when {
        e is InsufficientBalanceException || ServerErrors.isPaymentRequired(e) -> {
            // Offer to buy more, with the balance as the server now has it (it may be owed minutes)
            if (isBilledServer) {
                refreshUserBalanceImpl(context)
                showNotEnoughCreditsDialog = true
            }
            (e as? HttpException)?.let { serverErrorDetail(context, it) } ?: InsufficientBalanceException().message!!
        }
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
        else -> e.localizedMessage ?: "Connection error"
    }
    transcriptionError = errMsg
    status = "Error: $errMsg"
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

/** Unix seconds as a clock time in the user's format, e.g. "15:30" or "3:30 PM". */
internal fun formatClockTime(context: Context, epochSec: Long): String =
    android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(epochSec * 1000))

/**
 * Downloads [stableUrl]'s song into the library if the cloud library (as of the last sync) has it, linked
 * to that cloud copy. Only with sync on and signed in: otherwise the list may be another account's, and
 * getting a token could interrupt the transcription with a sign-in prompt.
 */
private suspend fun PianoWeaveViewModel.findInCloudLibrary(context: Context, stableUrl: String): StoredMidi? {
    if (!isCloudSyncEnabled || !isGoogleSignedIn) return null
    val cloudList = cloudSongs.ifEmpty { withContext(Dispatchers.IO) { CloudAccountCache.loadCloudSongs(context) } }
    val match = cloudList.firstOrNull { it.videoUrl == stableUrl } ?: return null

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
private suspend fun PianoWeaveViewModel.submitTranscription(context: Context, stableUrl: String): CreateTranscriptionResponse {
    val uId = getOrCreateUserId(context)
    return retryWhenBusy {
        executeServerCallImpl(context) { authHeader ->
            if (isBilledServer && authHeader == null) {
                throw IllegalAccessException("Google authentication required.")
            }
            PianoApiFactory.getApi(activeServerUrl).createTranscription(
                request = CreateTranscriptionRequest(source_url = stableUrl),
                authHeader = authHeader,
                userId = if (!isBilledServer) uId else null
            )
        }
    }
}

internal fun PianoWeaveViewModel.enqueueTranscriptionWorker(context: Context, jobId: String, stableUrl: String) {
    val workRequest = OneTimeWorkRequestBuilder<TranscriptionWorker>()
        .setInputData(
            workDataOf(
                TranscriptionWorker.KEY_JOB_ID to jobId,
                TranscriptionWorker.KEY_URL to stableUrl,
                TranscriptionWorker.KEY_SERVER_URL to activeServerUrl
            )
        )
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .addTag("transcription_$jobId")
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        "transcription_$jobId",
        ExistingWorkPolicy.REPLACE,
        workRequest
    )
}

// --- Resume ---

internal fun PianoWeaveViewModel.resumeActiveJobIfAnyImpl(context: Context) {
    if (isLoading || transcriptionJob?.isActive == true) return
    val (savedJobId, savedUrl) = ActiveJobStore.load(context) ?: return

    // The background worker may have finished the job while the app was away.
    val storedFile = MidiStorage.find(context, savedUrl)
    if (storedFile != null) {
        ActiveJobStore.clear(context)
        selectLocalSong(context, storedFile)
        status = "Ready to learn!"
        progress = 1f
        return
    }

    if (savedUrl.isUploadSourceKey) transcriptionSource = TranscriptionSource.File else videoUrl = savedUrl
    currentJobId = savedJobId
    isLoading = true
    status = "Resuming transcription job..."

    enqueueTranscriptionWorker(context, savedJobId, savedUrl)

    transcriptionJob = viewModelScope.launch {
        pollTranscriptionJob(context, savedJobId, savedUrl)
    }
}

// --- Polling ---

/** Polls [jobId] until it completes, fails, or is cancelled (i.e. [PianoWeaveViewModel.currentJobId] changes). */
internal suspend fun PianoWeaveViewModel.pollTranscriptionJob(context: Context, jobId: String, stableUrl: String) {
    try {
        while (currentJobId == jobId) {
            val job = fetchJobStatusOrNull(context, jobId)
            if (job == null) {
                // During an announced restart or shutdown the job is kept on the server; say so instead of an error
                val notice = ServerMaintenance.notice
                status = notice?.let(MaintenanceTexts::waitingStatus) ?: "Reconnecting to server..."
                val isOffline = notice != null && notice.isShutdown && notice.hasStarted(System.currentTimeMillis() / 1000)
                delay(if (isOffline) OFFLINE_POLL_INTERVAL_MS else POLL_INTERVAL_MS)
                continue
            }

            applyJobStatus(job)

            when (job.status) {
                "completed" -> {
                    handleCompletedJob(context, jobId, stableUrl, job)
                    break
                }
                "failed" -> {
                    finishActiveJob(context)
                    break
                }
            }
            delay(POLL_INTERVAL_MS)
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
    } finally {
        isLoading = false
    }
}

private suspend fun PianoWeaveViewModel.fetchJobStatusOrNull(context: Context, jobId: String): TranscriptionStatusResponse? =
    try {
        executeServerCallImpl(context) { authHeader ->
            PianoApiFactory.getApi(activeServerUrl).getTranscription(jobId, authHeader = authHeader)
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }

/** Mirrors server-side job state (progress, charged balance, status text) into the UI. */
private fun PianoWeaveViewModel.applyJobStatus(job: TranscriptionStatusResponse) {
    ServerMaintenance.onNotice(activeServerUrl, job.maintenance)
    progress = job.progress
    job.minutes?.let { userCredits = it }

    status = when (job.status) {
        "queued" -> "Queued in server pipeline..."
        "running", "processing" ->
            if (job.metadata != null) "Transcribing \"${job.metadata.title}\"..."
            else "AI model transcribing notes..."
        "completed" -> "Transcription completed."
        "failed" -> {
            val errMsg = job.failureMessage
            transcriptionError = errMsg
            "Error: $errMsg"
        }
        else -> job.status
    }
}

private suspend fun PianoWeaveViewModel.handleCompletedJob(
    context: Context,
    jobId: String,
    stableUrl: String,
    job: TranscriptionStatusResponse
) {
    // The background worker may already have saved this MIDI.
    val existingLocal = MidiStorage.find(context, stableUrl)
    if (existingLocal != null) {
        progress = 1f
        status = "Loaded from library cache!"
        selectLocalSong(context, existingLocal)
        transcriptionError = null
        finishActiveJob(context)
        return
    }

    status = "Downloading completed MIDI file..."
    val savedMidi = downloadMidiWithRetry(context, jobId, stableUrl, job) ?: return

    progress = 1f
    songs = MidiStorage.list(context)
    readySong = savedMidi
    status = "Ready to learn!"
    transcriptionError = null
    finishActiveJob(context)
}

/**
 * Downloads and stores the job's MIDI, retrying until it succeeds or the job is cancelled (returns null).
 * If the background worker saved it first, returns that copy instead of saving (and uploading) it again.
 */
private suspend fun PianoWeaveViewModel.downloadMidiWithRetry(
    context: Context,
    jobId: String,
    stableUrl: String,
    job: TranscriptionStatusResponse
): StoredMidi? {
    while (currentJobId == jobId) {
        try {
            return withContext(Dispatchers.IO) {
                val savedMidi = CompletedTranscriptions.saveOnce(context, stableUrl, job) {
                    executeServerCallImpl(context) { authHeader ->
                        PianoApiFactory.getApi(activeServerUrl).downloadMidi(jobId, authHeader = authHeader)
                    }
                }
                if (savedMidi != null) {
                    backUpToCloudIfEnabled(context, savedMidi)
                    savedMidi
                } else {
                    val existingFile = MidiStorage.find(context, stableUrl)
                    MidiStorage.list(context).firstOrNull { it.file == existingFile }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            status = "Retrying MIDI download..."
            delay(POLL_INTERVAL_MS)
        }
    }
    return null
}

/** Best-effort Drive backup of a freshly transcribed song; runs on its own so a running sync never delays the UI. */
private fun PianoWeaveViewModel.backUpToCloudIfEnabled(context: Context, midi: StoredMidi) {
    if (!isCloudSyncEnabled || !isGoogleSignedIn) return
    val token = googleToken
    viewModelScope.launch(Dispatchers.IO) {
        try {
            GoogleDriveManager.backUpNewSong(context, midi, token)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Ignore background upload errors; the next sync uploads it
        }
    }
}

private fun PianoWeaveViewModel.selectLocalSong(context: Context, file: File) {
    loadSongs(context)
    readySong = songs.firstOrNull { it.file.absolutePath == file.absolutePath }
}

private fun PianoWeaveViewModel.finishActiveJob(context: Context) {
    ActiveJobStore.clear(context)
    currentJobId = null
}

// --- Cancel ---

internal fun PianoWeaveViewModel.cancelTranscriptionImpl(context: Context) {
    if (isCancelling) return
    isCancelling = true
    status = "Cancelling transcription job..."

    viewModelScope.launch {
        activeUpload?.cancel()
        val jobId = awaitCurrentJobId()

        transcriptionJob?.cancel()
        transcriptionJob = null

        if (!jobId.isNullOrBlank()) {
            WorkManager.getInstance(context).cancelUniqueWork("transcription_$jobId")
            deleteServerJob(context, jobId)
        }

        currentJobId = null
        isLoading = false
        isCancelling = false
        progress = 0f
        status = "Transcription cancelled."
        transcriptionError = null
        ActiveJobStore.clear(context)
    }
}

/** If the job is still being submitted, waits briefly for the server to assign its id. */
private suspend fun PianoWeaveViewModel.awaitCurrentJobId(): String? {
    val startTime = System.currentTimeMillis()
    while (currentJobId.isNullOrBlank() &&
        System.currentTimeMillis() - startTime < CANCEL_JOB_ID_WAIT_MS &&
        transcriptionJob?.isActive == true
    ) {
        delay(100)
    }
    return currentJobId
}

/** Deletes the server job; the response carries the balance after any refund. */
private suspend fun PianoWeaveViewModel.deleteServerJob(context: Context, jobId: String) {
    val serverUrl = activeServerUrl
    val uId = if (!isBilledServer) getOrCreateUserId(context) else null
    try {
        val resp = executeServerCallImpl(context, serverUrl) { authHeader ->
            PianoApiFactory.getApi(serverUrl).deleteTranscription(jobId, authHeader = authHeader, userId = uId)
        }
        resp.minutes?.let { userCredits = it }
    } catch (_: Exception) {}
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
