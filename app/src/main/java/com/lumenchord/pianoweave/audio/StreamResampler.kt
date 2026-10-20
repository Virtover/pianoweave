package com.lumenchord.pianoweave.audio

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Streaming anti-aliased resampler (any input rate -> 16 kHz by default).
 *
 * A Kaiser-windowed sinc low-pass is applied at every output position, so content above the output
 * Nyquist (speech sibilants, breath noise, upper piano partials) is removed instead of folding back
 * into 0-8 kHz where the model would read it as pitched energy.
 *
 * For 48 kHz input the ratio is exactly 3, every output lands on an input sample and only phase 0
 * of the table is used (plain decimating FIR). Other rates (e.g. 44.1 kHz fallback) use the
 * nearest of [PHASES] precomputed fractional phases.
 *
 * Filter state and the fractional position are carried across [process] calls, so the output is a
 * continuous 16 kHz stream. Call [reset] after audio was dropped.
 */
internal class StreamResampler(
    inputRate: Int,
    private val outputRate: Int = 16000,
    cutoffHz: Float = CUTOFF_HZ,
) {
    companion object {
        // 96 taps at 48 kHz, Kaiser beta for ~60 dB stopband: passband to ~6.3 kHz, stopband from
        // ~8.1 kHz. Cost: ~96 MACs per output sample (~1.5 M MAC/s), negligible next to the model.
        private const val HALF_TAPS = 48
        private const val TAPS = 2 * HALF_TAPS
        private const val PHASES = 256
        private const val CUTOFF_HZ = 7200f
        private const val KAISER_BETA = 5.65

        private fun besselI0(x: Double): Double {
            var sum = 1.0
            var term = 1.0
            val q = x * x / 4.0
            var k = 1
            while (k < 50) {
                term *= q / (k * k)
                sum += term
                if (term < sum * 1e-12) break
                k++
            }
            return sum
        }
    }

    /** Input samples per output sample (3.0 for 48 kHz -> 16 kHz). */
    private val step = inputRate.toDouble() / outputRate

    // table[phase * TAPS + j] = kernel at offset (j - HALF_TAPS + 1 - phase / PHASES), DC-normalised.
    private val table = FloatArray(PHASES * TAPS)

    // Work buffer: TAPS samples of history followed by the newest block.
    private var work = FloatArray(TAPS + 4096)
    private var workStart = -TAPS.toLong()  // absolute input index of work[0]
    private var received = 0L               // absolute number of input samples received
    private var pos = 0.0                   // absolute input position of the next output sample

    init {
        val fc = min(cutoffHz.toDouble(), 0.45 * inputRate) / inputRate  // cycles per input sample
        val i0Beta = besselI0(KAISER_BETA)
        for (phase in 0 until PHASES) {
            val frac = phase.toDouble() / PHASES
            var sum = 0.0
            for (j in 0 until TAPS) {
                val t = j - HALF_TAPS + 1 - frac
                val x = 2.0 * fc * t
                val sinc = if (x == 0.0) 1.0 else sin(PI * x) / (PI * x)
                val r = t / HALF_TAPS
                val w = if (r * r >= 1.0) 0.0 else besselI0(KAISER_BETA * sqrt(1.0 - r * r)) / i0Beta
                val h = 2.0 * fc * sinc * w
                table[phase * TAPS + j] = h.toFloat()
                sum += h
            }
            for (j in 0 until TAPS) table[phase * TAPS + j] = (table[phase * TAPS + j] / sum).toFloat()
        }
    }

    /** Upper bound of output samples produced for [inputCount] input samples. */
    fun maxOutputFor(inputCount: Int): Int = (inputCount / step).toInt() + 2

    fun reset() {
        work.fill(0f)
        workStart = -TAPS.toLong()
        received = 0L
        pos = 0.0
    }

    /**
     * Resamples [count] samples of [src] (from [srcOffset]) into [dst] (from [dstOffset]).
     * Returns the number of output samples written (at most [maxOutputFor] of [count]).
     */
    fun process(src: FloatArray, srcOffset: Int, count: Int, dst: FloatArray, dstOffset: Int): Int {
        if (count <= 0) return 0
        if (work.size < TAPS + count) work = work.copyOf(TAPS + count)

        // work[0 until TAPS] already holds the history; append the new block after it.
        System.arraycopy(src, srcOffset, work, TAPS, count)
        received += count

        var written = 0
        while (true) {
            var base = floor(pos).toLong()
            var phase = ((pos - base) * PHASES + 0.5).toInt()
            if (phase == PHASES) {
                phase = 0
                base++
            }
            if (base + HALF_TAPS >= received) break  // needs samples not received yet

            val first = (base - HALF_TAPS + 1 - workStart).toInt()
            val k = phase * TAPS
            var acc = 0f
            for (j in 0 until TAPS) acc += work[first + j] * table[k + j]
            dst[dstOffset + written++] = acc
            pos += step
        }

        // Keep the last TAPS input samples as history for the next block.
        System.arraycopy(work, count, work, 0, TAPS)
        workStart = received - TAPS
        return written
    }
}
