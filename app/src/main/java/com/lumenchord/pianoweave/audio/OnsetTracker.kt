package com.lumenchord.pianoweave.audio

/**
 * Makes sure one piano strike is reported once.
 *
 * Successive analysis windows overlap (the window is at least a few frames, but only 2-3 new frames
 * arrive per run) and the model's onset output is smeared over 2-3 frames, so the same strike is the
 * best onset in several runs in a row. Each onset therefore gets an identity in absolute model-frame
 * time: a peak counts as a new strike only if it is at least [minGapFrames] after the last reported
 * one and the onset probability dipped below [rearmProb] in between (a real re-strike always has
 * that dip; a smeared or wobbling onset does not).
 *
 * Absolute frames come from the caller (model samples received so far / samples per frame). The
 * same peak can land ±1 frame apart in different runs, which [minGapFrames] absorbs.
 */
internal class OnsetTracker(
    private val minGapFrames: Int = 3,
    private val rearmProb: Float = 0.3f
) {
    private val lastOnsetFrame = LongArray(128) { NONE }

    fun reset() {
        lastOnsetFrame.fill(NONE)
    }

    /**
     * Whether the onset peak at [peakFrame] (index into the current model output, whose frame 0 is
     * absolute frame [firstAbsFrame]) is a new strike of [pitch]. [onsetProb] gives the onset
     * probability of [pitch] at an output frame index.
     */
    fun isNewStrike(pitch: Int, peakFrame: Int, firstAbsFrame: Long, onsetProb: (Int) -> Float): Boolean {
        val last = lastOnsetFrame[pitch]
        if (last == NONE) return true
        if (firstAbsFrame + peakFrame - last < minGapFrames) return false

        // Older than the whole model output (over a second ago): cannot be the same strike
        if (last < firstAbsFrame) return true

        // The dip has to be between the last onset and this peak
        for (frame in (last - firstAbsFrame + 1).toInt() until peakFrame) {
            if (onsetProb(frame) < rearmProb) return true
        }
        return false
    }

    /** Remembers that a strike of [pitch] was reported with its onset peak at [absFrame]. */
    fun record(pitch: Int, absFrame: Long) {
        lastOnsetFrame[pitch] = absFrame
    }

    private companion object {
        const val NONE = Long.MIN_VALUE
    }
}
