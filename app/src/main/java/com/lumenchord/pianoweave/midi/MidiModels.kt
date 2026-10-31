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

/** A parsed MIDI file: its notes, sorted by start, and when the sustain pedal is down. */
data class MidiSong(
    val notes: List<MidiNoteEvent>,
    val sustainPedal: List<SustainPedalSpan>
)

data class PianoPlaybackState(
    val currentPlayheadMs: Long = 0L,
    val speedMultiplier: Float = 1.0f,
    val loopStartMs: Long? = null,
    val loopEndMs: Long? = null,
    val isWaitModeEnabled: Boolean = false
)
