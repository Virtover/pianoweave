package com.lumenchord.pianoweave.ui.viewmodel

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.viewModelScope
import com.lumenchord.pianoweave.api.CreateTranscriptionResponse
import com.lumenchord.pianoweave.api.PianoApiFactory
import com.lumenchord.pianoweave.api.ServerErrors
import com.lumenchord.pianoweave.api.retryWhenBusy
import com.lumenchord.pianoweave.api.UploadFormats
import com.lumenchord.pianoweave.api.uploadSourceKey
import com.lumenchord.pianoweave.billing.canAfford
import com.lumenchord.pianoweave.worker.ActiveJobStore
import com.lumenchord.pianoweave.worker.PendingSubmissionStore
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink

/** An audio or video file picked for transcription. Size and duration are null when the device can't tell. */
data class SelectedMediaFile(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long?,
    val durationSeconds: Float?
)

/** What the input card transcribes: a pasted link, a picked file or a microphone recording. */
enum class TranscriptionSource { Link, File, Record }

/** The file the chosen source would upload, if any. */
internal val PianoWeaveViewModel.sourceMediaFile: SelectedMediaFile?
    get() = when (transcriptionSource) {
        TranscriptionSource.Link -> null
        TranscriptionSource.File -> selectedMediaFile
        TranscriptionSource.Record -> recordedMediaFile
    }

private const val BYTES_PER_MB = 1024f * 1024f

// --- Source ---

internal fun PianoWeaveViewModel.selectTranscriptionSourceImpl(source: TranscriptionSource) {
    if (isLoading || source == transcriptionSource) return
    transcriptionSource = source
    transcriptionError = null
    // The cost shown belongs to the chosen source.
    recalculateCostImpl()
}

/** Works out the cost of what the chosen source would transcribe, or clears it if there is nothing yet. */
internal fun PianoWeaveViewModel.recalculateCostImpl() {
    if (transcriptionSource == TranscriptionSource.Link) {
        calculateCostForUrlImpl(videoUrl)
    } else sourceMediaFile?.let { calculateCostForFile(it) } ?: run {
        costJob?.cancel()
        estimatedCostCredits = null
        isCalculatingCost = false
    }
}

// --- Picking ---

internal fun PianoWeaveViewModel.selectMediaFileImpl(context: Context, uri: Uri) {
    if (isLoading) return
    viewModelScope.launch {
        val file = withContext(Dispatchers.IO) { readMediaFile(context.contentResolver, context, uri) }
        val problem = when {
            file == null -> "That file couldn't be opened."
            !file.isAllowedFormat -> "That file type isn't supported. Choose one of: ${UploadFormats.DISPLAY_NAMES}."
            file.hasNoAudio -> "That file has no audio to transcribe."
            else -> limitProblem(file.media)
        }
        if (problem != null || file == null) {
            transcriptionError = problem
            status = "Error: $problem"
            return@launch
        }

        transcriptionError = null
        transcriptionSource = TranscriptionSource.File
        selectedMediaFile = file.media
        status = "Ready to convert."
        progress = 0f
        calculateCostForFile(file.media)
    }
}

internal fun PianoWeaveViewModel.clearSelectedMediaFileImpl() {
    if (isLoading) return
    costJob?.cancel()
    selectedMediaFile = null
    estimatedCostCredits = null
    isCalculatingCost = false
    status = "Choose a recording to begin."
}

/** The server's limits, checked up front so a large file isn't uploaded only to be rejected. */
internal fun PianoWeaveViewModel.limitProblem(file: SelectedMediaFile): String? {
    val maxMb = maxUploadSizeMb
    if (maxMb != null && file.sizeBytes != null && file.sizeBytes > maxMb * BYTES_PER_MB) {
        return "That file is larger than the server's ${"%.0f".format(maxMb)} MB limit."
    }
    val maxMinutes = maxVideoLengthMinutes
    if (maxMinutes != null && file.durationSeconds != null && file.durationSeconds > maxMinutes * 60f) {
        return "That recording is longer than the server's ${"%.0f".format(maxMinutes)} minute limit."
    }
    return null
}

