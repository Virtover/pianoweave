package com.lumenchord.pianoweave.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lumenchord.pianoweave.api.PianoApi
import com.lumenchord.pianoweave.api.PianoApiFactory
import com.lumenchord.pianoweave.api.TranscriptionStatusResponse
import com.lumenchord.pianoweave.api.executeServerCall
import com.lumenchord.pianoweave.auth.GoogleAuthManager
import com.lumenchord.pianoweave.cloud.GoogleDriveManager
import com.lumenchord.pianoweave.midi.CompletedTranscriptions
import com.lumenchord.pianoweave.midi.MidiStorage
import com.lumenchord.pianoweave.util.AppPrefs
import kotlinx.coroutines.delay

/** Background counterpart of the in-app poller: waits for a server job and saves its MIDI. */
class TranscriptionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val stableUrl = inputData.getString(KEY_URL) ?: return Result.failure()
        val serverUrl = inputData.getString(KEY_SERVER_URL) ?: DEFAULT_SERVER_URL
        // In a cold process: tells the official server apart (the token goes only there) and gives each
        // server its password, without which a password-protected server refuses every request.
        PianoApiFactory.initializeInBackground(applicationContext)
        val api = PianoApiFactory.getApi(serverUrl)

        try {
            while (!isStopped) {
                val job = try {
                    withAuth(serverUrl) { header -> api.getTranscription(jobId, authHeader = header) }
                } catch (_: Exception) {
                    return Result.retry()
                }

                when (job.status) {
                    "completed" -> return saveCompletedJob(api, serverUrl, jobId, job, stableUrl)
                    "failed" -> {
                        ActiveJobStore.clear(applicationContext, jobId)
                        return Result.failure()
                    }
                    else -> delay(POLL_INTERVAL_MS)
                }
            }
            return Result.success()
        } catch (_: Exception) {
            return Result.retry()
        }
    }

    private suspend fun saveCompletedJob(
        api: PianoApi,
        serverUrl: String,
        jobId: String,
        job: TranscriptionStatusResponse,
        stableUrl: String
    ): Result {
        if (MidiStorage.find(applicationContext, stableUrl) != null) {
            ActiveJobStore.clear(applicationContext, jobId)
            return Result.success()
        }

        // Null if the in-app poller saved it first; it then also takes care of the Drive backup.
        val savedMidi = try {
            CompletedTranscriptions.saveOnce(applicationContext, stableUrl, job) {
                withAuth(serverUrl) { header -> api.downloadMidi(jobId, authHeader = header) }
            }
        } catch (_: Exception) {
            return Result.retry()
        }

        val isCloudSyncEnabled = AppPrefs.get(applicationContext).getBoolean(AppPrefs.IS_CLOUD_SYNC_ENABLED, false)
        val token = GoogleAuthManager.getSavedToken(applicationContext)
        if (savedMidi != null && isCloudSyncEnabled && !token.isNullOrBlank()) {
            try {
                GoogleDriveManager.backUpNewSong(applicationContext, savedMidi, token)
            } catch (_: Exception) {}
        }

        ActiveJobStore.clear(applicationContext, jobId)
        return Result.success()
    }

    private suspend fun <T> withAuth(serverUrl: String, apiCall: suspend (authHeader: String?) -> T): T =
        executeServerCall(applicationContext, serverUrl, apiCall = apiCall)

    companion object {
        const val KEY_JOB_ID = "job_id"
        const val KEY_URL = "url"
        const val KEY_SERVER_URL = "server_url"

        private const val DEFAULT_SERVER_URL = "http://localhost:8000/"
        private const val POLL_INTERVAL_MS = 3000L
    }
}
