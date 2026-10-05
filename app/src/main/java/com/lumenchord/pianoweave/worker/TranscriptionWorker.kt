package com.lumenchord.pianoweave.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lumenchord.pianoweave.api.PianoApiFactory
import com.lumenchord.pianoweave.api.config.AppConfig
import com.lumenchord.pianoweave.auth.GoogleAuthManager
import com.lumenchord.pianoweave.midi.MidiStorage
import kotlinx.coroutines.delay
import retrofit2.HttpException

class TranscriptionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val stableUrl = inputData.getString(KEY_URL) ?: return Result.failure()
        val serverUrl = inputData.getString(KEY_SERVER_URL) ?: "http://localhost:8000/"

        val api = PianoApiFactory.getApi(serverUrl)
        val clientId = try {
            AppConfig.initialize(applicationContext)
            AppConfig.getConfig().webClientId ?: ""
        } catch (_: Exception) {
            ""
        }
        val prefs = applicationContext.getSharedPreferences("piano_weave_prefs", Context.MODE_PRIVATE)
        val customClientId = prefs.getString("custom_google_client_id", "") ?: ""
        val activeClientId = if (customClientId.isNotBlank()) customClientId else clientId

        var token = GoogleAuthManager.getSavedIdToken(applicationContext)
        var authHeader = GoogleAuthManager.getAuthHeader(token)

        suspend fun <T> executeWithWorkerAuthRetry(apiCall: suspend (String?) -> T): T {
            try {
                return apiCall(authHeader)
            } catch (e: HttpException) {
                if (e.code() == 401 || e.code() == 503) {
                    if (activeClientId.isNotBlank()) {
                        val refreshResult = GoogleAuthManager.silentRefresh(applicationContext, activeClientId)
                        if (refreshResult.isSuccess) {
                            val user = refreshResult.getOrNull()
                            if (user != null) {
                                token = user.idToken
                                authHeader = GoogleAuthManager.getAuthHeader(token)
                                return apiCall(authHeader)
                            }
                        }
                    }
                }
                throw e
            }
        }

        try {
            while (!isStopped) {
                val job = try {
                    executeWithWorkerAuthRetry { header ->
                        api.getTranscription(jobId, authHeader = header)
                    }
                } catch (e: Exception) {
                    return Result.retry()
                }

                when (job.status) {
                    "completed" -> {
                        val midiResponse = try {
                            executeWithWorkerAuthRetry { header ->
                                api.downloadMidi(jobId, authHeader = header)
                            }
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
