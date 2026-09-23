package com.example.pianoweave.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.example.pianoweave.midi.MidiInputManager
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object AcousticNoteDetector {
    private const val TAG = "AcousticNoteDetector"
    private const val MODEL_NAME = "onsets_frames_wavinput_no_offset_uni.tflite"
    
    // Model Constraints
    private const val INPUT_SAMPLES = 43844 
    private const val MIDI_KEYS = 88
    private const val MIDI_OFFSET = 21
    private const val OUTPUT_FRAMES = 172

    // Minimal Onset & Frames Thresholds
    private const val ONSET_THRESHOLD_BASE = 0.50f
    private const val FRAME_THRESHOLD_BASE = 0.35f
    private const val ANALYSIS_FRAMES = 5
    private const val REQUIRED_OFF_FRAMES = 2

    private const val INFERENCE_INTERVAL_MS = 60L

    private var isInitialized = false
    private var isRunning = false
    private var thread: Thread? = null
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    // Dynamic output mapping
    private var noteIndex = -1
    private var onsetIndex = -1

    @Volatile var suppressedPitches: Set<Int> = emptySet()
    @Volatile var targetPitches: Set<Int> = emptySet()
    private val activePitches = mutableSetOf<Int>()
    private val pendingPitches = mutableMapOf<Int, Int>()
    private val noteOffConfidence = IntArray(128)

    /**
     * One-time setup of the model and native engine.
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        try {
            Log.i(TAG, "Initializing model...")
            val modelBuffer = loadModelFile(context, MODEL_NAME)

            try {
                val options = Interpreter.Options()
                gpuDelegate = GpuDelegate()
                options.addDelegate(gpuDelegate)
                interpreter = Interpreter(modelBuffer, options)
                Log.i(TAG, "Interpreter initialized with GPU delegate.")
            } catch (e: Exception) {
                Log.w(TAG, "GPU initialization failed, falling back to CPU", e)
                gpuDelegate?.close()
                gpuDelegate = null
                
                val options = Interpreter.Options()
                options.setNumThreads(4)
                interpreter = Interpreter(modelBuffer, options)
                Log.i(TAG, "Interpreter initialized with CPU (4 threads).")
            }
            
            val interp = interpreter ?: return

            // Safely discover input tensors
            for (i in 0 until interp.inputTensorCount) {
                val tensor = interp.getInputTensor(i)
                val name = tensor.name() ?: ""
                val shapeStr = tensor.shape().joinToString("x")
                Log.i(TAG, "Input Tensor $i: name='$name', shape=[$shapeStr]")
            }

            // Safely discover output indices based on tensor names and shapes
            for (i in 0 until interp.outputTensorCount) {
                val tensor = interp.getOutputTensor(i)
                val name = tensor.name() ?: ""
                val shape = tensor.shape()
                val shapeStr = shape.joinToString("x")
                Log.i(TAG, "Output Tensor $i: name='$name', shape=[$shapeStr]")

                if (name.contains("onset", ignoreCase = true)) {
                    onsetIndex = i
                } else if (name.contains("frame", ignoreCase = true) || name.contains("note", ignoreCase = true)) {
                    noteIndex = i
                } else if (shape.isNotEmpty() && shape.last() == MIDI_KEYS) {
                    if (noteIndex == -1) noteIndex = i else onsetIndex = i
                }
            }
            Log.i(TAG, "Mapped Indices -> Frame/Note: $noteIndex, Onset: $onsetIndex")

            if (NativeAudioEngine.initialize()) {
                isInitialized = true
                Log.i(TAG, "AcousticNoteDetector initialized successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical: Onset & Frames setup failed", e)
        }
    }

    fun start(context: Context) {
        if (!isInitialized) initialize(context)
        if (!isInitialized || isRunning) return
        
        if (MidiInputManager.isMidiDeviceConnected()) return
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return

        if (!NativeAudioEngine.startCapture()) return

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
    }

    private fun runInferenceLoop() {
        val interp = interpreter ?: return
        val inputSamples = interp.getInputTensor(0).shape().lastOrNull()?.takeIf { it > 0 } ?: INPUT_SAMPLES
        val noteShape = noteIndex.takeIf { it >= 0 }?.let { interp.getOutputTensor(it).shape() } ?: intArrayOf()
        val outputFrames = noteShape.getOrNull(noteShape.size - 2) ?: OUTPUT_FRAMES
        val midiKeys = noteShape.lastOrNull() ?: MIDI_KEYS

        Log.i(TAG, "Config: input=$inputSamples, output=$outputFrames, keys=$midiKeys")

        val input = ByteBuffer.allocateDirect(inputSamples * 4).order(ByteOrder.nativeOrder())
        val notes = Array(1) { Array(outputFrames) { FloatArray(midiKeys) } }
        val onsets = Array(1) { Array(outputFrames) { FloatArray(midiKeys) } }
        val outputs = buildMap {
            if (noteIndex >= 0) put(noteIndex, notes)
            if (onsetIndex >= 0) put(onsetIndex, onsets)
        }

        val step = 44100f / 16000f
        val nativeSamples = (inputSamples * step).toInt() + 2
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

                interp.runForMultipleInputsOutputs(arrayOf(input), outputs)
                processOutputs(notes[0], onsets[0], outputFrames, midiKeys)
                Thread.sleep(INFERENCE_INTERVAL_MS)
            }
        } catch (ie: InterruptedException) {
            Log.d(TAG, "Inference loop interrupt", ie)
        } catch (e: Exception) {
            Log.e(TAG, "Inference loop crashed", e)
        }
    }

    private fun sigmoid(x: Float): Float {
        return 1.0f / (1.0f + kotlin.math.exp(-x))
    }

    private fun midiPitchModifier(pitch: Int): Float {
        val x = max(0f, ((pitch - 79f) / 29f).coerceIn(0f, 1f))
        return 0.11f * (0.667f + 0.333f * x).pow(8)
    }

    private fun processOutputs(
        notePosteriors: Array<FloatArray>,
        onsetPosteriors: Array<FloatArray>,
        outputFrames: Int,
        midiKeys: Int
    ) {
        val startFrame = max(0, outputFrames - ANALYSIS_FRAMES)
        val latestFrame = max(0, outputFrames - 1)
        val suppressed = suppressedPitches

        for (p in 0 until midiKeys) {
            val midiPitch = p + MIDI_OFFSET
            if (midiPitch in suppressed) {
                if (activePitches.remove(midiPitch)) {
                    MidiInputManager.simulateExternalNoteOff(midiPitch)
                }
                continue
            }

            val isTarget = midiPitch in targetPitches

            val onsetThreshold = ONSET_THRESHOLD_BASE + if (isTarget) -0.25f - midiPitchModifier(midiPitch) else 0.12f
            val frameThreshold = FRAME_THRESHOLD_BASE + if (isTarget) -0.20f - midiPitchModifier(midiPitch) / 2 else 0.10f

            val isActive = midiPitch in activePitches

            var onsetProb = 0f
            var onsetFrame = latestFrame
            if (isTarget) {
                for (frame in startFrame..latestFrame) {
                    val prob = sigmoid(onsetPosteriors[frame][p])
                    if (prob > onsetProb) {
                        onsetProb = prob
                        onsetFrame = frame
                    }
                }
            } else {
                onsetProb = sigmoid(onsetPosteriors[latestFrame][p])
            }
            val frameProb = sigmoid(notePosteriors[onsetFrame][p])

            if (!isActive) {
                val detected = onsetProb >= onsetThreshold && frameProb >= frameThreshold
                if (detected) {
                    val count = (pendingPitches[midiPitch] ?: 0) + 1
                    pendingPitches[midiPitch] = count

                    if (count >= 1) {
                        MidiInputManager.simulateExternalNoteOn(midiPitch)
                        activePitches.add(midiPitch)
                        noteOffConfidence[midiPitch] = 0
                        pendingPitches.remove(midiPitch)
                    }
                } else {
                    pendingPitches.remove(midiPitch)
                }
            } else {
                // Sustain or note-off
                if (frameProb >= frameThreshold) {
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

    fun stop() {
        isRunning = false
        NativeAudioEngine.stopCapture()
        thread?.interrupt()
        thread = null
        activePitches.forEach { MidiInputManager.simulateExternalNoteOff(it) }
        activePitches.clear()
        pendingPitches.clear()
    }

    fun cleanup() {
        stop()
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
        NativeAudioEngine.cleanup()
        isInitialized = false
    }
}
