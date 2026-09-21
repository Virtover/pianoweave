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
 * State-of-the-Art real-time piano note detector using Spotify's Basic Pitch.
 */
object AcousticNoteDetector {
    private const val TAG = "AcousticNoteDetector"
    private const val MODEL_NAME = "basic_pitch.tflite"
    
    // Model Constraints
    private const val INPUT_SAMPLES = 43844 
    private const val MIDI_KEYS = 88
    private const val MIDI_OFFSET = 21
    private const val OUTPUT_FRAMES = 172

    private var isInitialized = false
    private var isRunning = false
    private var thread: Thread? = null
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    @Volatile var suppressedPitches: Set<Int> = emptySet()
    private val suppressionMap = mutableMapOf<Int, Long>()
    private const val ECHO_WINDOW_MS = 400L

    private val activePitches = mutableSetOf<Int>()
    private val noteOffConfidence = IntArray(128)
    private const val REQUIRED_OFF_FRAMES = 3

    /**
     * One-time setup of the model and native engine.
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        try {
            Log.i(TAG, "Initializing Basic Pitch...")
            val options = Interpreter.Options()
            try {
                gpuDelegate = GpuDelegate()
                options.addDelegate(gpuDelegate)
                Log.i(TAG, "GPU delegate enabled")
            } catch (e: Exception) {
                Log.w(TAG, "GPU unavailable, using multithreaded CPU")
                options.setNumThreads(4)
            }
            
            val modelBuffer = loadModelFile(context, MODEL_NAME)
            interpreter = Interpreter(modelBuffer, options)
            
            if (NativeAudioEngine.initialize()) {
                isInitialized = true
                Log.i(TAG, "AcousticNoteDetector initialized successfully")
            } else {
                Log.e(TAG, "NativeAudioEngine initialization failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical: Basic Pitch initialization failed", e)
        }
    }

    fun start(context: Context) {
        if (!isInitialized) initialize(context)
        if (!isInitialized || isRunning) return
        
        if (MidiInputManager.isMidiDeviceConnected()) return
        
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO permission missing")
            return
        }

        if (!NativeAudioEngine.startCapture()) {
            Log.e(TAG, "Failed to start native capture")
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
        
        // Basic Pitch Outputs: 0: Contour, 1: Note, 2: Onset
        val noteOutput = Array(1) { Array(OUTPUT_FRAMES) { FloatArray(MIDI_KEYS) } }
        val onsetOutput = Array(1) { Array(OUTPUT_FRAMES) { FloatArray(MIDI_KEYS) } }
        val outputs = mapOf(1 to noteOutput, 2 to onsetOutput)

        val captureSamples = INPUT_SAMPLES * 2
        val captureBuffer = ByteBuffer.allocateDirect(captureSamples * 4).order(ByteOrder.nativeOrder())
        val floatData = FloatArray(captureSamples)

        while (isRunning) {
            if (MidiInputManager.isMidiDeviceConnected()) break

            val available = NativeAudioEngine.getAvailableFrames()
            if (available < captureSamples) {
                Thread.sleep(15)
                continue
            }

            captureBuffer.rewind()
            NativeAudioEngine.copyLatest(captureBuffer, captureSamples)
            captureBuffer.rewind()
            captureBuffer.asFloatBuffer().get(floatData)

            // Resample: Decimate 44.1kHz -> 22.05kHz
            inputBuffer.rewind()
            for (i in 0 until INPUT_SAMPLES) {
                inputBuffer.putFloat(floatData[i * 2])
            }

            interpreter?.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
            processOutputs(noteOutput[0], onsetOutput[0])
            
            Thread.sleep(60) 
        }
    }

    private fun processOutputs(notePosteriors: Array<FloatArray>, onsetPosteriors: Array<FloatArray>) {
        val now = System.currentTimeMillis()
        val suppressed = suppressedPitches
        suppressed.forEach { suppressionMap[it] = now + ECHO_WINDOW_MS }

        val framesToAnalyze = 8
        
        for (p in 0 until MIDI_KEYS) {
            val midiPitch = p + MIDI_OFFSET
            if ((suppressionMap[midiPitch] ?: 0L) > now) {
                if (activePitches.remove(midiPitch)) MidiInputManager.simulateExternalNoteOff(midiPitch)
                continue
            }

            var peakOnset = 0f
            var avgNoteProb = 0f
            for (f in (OUTPUT_FRAMES - framesToAnalyze) until OUTPUT_FRAMES) {
                peakOnset = max(peakOnset, onsetPosteriors[f][p])
                avgNoteProb += notePosteriors[f][p]
            }
            avgNoteProb /= framesToAnalyze

            if (peakOnset > 0.50f && avgNoteProb > 0.30f) {
                if (midiPitch !in activePitches) {
                    MidiInputManager.simulateExternalNoteOn(midiPitch)
                    activePitches.add(midiPitch)
                }
                noteOffConfidence[midiPitch] = 0
            } else if (avgNoteProb < 0.20f && midiPitch in activePitches) {
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
     * Completely releases resources (call on app exit if needed).
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
