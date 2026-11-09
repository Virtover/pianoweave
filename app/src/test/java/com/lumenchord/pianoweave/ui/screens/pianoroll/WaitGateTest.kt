package com.lumenchord.pianoweave.ui.screens.pianoroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaitGateTest {

    private val c = 60; private val f = 65; private val g = 67; private val a = 69

    /** Keys and the wait loop around a [WaitGate]: presses, and using them up when a chord passes. */
    private class Player {
        val gate = WaitGate()
        val lastPress = mutableMapOf<Int, Long>()
        val consumed = mutableMapOf<Int, Long>()

        fun press(at: Long, vararg keys: Int) = keys.forEach { lastPress[it] = at }

        fun check(onset: Long, required: Set<Int>, now: Long): WaitGate.Check {
            val result = gate.check(onset, required, now, { lastPress[it] ?: 0L }, { consumed[it] ?: 0L })
            if (result.passed) required.forEach { consumed[it] = lastPress[it] ?: 0L }
            return result
        }
    }

    @Test
    fun chordPassesWhenAllKeysArePlayed() {
        val p = Player()
        assertFalse(p.check(1000, setOf(c, g), now = 10_000).passed)
        p.press(10_100, c)
        assertFalse(p.check(1000, setOf(c, g), now = 10_100).passed)
        p.press(10_200, g)
        assertTrue(p.check(1000, setOf(c, g), now = 10_200).passed)
    }

    @Test
    fun closeNextChordTakesKeysPlayedTogether() {
        val p = Player()
        p.check(1000, setOf(c, g), now = 10_000)
        p.press(10_500, a, c, f, g)
        assertTrue(p.check(1000, setOf(c, g), now = 10_500).passed)
        // The playhead reaches the next chord a while later (slow speed): still passes at once
        assertTrue(p.check(1150, setOf(a, f), now = 11_200).passed)
    }

    @Test
    fun sharedKeyMustBePressedAgain() {
        val p = Player()
        p.check(1000, setOf(c, f, g), now = 10_000)
        p.press(10_500, a, c, f, g)
        assertTrue(p.check(1000, setOf(c, f, g), now = 10_500).passed)

        val waiting = p.check(1150, setOf(a, f), now = 10_700)
        assertFalse(waiting.passed)
        assertEquals(setOf(a), waiting.hits)

        // F again, much later: the carried A still counts
        p.press(13_000, f)
        assertTrue(p.check(1150, setOf(a, f), now = 13_000).passed)
    }

    /** C#4 + E5 look like one chord, but E5 starts 40 ms later, so it's the next gate with A4. */
    @Test
    fun keysOfASplitChordCountInAnyOrder() {
        val cSharp4 = 61; val e5 = 76; val a4 = 69
        val p = Player()
        p.check(1000, setOf(cSharp4), now = 10_000)
        p.press(10_000, e5)
        p.press(10_600, cSharp4)
        assertTrue(p.check(1000, setOf(cSharp4), now = 10_600).passed)

        val waiting = p.check(1040, setOf(e5, a4), now = 10_650)
        assertEquals(setOf(e5), waiting.hits)
        p.press(11_200, a4)
        assertTrue(p.check(1040, setOf(e5, a4), now = 11_200).passed)
    }

    /**
     * E5 33 ms after C#4: the playhead jump after C#4 used to skip it, so the next gate was A4's (with E5) and
     * the carry-over, tied to E5's onset, never applied.
     */
    @Test
    fun carryOverReachesTheNextGateWhateverItsOnset() {
        val cSharp4 = 61; val e5 = 76; val a4 = 69
        val p = Player()
        p.check(1000, setOf(cSharp4), now = 10_000)
        p.press(10_500, cSharp4, e5, a4)
        assertTrue(p.check(1000, setOf(cSharp4), now = 10_500).passed)
        assertTrue(p.check(1060, setOf(e5, a4), now = 10_600).passed)
    }

    @Test
    fun keyPressedOnTheWayToACloseChordCounts() {
        val p = Player()
        p.check(1000, setOf(c), now = 10_000)
        p.press(10_000, c)
        assertTrue(p.check(1000, setOf(c), now = 10_000).passed)
        p.press(10_100, a) // at slow speed the playhead takes a second to get there
        assertTrue(p.check(1250, setOf(a), now = 11_000).passed)
    }

    @Test
    fun farNextChordIsNotCarriedOver() {
        val p = Player()
        p.check(1000, setOf(c, g), now = 10_000)
        p.press(10_500, a, c, f, g)
        assertTrue(p.check(1000, setOf(c, g), now = 10_500).passed)
        assertFalse(p.check(2000, setOf(a, f), now = 11_500).passed)
    }

    @Test
    fun strayKeyFromBeforeTheChordIsNotCarriedOver() {
        val p = Player()
        p.check(1000, setOf(c, g), now = 10_000)
        p.press(10_100, a) // a key pressed long before the chord was played
        p.press(12_000, c, g)
        assertTrue(p.check(1000, setOf(c, g), now = 12_000).passed)
        val next = p.check(1150, setOf(a, f), now = 12_200)
        assertFalse(next.passed)
        assertTrue(next.hits.isEmpty())
    }

    @Test
    fun carriedKeysChainToAThirdCloseChord() {
        val p = Player()
        val e = 64
        p.check(1000, setOf(c), now = 10_000)
        p.press(10_500, c, e, g)
        assertTrue(p.check(1000, setOf(c), now = 10_500).passed)
        assertTrue(p.check(1100, setOf(e), now = 10_700).passed)
        assertTrue(p.check(1200, setOf(g), now = 10_900).passed)
    }

    @Test
    fun oldPressExpiresAndStopsShowingAsHit() {
        val p = Player()
        p.check(1000, setOf(c, g), now = 10_000)
        p.press(10_100, c)
        assertEquals(setOf(c), p.check(1000, setOf(c, g), now = 11_000).hits)

        val later = p.check(1000, setOf(c, g), now = 12_000)
        assertTrue(later.hits.isEmpty())
        assertEquals(setOf(c), later.expired)

        p.press(12_000, g)
        assertFalse(p.check(1000, setOf(c, g), now = 12_000).passed)
        p.press(12_100, c)
        assertTrue(p.check(1000, setOf(c, g), now = 12_100).passed)
    }

    @Test
    fun slightlyEarlyPressCountsButAnOlderOneDoesNot() {
        val p = Player()
        p.press(9_800, c, g)
        assertTrue(p.check(1000, setOf(c, g), now = 10_000).passed)

        val q = Player()
        q.press(9_500, c, g)
        assertFalse(q.check(1000, setOf(c, g), now = 10_000).passed)
    }

    @Test
    fun onePressNeverPassesTwoChords() {
        val p = Player()
        p.check(1000, setOf(c), now = 10_000)
        p.press(10_100, c)
        assertTrue(p.check(1000, setOf(c), now = 10_100).passed)
        assertFalse(p.check(1100, setOf(c), now = 10_200).passed)
    }

    @Test
    fun carryOverIsDroppedAfterASeek() {
        val p = Player()
        p.check(1000, setOf(c, g), now = 10_000)
        p.press(10_500, a, c, f, g)
        assertTrue(p.check(1000, setOf(c, g), now = 10_500).passed)
        p.gate.clearCarry()
        p.gate.restartWait()
        assertFalse(p.check(1150, setOf(a, f), now = 11_200).passed)
    }
}
