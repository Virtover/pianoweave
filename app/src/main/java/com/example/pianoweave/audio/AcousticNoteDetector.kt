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
import kotlin.math.*

/**
 * High-precision real-time piano note detector using Spotify's Basic Pitch.
 */
object AcousticNoteDetector {
    private const val TAG = "AcousticNoteDetector"
    private const val MODEL_NAME = "basic_pitch.tflite"
    
    // Model Constraints
    private const val INPUT_SAMPLES = 43844 
    private const val MIDI_KEYS = 88
    private const val MIDI_OFFSET = 21
    private const val OUTPUT_FRAMES = 172
    private const val CONTOUR_BINS = 264

    private var isInitialized = false
    private var isRunning = false
    private var thread: Thread? = null
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    // Dynamic output mapping
    private var contourIndex = -1
    private var noteIndex = -1
    private var onsetIndex = -1

    @Volatile var suppressedPitches: Set<Int> = emptySet()
    private val suppressionMap = mutableMapOf<Int, Long>()
    private const val ECHO_WINDOW_MS = 400L

    private val activePitches = mutableSetOf<Int>()
    private val noteOffConfidence = IntArray(128)
    private const val REQUIRED_OFF_FRAMES = 2 // Faster note-off response

    /**
     * One-time setup of the model and native engine.
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        try {
            Log.i(TAG, "Initializing Basic Pitch model...")
            val modelBuffer = loadModelFile(context, MODEL_NAME)

            // Try initializing with GPU
            try {
                val options = Interpreter.Options()
                gpuDelegate = GpuDelegate()
                options.addDelegate(gpuDelegate)
                interpreter = Interpreter(modelBuffer, options)
                Log.i(TAG, "Interpreter initialized with GPU delegate.")
            } catch (e: Exception) {
                Log.w(TAG, "GPU initialization OR application failed, falling back to CPU", e)
                gpuDelegate?.close()
                gpuDelegate = null
                
                val options = Interpreter.Options()
                options.setNumThreads(4)
                interpreter = Interpreter(modelBuffer, options)
                Log.i(TAG, "Interpreter initialized with CPU (4 threads).")
            }
            
            val interp = interpreter ?: return

            // Discover output indices based on tensor shapes
            for (i in 0 until interp.outputTensorCount) {
                val shape = interp.getOutputTensor(i).shape()
                Log.i(TAG, "Output $i shape: ${shape.contentToString()}")
                val lastDim = shape.last()
                when (lastDim) {
                    CONTOUR_BINS -> contourIndex = i
                    MIDI_KEYS -> {
                        // In Basic Pitch TFLite exports:
                        // Output 1 is usually Note posteriors
                        // Output 2 is usually Onset posteriors
                        if (noteIndex == -1) noteIndex = i else onsetIndex = i
                    }
                }
            }
            Log.i(TAG, "Mapped Indices -> Contour: $contourIndex, Note: $noteIndex, Onset: $onsetIndex")

            if (NativeAudioEngine.initialize()) {
                isInitialized = true
                Log.i(TAG, "AcousticNoteDetector initialized successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical: Basic Pitch setup failed", e)
        }
    }

    fun start(context: Context) {
        if (!isInitialized) initialize(context)
        if (!isInitialized || isRunning) return
        
        if (MidiInputManager.isMidiDeviceConnected()) return
        
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Permission RECORD_AUDIO missing.")
            return
        }

        if (!NativeAudioEngine.startCapture()) {
            Log.e(TAG, "Failed to start capture.")
            return
        }

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
            name = "BasicPitchThread"
            priority = Thread.MAX_PRIORITY
            start() 
        }
        Log.i(TAG, "Acoustic detection started")
    }

    private fun runInferenceLoop() {
        val inputBuffer = ByteBuffer.allocateDirect(INPUT_SAMPLES * 4).order(ByteOrder.nativeOrder())
        
        val contourOutput = Array(1) { Array(OUTPUT_FRAMES) { FloatArray(CONTOUR_BINS) } }
        val noteOutput = Array(1) { Array(OUTPUT_FRAMES) { FloatArray(MIDI_KEYS) } }
        val onsetOutput = Array(1) { Array(OUTPUT_FRAMES) { FloatArray(MIDI_KEYS) } }
        
        val outputs = mutableMapOf<Int, Any>()
        if (contourIndex != -1) outputs[contourIndex] = contourOutput
        if (noteIndex != -1) outputs[noteIndex] = noteOutput
        if (onsetIndex != -1) outputs[onsetIndex] = onsetOutput

        val captureSamples = INPUT_SAMPLES * 2
        val captureBuffer = ByteBuffer.allocateDirect(captureSamples * 4).order(ByteOrder.nativeOrder())
        val floatData = FloatArray(captureSamples)

        while (isRunning) {
            if (MidiInputManager.isMidiDeviceConnected()) break

            val available = NativeAudioEngine.getAvailableFrames()
            if (available < captureSamples) {
                Thread.sleep(10)
                continue
            }

            // Copy data from native bridge
            captureBuffer.rewind()
            NativeAudioEngine.copyLatest(captureBuffer, captureSamples)
            captureBuffer.rewind()
            captureBuffer.asFloatBuffer().get(floatData)

            // Normalize/Gain Boost: Basic Pitch can be sensitive to low volume
            var maxVal = 0f
            for (s in floatData) if (abs(s) > maxVal) maxVal = abs(s)
            val gain = if (maxVal > 0.001f) min(2.0f, 0.5f / maxVal) else 1.0f

            // Resample: Decimate 44.1kHz -> 22.05kHz
            inputBuffer.rewind()
            for (i in 0 until INPUT_SAMPLES) {
                inputBuffer.putFloat(floatData[i * 2] * gain)
            }

            interpreter?.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
            
            // Process outputs
            processOutputs(noteOutput[0], onsetOutput[0])
            
            Thread.sleep(40) // Increased frequency for better real-time response
        }
    }

    private fun processOutputs(notePosteriors: Array<FloatArray>, onsetPosteriors: Array<FloatArray>) {
        val now = System.currentTimeMillis()
        val suppressed = suppressedPitches
        suppressed.forEach { suppressionMap[it] = now + ECHO_WINDOW_MS }

        // Look at the trailing window for current state. 
        // 15 frames = ~174ms. This ensures we don't miss sharp onsets between inferences.
        val framesToAnalyze = 15
        val startFrame = max(0, OUTPUT_FRAMES - framesToAnalyze)
        
        for (p in 0 until MIDI_KEYS) {
            val midiPitch = p + MIDI_OFFSET
            
            // Check echo cancellation
            if ((suppressionMap[midiPitch] ?: 0L) > now) {
                if (activePitches.remove(midiPitch)) MidiInputManager.simulateExternalNoteOff(midiPitch)
                continue
            }

            var peakOnset = 0f
            var peakNote = 0f
            var avgNote = 0f
            
            for (f in startFrame until OUTPUT_FRAMES) {
                peakOnset = max(peakOnset, onsetPosteriors[f][p])
                peakNote = max(peakNote, notePosteriors[f][p])
                avgNote += notePosteriors[f][p]
            }
            avgNote /= framesToAnalyze

            // Optimized thresholds for sensitivity
            // Onset is very sharp, Note is more persistent.
            if (peakOnset > 0.45f && peakNote > 0.35f) {
                if (midiPitch !in activePitches) {
                    MidiInputManager.simulateExternalNoteOn(midiPitch)
                    activePitches.add(midiPitch)
                }
                noteOffConfidence[midiPitch] = 0
            } else if (peakNote < 0.25f && avgNote < 0.20f && midiPitch in activePitches) {
                noteOffConfidence[midiPitch]++
                if (noteOffConfidence[midiPitch] >= REQUIRED_OFF_FRAMES) {
                    MidiInputManager.simulateExternalNoteOff(midiPitch)
                    activePitches.remove(midiPitch)
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

    /**
     * Toggles microphone capture off, but keeps the model loaded.
     */
    fun stop() {
        isRunning = false
        NativeAudioEngine.stopCapture()
        thread?.interrupt()
        thread = null
        
        activePitches.forEach { MidiInputManager.simulateExternalNoteOff(it) }
        activePitches.clear()
        suppressionMap.clear()
        Log.i(TAG, "Acoustic detection stopped")
    }

    /**
     * Completely releases resources.
     */
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
