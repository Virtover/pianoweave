package com.lumenchord.pianoweave.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lumenchord.pianoweave.api.PianoApi
import com.lumenchord.pianoweave.api.PianoApiFactory
import com.lumenchord.pianoweave.api.TranscriptionStatusResponse
import com.lumenchord.pianoweave.auth.GoogleAuthManager
import com.lumenchord.pianoweave.cloud.GoogleDriveManager
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
        val api = PianoApiFactory.getApi(serverUrl)

        try {
            while (!isStopped) {
                val job = try {
                    withAuth { header -> api.getTranscription(jobId, authHeader = header) }
                } catch (_: Exception) {
                    return Result.retry()
                }

                when (job.status) {
                    "completed" -> return saveCompletedJob(api, jobId, job, stableUrl)
                    "failed" -> {
                        ActiveJobStore.clear(applicationContext)
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
        jobId: String,
        job: TranscriptionStatusResponse,
        stableUrl: String
    ): Result {
        if (MidiStorage.find(applicationContext, stableUrl) != null) {
            ActiveJobStore.clear(applicationContext)
            return Result.success()
        }

        val midiResponse = try {
            withAuth { header -> api.downloadMidi(jobId, authHeader = header) }
        } catch (_: Exception) {
            return Result.retry()
        }

        val savedMidi = MidiStorage.save(applicationContext, stableUrl, job.metadata, midiResponse)
        val isCloudSyncEnabled = AppPrefs.get(applicationContext).getBoolean(AppPrefs.IS_CLOUD_SYNC_ENABLED, false)
        val token = GoogleAuthManager.getSavedToken(applicationContext)
        if (isCloudSyncEnabled && !token.isNullOrBlank()) {
            try {
                GoogleDriveManager.uploadMidi(applicationContext, savedMidi, token)
            } catch (_: Exception) {}
        }

        ActiveJobStore.clear(applicationContext)
        return Result.success()
    }

    private suspend fun <T> withAuth(apiCall: suspend (authHeader: String?) -> T): T =
        GoogleAuthManager.executeWithAuthRetry(applicationContext, apiCall = apiCall)

    companion object {
        const val KEY_JOB_ID = "job_id"
        const val KEY_URL = "url"
        const val KEY_SERVER_URL = "server_url"

        private const val DEFAULT_SERVER_URL = "http://localhost:8000/"
        private const val POLL_INTERVAL_MS = 3000L
    }
}
