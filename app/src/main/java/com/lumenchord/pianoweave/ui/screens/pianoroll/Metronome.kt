package com.lumenchord.pianoweave.ui.screens.pianoroll

import com.lumenchord.pianoweave.midi.Beat

/** A count-in: [clicks] beats, [beatMs] apart in song time (divide by the playback speed for real time). */
internal data class CountIn(val clicks: Int, val beatMs: Long)

/** Index of the first beat at or after [ms]. */
private fun List<Beat>.firstAtOrAfter(ms: Long): Int =
    binarySearchBy(ms) { it.timeMs }.let { if (it < 0) -it - 1 else it }

/** The last beat at or before [ms], or null before the first. */
internal fun beatAtOrBefore(beats: List<Beat>, ms: Long): Beat? =
    beats.getOrNull(beats.firstAtOrAfter(ms + 1) - 1)

/**
 * One bar of clicks before playing from [fromMs], at the tempo of the beat about to play. The bar is the
 * first whole bar from there on (from before, near the end): a pickup bar of one beat would count in just
 * once, so bars shorter than two beats are passed over. Null without a beat grid.
 */
internal fun countInFor(beats: List<Beat>, fromMs: Long): CountIn? {
    if (beats.size < 2) return null
    val index = beats.firstAtOrAfter(fromMs).coerceAtMost(beats.size - 2)
    val beatMs = (beats[index + 1].timeMs - beats[index].timeMs).coerceIn(150L, 3000L)

    val barStarts = beats.indices.filter { beats[it].isBarStart }
    val bars = barStarts.zipWithNext { start, end -> start to end - start }.filter { it.second >= 2 }
    val barBeats = (bars.firstOrNull { it.first >= index } ?: bars.lastOrNull())?.second ?: 4
    return CountIn(barBeats.coerceAtMost(12), beatMs)
}

/** The beats after [afterMs] up to and including [untilMs], as the playback loop's clicks for one step. */
internal fun beatsBetween(beats: List<Beat>, afterMs: Long, untilMs: Long): List<Beat> {
    if (untilMs <= afterMs) return emptyList()
    val from = beats.firstAtOrAfter(afterMs + 1)
    val to = beats.firstAtOrAfter(untilMs + 1)
    return if (from < to) beats.subList(from, to) else emptyList()
}
