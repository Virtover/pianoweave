package com.lumenchord.pianoweave.ui.screens.pianoroll

import com.lumenchord.pianoweave.midi.Beat
import com.lumenchord.pianoweave.midi.MidiNoteEvent
import kotlin.math.roundToInt

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

/**
 * The playback position as a clock the click scheduler reads from its own thread: song time [headMs] at
 * [anchorNanos] (System.nanoTime), moving on at [speed] song ms per real ms. The playback loop re-anchors it
 * every step, so seeks, loop wraps and speed changes reach it within a step; null while stopped (paused,
 * counting in).
 */
internal data class PlaybackClock(val headMs: Double, val anchorNanos: Long, val speed: Float) {
    fun headAt(nanos: Long): Double = headMs + (nanos - anchorNanos) / 1_000_000.0 * speed

    /** When the song reaches [ms], in System.nanoTime. */
    fun nanosAt(ms: Long): Long = anchorNanos + ((ms - headMs) / speed * 1_000_000.0).toLong()
}

/**
 * Picks the beats to click a short way ahead of the playback clock, so each can be played at its own time
 * rather than whenever the playback loop next comes round. Each beat is handed out once; a head that
 * moves back, or jumps forward (a seek, a loop wrap), starts over from there without clicking the beats in
 * between. Beats before [clickFromMs] stay silent (the lead-in before the first note, when chosen).
 */
internal class ClickScheduler(private val beats: List<Beat>, private val clickFromMs: Long = Long.MIN_VALUE) {
    private var lastHeadMs: Long? = null
    private var cursorMs = 0L

    /** Forget where it was, as after a stop; the next call starts at its head. */
    fun reset() {
        lastHeadMs = null
    }

    /** The beats after the ones already handed out, up to [headMs] + [aheadMs]. */
    fun due(headMs: Long, aheadMs: Long): List<Beat> {
        val last = lastHeadMs
        // A little back is the clock being re-anchored, not a seek
        if (last == null || headMs < last - BACK_TOLERANCE_MS || headMs > last + MAX_STEP_MS) {
            // Just before the head, so a beat right on it clicks too
            cursorMs = headMs - 1
        }
        lastHeadMs = headMs
        val untilMs = headMs + aheadMs
        if (untilMs <= cursorMs) return emptyList()
        val due = beatsBetween(beats, cursorMs, untilMs)
        cursorMs = untilMs
        return due.filter { it.timeMs >= clickFromMs }
    }

    private companion object {
        const val BACK_TOLERANCE_MS = 25L
        const val MAX_STEP_MS = 500L
    }
}

/**
 * The tempo and meter at a point of the song, for showing: [bpm] in song time (the median of the last
 * four beats, so a rubato performance doesn't make it jump at every beat) and [beatsPerBar] of the bar
 * being played (of the first full bar while still in a pickup).
 */
internal data class BeatInfo(val bpm: Int, val beatsPerBar: Int, val beatUnit: Int = 4) {
    val meter: String get() = "$beatsPerBar/$beatUnit"
}

internal fun beatInfoAt(beats: List<Beat>, ms: Long): BeatInfo? {
    if (beats.size < 2) return null
    val index = (beats.firstAtOrAfter(ms + 1) - 1).coerceIn(0, beats.lastIndex)
    // The four beat lengths ending at this beat; at the very start the first four
    val end = index.coerceIn(minOf(4, beats.lastIndex), beats.lastIndex)
    val lengths = (end - minOf(4, end) until end).map { beats[it + 1].timeMs - beats[it].timeMs }.sorted()
    val median = if (lengths.size % 2 == 1) lengths[lengths.size / 2].toDouble()
    else (lengths[lengths.size / 2 - 1] + lengths[lengths.size / 2]) / 2.0
    if (median <= 0) return null
    val barStart = (index downTo 0).firstOrNull { beats[it].isBarStart }
        ?: beats.indexOfFirst { it.isBarStart }.takeIf { it >= 0 }
        ?: 0
    val nextBar = (barStart + 1..beats.lastIndex).firstOrNull { beats[it].isBarStart } ?: beats.size
    return BeatInfo(bpm = (60_000.0 / median).roundToInt(), beatsPerBar = nextBar - barStart, beatUnit = beats[barStart].beatUnit)
}

/**
 * Where the clicks start when the lead-in is kept silent: a little before the first note, as a beat can
 * fall just after a note it belongs to. Null (all beats click) for a song without notes.
 */
internal fun firstClickMs(notes: List<MidiNoteEvent>): Long? = notes.minOfOrNull { it.startMs }?.minus(LEAD_IN_TOLERANCE_MS)

private const val LEAD_IN_TOLERANCE_MS = 60L
