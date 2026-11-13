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
        assertEquals(CountIn(clicks = 3, beatMs = 400L, startBeatOfBar = 0), countInFor(grid(400L, 3, 3, 3), fromMs = 1200L))
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

    @Test
    fun countInRunsIntoTheBarWhereItStarts() {
        // Starting on beat 3 of 4: the clicks are beats 3, 4, 1, 2, stressed on the 1
        val plan = countInFor(grid(500L, 4, 4, 4), fromMs = 3000L)!!
        assertEquals(2, plan.startBeatOfBar)
        assertEquals(listOf(2, 3, 0, 1), (0 until plan.clicks).map(plan::beatOfBar))
        // On a bar start it's 1, 2, 3, 4
        val onBar = countInFor(grid(500L, 4, 4, 4), fromMs = 2000L)!!
        assertEquals(listOf(0, 1, 2, 3), (0 until onBar.clicks).map(onBar::beatOfBar))
    }

    @Test
    fun countInFromAPickupLeadsIntoItsFirstDownbeat() {
        // 2/4 with a one-beat pickup: from the start the count is "2, 1" and the pickup is the bar's 2
        val beats = listOf(Beat(0L, false)) + grid(500L, 2, 2, 2).map { it.copy(timeMs = it.timeMs + 500L) }
        val plan = countInFor(beats, fromMs = 0L)!!
        assertEquals(2, plan.clicks)
        assertEquals(1, plan.startBeatOfBar)
        assertEquals(listOf(1, 0), (0 until plan.clicks).map(plan::beatOfBar))
    }

    @Test
    fun schedulerHandsOutEachBeatOnceAShortWayAhead() {
        val scheduler = ClickScheduler(grid(500L, 4, 4))
        assertEquals(listOf(0L), scheduler.due(0L, 40L).map { it.timeMs })
        assertEquals(emptyList<Long>(), scheduler.due(10L, 40L).map { it.timeMs })
        assertEquals(listOf(500L), scheduler.due(470L, 40L).map { it.timeMs })
        assertEquals(emptyList<Long>(), scheduler.due(480L, 40L).map { it.timeMs })
    }

    @Test
    fun aSeekSkipsTheBeatsInBetweenAndBackClicksThemAgain() {
        val scheduler = ClickScheduler(grid(500L, 4, 4, 4))
        scheduler.due(0L, 40L)
        // Forward: from the new place, not every beat jumped over
        assertEquals(listOf(3000L), scheduler.due(3000L, 40L).map { it.timeMs })
        // Back (a loop wrap): the beat there clicks again
        assertEquals(listOf(1000L), scheduler.due(1000L, 40L).map { it.timeMs })
    }

    @Test
    fun afterAPauseItStartsFromThePlayhead() {
        val scheduler = ClickScheduler(grid(500L, 4, 4))
        scheduler.due(1200L, 40L)
        scheduler.reset()
        assertEquals(listOf(1500L), scheduler.due(1490L, 40L).map { it.timeMs })
    }

    @Test
    fun theClockGivesTheRealTimeOfABeatAtAnySpeed() {
        val clock = PlaybackClock(headMs = 1000.0, anchorNanos = 5_000_000_000L, speed = 0.5f)
        assertEquals(5_000_000_000L + 1_000_000_000L, clock.nanosAt(1500L))
        assertEquals(1250.0, clock.headAt(5_500_000_000L), 1e-9)
    }
}
