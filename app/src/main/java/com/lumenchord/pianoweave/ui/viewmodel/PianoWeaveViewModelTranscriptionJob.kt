package com.lumenchord.pianoweave.ui.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.lumenchord.pianoweave.api.MaintenanceTexts
import com.lumenchord.pianoweave.api.ServerMaintenance
import com.lumenchord.pianoweave.api.PianoApiFactory
import com.lumenchord.pianoweave.api.ServerErrors
import com.lumenchord.pianoweave.api.TranscriptionStatusResponse
import com.lumenchord.pianoweave.api.failureMessage
import com.lumenchord.pianoweave.api.isUploadSourceKey
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

private const val POLL_INTERVAL_MS = 2000L
// While the server is offline after an announced shutdown
private const val OFFLINE_POLL_INTERVAL_MS = 30_000L
private const val CANCEL_JOB_ID_WAIT_MS = 10000L
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
        if (ServerErrors.isGoneFromServer(e) && currentJobId == jobId) failGoneJob(context)
    } finally {
        isLoading = false
    }
}
/** Null while the server can't be reached or is busy; throws if the job is gone from it for good. */
private suspend fun PianoWeaveViewModel.fetchJobStatusOrNull(context: Context, jobId: String): TranscriptionStatusResponse? =
    try {
        executeServerCallImpl(context) { authHeader ->
            PianoApiFactory.getApi(activeServerUrl).getTranscription(jobId, authHeader = authHeader)
        }
    } catch (e: Exception) {
        if (e is CancellationException || ServerErrors.isGoneFromServer(e)) throw e
        null
    }
/** The job or its MIDI expired on the server (or was removed): waiting longer would never end. */
private fun PianoWeaveViewModel.failGoneJob(context: Context) {
    showTranscriptionError(
        "This transcription is no longer on the server; it may have expired. Tap Transcribe to start it again.",
        offerUpload = false
    )
    finishActiveJob(context)
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
            uploadOfferedFor = errMsg.takeIf { ServerErrors.uploadMayWork(job.errorCode) && canOfferUpload() }
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
            if (ServerErrors.isGoneFromServer(e)) {
                failGoneJob(context)
                return null
            }
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
internal fun PianoWeaveViewModel.selectLocalSong(context: Context, file: File) {
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
