package com.lumenchord.pianoweave.api

import java.util.Locale
import kotlin.math.roundToInt

/**
 * A part of a song's audio for the server to transcribe (server-info `trimming`), in whole seconds from its start;
 * [endSeconds] null runs to the end. The server checks it against the length it measures and charges only the part.
 */
data class Clip(val startSeconds: Int, val endSeconds: Int?) {

    /** The part's length in a song of [totalSeconds]. */
    fun lengthSeconds(totalSeconds: Float): Float = (endSeconds?.toFloat() ?: totalSeconds) - startSeconds

    /** For the library key and the submission's idempotency source: "30-95", or "30-" to the end. */
    val keyText: String
        get() = "$startSeconds-${endSeconds ?: ""}"

    companion object {
        /** The server refuses shorter parts. */
        const val MIN_SECONDS = 1

        /**
         * The part from [start] to [end] seconds of a song [totalSeconds] long, kept within it and at least
         * [MIN_SECONDS] long; null when that is the whole song. An end at the song's end means "to the end", so a
         * length the server measures a little differently never refuses it.
         */
        fun within(start: Int, end: Int, totalSeconds: Float): Clip? {
            val total = totalSeconds.toInt().coerceAtLeast(MIN_SECONDS)
            val first = start.coerceIn(0, total - MIN_SECONDS)
            val last = end.coerceIn(first + MIN_SECONDS, total)
            val clip = Clip(first, last.takeIf { it < total })
            return clip.takeIf { it.startSeconds > 0 || it.endSeconds != null }
        }

        fun fromKeyText(text: String): Clip? {
            val start = text.substringBefore('-', "").toIntOrNull() ?: return null
            val endText = text.substringAfter('-', "")
            val end = if (endText.isEmpty()) null else endText.toIntOrNull() ?: return null
            if (start < 0 || (end != null && end < start + MIN_SECONDS) || (start == 0 && end == null)) return null
            return Clip(start, end)
        }
    }
}

/**
 * Marks a part's library key: a part of a link is a different song from the whole of it, so its key gets this
 * fragment, before an arrangement's mark (`<link>#part=30-95#piano-arrangement`). As a URL fragment it keeps the
 * key a working link.
 */
private const val CLIP_KEY_MARK = "#part="

/** [sourceKey] for only [clip] of it; [sourceKey] itself for the whole song. */
fun clipSourceKey(sourceKey: String, clip: Clip?): String =
    if (clip == null || sourceKey.clipOfSourceKey != null) sourceKey else sourceKey + CLIP_KEY_MARK + clip.keyText

/** The part a source key (no arrangement mark) was made from; null for the whole song. */
internal val String.clipOfSourceKey: Clip?
    get() {
        val at = lastIndexOf(CLIP_KEY_MARK)
        if (at < 0) return null
        return Clip.fromKeyText(substring(at + CLIP_KEY_MARK.length))
    }

/** [this] source key without its part's mark. */
internal val String.withoutClip: String
    get() = if (clipOfSourceKey != null) substring(0, lastIndexOf(CLIP_KEY_MARK)) else this

/** "1:05", or "1:02:05" past an hour. */
fun formatClipTime(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    val hours = s / 3600
    val minutes = s % 3600 / 60
    return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, s % 60)
    else String.format(Locale.ROOT, "%d:%02d", minutes, s % 60)
}

fun formatClipTime(seconds: Float): String = formatClipTime(seconds.roundToInt())

/**
 * A time as typed: "95", "1:35" or "1:02:05". Null when it can't be read; the caller keeps it within the song.
 */
fun parseClipTime(text: String): Int? {
    val parts = text.trim().split(':')
    if (parts.isEmpty() || parts.size > 3 || parts.any { it.isEmpty() || it.length > 6 || !it.all(Char::isDigit) }) return null
    val numbers = parts.map { it.toInt() }
    // Minutes and seconds after the first part are on a clock: 1:75 is a typo, not 2:15
    if (numbers.drop(1).any { it >= 60 }) return null
    return numbers.fold(0L) { total, part -> total * 60 + part }.takeIf { it <= Int.MAX_VALUE }?.toInt()
}

/**
 * The parts of [source] already made in [mode] (among library keys [keys]) that overlap [clip]: null in the list
 * stands for the whole song, which overlaps every part. The same part is left out: asking for it again loads it
 * from the library instead. Parts that only touch (one ends where the other starts) don't overlap.
 */
fun overlappingParts(keys: Collection<String>, source: String, mode: TranscriptionMode, clip: Clip?): List<Clip?> =
    keys.asSequence()
        .filter { it.sourceOfLibraryKey == source && it.libraryKeyMode == mode }
        .map { it.libraryKeyClip }
        .filter { it != clip && overlaps(it, clip) }
        .distinct()
        .sortedBy { it?.startSeconds ?: -1 }
        .toList()

private fun overlaps(a: Clip?, b: Clip?): Boolean {
    val aStart = a?.startSeconds ?: 0
    val bStart = b?.startSeconds ?: 0
    val aEnd = a?.endSeconds ?: Int.MAX_VALUE
    val bEnd = b?.endSeconds ?: Int.MAX_VALUE
    return aStart < bEnd && bStart < aEnd
}

/** "0:30 – 1:35", or "0:30 – end" for a part that runs to the end. */
fun Clip.describe(): String = "${formatClipTime(startSeconds)} – ${endSeconds?.let(::formatClipTime) ?: "end"}"
