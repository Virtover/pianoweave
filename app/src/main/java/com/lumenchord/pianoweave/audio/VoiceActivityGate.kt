package com.lumenchord.pianoweave.audio

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Streaming voice activity gate based on Silero VAD v5 (TFLite/LiteRT export, ~1.3 MB, ~0.4 M params).
 *
 * The model works on 32 ms chunks (512 samples @ 16 kHz) with a 64-sample context and an LSTM
 * state carried between chunks, so it is fed continuously with the new audio of every inference
 * loop iteration (2-3 chunks per iteration, well under 1 ms each). The audio is the same
 * anti-aliased 16 kHz stream the note model gets, taken BEFORE the input level control, so the
 * VAD sees the raw level it was calibrated for. The result is a voice
 * probability per 32 ms, smoothed into a 0..1 [evidence] that AcousticNoteDetector uses to make
 * NON-target pitch detection stricter while someone is speaking or singing.
 *
 * While a [chunkListener] is set (voice commands listen), each chunk also goes through a second
 * stream of the same model, with its own state, on level-boosted audio: the mic is unprocessed, and
 * the model's voice probability falls with level (a word 30 dB down peaks at ~0.4 instead of ~1), so a
 * voice that isn't loud was never seen. The listener gets the raw chunk with that probability
 * (VoiceCommandRecognizer cuts its utterances on them). The note gate above keeps the raw level.
 * That voice stream (and the chunks the listener gets) is first cleaned by [SpeechEnhancer], which
 * takes the piano out: Silero ignores a voice over music, and the recognizer hears it better too.
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

    // Voice stream gain: brings the recent peak level (fast attack, ~1 s release per -4 dB) to
    // VOICE_TARGET_PEAK, by at most MAX_VOICE_GAIN (+30 dB). The level floor keeps silence from
    // asking for more than that.
    private const val VOICE_TARGET_PEAK = 0.3f
    private const val MAX_VOICE_GAIN = 32f
    private const val VOICE_LEVEL_DECAY = 0.985f
    private const val VOICE_LEVEL_FLOOR = 0.002f

    /**
     * Whether the voice probability comes from audio cleaned by [SpeechEnhancer] (the piano taken out);
     * off only to compare, in tests. The listener always gets the raw chunks: the recognizer lost short
     * words like "play" in cleaned audio.
     */
    @Volatile
    var enhanceVoice = true

    /** Receives every 32 ms chunk with its raw (unsmoothed) voice probability, on the audio thread. */
    fun interface ChunkListener {
        fun onChunk(chunk: FloatArray, probability: Float)
    }

    @Volatile
    var chunkListener: ChunkListener? = null

    /** False if the model is missing or wasn't recognised: no evidence and no chunks then. */
    val isAvailable: Boolean
        get() = interpreter != null

    /** Smoothed voice evidence, 0 (none) .. 1 (clearly voice). */
    @Volatile
    var evidence: Float = 0f
        private set

    private var interpreter: Interpreter? = null
    private var audioIn = -1
    private var stateIn = -1
    private var probOut = -1
    private var stateOut = -1
    private var failed = false

    /** One continuous run of the model: its LSTM state and audio context, carried chunk to chunk. */
    private class Stream {
        private val order = ByteOrder.nativeOrder()
        val audio: ByteBuffer = ByteBuffer.allocateDirect((CONTEXT + CHUNK) * 4).order(order)
        val stateIn: ByteBuffer = ByteBuffer.allocateDirect(STATE_SIZE * 4).order(order)
        val prob: ByteBuffer = ByteBuffer.allocateDirect(4).order(order)
        val stateOut: ByteBuffer = ByteBuffer.allocateDirect(STATE_SIZE * 4).order(order)
        val context = FloatArray(CONTEXT)

        fun reset() {
            context.fill(0f)
            stateIn.rewind()
            for (i in 0 until STATE_SIZE) stateIn.putFloat(0f)
            stateIn.rewind()
        }
    }

    private var gateStream: Stream? = null
    private var voiceStream: Stream? = null
    private var enhancer: SpeechEnhancer? = null
    private val voiceChunk = FloatArray(CHUNK)

    // Stream state
    private val chunk = FloatArray(CHUNK)
    private val boosted = FloatArray(CHUNK)
    private var chunkFill = 0
    private var smoothed = 0f
    private var voiceLevel = VOICE_LEVEL_FLOOR
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

            gateStream = Stream()
            voiceStream = Stream()
            enhancer = SpeechEnhancer.create(context)
            interpreter = interp
            resetStream()
            Log.i(TAG, "Silero VAD ready (in: audio=$audioIn state=$stateIn, out: prob=$probOut state=$stateOut)")
        } catch (e: Exception) {
            Log.w(TAG, "Silero VAD unavailable, voice gate disabled", e)
            failed = true
        }
    }

    /**
     * Feed newly captured audio: [count] samples of the continuous 16 kHz stream (un-normalised),
     * starting at [offset]. Call [reset] when audio was dropped since the previous call (the
     * LSTM state depends on a continuous stream).
     */
    @Synchronized
    fun feed(samples: FloatArray, offset: Int, count: Int) {
        val interp = interpreter ?: return
        try {
            for (i in offset until offset + count) {
                chunk[chunkFill++] = samples[i]
                if (chunkFill == CHUNK) {
                    runChunk(interp)
                    chunkFill = 0
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Silero VAD inference failed", e)
        }
    }

    private fun runChunk(interp: Interpreter) {
        val gate = gateStream ?: return
        val p = run(interp, gate, chunk)
        if (p.isNaN()) return
        smoothed += (p - smoothed) * (if (p > smoothed) ATTACK else RELEASE)
        evidence = ((smoothed - EVIDENCE_LOW) / (EVIDENCE_HIGH - EVIDENCE_LOW)).coerceIn(0f, 1f)

        val listener = chunkListener
        val voice = voiceStream
        if (listener != null && voice != null) {
            System.arraycopy(chunk, 0, voiceChunk, 0, CHUNK)
            if (enhanceVoice) enhancer?.process(voiceChunk)
            var peak = 0f
            for (s in voiceChunk) peak = maxOf(peak, kotlin.math.abs(s))
            voiceLevel = maxOf(peak, voiceLevel * VOICE_LEVEL_DECAY, VOICE_LEVEL_FLOOR)
            val gain = (VOICE_TARGET_PEAK / voiceLevel).coerceIn(1f, MAX_VOICE_GAIN)
            for (i in 0 until CHUNK) boosted[i] = (voiceChunk[i] * gain).coerceIn(-1f, 1f)
            val voiceP = run(interp, voice, boosted)
            // The raw chunk: a recording is replayed through the whole voice path, enhancer included
            VoiceDebugRecorder.write(chunk, voiceP)
            if (!voiceP.isNaN()) listener.onChunk(chunk, voiceP)
        }

        if (++debugCounter % 16 == 0) {
            Log.d(TAG, "Silero: p=$p smoothed=$smoothed evidence=$evidence")
        }
    }

    /** One chunk through [stream]: its voice probability, with the stream's state carried on. */
    private fun run(interp: Interpreter, stream: Stream, samples: FloatArray): Float {
        val audio = stream.audio
        audio.rewind()
        for (c in stream.context) audio.putFloat(c)
        for (s in samples) audio.putFloat(s)
        audio.rewind()
        stream.stateIn.rewind()
        stream.prob.rewind()
        stream.stateOut.rewind()

        val inputs = arrayOfNulls<Any>(interp.inputTensorCount)
        inputs[audioIn] = audio
        inputs[stateIn] = stream.stateIn
        interp.runForMultipleInputsOutputs(inputs, mapOf(probOut to stream.prob, stateOut to stream.stateOut))

        // carry LSTM state and the last 64 samples into the next chunk
        stream.stateOut.rewind()
        stream.stateIn.rewind()
        stream.stateIn.put(stream.stateOut)
        stream.stateIn.rewind()
        System.arraycopy(samples, CHUNK - CONTEXT, stream.context, 0, CONTEXT)
        return stream.prob.getFloat(0)
    }

    /** Clears LSTM state, context and smoothing. */
    private fun resetStream() {
        chunkFill = 0
        smoothed = 0f
        evidence = 0f
        voiceLevel = VOICE_LEVEL_FLOOR
        gateStream?.reset()
        voiceStream?.reset()
        enhancer?.reset()
    }

    @Synchronized
    fun reset() = resetStream()

    @Synchronized
    fun cleanup() {
        interpreter?.close()
        interpreter = null
        enhancer?.close()
        enhancer = null
        gateStream = null
        voiceStream = null
        audioIn = -1
        stateIn = -1
        probOut = -1
        stateOut = -1
        evidence = 0f
        failed = false
    }
}