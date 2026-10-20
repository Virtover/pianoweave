package com.lumenchord.pianoweave.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Streaming level control for the model input: a smoothed AGC plus a soft noise gate.
 *
 * Replaces the old per-window peak normalisation, which (a) recomputed the gain for the whole
 * 1.1 s window on every run, so the same note got a different level as it slid through the window,
 * (b) let one loud event anywhere in the window shrink the gain for a soft note right after it, and
 * (c) muted everything below a 0.005 peak, silencing soft or distant notes.
 *
 * The gain is applied once, when a sample enters the model ring buffer, so every run sees the same
 * values for the same audio.
 *
 * Work is done in short segments ([SEGMENT_MS]). Each segment is processed as a whole, which gives
 * the envelope a few ms of lookahead at no extra latency: a loud attack lowers the gain from the
 * start of its own segment, so nothing clips and the hammer transient is preserved. Gain changes
 * caused by an attack or by the gate opening are applied instantly; slow changes (release, gate
 * closing) are ramped across the segment.
 */
internal class InputLevelControl(private val sampleRate: Int = 16000) {
    companion object {
        private const val SEGMENT_MS = 5

        // AGC: envelope (segment peak, instant attack) is brought to TARGET_PEAK.
        private const val TARGET_PEAK = 0.5f      // same target as the old per-window normalisation
        private const val MAX_GAIN = 10f          // UNPROCESSED input is often 10-20 dB quieter than before
        // Envelope release. Longer = less flattening of note decays (later note-offs), but a loud
        // event keeps the gain lower for longer afterwards. 1-2 s is the useful range.
        private const val RELEASE_S = 1.5f

        // Noise floor: tracks segment RMS downwards quickly, rises slowly, capped so continuous
        // playing can never push it up to the playing level.
        private const val NOISE_FLOOR_INIT = 0.0005f
        private const val NOISE_FLOOR_MIN = 0.000001f
        private const val NOISE_FLOOR_MAX = 0.003f
        private const val NOISE_FLOOR_RISE_DB_PER_S = 1f
        private const val NOISE_FLOOR_FALL = 0.5f  // per segment, towards a quieter segment

        // Gate: opens when the segment peak exceeds GATE_RATIO x noise floor (and an absolute
        // minimum), stays open for GATE_HOLD_MS, then fades to GATE_FLOOR (-20 dB, not a hard mute).
        // 6x noise-floor RMS: Gaussian room noise peaks rarely reach it within a 5 ms segment.
        private const val GATE_RATIO = 6f
        private const val GATE_ABS_MIN = 0.0004f
        private const val GATE_HOLD_MS = 60
        private const val GATE_FLOOR = 0.1f
        private const val GATE_RELEASE_S = 0.08f
    }

    private val segmentLen = max(1, sampleRate * SEGMENT_MS / 1000)
    private val holdSamples = sampleRate * GATE_HOLD_MS / 1000
    private val riseDbPerSample = NOISE_FLOOR_RISE_DB_PER_S / sampleRate

    private var envelope = 0f
    private var noiseFloor = NOISE_FLOOR_INIT
    private var gateOpen = false
    private var gateHoldLeft = 0
    private var gateValue = GATE_FLOOR
    private var lastGain = MAX_GAIN * GATE_FLOOR

    /** Current total gain (AGC x gate), for logging. */
    val currentGain: Float get() = lastGain

    fun reset() {
        envelope = 0f
        noiseFloor = NOISE_FLOOR_INIT
        gateOpen = false
        gateHoldLeft = 0
        gateValue = GATE_FLOOR
        lastGain = MAX_GAIN * GATE_FLOOR
    }

    /** Applies the gain to [count] samples of [buf] starting at [offset], in place. */
    fun process(buf: FloatArray, offset: Int, count: Int) {
        var i = offset
        val end = offset + count
        while (i < end) {
            val len = min(segmentLen, end - i)
            processSegment(buf, i, len)
            i += len
        }
    }

    private fun processSegment(buf: FloatArray, start: Int, len: Int) {
        var peak = 0f
        var sumSq = 0f
        for (j in start until start + len) {
            val a = abs(buf[j])
            if (a > peak) peak = a
            sumSq += a * a
        }
        val rms = sqrt(sumSq / len)

        // Envelope: instant attack (segment lookahead), exponential release.
        val decayed = envelope * exp(-len / (RELEASE_S * sampleRate))
        val attack = peak > decayed
        envelope = if (attack) peak else decayed

        // Noise floor (minimum tracker with slow rise and a hard cap).
        noiseFloor = if (rms < noiseFloor) {
            noiseFloor + (rms - noiseFloor) * NOISE_FLOOR_FALL
        } else {
            noiseFloor * 10f.pow(riseDbPerSample * len / 20f)
        }.coerceIn(NOISE_FLOOR_MIN, NOISE_FLOOR_MAX)

        // Gate with hold; opening is instant, closing fades.
        val wasOpen = gateOpen
        if (peak > max(GATE_ABS_MIN, noiseFloor * GATE_RATIO)) {
            gateOpen = true
            gateHoldLeft = holdSamples
        } else {
            gateHoldLeft -= len
            if (gateHoldLeft <= 0) gateOpen = false
        }
        gateValue = if (gateOpen) {
            1f
        } else {
            GATE_FLOOR + (gateValue - GATE_FLOOR) * exp(-len / (GATE_RELEASE_S * sampleRate))
        }

        val agcGain = min(MAX_GAIN, TARGET_PEAK / max(envelope, 1e-9f))
        val gain = agcGain * gateValue

        // envelope >= segment peak, so |output| <= TARGET_PEAK for a stepped gain; ramps only
        // happen between gains that are both <= that bound. The clamp is a safety net only.
        val g0 = lastGain
        if (attack || (gateOpen && !wasOpen) || len == 1) {
            for (j in start until start + len) buf[j] = (buf[j] * gain).coerceIn(-1f, 1f)
        } else {
            val stepPerSample = (gain - g0) / len
            var g = g0
            for (j in start until start + len) {
                g += stepPerSample
                buf[j] = (buf[j] * g).coerceIn(-1f, 1f)
            }
        }
        lastGain = gain
    }
}
