package com.lumenchord.pianoweave.midi

data class MidiNoteEvent(
    val pitch: Int,        // MIDI note number (e.g. 60 = Middle C)
    val startMs: Long,     // Start time in milliseconds from beginning of song
    val durationMs: Long,  // Duration of note in milliseconds
    val velocity: Int      // Striking velocity (0-127)
)

/** A span with the sustain pedal (CC64) held down; [endMs] is [Long.MAX_VALUE] if it is never lifted. */
data class SustainPedalSpan(
    val startMs: Long,
    val endMs: Long
)

/** One beat of a song's grid; [isBarStart] on the first beat of each bar. */
data class Beat(
    val timeMs: Long,
    val isBarStart: Boolean
)

/**
 * A parsed MIDI file: its notes, sorted by start, when the sustain pedal is down, and its [beats] up to the
 * end of the last note. [beats] is empty when the file has no real beat grid, such as a transcription whose
 * beats the server could not trust (left at a fixed 120 BPM in 4/4).
 */
data class MidiSong(
    val notes: List<MidiNoteEvent>,
    val sustainPedal: List<SustainPedalSpan>,
    val beats: List<Beat> = emptyList()
)

data class PianoPlaybackState(
    val currentPlayheadMs: Long = 0L,
    val speedMultiplier: Float = 1.0f,
    val loopStartMs: Long? = null,
    val loopEndMs: Long? = null,
    val isWaitModeEnabled: Boolean = false
)
