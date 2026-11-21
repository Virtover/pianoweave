package com.lumenchord.pianoweave.ui.screens.pianoroll

import com.lumenchord.pianoweave.midi.Beat
import com.lumenchord.pianoweave.midi.MidiNoteEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which keys the piano roll shows (Song / Follow / Custom), and the song map's bars. */
class KeyboardWindowTest {

    private fun note(pitch: Int, startMs: Long = 0L) = MidiNoteEvent(pitch = pitch, startMs = startMs, durationMs = 400L, velocity = 80)

    @Test
    fun songRangeIsWholeOctaves() {
        val window = songWindow(listOf(note(62), note(67), note(79)))
        assertEquals(60, window.low)   // C4
        assertEquals(83, window.high)  // B5
        assertFalse(window.isWhole)
    }

    @Test
    fun songRangeStaysOnThePiano() {
        val window = songWindow(listOf(note(21), note(108)))
        assertEquals(21, window.low)
        assertEquals(108, window.high)
        assertTrue(window.isWhole)
    }

    @Test
    fun customRangeWidensToWhiteKeys() {
        val window = whiteBounded(61, 70) // C♯4 .. A♯4
        assertEquals(60, window.low)
        assertEquals(71, window.high)
    }

    @Test
    fun followStaysWhileTheNotesFit() {
        assertEquals(60, bestFollowLow(listOf(60, 72, 84), current = 60))
    }

    @Test
    fun followMovesToCoverTheMostNotes() {
        // A run in the fourth and fifth octaves, one stray low note: the window goes where most of them are
        assertEquals(48, bestFollowLow(listOf(52, 55, 60, 64, 67, 31), current = 72))
    }

    @Test
    fun followNeverLeavesThePiano() {
        val low = bestFollowLow(listOf(105, 107, 108), current = 60)
        assertTrue(low + FOLLOW_SPAN <= 108)
        assertTrue(low >= 24)
    }

    @Test
    fun songMapCountsNotesPerBar() {
        val beats = listOf(Beat(0L, true), Beat(500L, false), Beat(1000L, true), Beat(1500L, false))
        val bars = songMapBars(listOf(note(60, 0L), note(62, 100L), note(64, 1200L)), beats, durationMs = 2000L)
        assertEquals(listOf(2, 1), bars.map { it.notes })
        assertEquals(2, barAt(bars, 1500L))
        assertNull(barAt(emptyList(), 0L))
    }

    @Test
    fun songMapWithoutBeatsUsesTwoSecondStretches() {
        val bars = songMapBars(listOf(note(60, 3000L)), emptyList(), durationMs = 6000L)
        assertEquals(3, bars.size)
        assertEquals(listOf(0, 1, 0), bars.map { it.notes })
    }
}
