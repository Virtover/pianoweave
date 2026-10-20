package com.lumenchord.pianoweave.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class InputPipelineTest {

    /** Resamples one second of a sine in uneven blocks and returns the output level in dB. */
    private fun resampledLevelDb(inputRate: Int, freq: Double): Double {
        val input = FloatArray(inputRate) { sin(2 * PI * freq * it / inputRate).toFloat() }
        val rs = StreamResampler(inputRate)
        val out = FloatArray(rs.maxOutputFor(input.size) + 64)
        var produced = 0
        var i = 0
        while (i < input.size) {
            val n = minOf(1234, input.size - i)
            produced += rs.process(input, i, n, out, produced)
            i += n
        }
        assertTrue("output length $produced", abs(produced - 16000) < 40)
        var sumSq = 0.0
        for (k in 2000 until 15000) sumSq += out[k] * out[k]
        val amplitude = sqrt(2 * sumSq / 13000)
        return 20 * log10(amplitude + 1e-12)
    }

    @Test
    fun resampler48kPassesPianoBandAndRejectsAliases() {
        for (f in listOf(440.0, 4000.0, 6000.0)) {
            assertEquals("passband $f Hz", 0.0, resampledLevelDb(48000, f), 0.5)
        }
        for (f in listOf(8400.0, 9000.0, 10000.0, 12000.0)) {
            assertTrue("alias $f Hz", resampledLevelDb(48000, f) < -55.0)
        }
    }

    @Test
    fun resampler44kFallbackAlsoFilters() {
        assertEquals(0.0, resampledLevelDb(44100, 1000.0), 0.5)
        assertTrue(resampledLevelDb(44100, 10000.0) < -55.0)
    }

    @Test
    fun levelControlBoostsSoftNotesGatesNoiseAndNeverClips() {
        val fs = 16000
        val rnd = Random(0)
        fun noise(n: Int) = FloatArray(n) { (rnd.nextGaussian() * 0.0003).toFloat() }
        fun note(seconds: Double, peak: Double, freq: Double): FloatArray {
            val n = (seconds * fs).toInt()
            val nz = noise(n)
            return FloatArray(n) { (peak * exp(-2.0 * it / fs) * sin(2 * PI * freq * it / fs)).toFloat() + nz[it] }
        }

        val noiseOnly = noise(fs)
        val softNote = note(1.0, 0.004, 523.0)  // muted by the old 0.005 peak gate
        val loudNote = note(1.0, 0.8, 131.0)
        val signal = noiseOnly + softNote + loudNote
        val lc = InputLevelControl(fs)
        val out = signal.copyOf()
        var i = 0
        while (i < out.size) {  // arbitrary block sizes, like the inference loop
            val n = minOf(997, out.size - i)
            lc.process(out, i, n)
            i += n
        }

        fun peak(a: FloatArray, from: Int, to: Int) = (from until to).maxOf { abs(a[it]) }
        assertTrue("noise stays quiet", peak(out, fs / 2, fs) < 0.005f)
        assertTrue("soft note boosted", peak(out, fs, fs + fs / 5) > 0.03f)
        assertTrue("never above target", out.maxOf { abs(it) } <= 0.5001f)
        assertTrue("loud note kept loud", peak(out, 2 * fs, 2 * fs + fs / 10) > 0.4f)
    }
}
