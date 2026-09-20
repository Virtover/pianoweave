package com.example.pianoweave.midi

data class MidiNoteEvent(
    val pitch: Int,        // MIDI note number (e.g. 60 = Middle C)
    val startMs: Long,     // Start time in milliseconds from beginning of song
    val durationMs: Long,  // Duration of note in milliseconds
    val velocity: Int      // Striking velocity (0-127)
)

data class PianoPlaybackState(
    val currentPlayheadMs: Long = 0L,
    val speedMultiplier: Float = 1.0f,
    val loopStartMs: Long? = null,
    val loopEndMs: Long? = null,
    val isWaitModeEnabled: Boolean = false
)
