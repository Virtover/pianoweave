package com.lumenchord.pianoweave.audio

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Takes what isn't speech (the piano, the room) out of a 16 kHz stream, for voice commands: DTLN
 * (Westhausen & Meyer 2020, MIT, assets/dtln_1.tflite + dtln_2.tflite, ~4 MB). Two small LSTM stages,
 * a spectral mask then a time-domain one, on 32 ms blocks every 8 ms; the output lags the input by
 * 24 ms. As in the reference real-time code: unwindowed rFFT, overlap-add of the output blocks.
 *
 * Not thread-safe: one stream, fed from one thread. [create] returns null if the models can't load.
 */
internal class SpeechEnhancer private constructor(
    private val stage1: Interpreter,
    private val stage2: Interpreter
) : AutoCloseable {
    companion object {
        private const val TAG = "SpeechEnhancer"
        private const val BLOCK = 512
        private const val SHIFT = 128
        private const val BINS = BLOCK / 2 + 1
        private const val STATE = 2 * 128 * 2

        fun create(context: Context): SpeechEnhancer? = try {
            SpeechEnhancer(load(context, "dtln_1.tflite"), load(context, "dtln_2.tflite"))
        } catch (e: Throwable) {
            Log.w(TAG, "Speech enhancer unavailable", e)
            null
        }

        private fun load(context: Context, name: String): Interpreter {
            val fd = context.assets.openFd(name)
            val buffer = FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.length)
            return Interpreter(buffer, Interpreter.Options().apply { setNumThreads(1) })
        }

        private fun direct(floats: Int): ByteBuffer = ByteBuffer.allocateDirect(floats * 4).order(ByteOrder.nativeOrder())
    }

    // Tensor indices, found by shape (the exported models don't keep a fixed order): audio and spectra
    // are [1, 1, n], the LSTM states [1, 2, 128, 2]
    private val s1Mag = indexOf(stage1, input = true, 3)
    private val s1State = indexOf(stage1, input = true, 4)
    private val s1Mask = indexOf(stage1, input = false, 3)
    private val s1StateOut = indexOf(stage1, input = false, 4)
    private val s2Block = indexOf(stage2, input = true, 3)
    private val s2State = indexOf(stage2, input = true, 4)
    private val s2Out = indexOf(stage2, input = false, 3)
    private val s2StateOut = indexOf(stage2, input = false, 4)

    private val magIn = direct(BINS)
    private val state1 = direct(STATE)
    private val maskOut = direct(BINS)
    private val state1Out = direct(STATE)
    private val blockIn = direct(BLOCK)
    private val state2 = direct(STATE)
    private val blockOut = direct(BLOCK)
    private val state2Out = direct(STATE)

    private val inBuffer = FloatArray(BLOCK)
    private val outBuffer = FloatArray(BLOCK)
    private val re = FloatArray(BLOCK)
    private val im = FloatArray(BLOCK)
    private val mag = FloatArray(BINS)
    private val phase = FloatArray(BINS)
    private val fft = Fft(BLOCK)

    private val inputs1 = arrayOfNulls<Any>(stage1.inputTensorCount)
    private val inputs2 = arrayOfNulls<Any>(stage2.inputTensorCount)

    init {
        check(minOf(s1Mag, s1State, s1Mask, s1StateOut, s2Block, s2State, s2Out, s2StateOut) >= 0) { "DTLN tensor layout not recognised" }
        reset()
    }

    private fun indexOf(interp: Interpreter, input: Boolean, rank: Int): Int {
        val count = if (input) interp.inputTensorCount else interp.outputTensorCount
        return (0 until count).firstOrNull {
            (if (input) interp.getInputTensor(it) else interp.getOutputTensor(it)).shape().size == rank
        } ?: -1
    }

    /** Clears the stream (after audio was lost). */
    fun reset() {
        inBuffer.fill(0f)
        outBuffer.fill(0f)
        for (b in listOf(state1, state2)) {
            b.clear()
            repeat(STATE) { b.putFloat(0f) }
            b.rewind()
        }
    }

    /** Enhances [samples] (a multiple of 8 ms, 128 samples) in place. */
    fun process(samples: FloatArray) {
        var off = 0
        while (off + SHIFT <= samples.size) {
            System.arraycopy(inBuffer, SHIFT, inBuffer, 0, BLOCK - SHIFT)
            System.arraycopy(samples, off, inBuffer, BLOCK - SHIFT, SHIFT)
            block()
            System.arraycopy(outBuffer, 0, samples, off, SHIFT)
            off += SHIFT
        }
    }

    private fun block() {
        // Stage 1: a mask on the magnitude spectrum
        System.arraycopy(inBuffer, 0, re, 0, BLOCK)
        im.fill(0f)
        fft.transform(re, im, inverse = false)
        magIn.clear()
        for (k in 0 until BINS) {
            mag[k] = sqrt(re[k] * re[k] + im[k] * im[k])
            phase[k] = atan2(im[k], re[k])
            magIn.putFloat(mag[k])
        }
        magIn.rewind()
        inputs1[s1Mag] = magIn
        inputs1[s1State] = state1
        maskOut.clear(); state1Out.clear()
        stage1.runForMultipleInputsOutputs(inputs1, mapOf(s1Mask to maskOut, s1StateOut to state1Out))
        copyState(state1Out, state1)
        maskOut.rewind()
        for (k in 0 until BINS) {
            val m = mag[k] * maskOut.getFloat()
            re[k] = m * cos(phase[k])
            im[k] = m * sin(phase[k])
        }
        for (k in 1 until BLOCK / 2) {
            re[BLOCK - k] = re[k]
            im[BLOCK - k] = -im[k]
        }
        fft.transform(re, im, inverse = true)

        // Stage 2: the time-domain block
        blockIn.clear()
        for (i in 0 until BLOCK) blockIn.putFloat(re[i])
        blockIn.rewind()
        inputs2[s2Block] = blockIn
        inputs2[s2State] = state2
        blockOut.clear(); state2Out.clear()
        stage2.runForMultipleInputsOutputs(inputs2, mapOf(s2Out to blockOut, s2StateOut to state2Out))
        copyState(state2Out, state2)

        // Overlap-add
        System.arraycopy(outBuffer, SHIFT, outBuffer, 0, BLOCK - SHIFT)
        java.util.Arrays.fill(outBuffer, BLOCK - SHIFT, BLOCK, 0f)
        blockOut.rewind()
        for (i in 0 until BLOCK) outBuffer[i] += blockOut.getFloat()
    }

    private fun copyState(from: ByteBuffer, to: ByteBuffer) {
        from.rewind(); to.clear()
        to.put(from)
        to.rewind()
    }

    override fun close() {
        stage1.close()
        stage2.close()
    }

    /** In-place radix-2 complex FFT of a fixed size; the inverse is scaled by 1/n. */
    private class Fft(private val n: Int) {
        private val cosT = FloatArray(n / 2) { cos(-2.0 * Math.PI * it / n).toFloat() }
        private val sinT = FloatArray(n / 2) { sin(-2.0 * Math.PI * it / n).toFloat() }
        private val rev = IntArray(n).also { r ->
            val bits = Integer.numberOfTrailingZeros(n)
            for (i in 0 until n) r[i] = Integer.reverse(i) ushr (32 - bits)
        }

        fun transform(re: FloatArray, im: FloatArray, inverse: Boolean) {
            for (i in 0 until n) {
                val j = rev[i]
                if (j > i) {
                    var t = re[i]; re[i] = re[j]; re[j] = t
                    t = im[i]; im[i] = im[j]; im[j] = t
                }
            }
            val sign = if (inverse) -1f else 1f
            var size = 2
            while (size <= n) {
                val half = size / 2
                val step = n / size
                var start = 0
                while (start < n) {
                    for (k in 0 until half) {
                        val wr = cosT[k * step]
                        val wi = sign * sinT[k * step]
                        val a = start + k
                        val b = a + half
                        val tr = re[b] * wr - im[b] * wi
                        val ti = re[b] * wi + im[b] * wr
                        re[b] = re[a] - tr; im[b] = im[a] - ti
                        re[a] += tr; im[a] += ti
                    }
                    start += size
                }
                size *= 2
            }
            if (inverse) for (i in 0 until n) { re[i] /= n; im[i] /= n }
        }
    }
}
