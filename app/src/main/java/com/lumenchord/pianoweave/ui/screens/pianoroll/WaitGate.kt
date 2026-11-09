package com.lumenchord.pianoweave.ui.screens.pianoroll

/**
 * Wait mode's decision of when the chord at the playhead counts as played. Pure (times and presses are
 * passed in), so the rules below are unit-tested in `WaitGateTest`.
 *
 * - A key counts once per press: a press used by one chord ([consumedAt]) never counts for the next.
 * - Presses made up to [EARLY_PRESS_MS] before the playhead reached the chord count (playing slightly early).
 * - The chord's keys must be played together: a press [CHORD_SPREAD_MS] old drops out (and stops showing as
 *   hit), so it has to be pressed again.
 * - Carry-over: when the next chord starts at most [CARRY_OVER_GAP_MS] (song time) after the one just played,
 *   its keys pressed together with that chord (within [CHORD_SPREAD_MS] before the key that completed it, in
 *   any order) or on the way there count for it too, and don't expire. Notes only a little more than 30 ms
 *   apart look like one chord but are two gates, so this is what makes playing them at once work. Keys both
 *   chords share were used by the first, so they still have to be pressed again.
 */
internal class WaitGate {
    /** Onset (song ms) of the chord being waited at, or -1. */
    var onsetMs = -1L
        private set
    private var arrivalRealTime = 0L
    /** Onset of the chord that last passed, while chords right after it may take its presses; else -1. */
    private var carryFromOnsetMs = -1L
    private var carrySinceRealTime = 0L

    class Check(
        /** Keys of the chord that count as played right now. */
        val hits: Set<Int>,
        /** Presses that were part of the chord but are too old to be joined by the rest. */
        val expired: Set<Int>,
        val passed: Boolean
    )

    /** Drops the carry-over (wait mode off, playhead moved back or jumped). */
    fun clearCarry() {
        carryFromOnsetMs = -1L
    }

    /** An interrupted wait restarts its timing when the chord is reached again. */
    fun restartWait() {
        onsetMs = -1L
    }

    /**
     * Checks the chord at [chordOnsetMs] with keys [required]. [lastPressAt] / [consumedAt] give a key's latest
     * press time and the press time last used by a chord (0 if none).
     */
    fun check(
        chordOnsetMs: Long,
        required: Set<Int>,
        now: Long,
        lastPressAt: (Int) -> Long,
        consumedAt: (Int) -> Long
    ): Check {
        if (chordOnsetMs != onsetMs) {
            onsetMs = chordOnsetMs
            arrivalRealTime = now
        }
        // Whichever chord comes next within the gap, however the song's notes were grouped into chords
        val hasCarry = carryFromOnsetMs != -1L &&
                chordOnsetMs > carryFromOnsetMs && chordOnsetMs - carryFromOnsetMs <= CARRY_OVER_GAP_MS
        // Presses between the previous chord and arriving here belong to it; later ones are fresh
        val carryUntilRealTime = arrivalRealTime
        val freshSince = arrivalRealTime - EARLY_PRESS_MS

        val presses = mutableMapOf<Int, Long>()
        val expired = mutableSetOf<Int>()
        for (p in required) {
            val t = lastPressAt(p)
            if (t <= consumedAt(p)) continue
            when {
                // Played together with the previous chord: counts however long the rest takes
                hasCarry && t >= carrySinceRealTime && t <= carryUntilRealTime -> presses[p] = t
                t < freshSince -> {}
                t < now - CHORD_SPREAD_MS -> expired += p
                else -> presses[p] = t
            }
        }

        val passed = required.isNotEmpty() && presses.size == required.size
        if (passed) {
            onsetMs = -1L
            carryFromOnsetMs = chordOnsetMs
            // Played together = within the chord spread before the key that completed this chord
            carrySinceRealTime = presses.values.max() - CHORD_SPREAD_MS
        }
        return Check(presses.keys, expired, passed)
    }

    companion object {
        const val EARLY_PRESS_MS = 300L
        const val CHORD_SPREAD_MS = 1500L
        const val CARRY_OVER_GAP_MS = 300L
    }
}
