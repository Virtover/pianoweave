package com.lumenchord.pianoweave.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnsetTrackerTest {

    private val pitch = 39

    /** Onset probabilities of one pitch over absolute frames; frames not listed are 0. */
    private fun timeline(vararg peaks: Pair<Long, Float>): (Long) -> Float {
        val map = peaks.toMap()
        return { map[it] ?: 0f }
    }

    /**
     * Checks a run whose output starts at absolute frame [first] and is [frames] long. Like the detector,
     * the peak is searched only in the newest [window] frames.
     */
    private fun OnsetTracker.check(onsets: (Long) -> Float, first: Long, frames: Int = 32, window: Int = 5): Pair<Boolean, Long> {
        val peak = (frames - window until frames).maxBy { onsets(first + it) }
        return isNewStrike(pitch, peak, first) { onsets(first + it) } to first + peak
    }

    @Test
    fun firstStrikeIsNew() {
        val tracker = OnsetTracker()
        val (isNew, _) = tracker.check(timeline(100L to 0.9f), first = 70)
        assertTrue(isNew)
    }

    @Test
    fun sameStrikeInOverlappingWindowsIsReportedOnce() {
        val tracker = OnsetTracker()
        // Smeared over three frames, as the model does
        val onsets = timeline(99L to 0.6f, 100L to 0.9f, 101L to 0.7f)
        val (isNew, abs) = tracker.check(onsets, first = 70)
        assertTrue(isNew)
        tracker.record(pitch, abs)

        // Next runs advance by 2-3 frames; the peak may also land one frame off
        for (first in listOf(72L, 73L, 74L)) {
            assertFalse("run starting at $first", tracker.check(onsets, first).first)
        }
        val jittered = timeline(100L to 0.6f, 101L to 0.9f, 102L to 0.7f)
        assertFalse(tracker.check(jittered, first = 72).first)
    }

    @Test
    fun reStrikeAfterDipIsNew() {
        val tracker = OnsetTracker()
        tracker.record(pitch, 100)
        // Fast repeat, ~140 ms later, with the onset back near zero in between
        val onsets = timeline(100L to 0.9f, 101L to 0.5f, 104L to 0.85f)
        assertTrue(tracker.check(onsets, first = 90, frames = 16).first)
    }

    @Test
    fun laterPeakWithoutDipIsNotNew() {
        val tracker = OnsetTracker()
        tracker.record(pitch, 100)
        // Onset stays high (wobbling) instead of dipping: not a new strike
        val onsets: (Long) -> Float = { f ->
            when {
                f in 100L..103L -> 0.5f
                f == 104L -> 0.8f
                else -> 0f
            }
        }
        assertFalse(tracker.check(onsets, first = 95, frames = 10).first)
    }

    @Test
    fun peakOlderThanLastReportIsNotNew() {
        val tracker = OnsetTracker()
        tracker.record(pitch, 100)
        // A non-target window is shifted back, so it can still see an earlier peak
        assertFalse(tracker.check(timeline(97L to 0.9f), first = 90, frames = 10).first)
    }

    @Test
    fun onsetOlderThanOutputCountsAsRearmed() {
        val tracker = OnsetTracker()
        tracker.record(pitch, 10)
        val onsets: (Long) -> Float = { f -> if (f == 150L) 0.9f else 0.5f }
        assertTrue(tracker.check(onsets, first = 120, frames = 32).first)
    }

    @Test
    fun resetForgetsEverything() {
        val tracker = OnsetTracker()
        tracker.record(pitch, 100)
        tracker.reset()
        assertTrue(tracker.check(timeline(100L to 0.9f), first = 90, frames = 12).first)
    }
}
