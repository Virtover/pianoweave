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

    /** Beats with these lengths, in bars of four. */
    private fun beatsOf(lengths: List<Long>): List<Beat> =
        (lengths.runningFold(0L) { t, l -> t + l }).mapIndexed { i, t -> Beat(t, i % 4 == 0) }

    @Test
    fun aSteadyPerformanceIsStable() {
        // Small give around 500 ms, as any recording has
        assertEquals(false, isTempoUnstable(beatsOf(List(40) { 490L + (it * 7) % 25 })))
        // A gentle ritardando at the end stays a nuance
        assertEquals(false, isTempoUnstable(beatsOf(List(32) { 500L } + listOf(520L, 540L, 560L))))
    }

    @Test
    fun anUnsteadyPulseIsUnstable() {
        // Every other beat pulled or pushed by 20-30 %
        assertEquals(true, isTempoUnstable(beatsOf(List(40) { if (it % 2 == 0) 500L else listOf(380L, 640L)[it / 2 % 2] })))
    }

    @Test
    fun aDifferentTempoForAGoodPartIsUnstable() {
        // Steady, but half the song at 120 BPM and half at 80: one tempo per section, still not one tempo
        assertEquals(true, isTempoUnstable(beatsOf(List(20) { 500L } + List(20) { 750L })))
    }

    @Test
    fun tooFewBeatsAreNotJudged() {
        assertEquals(false, isTempoUnstable(beatsOf(listOf(300L, 900L, 300L))))
        assertEquals(false, isTempoUnstable(emptyList()))
    }

    @Test
    fun aWanderingBalladIsUnstable() {
        // The beat lengths (ms) of a real transcription of a rubato piano ballad: no beat far off its
        // neighbours, but the tempo wanders between about 33 and 61 BPM
        val lengths = listOf(
            1660L, 1660L, 1560L, 1560L, 1560L, 1680L, 1300L, 1300L, 1280L, 1380L, 1360L, 1580L, 1480L, 1780L,
            1820L, 1260L, 1400L, 1420L, 1360L, 1360L, 1020L, 1000L, 1140L, 1060L, 1060L, 1080L, 1140L, 1220L,
            1040L, 1160L, 1280L, 1300L, 1100L, 1260L, 1320L, 1340L, 1060L, 1200L, 1300L, 1400L, 1120L, 1240L,
            1260L, 1380L, 1200L, 1420L, 1400L, 1460L, 1260L, 1400L, 1580L, 1540L, 1260L, 1400L, 1320L, 1440L,
            1340L, 1340L, 1340L, 1400L, 1120L, 1180L, 1160L, 1200L, 1120L, 1200L, 1320L, 1260L, 1060L, 1040L,
            1200L, 1220L, 1160L, 1140L, 1160L, 1220L, 1140L, 1160L, 1040L, 1160L, 1020L, 1180L, 1100L, 1080L,
            1040L, 1140L, 1020L, 1160L, 1080L, 1060L, 1020L, 1100L, 1260L, 1240L, 1460L, 1400L, 1400L, 1460L,
            1500L, 1300L, 1120L, 1380L, 1380L, 1340L, 1360L, 1580L, 1540L, 1420L, 1300L, 1100L, 1080L, 1140L,
            1160L, 1200L, 1100L, 980L, 1080L, 1060L, 980L, 1020L, 1040L, 1080L
        )
        assertEquals(true, isTempoUnstable(beatsOf(lengths)))
    }
}
