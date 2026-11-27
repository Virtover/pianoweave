package com.lumenchord.pianoweave.midi

import android.content.Context
import com.lumenchord.pianoweave.api.TranscriptionStatusResponse
import com.lumenchord.pianoweave.api.VideoMetadata
import com.lumenchord.pianoweave.api.isEnsembleKey
import com.lumenchord.pianoweave.api.libraryKeyClip
import com.lumenchord.pianoweave.api.placeholderMetadata
import com.lumenchord.pianoweave.api.sanitizeVideoMetadata
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.ResponseBody
import retrofit2.HttpException

/**
 * The in-app poller and TranscriptionWorker both see the same job complete. Only the first one
 * downloads and saves it; before this, both did, and both uploaded it to Drive (duplicate copies).
 */
object CompletedTranscriptions {

    private val mutex = Mutex()

    /**
     * Saves the job's MIDI unless the song is already stored. Returns the new song, or null if it existed.
     * [download] fetches one result by name (null: the default one); an ensemble job's results all go into
     * the song's one file ([MidiVariants.download]), written once all are in, so a crash leaves no half song.
     * [refreshStatus] reads the job again while an ensemble's levels may still be on their way
     * ([MidiVariants.awaitLateLevels]).
     */
    suspend fun saveOnce(
        context: Context,
        stableUrl: String,
        job: TranscriptionStatusResponse,
        refreshStatus: suspend () -> TranscriptionStatusResponse,
        download: suspend (variant: String?) -> ResponseBody
    ): StoredMidi? = mutex.withLock {
        if (MidiStorage.find(context, stableUrl) != null) return@withLock null
        val isEnsemble = stableUrl.isEnsembleKey
        val listed = MidiVariants.awaitLateLevels(job.variants, mayGetLevels = isEnsemble, refresh = { refreshStatus().variants })
        val bytes = MidiVariants.download(listed, { variant -> download(variant).use { it.bytes() } }, ::isMissing, isEnsemble)
        MidiStorage.saveBytes(context, stableUrl, metadataFor(job, stableUrl), bytes)
    }

    /** A result the server doesn't have (404, error_code `variant_unavailable`), not one it couldn't send just now. */
    private fun isMissing(e: Throwable): Boolean = (e as? HttpException)?.code() == 404

    /**
     * The server sends `metadata: null` when its stored metadata fails validation, but still sends the
     * plain title; use it rather than the "Piano Performance" placeholder.
     */
    private fun metadataFor(job: TranscriptionStatusResponse, stableUrl: String): VideoMetadata? =
        (job.metadata?.let { sanitizeVideoMetadata(it, fallbackUrl = stableUrl) }
            ?: job.title?.takeIf { it.isNotBlank() }?.let { placeholderMetadata(stableUrl).copy(title = it) })
            ?.let { partLength(it, stableUrl) }

    /** A part's song lasts as long as the part; the server's metadata gives the whole video's length. */
    internal fun partLength(metadata: VideoMetadata, libraryKey: String): VideoMetadata {
        val clip = libraryKey.libraryKeyClip ?: return metadata
        if (metadata.duration <= 0f) return metadata
        return metadata.copy(duration = clip.lengthSeconds(metadata.duration).coerceAtLeast(0f))
    }
}
