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

    fun clear(context: Context) {
        AppPrefs.get(context).edit()
            .remove(AppPrefs.ACTIVE_JOB_ID)
            .remove(AppPrefs.ACTIVE_JOB_URL)
            .apply()
    }
}
