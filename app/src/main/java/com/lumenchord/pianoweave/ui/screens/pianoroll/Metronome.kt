package com.lumenchord.pianoweave.ui.screens.pianoroll

import com.lumenchord.pianoweave.midi.Beat

/**
 * A count-in: [clicks] beats, [beatMs] apart in song time (divide by the playback speed for real time),
 * leading into a beat that is beat [startBeatOfBar] (0 = the first) of its bar.
 */
internal data class CountIn(val clicks: Int, val beatMs: Long, val startBeatOfBar: Int = 0) {
    /**
     * The beat of the bar (0 = the first) that [click] stands for: the clicks are the bar's beats just
     * before the start, so starting on beat 3 of 4 counts 3, 4, 1, 2 with the stress on the "1".
     */
    fun beatOfBar(click: Int): Int = Math.floorMod(startBeatOfBar - clicks + click, clicks)
}

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
    val clicks = barBeats.coerceAtMost(12)
    // Where the beat about to play sits in its bar (the beat at or after fromMs)
    val startIndex = beats.firstAtOrAfter(fromMs).coerceAtMost(beats.lastIndex)
    // In a pickup, before the first bar start, the beats are the end of a bar: one beat before it is the last
    val startBeatOfBar = barStarts.lastOrNull { it <= startIndex }?.let { startIndex - it }
        ?: Math.floorMod(startIndex - (barStarts.firstOrNull() ?: 0), clicks)
    return CountIn(clicks, beatMs, startBeatOfBar = startBeatOfBar % clicks)
}

/** The beats after [afterMs] up to and including [untilMs], as the playback loop's clicks for one step. */
internal fun beatsBetween(beats: List<Beat>, afterMs: Long, untilMs: Long): List<Beat> {
    if (untilMs <= afterMs) return emptyList()
    val from = beats.firstAtOrAfter(afterMs + 1)
    val to = beats.firstAtOrAfter(untilMs + 1)
    return if (from < to) beats.subList(from, to) else emptyList()
}
