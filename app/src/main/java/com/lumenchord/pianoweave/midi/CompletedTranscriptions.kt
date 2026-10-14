package com.lumenchord.pianoweave.midi

import android.content.Context
import com.lumenchord.pianoweave.api.TranscriptionStatusResponse
import com.lumenchord.pianoweave.api.VideoMetadata
import com.lumenchord.pianoweave.api.placeholderMetadata
import com.lumenchord.pianoweave.api.sanitizeVideoMetadata
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.ResponseBody

/**
 * The in-app poller and TranscriptionWorker both see the same job complete. Only the first one
 * downloads and saves it; before this, both did, and both uploaded it to Drive (duplicate copies).
 */
object CompletedTranscriptions {

    private val mutex = Mutex()

    /** Saves the job's MIDI unless the song is already stored. Returns the new song, or null if it existed. */
    suspend fun saveOnce(
        context: Context,
        stableUrl: String,
        job: TranscriptionStatusResponse,
        download: suspend () -> ResponseBody
    ): StoredMidi? = mutex.withLock {
        if (MidiStorage.find(context, stableUrl) != null) return@withLock null
        MidiStorage.save(context, stableUrl, metadataFor(job, stableUrl), download())
    }

    /**
     * The server sends `metadata: null` when its stored metadata fails validation, but still sends the
     * plain title; use it rather than the "Piano Performance" placeholder.
     */
    private fun metadataFor(job: TranscriptionStatusResponse, stableUrl: String): VideoMetadata? =
        job.metadata?.let { sanitizeVideoMetadata(it, fallbackUrl = stableUrl) }
            ?: job.title?.takeIf { it.isNotBlank() }?.let { placeholderMetadata(stableUrl).copy(title = it) }
}
