package com.lumenchord.pianoweave.midi

import org.junit.Assert.assertEquals
import org.junit.Test

class SongVoicesTest {

    private fun note(pitch: Int, startMs: Long, endMs: Long, soundEndMs: Long = endMs, channel: Int = 0) =
        MidiNoteEvent(pitch, startMs, endMs - startMs, 90, channel, pedalHoldMs = soundEndMs - endMs)

    /** Starts [notes] in [voices] and gives back the pitches released at each of [heads], in order. */
    private fun releasesAt(voices: SongVoices, notes: List<MidiNoteEvent>, vararg heads: Long): List<List<Int>> =
        heads.map { head ->
            notes.filter { it.startMs <= head && voices.sounding.none { p -> p == it.pitch } && head < it.soundEndMs }
                .forEach { voices.started(it.pitch) }
            val released = mutableListOf<Int>()
            voices.releaseEnded(notes, head) { released += it }
            released
        }

    @Test
    fun aNoteReleasedUnderThePedalRingsUntilTheLift() {
        val notes = listOf(note(60, 0, 300, soundEndMs = 1000))
        assertEquals(listOf(emptyList(), emptyList(), listOf(60)), releasesAt(SongVoices(), notes, 0, 600, 1000))
    }

    @Test
    fun anotherChannelsNotesEndWithTheirKeysWhileThePianosPedalIsDown() {
        // The piano held on its pedal to 1000 ms; a drum and a string note end with their keys at 200 ms. One
        // pedal for the whole song held them on too, as long as the piano's pedal was down
        val notes = listOf(
            note(60, 0, 300, soundEndMs = 1000),
            note(36, 0, 200, channel = DRUM_CHANNEL),
            note(72, 0, 200, channel = 1)
        )
        assertEquals(listOf(emptyList(), listOf(36, 72), emptyList(), listOf(60)), releasesAt(SongVoices(), notes, 0, 250, 600, 1000).map { it.sorted() })
    }

    @Test
    fun aJumpPastALiftReleasesEvenWhenThePedalIsDownAgain() {
        // Lifted at 1000 ms and down again from 1010 to 2000: a jump (wait mode, a slow step) from 400 to 1200
        // landed with the pedal down, and the note held on the first pedalling rang on to 2000
        val notes = listOf(note(60, 0, 300, soundEndMs = 1000), note(64, 1100, 1150, soundEndMs = 2000))
        val voices = SongVoices()
        assertEquals(listOf(emptyList(), listOf(60), emptyList(), listOf(64)), releasesAt(voices, notes, 0, 1200, 1500, 2000))
    }

    @Test
    fun aReStrikeUnderThePedalIsOneVoiceReleasedOnce() {
        // Struck at 0 and again at 400, both under the pedal to 1000 ms: the synthesizer replaces the voice
        val notes = listOf(note(60, 0, 100, soundEndMs = 1000), note(60, 400, 500, soundEndMs = 1000))
        val voices = SongVoices()
        assertEquals(listOf(emptyList(), emptyList(), emptyList(), listOf(60)), releasesAt(voices, notes, 0, 450, 900, 1000))
        assertEquals(emptySet<Int>(), voices.sounding)
    }

    @Test
    fun aReStrikeAfterTheLiftRingsByItsOwnKey() {
        val notes = listOf(note(60, 0, 100, soundEndMs = 500), note(60, 600, 800))
        assertEquals(
            listOf(emptyList(), listOf(60), emptyList(), listOf(60)),
            releasesAt(SongVoices(), notes, 0, 550, 700, 800)
        )
    }

    @Test
    fun theStateFollowsThePositionAfterASeekOrLoop() {
        val notes = listOf(note(60, 0, 100, soundEndMs = 1000), note(64, 2000, 2100))
        val voices = SongVoices()
        releasesAt(voices, notes, 2050)
        // A seek back into the pedalled passage: everything stopped, the note under the playhead struck again
        voices.clear()
        assertEquals(listOf(emptyList(), listOf(60)), releasesAt(voices, notes, 50, 1000))
        // A loop wrap back to the start forgets everything, so nothing stays held from the loop's end
        releasesAt(voices, notes, 2050)
        voices.clear()
        assertEquals(emptySet<Int>(), voices.sounding)
    }

    @Test
    fun soundEndSaturatesForAPedalNeverLifted() {
        val held = MidiNoteEvent(60, 1000, 500, 90, pedalHoldMs = Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, held.soundEndMs)
        // A copy moved in time keeps its pedal hold
        assertEquals(2300L, note(60, 0, 300, soundEndMs = 1300).copy(startMs = 1000).soundEndMs)
    }
}