internal fun PianoWeaveViewModel.calculateCostForFile(file: SelectedMediaFile) {
    costJob?.cancel()
    val duration = file.durationSeconds
    val context = appContext
    if (duration == null || !isBilledServer || !isGoogleSignedIn || context == null) {
        // Unknown length: the server works out the cost after the upload and still enforces the balance.
        // Signed out: a billed server tells the cost only after sign-in, which recalculates it.
        estimatedCostCredits = null
        isCalculatingCost = false
        return
    }
    isCalculatingCost = true
    costJob = viewModelScope.launch {
        try {
            estimatedCostCredits = fetchBillingCost(context, durationSeconds = duration)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            estimatedCostCredits = null
        } finally {
            isCalculatingCost = false
        }
    }
}

private class ReadMediaFile(val media: SelectedMediaFile, val hasNoAudio: Boolean, val isAllowedFormat: Boolean)

private fun readMediaFile(resolver: ContentResolver, context: Context, uri: Uri): ReadMediaFile? {
    var name: String? = null
    var size: Long? = null
    val mimeType = runCatching { resolver.getType(uri) }.getOrNull()
    try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(0)
                size = if (cursor.isNull(1)) null else cursor.getLong(1)
            }
        }
    } catch (_: Exception) {
        return null
    }

    // Formats Android can't read (e.g. WMA) still upload; the server decides whether it can use them.
    var durationSeconds: Float? = null
    var hasNoAudio = false
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        durationSeconds = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()?.takeIf { it > 0 }?.let { it / 1000f }
        hasNoAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == null &&
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
    } catch (_: Exception) {
    } finally {
        runCatching { retriever.release() }
    }

    return ReadMediaFile(
        media = SelectedMediaFile(
            uri = uri,
            name = name?.takeIf { it.isNotBlank() } ?: "Recording",
            sizeBytes = size?.takeIf { it > 0 },
            durationSeconds = durationSeconds
        ),
        hasNoAudio = hasNoAudio,
        isAllowedFormat = UploadFormats.isAllowed(mimeType, name)
    )
}

// --- Uploading ---

