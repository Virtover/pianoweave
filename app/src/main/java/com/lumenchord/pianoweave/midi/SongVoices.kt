package com.lumenchord.pianoweave.midi

/**
 * The pitches the song itself is sounding during playback, so each gets its note off once no note of it sounds
 * any more: its key is up and its channel's sustain pedal doesn't hold it ([MidiNoteEvent.soundEndMs]). That
 * depends only on the song position, so seeks, loops and wait mode's jumps find the pedal as it is there, and
 * a short lift between two pedallings releases what it should however far one playback step goes.
 *
 * One instance lives as long as the piano roll: the playback loop restarts on a speed, wait mode or difficulty
 * change without stopping the sound, and a set of its own lost the pitches ringing on the pedal, which then
 * never got their note off.
 */
internal class SongVoices {
    private val pitches = mutableSetOf<Int>()

    val sounding: Set<Int> get() = pitches

    /** The song struck [pitch] (a re-strike of a ringing one replaces its voice in the synthesizer). */
    fun started(pitch: Int) {
        pitches += pitch
    }

    /** Everything was silenced (pause, seek, loop wrap). */
    fun clear() = pitches.clear()

    /** Forgets and passes to [release] every pitch no note of [notes] sounds at [headMs] any more. */
    fun releaseEnded(notes: List<MidiNoteEvent>, headMs: Long, release: (pitch: Int) -> Unit) {
        if (pitches.isEmpty()) return
        val stillSounding = HashSet<Int>()
        for (note in notes) {
            // Sorted by start: the rest start later
            if (note.startMs > headMs) break
            if (note.pitch in pitches && headMs < note.soundEndMs) stillSounding += note.pitch
        }
        pitches.retainAll { pitch ->
            val keep = pitch in stillSounding
            if (!keep) release(pitch)
            keep
        }
    }
}
