package com.lumenchord.pianoweave.worker

import android.content.Context
import com.lumenchord.pianoweave.util.AppPrefs
import java.util.UUID

/**
 * The Idempotency-Key of a submission whose outcome the app doesn't know yet. The server derives the job
 * from it, so sending it again finds the job a lost answer was about, instead of queuing and charging a
 * second one. Saved before sending, so trying again after the app was closed still finds that job.
 */
object PendingSubmissionStore {

    /**
     * The key to submit [source] with: the saved one if the last submission of [source] got no final
     * answer, otherwise a new one, saved before it is used. [source] names what is sent and where to.
     */
    @Synchronized
    fun keyFor(context: Context, source: String): String {
        val prefs = AppPrefs.get(context)
        val savedKey = prefs.getString(AppPrefs.PENDING_SUBMISSION_KEY, null)
        if (!savedKey.isNullOrBlank() && prefs.getString(AppPrefs.PENDING_SUBMISSION_SOURCE, null) == source) {
            return savedKey
        }
        val key = UUID.randomUUID().toString()
        // commit, not apply: the key has to be on disk before the request can reach the server
        prefs.edit()
            .putString(AppPrefs.PENDING_SUBMISSION_KEY, key)
            .putString(AppPrefs.PENDING_SUBMISSION_SOURCE, source)
            .commit()
        return key
    }

    /** The server answered for good (a job id, or a refusal): the next submission is a new one. */
    @Synchronized
    fun settle(context: Context, key: String) {
        val prefs = AppPrefs.get(context)
        if (prefs.getString(AppPrefs.PENDING_SUBMISSION_KEY, null) != key) return
        prefs.edit()
            .remove(AppPrefs.PENDING_SUBMISSION_KEY)
            .remove(AppPrefs.PENDING_SUBMISSION_SOURCE)
            .apply()
    }
}