internal fun PianoWeaveViewModel.proceedStartUpload(context: Context, file: SelectedMediaFile) {
    if (isLoading) return

    transcriptionJob = viewModelScope.launch {
        isLoading = true
        readySong = null
        progress = 0f

        // The server turns a low balance away before it reads the upload, and the client then often
        // reports a broken connection instead of why, so the balance is checked before sending anything.
        if (isBilledServer && !hasBalanceForUpload(context, file)) {
            isLoading = false
            status = "Ready to convert."
            showNotEnoughCreditsDialog = true
            return@launch
        }

        isUploading = true
        status = "Uploading \"${file.name}\"..."

        val body = MediaUploadBody(context.contentResolver, file) { fraction ->
            progress = fraction
            status = "Uploading \"${file.name}\"... ${(fraction * 100).toInt()}%"
        }
        activeUpload = body
        val idempotencyKey = PendingSubmissionStore.keyFor(context, "upload|$activeServerUrl|${file.uri}|${file.sizeBytes}")

        try {
            // Once the whole file is sent the server queues and bills the job, so its id must not be
            // lost to a cancellation: cancelling stops the body instead (see cancelTranscriptionImpl).
            val submitted = withContext(NonCancellable) {
                submitUpload(context, file, body, idempotencyKey)
            }
            val jobId = submitted.job_id
            // The server measures the decoded audio, so this, not the estimate, is what was charged
            submitted.minutes?.let { userCredits = it }
            currentJobId = jobId
            isUploading = false
            if (body.isCancelled) return@launch // the cancel flow deletes the job

            val key = uploadSourceKey(jobId)
            ActiveJobStore.save(context, jobId, key)
            enqueueTranscriptionWorker(context, jobId, key)
            status = "Job successfully queued..."
            progress = 0f

            pollTranscriptionJob(context, jobId, key)
            // Kept on screen while it is transcribed, and kept after a failure so it can be retried.
            if (readySong != null) {
                if (file == recordedMediaFile) {
                    file.uri.path?.let { java.io.File(it).delete() }
                    recordedMediaFile = null
                } else {
                    selectedMediaFile = null
                }
                estimatedCostCredits = null
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (body.isCancelled) {
                // Stopped before the whole file was sent, so nothing was queued: the next try is a new one
                PendingSubmissionStore.settle(context, idempotencyKey)
                return@launch
            }
            // A connection broken mid-upload may be the server's refusal of a low balance (402) in disguise
            val balance = if (isBilledServer && ServerErrors.isBrokenUpload(e)) loadUserBalance(context) else null
            if (balance != null && !canAfford(balance, estimatedCostCredits)) {
                failTranscriptionStart(context, InsufficientBalanceException())
            } else {
                failTranscriptionStart(context, e)
            }
        } finally {
            isUploading = false
            activeUpload = null
        }
    }
}

/**
 * True if the balance covers [file], or if that can't be told now (the server still enforces it). Uses
 * the cost shown, or asks for it when the length is known but the cost isn't yet.
 */
private suspend fun PianoWeaveViewModel.hasBalanceForUpload(context: Context, file: SelectedMediaFile): Boolean {
    status = "Checking your balance..."
    val balance = loadUserBalance(context) ?: return true
    val cost = estimatedCostCredits ?: file.durationSeconds?.let { duration ->
        try {
            fetchBillingCost(context, durationSeconds = duration).also { estimatedCostCredits = it }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }
    return canAfford(balance, cost)
}

private suspend fun PianoWeaveViewModel.submitUpload(
    context: Context,
    file: SelectedMediaFile,
    body: MediaUploadBody,
    key: String
): CreateTranscriptionResponse {
    val uId = getOrCreateUserId(context)
    val serverUrl = activeServerUrl
    // A retry with the same key is answered before the server reads the file, so the body is not stored twice
    return settlingSubmission(context, key) {
        retryWhenBusy(attempts = ServerErrors.SUBMIT_ATTEMPTS, retryDelayMs = ServerErrors::submitRetryDelayMs) {
            executeServerCallImpl(context) { authHeader ->
                if (isBilledServer && authHeader == null) {
                    throw IllegalAccessException("Google authentication required.")
                }
                PianoApiFactory.getApi(serverUrl).uploadTranscription(
                    filename = file.name,
                    file = body,
                    authHeader = authHeader,
                    userId = if (!isBilledServer) uId else null,
                    idempotencyKey = key
                )
            }
        }
    }
}

/**
 * Streams the picked file as the request body, reporting progress. It can be sent again (the auth
 * retry re-sends after a 401), and [cancel] aborts it mid-transfer so the server never queues the job.
 */
internal class MediaUploadBody(
    private val resolver: ContentResolver,
    private val file: SelectedMediaFile,
    private val onProgress: (Float) -> Unit
) : RequestBody() {

    @Volatile
    var isCancelled = false
        private set

    fun cancel() {
        isCancelled = true
    }

    override fun contentType() = "application/octet-stream".toMediaType()

    override fun contentLength(): Long = file.sizeBytes ?: -1L

    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(file.uri) ?: throw FileNotFoundException("The file is no longer available")
        input.use {
            val buffer = ByteArray(64 * 1024)
            var sent = 0L
            var lastPercent = -1
            while (true) {
                if (isCancelled) throw IOException("Upload cancelled")
                val read = it.read(buffer)
                if (read == -1) break
                sink.write(buffer, 0, read)
                sent += read
                val total = file.sizeBytes ?: continue
                val percent = (sent * 100 / total).toInt()
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress((sent.toFloat() / total).coerceAtMost(1f))
                }
            }
        }
    }
}
