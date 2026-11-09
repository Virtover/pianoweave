package com.lumenchord.pianoweave.ui.screens.pianoroll

import com.lumenchord.pianoweave.midi.Beat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetronomeTest {

    /** Beats [beatMs] apart from 0, with bars of the given lengths in beats. */
    private fun grid(beatMs: Long, vararg barLengths: Int): List<Beat> =
        barLengths.flatMap { length -> List(length) { it == 0 } }
            .mapIndexed { index, isBarStart -> Beat(index * beatMs, isBarStart) }

    @Test
    fun countInIsOneBarAtTheTempoAboutToPlay() {
        assertEquals(CountIn(clicks = 3, beatMs = 400L), countInFor(grid(400L, 3, 3, 3), fromMs = 1200L))
    }

    @Test
    fun aPickupBarCountsInWithTheFullBarAfterIt() {
        // A one-beat pickup, then 4/4: four clicks, not one
        assertEquals(4, countInFor(grid(500L, 1, 4, 4), fromMs = 0L)?.clicks)
    }

    @Test
    fun nearTheEndTheLastWholeBarIsUsed() {
        // From the last bar, which has no bar after it to measure: the 3/4 bar before it
        assertEquals(3, countInFor(grid(500L, 3, 3, 3), fromMs = 4000L)?.clicks)
    }

    @Test
    fun noGridNoCountIn() {
        assertNull(countInFor(emptyList(), 0L))
    }

    @Test
    fun beatsBetweenIncludesTheEndButNotTheStart() {
        val beats = grid(500L, 4, 4)
        assertEquals(listOf(500L, 1000L), beatsBetween(beats, 0L, 1000L).map { it.timeMs })
        assertEquals(listOf(0L), beatsBetween(beats, -1L, 10L).map { it.timeMs })
        assertEquals(emptyList<Beat>(), beatsBetween(beats, 1000L, 1400L))
        assertEquals(emptyList<Beat>(), beatsBetween(beats, 1000L, 900L))
    }

    @Test
    fun countInStartsFromTheBeatAtOrBeforeThePlayhead() {
        val beats = grid(500L, 4, 4)
        assertEquals(1000L, beatAtOrBefore(beats, 1499L)?.timeMs)
        assertEquals(1500L, beatAtOrBefore(beats, 1500L)?.timeMs)
        assertNull(beatAtOrBefore(beats.drop(1), 100L))
    }
}
