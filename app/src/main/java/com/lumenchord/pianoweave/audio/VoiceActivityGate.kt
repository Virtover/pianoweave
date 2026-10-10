package com.lumenchord.pianoweave.audio

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.floor

/**
 * Streaming voice activity gate based on Silero VAD v5 (TFLite/LiteRT export, ~1.3 MB, ~0.4 M params).
 *
 * The model works on 32 ms chunks (512 samples @ 16 kHz) with a 64-sample context and an LSTM
 * state carried between chunks, so it is fed continuously with the new audio of every inference
 * loop iteration (2-3 chunks per iteration, well under 1 ms each). The result is a voice
 * probability per 32 ms, smoothed into a 0..1 [evidence] that AcousticNoteDetector uses to make
 * NON-target pitch detection stricter while someone is speaking or singing.
 *
 * If the model asset is missing or its layout is not recognised, [evidence] stays 0 and detection
 * behaves as if the gate did not exist.
 *
 * Expects assets/silero-vad.tflite (soniqo/Silero-VAD-v5-LiteRT):
 *   inputs : audio [1, 576] (64 context + 512 chunk), state [2, 1, 128]
 *   outputs: probability [1, 1], state_out [2, 1, 128]
 */
object VoiceActivityGate {
    private const val TAG = "VoiceActivityGate"
    private const val MODEL_NAME = "silero-vad.tflite"

    private const val CHUNK = 512
    private const val CONTEXT = 64
    private const val STATE_SIZE = 2 * 1 * 128

    // Per-chunk (32 ms) smoothing of the raw probability: fast attack, ~1 s release so the gate
    // stays on between syllables and breaths.
    private const val ATTACK = 0.6f
    private const val RELEASE = 0.04f

    // smoothed probability -> evidence: below LOW = 0, above HIGH = 1.
    private const val EVIDENCE_LOW = 0.25f
    private const val EVIDENCE_HIGH = 0.65f

    /** Smoothed voice evidence, 0 (none) .. 1 (clearly voice). */
    @Volatile
    var evidence: Float = 0f
        private set

    private var interpreter: Interpreter? = null
    private var audioIn = -1
    private var stateIn = -1
    private var probOut = -1
    private var stateOut = -1
    private var audioBuf: ByteBuffer? = null
    private var stateInBuf: ByteBuffer? = null
    private var probBuf: ByteBuffer? = null
    private var stateOutBuf: ByteBuffer? = null
    private var failed = false

    // Stream state
    private val context = FloatArray(CONTEXT)
    private val chunk = FloatArray(CHUNK)
    private var chunkFill = 0
    private var nextPos = 0.0      // next 16 kHz sample position, in native-sample units of the next block
    private var prevLast = 0f      // last native sample of the previous block (for interpolation)
    private var smoothed = 0f
    private var debugCounter = 0

