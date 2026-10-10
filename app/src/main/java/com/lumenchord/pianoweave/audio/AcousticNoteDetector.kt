package com.lumenchord.pianoweave.audio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.lumenchord.pianoweave.midi.MidiInputManager
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object AcousticNoteDetector {
    private const val TAG = "AcousticNoteDetector"
    private const val MODEL_NAME = "onsets_frames_wavinput_no_offset_uni.tflite"

    // Model Constraints (fallbacks only; the real values are read from the tensors, e.g. 17920 / 32 here)
    private const val INPUT_SAMPLES = 43844
    private const val MIDI_KEYS = 88
    private const val MIDI_OFFSET = 21
    private const val OUTPUT_FRAMES = 172

    // Minimal Onset & Frames Thresholds
    private const val ONSET_THRESHOLD_BASE = 0.50f
    private const val FRAME_THRESHOLD_BASE = 0.35f

    // Non-target pitches get stricter thresholds by these amounts.
    private const val NON_TARGET_ONSET_PENALTY = 0.12f
    private const val NON_TARGET_FRAME_PENALTY = 0.10f

    // --- Hold check (non-target pitches only; targets are never affected and stay immediate) ---
    // A piano string keeps ringing after the attack, a spoken syllable fades within 2-3 frames.
    // A NEW non-target note is therefore decided HOLD_FRAMES frames late (1 frame = 35 ms here): its
    // onset is searched in the window shifted back by HOLD_FRAMES, and it is accepted only if the
    // frame probability HOLD_FRAMES after the onset peak is still >= HOLD_MIN_FRAME_PROB.
    // Cost: non-target notes are reported HOLD_FRAMES * 35 ms later. Start at 3, lower to cut latency.
    private const val HOLD_FRAMES = 3
    private const val NON_TARGET_HOLD_MIN_FRAME_PROB = 0.4f
    private const val TARGET_HOLD_MIN_FRAME_PROB = 0.2f

    private const val NON_TARGET_HOLD_VOICE_EXTRA = 0.25f
    private const val TARGET_HOLD_VOICE_EXTRA = 0.15f

    // The analysis window is derived per run from the audio received since the previous run.
    // MAX is clamped to the model's output frame count (32 for this model).
    private const val MIN_ANALYSIS_FRAMES = 5
    private const val MAX_ANALYSIS_FRAMES = 40
    private const val REQUIRED_OFF_FRAMES = 2

    private const val INFERENCE_INTERVAL_MS = 60L

    // Logs onset/frame profile + hold value around every non-target note that passes the onset/frame
    // thresholds (for tuning / comparing voice vs piano).
    private const val DEBUG_PROFILES = false

    private var isInitialized = false
    @Volatile private var isRunning = false
    private var thread: Thread? = null
    private var interpreter: Interpreter? = null

    // Dynamic output mapping
    private var noteIndex = -1
    private var onsetIndex = -1

    @Volatile var suppressedPitches: Set<Int> = emptySet()
    @Volatile var targetPitches: Set<Int> = emptySet()
    private val activePitches = mutableSetOf<Int>()
    private val noteOffConfidence = IntArray(128)

    // Per-pitch scratch for the two-pass processOutputs (avoids per-run allocation).
    private val candOnset = FloatArray(128)
    private val candFrame = FloatArray(128)        // note prob at the onset frame
    private val candHold = FloatArray(128)         // note prob HOLD_FRAMES after the onset frame
    private val candFrameLatest = FloatArray(128)  // note prob at the newest frame (for note-off)
    private val bestFrame = IntArray(128)

    /**
     * One-time setup of the model and native engine.
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        try {
            Log.i(TAG, "Initializing model...")
            val modelBuffer = loadModelFile(context, MODEL_NAME)

            // CPU with XNNPACK and 4 threads is explicitly chosen over GPU Delegate
            // for this 72MB LSTM model to avoid GPU graph partitioning and fallback penalties.
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseXNNPACK(true)
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.i(TAG, "Interpreter initialized with CPU XNNPACK (4 threads).")

            val interp = interpreter ?: return

            // Safely discover input tensors
            for (i in 0 until interp.inputTensorCount) {
                val tensor = interp.getInputTensor(i)
                val name = tensor.name() ?: ""
                val shapeStr = tensor.shape().joinToString("x")
                Log.i(TAG, "Input Tensor $i: name='$name', shape=[$shapeStr]")
            }

            // Discover output indices. Match by name first; velocity/offset heads are never used
            // (they also have an [1x32x88] shape and must not be picked by the shape fallback).
            noteIndex = -1
            onsetIndex = -1
            val shapeFallback = mutableListOf<Int>()
            for (i in 0 until interp.outputTensorCount) {
                val tensor = interp.getOutputTensor(i)
                val name = tensor.name() ?: ""
                val lowerName = name.lowercase()
                val shape = tensor.shape()
                Log.i(TAG, "Output Tensor $i: name='$name', shape=[${shape.joinToString("x")}]")

                when {
                    "velocity" in lowerName || "offset" in lowerName -> Unit
                    "onset" in lowerName -> onsetIndex = i
                    "frame" in lowerName || "note" in lowerName -> noteIndex = i
                    shape.isNotEmpty() && shape.last() == MIDI_KEYS -> shapeFallback.add(i)
                }
            }
            if (noteIndex == -1 && shapeFallback.isNotEmpty()) noteIndex = shapeFallback.removeAt(0)
            if (onsetIndex == -1 && shapeFallback.isNotEmpty()) onsetIndex = shapeFallback.removeAt(0)
            Log.i(TAG, "Mapped Indices -> Frame/Note: $noteIndex, Onset: $onsetIndex")

            // Optional voice gate; failure here never blocks note detection.
            VoiceActivityGate.initialize(context)

            if (NativeAudioEngine.initialize()) {
                isInitialized = true
                Log.i(TAG, "AcousticNoteDetector initialized successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical: Onset & Frames setup failed", e)
        }
    }

    fun hasMicrophonePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun openAppSettings(context: Context) {
        try {
            val permissionIntent = Intent("android.intent.action.MANAGE_APP_PERMISSIONS").apply {
                putExtra("android.intent.extra.PACKAGE_NAME", context.packageName)
                putExtra("extra_pkg_name", context.packageName)
                putExtra("android.intent.extra.PERMISSION_GROUP", "android.permission-group.MICROPHONE")
                putExtra("android.intent.extra.PERMISSION_NAME", Manifest.permission.RECORD_AUDIO)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(permissionIntent)
        } catch (e: Exception) {
            try {
                val detailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(detailsIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open app settings", e2)
            }
        }
    }

    @Synchronized
    fun start(context: Context): Boolean {
        if (!isInitialized) initialize(context)
        if (!isInitialized) return false

        if (MidiInputManager.isMidiDeviceConnected()) return false

        if (!hasMicrophonePermission(context)) {
            Log.w(TAG, "Microphone permission not granted.")
            return false
        }

        if (isRunning) {
            stop()
        }

        if (!NativeAudioEngine.startCapture()) {
            Log.e(TAG, "Failed to start audio capture in start()")
            return false
        }

        VoiceActivityGate.reset()
        isRunning = true
        thread = Thread {
            try {
                runInferenceLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Inference loop crashed", e)
            } finally {
                isRunning = false
            }
        }.apply {
            name = "AcousticThread"
            priority = Thread.NORM_PRIORITY + 1
            start()
        }
        return true
    }

    private fun runInferenceLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

        val interp = interpreter ?: return
        val inputSamples = interp.getInputTensor(0).shape().lastOrNull()?.takeIf { it > 0 } ?: INPUT_SAMPLES
        val noteShape = noteIndex.takeIf { it >= 0 }?.let { interp.getOutputTensor(it).shape() } ?: intArrayOf()
        val outputFrames = noteShape.getOrNull(noteShape.size - 2) ?: OUTPUT_FRAMES
        val midiKeys = noteShape.lastOrNull() ?: MIDI_KEYS

        Log.i(TAG, "Config: input=$inputSamples, output=$outputFrames, keys=$midiKeys")

        val input = ByteBuffer.allocateDirect(inputSamples * 4).order(ByteOrder.nativeOrder())
        val inputs = arrayOf<Any>(input)
        val notes = Array(1) { Array(outputFrames) { FloatArray(midiKeys) } }
        val onsets = Array(1) { Array(outputFrames) { FloatArray(midiKeys) } }
        val outputs = buildMap {
            if (noteIndex >= 0) put(noteIndex, notes)
            if (onsetIndex >= 0) put(onsetIndex, onsets)
        }

        val step = 44100f / 16000f
        val nativeSamples = (inputSamples * step).toInt() + 2
        // Model samples (16 kHz) per output frame (560 = 35 ms for the 17920 / 32 model).
        val samplesPerFrame = inputSamples.toFloat() / outputFrames
        val maxWindow = min(MAX_ANALYSIS_FRAMES, outputFrames)
        val capture = ByteBuffer.allocateDirect(nativeSamples * 4).order(ByteOrder.nativeOrder())
        val samples = FloatArray(nativeSamples)
        var readIndex = NativeAudioEngine.getAvailableFrames().coerceAtLeast(nativeSamples.toLong()) - nativeSamples

        try {
            while (isRunning) {
                if (MidiInputManager.isMidiDeviceConnected()) break

                val writeIndex = NativeAudioEngine.getAvailableFrames()
                val newFrames = (writeIndex - readIndex).toInt()
                if (newFrames <= 0) {
                    Thread.sleep(10)
                    continue
                }

                val count = newFrames.coerceAtMost(nativeSamples)
                val start = writeIndex - count
                if (count < nativeSamples) System.arraycopy(samples, count, samples, 0, nativeSamples - count)

                capture.rewind()
                if (NativeAudioEngine.copyLatest(capture, count, start) != count) continue
                capture.rewind()
                capture.asFloatBuffer().get(samples, nativeSamples - count, count)
                readIndex = writeIndex

                // Keeps VoiceActivityGate.evidence up to date (cheap). Not used for note decisions
                // while voiceWeight is disabled in processOutputs; comment out to save the CPU.
                VoiceActivityGate.feed(samples, count, step, contiguous = newFrames <= nativeSamples)

                // Analysis window = audio received since the previous run (this includes the time
                // spent in inference and sleep), converted to output frames (+1 frame of margin).
                val newModelSamples = count / step
                val windowFrames = (ceil(newModelSamples / samplesPerFrame).toInt() + 1)
                    .coerceIn(min(MIN_ANALYSIS_FRAMES, maxWindow), maxWindow)

                var peak = 0.0001f
                for (sample in samples) peak = max(peak, abs(sample))
                val gain = if (peak < 0.005f) 0f else min(3f, 0.5f / peak)

                input.rewind()
                for (i in 0 until inputSamples) {
                    val pos = i * step
                    val i0 = pos.toInt()
                    val frac = pos - i0
                    input.putFloat((samples[i0] * (1f - frac) + samples[min(i0 + 1, nativeSamples - 1)] * frac) * gain)
                }

                interp.runForMultipleInputsOutputs(inputs, outputs)
                processOutputs(notes[0], onsets[0], outputFrames, midiKeys, windowFrames)
                Thread.sleep(INFERENCE_INTERVAL_MS)
            }
        } catch (ie: InterruptedException) {
            Log.d(TAG, "Inference loop interrupt", ie)
        } catch (e: Exception) {
            Log.e(TAG, "Inference loop crashed", e)
        }
    }

    private fun sigmoid(x: Float): Float {
        return 1.0f / (1.0f + exp(-x))
    }

    private fun midiPitchModifier(pitch: Int): Float {
        val x = max(0f, ((pitch - 79f) / 29f).coerceIn(0f, 1f))
        return 0.11f * (0.667f + 0.333f * x).pow(8)
    }

    private fun processOutputs(
        notePosteriors: Array<FloatArray>,
        onsetPosteriors: Array<FloatArray>,
        outputFrames: Int,
        midiKeys: Int,
        windowFrames: Int
    ) {
        val startFrame = max(0, outputFrames - windowFrames)
        val latestFrame = max(0, outputFrames - 1)
        val suppressed = suppressedPitches
        val targets = targetPitches

        // Pass 1: per pitch, the best onset in the window and the note probabilities around it.
        // Targets search the newest window (immediate). Non-targets search the window shifted back by
        // HOLD_FRAMES, so the frames after the onset peak are already available for the hold check.
        for (p in 0 until midiKeys) {
            val midiPitch = p + MIDI_OFFSET
            if (midiPitch in suppressed) {
                candOnset[p] = 0f
                candFrame[p] = 0f
                candHold[p] = 0f
                candFrameLatest[p] = 0f
                continue
            }

            val delay = if (midiPitch in targets) 0 else HOLD_FRAMES
            val hi = max(0, latestFrame - delay)
            val lo = min(max(0, startFrame - delay), hi)

            var onsetProb = 0f
            var best = hi
            for (frame in lo..hi) {
                val prob = sigmoid(onsetPosteriors[frame][p])
                if (prob > onsetProb) {
                    onsetProb = prob
                    best = frame
                }
            }

            bestFrame[p] = best
            candOnset[p] = onsetProb
            candFrame[p] = sigmoid(notePosteriors[best][p])
            candHold[p] = sigmoid(notePosteriors[min(best + delay, latestFrame)][p])
            candFrameLatest[p] = sigmoid(notePosteriors[latestFrame][p])
        }

        val voiceWeight = VoiceActivityGate.evidence

        // Pass 2: thresholds, hold check and note on/off.
        for (p in 0 until midiKeys) {
            val midiPitch = p + MIDI_OFFSET
            if (midiPitch in suppressed) {
                if (activePitches.remove(midiPitch)) {
                    MidiInputManager.simulateExternalNoteOff(midiPitch)
                }
                continue
            }

            val isTarget = midiPitch in targets
            val onsetProb = candOnset[p]
            val frameProb = candFrame[p]

            val onsetThreshold = ONSET_THRESHOLD_BASE + if (isTarget) {
                -0.25f - midiPitchModifier(midiPitch)
            } else {
                NON_TARGET_ONSET_PENALTY
            }
            val frameThreshold = FRAME_THRESHOLD_BASE + if (isTarget) {
                -0.20f - midiPitchModifier(midiPitch) / 2
            } else {
                NON_TARGET_FRAME_PENALTY
            }

            val isActive = midiPitch in activePitches
            if (!isActive) {
                val holdThreshold = if (isTarget) {
                    TARGET_HOLD_MIN_FRAME_PROB + TARGET_HOLD_VOICE_EXTRA * voiceWeight
                } else {
                    NON_TARGET_HOLD_MIN_FRAME_PROB + NON_TARGET_HOLD_VOICE_EXTRA * voiceWeight
                }
                val held = candHold[p] >= holdThreshold
                val detected = onsetProb >= onsetThreshold && frameProb >= frameThreshold && held

                if (detected) {
                    MidiInputManager.simulateExternalNoteOn(midiPitch)
                    activePitches.add(midiPitch)
                    noteOffConfidence[midiPitch] = 0
                }
            } else {
                val targetRedetected = isTarget
                        && onsetProb >= ONSET_THRESHOLD_BASE + 0.12f
                        && frameProb >= FRAME_THRESHOLD_BASE + 0.1f
                if (targetRedetected) MidiInputManager.simulateExternalNoteOn(midiPitch)

                // Non-targets are searched in a shifted window, so judge note-off on the newest frame.
                val offFrameProb = if (isTarget) frameProb else candFrameLatest[p]
                if (offFrameProb >= frameThreshold) {
                    noteOffConfidence[midiPitch] = 0
                } else {
                    noteOffConfidence[midiPitch]++
                    if (noteOffConfidence[midiPitch] >= REQUIRED_OFF_FRAMES) {
                        MidiInputManager.simulateExternalNoteOff(midiPitch)
                        activePitches.remove(midiPitch)
                    }
                }
            }
        }
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.length
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    @Synchronized
    fun stop() {
        isRunning = false
        try {
            NativeAudioEngine.stopCapture()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping capture", e)
        }
        thread?.let {
            it.interrupt()
            try {
                it.join(500)
            } catch (_: InterruptedException) {}
        }
        thread = null
        activePitches.forEach { MidiInputManager.simulateExternalNoteOff(it) }
        activePitches.clear()
    }

    fun cleanup() {
        stop()
        interpreter?.close()
        interpreter = null
        VoiceActivityGate.cleanup()
        NativeAudioEngine.cleanup()
        isInitialized = false
    }
}