package com.lumenchord.pianoweave.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lumenchord.pianoweave.api.PianoApiFactory
import com.lumenchord.pianoweave.auth.GoogleAuthManager
import com.lumenchord.pianoweave.midi.MidiStorage
import kotlinx.coroutines.delay

class TranscriptionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val stableUrl = inputData.getString(KEY_URL) ?: return Result.failure()
        val serverUrl = inputData.getString(KEY_SERVER_URL) ?: "http://localhost:8000/"

        val api = PianoApiFactory.getApi(serverUrl)
        val token = GoogleAuthManager.getSavedIdToken(applicationContext)
        val authHeader = GoogleAuthManager.getAuthHeader(token)

        try {
            while (!isStopped) {
                val job = try {
                    api.getTranscription(jobId, authHeader = authHeader)
                } catch (e: Exception) {
                    return Result.retry()
                }

                when (job.status) {
                    "completed" -> {
                        val midiResponse = try {
                            api.downloadMidi(jobId, authHeader = authHeader)
                        } catch (e: Exception) {
                            return Result.retry()
                        }
                        MidiStorage.save(applicationContext, stableUrl, job.metadata, midiResponse)
                        clearActiveJob(applicationContext)
                        return Result.success()
                    }
                    "failed" -> {
                        clearActiveJob(applicationContext)
                        return Result.failure()
                    }
                    else -> {
                        delay(3000)
                    }
                }
            }
            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }

    private fun clearActiveJob(context: Context) {
        val prefs = context.getSharedPreferences("piano_weave_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("active_job_id")
            .remove("active_job_url")
            .apply()
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
        const val KEY_URL = "url"
        const val KEY_SERVER_URL = "server_url"
    }
}
