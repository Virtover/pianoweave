package com.lumenchord.pianoweave.ui.viewmodel

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.lifecycle.viewModelScope
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val RECORDINGS_DIR = "recordings"
private const val TICK_MS = 100L
private const val MIN_RECORDING_MS = 1000L

// --- Recording ---

/** Records from the microphone into an M4A in the cache; the caller has already been granted RECORD_AUDIO. */
internal fun PianoWeaveViewModel.startRecordingImpl(context: Context) {
    if (isLoading || isRecording) return
    discardRecordingImpl()
    transcriptionError = null

    val dir = File(context.cacheDir, RECORDINGS_DIR).apply { mkdirs() }
    // Leftovers from earlier sessions are never uploaded again.
    dir.listFiles()?.forEach { it.delete() }
    val stamp = SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.US).format(Date())
    val file = File(dir, "Recording $stamp.m4a")

    val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
    try {
        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioChannels(1)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
    } catch (_: Exception) {
        recorder.release()
        file.delete()
        val problem = "The microphone couldn't be started. Close other apps using it and try again."
        transcriptionError = problem
        status = "Error: $problem"
        return
    }

    activeRecorder = recorder
    recordingFile = file
    isRecording = true
    recordingElapsedMs = 0L
    recordingLevel = 0f
    status = "Recording..."

    val maxMs = maxVideoLengthMinutes?.let { (it * 60_000f).toLong() }
    val startedAt = System.currentTimeMillis()
    recordingTickerJob = viewModelScope.launch {
        while (isActive && isRecording) {
            recordingElapsedMs = System.currentTimeMillis() - startedAt
            // maxAmplitude is peak since the last call; the square root makes quiet playing still visible.
            recordingLevel = runCatching { sqrt(recorder.maxAmplitude / 32767f).coerceIn(0f, 1f) }.getOrDefault(0f)
            if (maxMs != null && recordingElapsedMs >= maxMs) {
                stopRecordingImpl()
                break
            }
            delay(TICK_MS)
        }
    }
}

/** Finishes the recording and offers it for transcription, like a picked file. */
internal fun PianoWeaveViewModel.stopRecordingImpl() {
    val recorder = activeRecorder ?: return
    val file = recordingFile
    val elapsed = recordingElapsedMs
    recordingTickerJob?.cancel()
    recordingTickerJob = null
    activeRecorder = null
    recordingFile = null
    isRecording = false
    recordingLevel = 0f

    // stop() throws when nothing usable was captured (e.g. stopped right after starting).
    val stopped = runCatching { recorder.stop() }.isSuccess
    recorder.release()
    if (file == null || !stopped || elapsed < MIN_RECORDING_MS || !file.exists()) {
        file?.delete()
        val problem = "The recording was too short. Record at least a few seconds."
        transcriptionError = problem
        status = "Error: $problem"
        return
    }

    viewModelScope.launch {
        val duration = withContext(Dispatchers.IO) { readDurationSeconds(file) } ?: (elapsed / 1000f)
        val media = SelectedMediaFile(
            uri = Uri.fromFile(file),
            name = file.name,
            sizeBytes = file.length().takeIf { it > 0 },
            durationSeconds = duration
        )
        limitProblem(media)?.let { problem ->
            file.delete()
            transcriptionError = problem
            status = "Error: $problem"
            return@launch
        }
        recordedMediaFile = media
        transcriptionError = null
        status = "Ready to convert."
        progress = 0f
        if (transcriptionSource == TranscriptionSource.Record) calculateCostForFile(media)
    }
}

/** Throws away an unfinished or finished recording. */
internal fun PianoWeaveViewModel.discardRecordingImpl() {
    if (isLoading) return
    recordingTickerJob?.cancel()
    recordingTickerJob = null
    activeRecorder?.let { recorder ->
        runCatching { recorder.stop() }
        recorder.release()
    }
    activeRecorder = null
    recordingFile?.delete()
    recordingFile = null
    isRecording = false
    recordingLevel = 0f
    recordingElapsedMs = 0L

    recordedMediaFile?.uri?.path?.let { File(it).delete() }
    recordedMediaFile = null
    if (transcriptionSource == TranscriptionSource.Record) {
        costJob?.cancel()
        estimatedCostCredits = null
        isCalculatingCost = false
    }
}

private fun readDurationSeconds(file: File): Float? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()?.takeIf { it > 0 }?.let { it / 1000f }
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}
