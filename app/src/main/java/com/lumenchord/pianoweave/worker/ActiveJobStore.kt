package com.lumenchord.pianoweave.worker

import android.content.Context
import com.lumenchord.pianoweave.util.AppPrefs

/** Persists the in-flight transcription job so it can be resumed after process death. */
object ActiveJobStore {

    data class ActiveJob(val jobId: String, val url: String)

    fun save(context: Context, jobId: String, url: String) {
        AppPrefs.get(context).edit()
            .putString(AppPrefs.ACTIVE_JOB_ID, jobId)
            .putString(AppPrefs.ACTIVE_JOB_URL, url)
            .apply()
    }

    fun load(context: Context): ActiveJob? {
        val prefs = AppPrefs.get(context)
        val jobId = prefs.getString(AppPrefs.ACTIVE_JOB_ID, null)
        val url = prefs.getString(AppPrefs.ACTIVE_JOB_URL, null)
        if (jobId.isNullOrBlank() || url.isNullOrBlank()) return null
        return ActiveJob(jobId, url)
    }

    /**
     * Forgets the saved job; with [jobId], only if it is still that job. A background worker finishing an
     * older job must not forget the one the user started since.
     */
    @Synchronized
    fun clear(context: Context, jobId: String? = null) {
        if (jobId != null && load(context)?.jobId != jobId) return
        AppPrefs.get(context).edit()
            .remove(AppPrefs.ACTIVE_JOB_ID)
            .remove(AppPrefs.ACTIVE_JOB_URL)
            .apply()
    }
}
