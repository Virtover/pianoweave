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
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
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

    // --- Input pipeline ---
    // Capture rate requested from AAudio (native rate on almost every device); the real rate is read
    // back from the stream. Audio is downsampled to the model rate with an anti-alias filter
    // (StreamResampler) and level-controlled (InputLevelControl) once, as it arrives.
    private const val CAPTURE_SAMPLE_RATE = 48000
    private const val MODEL_SAMPLE_RATE = 16000
    // Must match RING_BUFFER_SIZE in native-audio-bridge.cpp.
    private const val NATIVE_RING_FRAMES = 131072L
    // Max native frames copied per read (~340 ms at 48 kHz); larger backlogs are read in a loop.
    private const val READ_CHUNK_FRAMES = 16384

    // Logs onset/frame profile + hold value around every non-target note that passes the onset/frame
    // thresholds (for tuning / comparing voice vs piano).
    private const val DEBUG_PROFILES = false

    @Volatile private var isEngineReady = false
    @Volatile private var isRunning = false
    private var thread: Thread? = null
    @Volatile private var interpreter: Interpreter? = null

    // The capture has two independent users: note detection (wait mode) and voice commands. It runs
    // while either wants it, and the expensive note model only runs while note detection does.
    @Volatile private var notesWanted = false
    @Volatile private var voiceWanted = false
    // Bumped on every note detection start, so the loop starts it from fresh audio (no stale speech)
    @Volatile private var notesGeneration = 0

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
    private val onsetTracker = OnsetTracker()

    /** One-time setup of the capture engine and voice gate, which voice commands need without the note model. */
    @Synchronized
    private fun initializeEngine(context: Context): Boolean {
        if (isEngineReady) return true
        // Optional voice gate; failure here never blocks note detection.
        VoiceActivityGate.initialize(context)
        // The mic stream opens here only if the app already has the microphone; the app no longer asks
        // for it on launch, and a stream opened without it fails (or stays dead after it's granted).
        // Otherwise ensureCapture opens it, after wait mode or voice commands asked for the permission.
        if (hasMicrophonePermission(context)) NativeAudioEngine.initialize()
        isEngineReady = true
        return true
    }

    /**
     * One-time setup of the model and native engine.
     */
    @Synchronized
    fun initialize(context: Context) {
        if (!initializeEngine(context) || interpreter != null) return
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
            Log.i(TAG, "AcousticNoteDetector initialized successfully")
        } catch (e: Exception) {
            interpreter?.close()
            interpreter = null
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

    /** Starts note detection (wait mode through the microphone). False if it can't run. */
    @Synchronized
    fun start(context: Context): Boolean {
        initialize(context)
        if (!isEngineReady || interpreter == null) return false

        if (MidiInputManager.isMidiDeviceConnected()) return false

        if (!hasMicrophonePermission(context)) {
            Log.w(TAG, "Microphone permission not granted.")
            return false
        }

        notesGeneration++
        notesWanted = true
        applyCaptureMode()
        if (!ensureCapture()) {
            notesWanted = false
            return false
        }
        return true
    }

    /**
     * Starts listening for voice commands only: the capture and voice gate run, the note model doesn't.
     * Works with a MIDI keyboard connected too. False if the microphone can't be used.
     */
    @Synchronized
    fun startVoice(context: Context): Boolean {
        if (!hasMicrophonePermission(context) || !initializeEngine(context)) return false
        voiceWanted = true
        applyCaptureMode()
        if (!ensureCapture()) {
            voiceWanted = false
            return false
        }
        return true
    }

    @Synchronized
    fun stopVoice() {
        voiceWanted = false
        if (notesWanted) applyCaptureMode() else shutdownCapture()
    }

    /**
     * Opens the mic the way its users need: with the device's echo canceller for voice commands alone
     * (it takes the app's own song out of what the mic hears), unprocessed whenever note detection runs.
     * Reopens a stream that is the other way; a short gap in the audio, only when wait mode changes.
     */
    private fun applyCaptureMode() {
        if (!NativeAudioEngine.setVoiceMode(voiceWanted && !notesWanted)) return
        val wasRunning = isRunning
        if (wasRunning) shutdownCapture()
        NativeAudioEngine.cleanup()
        if (wasRunning) ensureCapture()
    }

    /** Starts the capture and its thread unless already running (for the other user). */
    private fun ensureCapture(): Boolean {
        if (isRunning) return true

        if (!NativeAudioEngine.startCapture()) {
            // A stream left in a bad state (opened before the permission, or disconnected) won't start;
            // close it so startCapture opens a fresh one
            Log.w(TAG, "Audio capture didn't start; reopening the stream")
            NativeAudioEngine.cleanup()
            if (!NativeAudioEngine.startCapture()) {
                Log.e(TAG, "Failed to start audio capture")
                return false
            }
        }

        VoiceActivityGate.reset()
        onsetTracker.reset()
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

    /** Note model buffers and its rolling 16 kHz input; built when note detection first runs. */
    private class NotePass(val interp: Interpreter) {
        val inputSamples = interp.getInputTensor(0).shape().lastOrNull()?.takeIf { it > 0 } ?: INPUT_SAMPLES
        private val noteShape = noteIndex.takeIf { it >= 0 }?.let { interp.getOutputTensor(it).shape() } ?: intArrayOf()
        val outputFrames = noteShape.getOrNull(noteShape.size - 2) ?: OUTPUT_FRAMES
        val midiKeys = noteShape.lastOrNull() ?: MIDI_KEYS

        val input: ByteBuffer = ByteBuffer.allocateDirect(inputSamples * 4).order(ByteOrder.nativeOrder())
        val inputs = arrayOf<Any>(input)
        val notes = Array(1) { Array(outputFrames) { FloatArray(midiKeys) } }
        val onsets = Array(1) { Array(outputFrames) { FloatArray(midiKeys) } }
        val outputs = buildMap {
            if (noteIndex >= 0) put(noteIndex, notes)
            if (onsetIndex >= 0) put(onsetIndex, onsets)
        }

        // Model samples (16 kHz) per output frame (560 = 35 ms for the 17920 / 32 model).
        val samplesPerFrame = inputSamples.toFloat() / outputFrames
        val maxWindow = min(MAX_ANALYSIS_FRAMES, outputFrames)
        val inputFloats = input.asFloatBuffer()

        // Level-controlled 16 kHz audio; the model input is always its last inputSamples samples.
        val modelRing = FloatArray(inputSamples)
        var ringPos = 0
        var newModelSamples = 0
        // All model samples received this session; places each output frame in absolute time
        var totalModelSamples = 0L

        init {
            Log.i(TAG, "Config: input=$inputSamples, output=$outputFrames, keys=$midiKeys")
        }

        fun reset() {
            modelRing.fill(0f)
            ringPos = 0
            newModelSamples = 0
        }

        fun push(samples: FloatArray, count: Int) {
            var off = 0
            while (off < count) {
                val n = min(count - off, inputSamples - ringPos)
                System.arraycopy(samples, off, modelRing, ringPos, n)
                ringPos = (ringPos + n) % inputSamples
                off += n
            }
            newModelSamples += count
            totalModelSamples += count
        }
    }

    private fun runInferenceLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

        val captureRate = NativeAudioEngine.getSampleRate().takeIf { it > 0 } ?: CAPTURE_SAMPLE_RATE
        val resampler = StreamResampler(captureRate, MODEL_SAMPLE_RATE)
        val levelControl = InputLevelControl(MODEL_SAMPLE_RATE)
        Log.i(TAG, "Input pipeline: capture=$captureRate Hz -> model=$MODEL_SAMPLE_RATE Hz")

        val capture = ByteBuffer.allocateDirect(READ_CHUNK_FRAMES * 4).order(ByteOrder.nativeOrder())
        val captureFloats = capture.asFloatBuffer()
        val raw = FloatArray(READ_CHUNK_FRAMES)
        val resampled = FloatArray(resampler.maxOutputFor(READ_CHUNK_FRAMES))

        var pass: NotePass? = null
        var seenGeneration = -1

        // Start from "now": older ring content may be stale audio from a previous capture session.
        var readIndex = NativeAudioEngine.getAvailableFrames()

        try {
            while (isRunning) {
                val midiConnected = MidiInputManager.isMidiDeviceConnected()
                if (midiConnected && !voiceWanted) break
                val notesOn = notesWanted && !midiConnected
                if (notesOn) {
                    val interp = interpreter ?: break
                    if (pass?.interp !== interp) pass = NotePass(interp)
                    if (seenGeneration != notesGeneration) {
                        seenGeneration = notesGeneration
                        pass!!.reset()
                        levelControl.reset()
                        onsetTracker.reset()
                    }
                }
                val notePass = if (notesOn) pass else null

                val writeIndex = NativeAudioEngine.getAvailableFrames()
                if (writeIndex - readIndex > NATIVE_RING_FRAMES - READ_CHUNK_FRAMES) {
                    // The native ring overran (thread stalled), audio was lost: restart all stream state.
                    Log.w(TAG, "Audio backlog of ${writeIndex - readIndex} frames, resetting input stream")
                    readIndex = writeIndex
                    resampler.reset()
                    levelControl.reset()
                    VoiceActivityGate.reset()
                    VoiceCommandRecognizer.resetStream()
                    onsetTracker.reset()
                }

                while (readIndex < writeIndex) {
                    val count = min(writeIndex - readIndex, READ_CHUNK_FRAMES.toLong()).toInt()
                    capture.rewind()
                    if (NativeAudioEngine.copyLatest(capture, count, readIndex) != count) break
                    captureFloats.clear()
                    captureFloats.get(raw, 0, count)
                    readIndex += count

                    val produced = resampler.process(raw, 0, count, resampled, 0)
                    // The VAD gets the raw (pre-gain) 16 kHz stream. It raises the hold thresholds
                    // in processOutputs while voice is detected, and its chunks carry voice commands.
                    VoiceActivityGate.feed(resampled, 0, produced)
                    if (notePass != null) {
                        levelControl.process(resampled, 0, produced)
                        notePass.push(resampled, produced)
                    }
                }

                if (notePass == null) {
                    // Voice only: the gate ran above, nothing else to do until more audio arrives
                    Thread.sleep(INFERENCE_INTERVAL_MS)
                    continue
                }

                if (notePass.newModelSamples <= 0) {
                    Thread.sleep(10)
                    continue
                }

                // Analysis window = audio received since the previous run (this includes the time
                // spent in inference and sleep), converted to output frames (+1 frame of margin).
                val windowFrames = (ceil(notePass.newModelSamples / notePass.samplesPerFrame).toInt() + 1)
                    .coerceIn(min(MIN_ANALYSIS_FRAMES, notePass.maxWindow), notePass.maxWindow)
                notePass.newModelSamples = 0

                // Oldest sample first: ring from ringPos to the end, then from 0 to ringPos.
                val inputSamples = notePass.inputSamples
                notePass.inputFloats.clear()
                notePass.inputFloats.put(notePass.modelRing, notePass.ringPos, inputSamples - notePass.ringPos)
                notePass.inputFloats.put(notePass.modelRing, 0, notePass.ringPos)
                notePass.input.rewind()

                notePass.interp.runForMultipleInputsOutputs(notePass.inputs, notePass.outputs)
                // The input is the newest inputSamples samples, so output frame 0 starts here
                val firstAbsFrame = floor((notePass.totalModelSamples - inputSamples) / notePass.samplesPerFrame.toDouble()).toLong()
                // Note detection may have been stopped during inference; its pitches were released then
                if (notesWanted) {
                    processOutputs(
                        notePass.notes[0], notePass.onsets[0], notePass.outputFrames, notePass.midiKeys,
                        windowFrames, firstAbsFrame
                    )
                }
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
        windowFrames: Int,
        firstAbsFrame: Long
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
                val detected = onsetProb >= onsetThreshold && frameProb >= frameThreshold && held &&
                        isNewStrike(onsetPosteriors, p, firstAbsFrame)

                if (detected) {
                    onsetTracker.record(p, firstAbsFrame + bestFrame[p])
                    MidiInputManager.simulateExternalNoteOn(midiPitch)
                    activePitches.add(midiPitch)
                    noteOffConfidence[midiPitch] = 0
                }
            } else {
                // A target struck again while still ringing. Overlapping windows see the same strike
                // several times; only a new onset peak counts, or one strike would satisfy repeated notes.
                val targetRedetected = isTarget
                        && onsetProb >= ONSET_THRESHOLD_BASE + 0.12f
                        && frameProb >= FRAME_THRESHOLD_BASE + 0.1f
                        && isNewStrike(onsetPosteriors, p, firstAbsFrame)
                if (targetRedetected) {
                    onsetTracker.record(p, firstAbsFrame + bestFrame[p])
                    MidiInputManager.simulateExternalNoteOn(midiPitch)
                }

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

    private fun isNewStrike(onsetPosteriors: Array<FloatArray>, p: Int, firstAbsFrame: Long): Boolean =
        onsetTracker.isNewStrike(p, bestFrame[p], firstAbsFrame) { frame -> sigmoid(onsetPosteriors[frame][p]) }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.length
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    @Synchronized
    /** Stops note detection; the capture keeps running while voice commands listen. */
    fun stop() {
        notesWanted = false
        if (voiceWanted) applyCaptureMode() else shutdownCapture()
        activePitches.forEach { MidiInputManager.simulateExternalNoteOff(it) }
        activePitches.clear()
    }

    private fun shutdownCapture() {
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
    }

    @Synchronized
    fun cleanup() {
        voiceWanted = false
        stop()
        interpreter?.close()
        interpreter = null
        VoiceActivityGate.cleanup()
        NativeAudioEngine.cleanup()
        isEngineReady = false
    }
}