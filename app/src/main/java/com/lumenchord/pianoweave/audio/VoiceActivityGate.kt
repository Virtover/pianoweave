package com.lumenchord.pianoweave.audio

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.min

/**
 * Runs YAMNet (tiny audio-event classifier) at ~2 Hz on the latest captured audio and exposes a
 * smoothed 0..1 "voice evidence" value. AcousticNoteDetector uses it to make NON-target pitch
 * detection stricter while someone is speaking or singing. If the model asset is missing or
 * fails, evidence stays 0 and detection behaves as if the gate did not exist.
 *
 * Expects assets/yamnet.tflite: float32 waveform input (16 kHz, ~15600 samples),
 * scores output [N, 521].
 */
object VoiceActivityGate {
    private const val TAG = "VoiceActivityGate"
    private const val MODEL_NAME = "lite-model_yamnet_classification_tflite_1.tflite"
    private const val NUM_CLASSES = 521

    // YAMNet class indices (verify against yamnet_class_map.csv shipped with the model).
    // 0 Speech, 1 Child speech, 2 Conversation, 3 Narration, 24 Singing, 25 Choir,
    // 29 Child singing, 32 Humming
    private val VOICE_CLASSES = intArrayOf(0, 1, 2, 3, 24, 25, 29, 32)
    // 147 Keyboard, 148 Piano, 149 Electric piano, 153 Synthesizer, 154 Sampler, 155 Harpsichord
    private val PIANO_CLASSES = intArrayOf(147, 148, 149, 153, 154, 155)

    private const val UPDATE_INTERVAL_NS = 200_000_000L
    private const val ATTACK = 0.9f   // how fast evidence rises toward a higher reading
    private const val RELEASE = 0.25f // how fast it falls

    // raw = (voice - PIANO_WEIGHT * piano - DEADZONE) / SPAN, clamped to 0..1
    private const val PIANO_WEIGHT = 0.5f
    private const val DEADZONE = 0.15f
    private const val SPAN = 0.45f

    /** Smoothed voice evidence, 0 (none) .. 1 (clearly voice, no piano). */
    @Volatile
    var evidence: Float = 0f
        private set

    private var interpreter: Interpreter? = null
    private var scoreOutputIndex = -1
    private var inputSamples = 0
    private var inputBuffer: ByteBuffer? = null
    private var scores: Array<FloatArray>? = null
    private var lastUpdateNs = 0L
    private var failed = false

    @Synchronized
    fun initialize(context: Context) {
        if (interpreter != null || failed) return
        try {
            val fd = context.assets.openFd(MODEL_NAME)
            val buffer = FileInputStream(fd.fileDescriptor).channel
                .map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.length)
            val options = Interpreter.Options().apply {
                setNumThreads(1)
                setUseXNNPACK(true)
            }
            val interp = Interpreter(buffer, options)

            inputSamples = interp.getInputTensor(0).shape().fold(1) { acc, d -> acc * d }
            for (i in 0 until interp.outputTensorCount) {
                val shape = interp.getOutputTensor(i).shape()
                if (shape.size == 2 && shape[1] == NUM_CLASSES) {
                    scoreOutputIndex = i
                    scores = Array(shape[0]) { FloatArray(NUM_CLASSES) }
                    break
                }
            }
            if (scoreOutputIndex < 0 || inputSamples <= 0) {
                Log.w(TAG, "YAMNet output/input layout not recognised; voice gate disabled")
                interp.close()
                failed = true
                return
            }
            inputBuffer = ByteBuffer.allocateDirect(inputSamples * 4).order(ByteOrder.nativeOrder())
            interpreter = interp
            Log.i(TAG, "YAMNet ready: input=$inputSamples samples, scores output=$scoreOutputIndex")
        } catch (e: Exception) {
            Log.w(TAG, "YAMNet unavailable, voice gate disabled", e)
            failed = true
        }
    }

    /**
     * Call every loop iteration; it only does work every UPDATE_INTERVAL_NS.
     * [samples] are raw (un-normalised) 44.1 kHz floats, newest audio at the end;
     * [step] is 44100 / 16000.
     */
    fun maybeUpdate(samples: FloatArray, step: Float) {
        val interp = interpreter ?: return
        val buf = inputBuffer ?: return
        val out = scores ?: return
        val now = System.nanoTime()
        if (now - lastUpdateNs < UPDATE_INTERVAL_NS) return
        lastUpdateNs = now

        try {
            val span = (inputSamples * step).toInt() + 2
            val base = (samples.size - span).coerceAtLeast(0)
            val last = samples.size - 1
            buf.rewind()
            for (i in 0 until inputSamples) {
                val pos = i * step
                val whole = pos.toInt()
                val frac = pos - whole
                val i0 = min(base + whole, last)
                buf.putFloat(samples[i0] * (1f - frac) + samples[min(i0 + 1, last)] * frac)
            }

            interp.runForMultipleInputsOutputs(arrayOf<Any>(buf), mapOf(scoreOutputIndex to out))

            var voice = 0f
            for (c in VOICE_CLASSES) voice += meanScore(out, c)
            var piano = 0f
            for (c in PIANO_CLASSES) piano += meanScore(out, c)
            voice = voice.coerceAtMost(1f)
            piano = piano.coerceAtMost(1f)

            val raw = ((voice - PIANO_WEIGHT * piano - DEADZONE) / SPAN).coerceIn(0f, 1f)
            val prev = evidence
            evidence = prev + (raw - prev) * (if (raw > prev) ATTACK else RELEASE)
//            Log.d(TAG, "Voice: raw=$raw, smoothed=$evidence | voice=$voice, piano=$piano")
        } catch (e: Exception) {
            Log.w(TAG, "YAMNet inference failed", e)
        }
    }

    private fun meanScore(out: Array<FloatArray>, cls: Int): Float {
        var sum = 0f
        for (row in out) sum += row[cls]
        return sum / out.size
    }

    fun reset() {
        evidence = 0f
        lastUpdateNs = 0L
    }

    @Synchronized
    fun cleanup() {
        interpreter?.close()
        interpreter = null
        inputBuffer = null
        scores = null
        evidence = 0f
        failed = false
    }
}