    @Synchronized
    fun initialize(context: Context) {
        if (interpreter != null || failed) return
        try {
            val fd = context.assets.openFd(MODEL_NAME)
            val buffer = FileInputStream(fd.fileDescriptor).channel
                .map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.length)
            val interp = Interpreter(buffer, Interpreter.Options().apply { setNumThreads(1) })

            for (i in 0 until interp.inputTensorCount) {
                when (interp.getInputTensor(i).shape().fold(1) { acc, d -> acc * d }) {
                    CONTEXT + CHUNK -> audioIn = i
                    STATE_SIZE -> stateIn = i
                }
            }
            for (i in 0 until interp.outputTensorCount) {
                when (interp.getOutputTensor(i).shape().fold(1) { acc, d -> acc * d }) {
                    1 -> probOut = i
                    STATE_SIZE -> stateOut = i
                }
            }
            if (audioIn < 0 || stateIn < 0 || probOut < 0 || stateOut < 0) {
                Log.w(TAG, "Silero VAD tensor layout not recognised; voice gate disabled")
                interp.close()
                failed = true
                return
            }

            val order = ByteOrder.nativeOrder()
            audioBuf = ByteBuffer.allocateDirect((CONTEXT + CHUNK) * 4).order(order)
            stateInBuf = ByteBuffer.allocateDirect(STATE_SIZE * 4).order(order)
            probBuf = ByteBuffer.allocateDirect(4).order(order)
            stateOutBuf = ByteBuffer.allocateDirect(STATE_SIZE * 4).order(order)
            interpreter = interp
            resetStream()
            Log.i(TAG, "Silero VAD ready (in: audio=$audioIn state=$stateIn, out: prob=$probOut state=$stateOut)")
        } catch (e: Exception) {
            Log.w(TAG, "Silero VAD unavailable, voice gate disabled", e)
            failed = true
        }
    }

    /**
     * Feed newly captured audio. [samples] are raw (un-normalised) 44.1 kHz floats whose LAST
     * [count] entries are the audio received since the previous call; [step] is 44100 / 16000.
     * Set [contiguous] to false when audio was dropped since the previous call (the stream state
     * is then restarted).
     */
    @Synchronized
    fun feed(samples: FloatArray, count: Int, step: Float, contiguous: Boolean = true) {
        val interp = interpreter ?: return
        if (!contiguous) resetStream()
        val n = count.coerceAtMost(samples.size)
        if (n <= 0) return
        val first = samples.size - n
        val limit = n - 1.0

        try {
            // Linear-interpolation resampling to 16 kHz with a phase carried across calls, so the
            // stream stays continuous (the LSTM state depends on it).
            while (nextPos < limit) {
                val idx = floor(nextPos).toInt() // -1 when between the previous block and this one
                val frac = (nextPos - idx).toFloat()
                val a = if (idx < 0) prevLast else samples[first + idx]
                val b = samples[first + idx + 1]
                chunk[chunkFill++] = a * (1f - frac) + b * frac
                if (chunkFill == CHUNK) {
                    runChunk(interp)
                    chunkFill = 0
                }
                nextPos += step
            }
            nextPos -= n
            prevLast = samples[first + n - 1]
        } catch (e: Exception) {
            Log.w(TAG, "Silero VAD inference failed", e)
        }
    }

    private fun runChunk(interp: Interpreter) {
        val audio = audioBuf ?: return
        val stateIn = stateInBuf ?: return
        val prob = probBuf ?: return
        val stateOut = stateOutBuf ?: return

        audio.rewind()
        for (c in context) audio.putFloat(c)
        for (s in chunk) audio.putFloat(s)
        audio.rewind()
        stateIn.rewind()
        prob.rewind()
        stateOut.rewind()

        val inputs = arrayOfNulls<Any>(interp.inputTensorCount)
        inputs[audioIn] = audio
        inputs[this.stateIn] = stateIn
        interp.runForMultipleInputsOutputs(
            inputs,
            mapOf(probOut to prob, this.stateOut to stateOut)
        )

        // carry LSTM state and the last 64 samples into the next chunk
        stateOut.rewind()
        stateIn.rewind()
        stateIn.put(stateOut)
        stateIn.rewind()
        System.arraycopy(chunk, CHUNK - CONTEXT, context, 0, CONTEXT)

        val p = prob.getFloat(0)
        if (p.isNaN()) return
        smoothed += (p - smoothed) * (if (p > smoothed) ATTACK else RELEASE)
        evidence = ((smoothed - EVIDENCE_LOW) / (EVIDENCE_HIGH - EVIDENCE_LOW)).coerceIn(0f, 1f)

        if (++debugCounter % 16 == 0) {
            Log.d(TAG, "Silero: p=$p smoothed=$smoothed evidence=$evidence")
        }
    }

    /** Clears LSTM state, context, resampler phase and smoothing. */
    private fun resetStream() {
        context.fill(0f)
        chunkFill = 0
        nextPos = 0.0
        prevLast = 0f
        smoothed = 0f
        evidence = 0f
        stateInBuf?.let { buf ->
            buf.rewind()
            for (i in 0 until STATE_SIZE) buf.putFloat(0f)
            buf.rewind()
        }
    }

    @Synchronized
    fun reset() = resetStream()

    @Synchronized
    fun cleanup() {
        interpreter?.close()
        interpreter = null
        audioBuf = null
        stateInBuf = null
        probBuf = null
        stateOutBuf = null
        audioIn = -1
        stateIn = -1
        probOut = -1
        stateOut = -1
        evidence = 0f
        failed = false
    }
}