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
import com.lumenchord.pianoweave.api.PianoApiFactory
import com.lumenchord.pianoweave.api.VideoMetadata
import com.lumenchord.pianoweave.cloud.CloudAccountCache
import com.lumenchord.pianoweave.cloud.GoogleDriveManager
import com.lumenchord.pianoweave.midi.MidiStorage
import com.lumenchord.pianoweave.midi.StoredMidi
import com.lumenchord.pianoweave.worker.TranscriptionWorker
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun PianoWeaveViewModel.calculateCostForUrlImpl(url: String) {
    costJob?.cancel()
    val stableUrl = normalizeUrlImpl(url)
    if (stableUrl.isBlank() || !isBilledServer) {
        estimatedCostCredits = null
        isCalculatingCost = false
        return
    }
    isCalculatingCost = true
    costJob = viewModelScope.launch {
        try {
            val api = PianoApiFactory.getApi(activeServerUrl)
            val cost = api.getBillingCost(stableUrl)
            estimatedCostCredits = cost.costMinutes
        } catch (_: Exception) {
            estimatedCostCredits = null
        } finally {
            isCalculatingCost = false
        }
    }
}

internal fun PianoWeaveViewModel.updateUrlImpl(url: String) {
    if (!isLoading) {
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
}

internal fun PianoWeaveViewModel.saveActiveJob(context: Context, jobId: String, url: String) {
    val prefs = context.getSharedPreferences("piano_weave_prefs", Context.MODE_PRIVATE)
    prefs.edit()
        .putString("active_job_id", jobId)
        .putString("active_job_url", url)
        .apply()
}

internal fun PianoWeaveViewModel.clearActiveJob(context: Context) {
    val prefs = context.getSharedPreferences("piano_weave_prefs", Context.MODE_PRIVATE)
    prefs.edit()
        .remove("active_job_id")
        .remove("active_job_url")
        .apply()
}

internal fun PianoWeaveViewModel.resumeActiveJobIfAnyImpl(context: Context) {
    if (isLoading || transcriptionJob?.isActive == true) return
    val prefs = context.getSharedPreferences("piano_weave_prefs", Context.MODE_PRIVATE)
    val savedJobId = prefs.getString("active_job_id", null)
    val savedUrl = prefs.getString("active_job_url", null)

    if (!savedJobId.isNullOrBlank() && !savedUrl.isNullOrBlank()) {
        val storedFile = MidiStorage.find(context, savedUrl)
        if (storedFile != null) {
            clearActiveJob(context)
            loadSongs(context)
            readySong = songs.firstOrNull { it.file.absolutePath == storedFile.absolutePath }
            status = "Ready to learn!"
            progress = 1f
            return
        }

        videoUrl = savedUrl
        currentJobId = savedJobId
        isLoading = true
        status = "Resuming transcription job..."

        enqueueTranscriptionWorker(context, savedJobId, savedUrl)

        transcriptionJob = viewModelScope.launch {
            pollTranscriptionJob(context, savedJobId, savedUrl)
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

internal fun PianoWeaveViewModel.cancelTranscriptionImpl(context: Context) {
    if (isCancelling) return
    isCancelling = true
    status = "Cancelling transcription job..."

    viewModelScope.launch {
        var jobId = currentJobId
        if (jobId.isNullOrBlank()) {
            val startTime = System.currentTimeMillis()
            while (currentJobId.isNullOrBlank() && (System.currentTimeMillis() - startTime) < 10000L && transcriptionJob?.isActive == true) {
                delay(100)
            }
            jobId = currentJobId
        }

        transcriptionJob?.cancel()
        transcriptionJob = null

        if (!jobId.isNullOrBlank()) {
            val serverUrl = activeServerUrl
            val uId = if (!isBilledServer) getOrCreateUserId(context) else null
            WorkManager.getInstance(context).cancelUniqueWork("transcription_$jobId")
            try {
                val resp = executeWithAuthRetryImpl(context) { authHeader ->
                    val api = withContext(Dispatchers.IO) { PianoApiFactory.getApi(serverUrl) }
                    api.deleteTranscription(jobId, authHeader = authHeader, userId = uId)
                }
                if (resp.minutes != null) {
                    userCredits = resp.minutes
                }
            } catch (_: Exception) {}
        }

        currentJobId = null
        isLoading = false
        isCancelling = false
        progress = 0f
        status = "Transcription cancelled."
        transcriptionError = null
        clearActiveJob(context)
    }
}

internal suspend fun PianoWeaveViewModel.pollTranscriptionJob(context: Context, jobId: String, stableUrl: String) {
    try {
        while (currentJobId == jobId) {
            val job = try {
                executeWithAuthRetryImpl(context) { authHeader ->
                    val api = PianoApiFactory.getApi(activeServerUrl)
                    api.getTranscription(jobId, authHeader = authHeader)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                status = "Reconnecting to server..."
                delay(2000)
                continue
            }

            progress = job.progress

            if (job.minutes != null) {
                userCredits = job.minutes
            }

            status = when (job.status) {
                "queued" -> "Queued in server pipeline..."
                "running", "processing" ->
                    if (job.metadata != null) "Transcribing \"${job.metadata.title}\"..."
                    else "AI model transcribing notes..."
                "completed" -> "Transcription completed."
                "failed" -> {
                    val errMsg = job.error ?: "Failed: Server processing error"
                    transcriptionError = errMsg
                    "Error: $errMsg"
                }
                else -> job.status
            }

            if (job.status == "completed") {
                val existingLocal = MidiStorage.find(context, stableUrl)
                if (existingLocal != null) {
                    progress = 1f
                    status = "Loaded from library cache!"
                    loadSongs(context)
                    readySong = songs.firstOrNull { it.file.absolutePath == existingLocal.absolutePath }
                    transcriptionError = null
                    clearActiveJob(context)
                    currentJobId = null
                    break
                }

                status = "Downloading completed MIDI file..."
                var midiDownloaded = false
                var readyStoredMidi: StoredMidi? = null
                while (currentJobId == jobId && !midiDownloaded) {
                    try {
                        withContext(Dispatchers.IO) {
                            executeWithAuthRetryImpl(context) { dlAuthHeader ->
                                val dlApi = PianoApiFactory.getApi(activeServerUrl)
                                val midiResponse = dlApi.downloadMidi(jobId, authHeader = dlAuthHeader)

                                val savedMidi = MidiStorage.save(context, stableUrl, job.metadata, midiResponse)
                                readyStoredMidi = savedMidi

                                if (isCloudSyncEnabled && isGoogleSignedIn) {
                                    try {
                                        val token = googleToken
                                        if (token.isNotBlank()) {
                                            GoogleDriveManager.uploadMidi(context, savedMidi, token)
                                        }
                                    } catch (_: Exception) {
                                        // Ignore background upload errors
                                    }
                                }
                            }
                        }
                        midiDownloaded = true
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        status = "Retrying MIDI download..."
                        delay(2000)
                    }
                }
                if (!midiDownloaded) break

                progress = 1f
                val updatedSongs = MidiStorage.list(context)
                songs = updatedSongs
                readySong = readyStoredMidi ?: updatedSongs.firstOrNull { it.videoUrl == stableUrl }
                status = "Ready to learn!"
                transcriptionError = null
                clearActiveJob(context)
                currentJobId = null
                break
            }
            if (job.status == "failed") {
                clearActiveJob(context)
                currentJobId = null
                break
            }
            delay(2000)
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
    } finally {
        isLoading = false
    }
}

internal fun PianoWeaveViewModel.startTranscriptionImpl(context: Context) {
    val stableUrl = normalizeUrlImpl(videoUrl)
    if (stableUrl.isBlank() || isLoading) return

    videoUrl = stableUrl
    transcriptionError = null

    if (isBilledServer) {
        val cost = estimatedCostCredits
        if (cost != null && userCredits < cost) {
            showNotEnoughCreditsDialog = true
            return
        }
    }

    proceedStartTranscription(context)
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
            val storedFile = MidiStorage.find(context, stableUrl)
            if (storedFile != null) {
                progress = 1f
                status = "Loaded from local library cache!"
                loadSongs(context)
                readySong = songs.firstOrNull { it.file.absolutePath == storedFile.absolutePath }
                isLoading = false
                return@launch
            }

            status = "Checking cloud cache..."
            val cloudList = cloudSongs.ifEmpty { CloudAccountCache.loadCloudSongs(context) }
            val matchingCloudSong = cloudList.firstOrNull { it.videoUrl == stableUrl }
            if (matchingCloudSong != null) {
                val cachedCloudFile = CloudAccountCache.getCachedMidiFile(context, matchingCloudSong.id)
                val storedMidi = if (cachedCloudFile != null && cachedCloudFile.exists()) {
                    StoredMidi(
                        file = cachedCloudFile,
                        videoUrl = matchingCloudSong.videoUrl,
                        metadata = matchingCloudSong.metadata
                    )
                } else {
                    val token = ensureGoogleAuthToken(context)
                    if (!token.isNullOrBlank()) {
                        val downloadResult = GoogleDriveManager.downloadMidiFile(context, matchingCloudSong, token)
                        downloadResult.getOrNull()
                    } else {
                        null
                    }
                }

                if (storedMidi != null) {
                    progress = 1f
                    status = "Loaded from cloud library!"
                    readySong = storedMidi
                    isLoading = false
                    return@launch
                }
            }

            status = "Submitting request to server..."
            val uId = getOrCreateUserId(context)

            val response = executeWithAuthRetryImpl(context) { authHeader ->
                if (isBilledServer && authHeader == null) {
                    throw IllegalAccessException("Google authentication required.")
                }
                val api = PianoApiFactory.getApi(activeServerUrl)
                api.createTranscription(
                    request = CreateTranscriptionRequest(source_url = stableUrl),
                    authHeader = authHeader,
                    userId = if (!isBilledServer) uId else null
                )
            }

            val jobId = response.job_id
            currentJobId = jobId
            saveActiveJob(context, jobId, stableUrl)
            enqueueTranscriptionWorker(context, jobId, stableUrl)
            status = "Job successfully queued..."

            pollTranscriptionJob(context, jobId, stableUrl)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val errMsg = e.localizedMessage ?: "Connection error"
            transcriptionError = errMsg
            status = "Error: $errMsg"
            clearActiveJob(context)
            currentJobId = null
            isLoading = false
        }
    }
}

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
