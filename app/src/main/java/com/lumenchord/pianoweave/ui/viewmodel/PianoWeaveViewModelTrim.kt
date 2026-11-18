package com.lumenchord.pianoweave.ui.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.lumenchord.pianoweave.api.Clip
import com.lumenchord.pianoweave.api.ServerErrors
import com.lumenchord.pianoweave.api.TranscriptionMode
import com.lumenchord.pianoweave.api.overlappingParts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** A part chosen with Trim, for the source it was chosen for (a link, or a file's URI). */
internal data class TrimChoice(val sourceId: String, val clip: Clip)

/** Songs shorter than this have no part worth cutting, so Trim isn't offered for them. */
private const val MIN_TRIM_SOURCE_SECONDS = 2f

/** What a part would be chosen for now: the link, or the picked or recorded file. */
internal val PianoWeaveViewModel.trimSourceId: String?
    get() = when (transcriptionSource) {
        TranscriptionSource.Link -> normalizeUrlImpl(videoUrl).takeIf { it.isNotBlank() }
        else -> sourceMediaFile?.uri?.toString()
    }

/** The chosen source's length in seconds, once known: a link's from the server, a file's from the device. */
val PianoWeaveViewModel.trimSourceSeconds: Float?
    get() = when (transcriptionSource) {
        TranscriptionSource.Link -> trimSourceId?.let { linkDurations[it] }
        else -> sourceMediaFile?.durationSeconds
    }?.takeIf { it.isFinite() && it > 0f }

/**
 * The part to send with the next submission: what Trim chose for the current source, on a server that cuts it.
 * A new link or file leaves the choice behind by itself, as it was made for another source.
 */
val PianoWeaveViewModel.activeClip: Clip?
    get() = trimChoice?.takeIf { supportsTrimming && it.sourceId == trimSourceId }?.clip

/**
 * Whether the card offers Trim now. A file whose length the device couldn't read has nothing to bound the part
 * by; a link's length is asked for when Trim opens.
 */
val PianoWeaveViewModel.canTrim: Boolean
    get() {
        if (!supportsTrimming || trimSourceId == null || isRecording) return false
        val seconds = trimSourceSeconds
        return if (transcriptionSource == TranscriptionSource.Link) seconds == null || seconds >= MIN_TRIM_SOURCE_SECONDS
        else seconds != null && seconds >= MIN_TRIM_SOURCE_SECONDS
    }

internal fun PianoWeaveViewModel.openTrimImpl(context: Context) {
    if (isLoading || !canTrim) return
    trimLengthError = null
    showTrimDialog = true
    if (trimSourceSeconds == null) readLinkLength(context)
}

/** Asks the server for the link's length, as the cost check does; it reuses that check when one was made. */
private fun PianoWeaveViewModel.readLinkLength(context: Context) {
    val url = trimSourceId ?: return
    if (transcriptionSource != TranscriptionSource.Link || isReadingTrimLength) return
    isReadingTrimLength = true
    viewModelScope.launch {
        try {
            val cost = fetchBillingCostResponse(context, sourceUrl = url)
            linkDurations = linkDurations + (url to cost.durationSeconds)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val serverMessage = (e as? HttpException)?.let { ServerErrors.detailMessage(ServerErrors.errorBody(it)) }
            trimLengthError = serverMessage ?: connectionMessage(e) ?: "The video's length couldn't be read. Try again in a moment."
        } finally {
            isReadingTrimLength = false
        }
    }
}

/** Keeps [clip] (null: the whole song) for the current source, and works the cost out for it. */
internal fun PianoWeaveViewModel.applyTrimImpl(clip: Clip?) {
    showTrimDialog = false
    if (isLoading) return
    val source = trimSourceId ?: return
    val previous = activeClip
    trimChoice = clip?.let { TrimChoice(source, it) }
    if (activeClip != previous) recalculateCostImpl()
}

/** What the overlap warning tells: the part about to be made ([clip] null: the whole song) and those it overlaps. */
data class OverlapWarning(val mode: TranscriptionMode, val clip: Clip?, val overlapping: List<Clip?>)

/**
 * Parts of [source] in [mode] that the library has (this device's songs, and the cloud library's when sync is on)
 * and that overlap [clip]. Deleted songs don't count, as they don't show.
 */
internal fun PianoWeaveViewModel.overlappingLibraryParts(source: String, mode: TranscriptionMode, clip: Clip?): List<Clip?> {
    val keys = songs.filterNot { it.isDeleted }.map { it.videoUrl } +
        (if (isCloudSyncEnabled) cloudSongs.map { it.videoUrl } else emptyList())
    return overlappingParts(keys, source, mode, clip)
}

/** Length in seconds to charge for: the chosen part's, or null for the whole source (asked by link or file). */
internal val PianoWeaveViewModel.clipCostSeconds: Float?
    get() {
        val clip = activeClip ?: return null
        val total = trimSourceSeconds ?: return null
        return clip.lengthSeconds(total).takeIf { it > 0f }
    }
