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

/**
 * Basic real-time piano note detector using lightweight Onset & Frames architecture.
 */
object AcousticNoteDetector {
    private const val TAG = "AcousticNoteDetector"
    private const val MODEL_NAME = "onsets_frames_wavinput_no_offset_uni.tflite"
    
    // Model Constraints
    private const val INPUT_SAMPLES = 43844 
    private const val MIDI_KEYS = 88
    private const val MIDI_OFFSET = 21
    private const val OUTPUT_FRAMES = 172

    // Minimal Onset & Frames Thresholds
    private const val ONSET_THRESHOLD = 0.50f
    private const val FRAME_THRESHOLD = 0.35f
    private const val ANALYSIS_FRAMES = 3
    private const val REQUIRED_OFF_FRAMES = 2

    private var isInitialized = false
    private var isRunning = false
    private var thread: Thread? = null
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    // Dynamic output mapping
    private var noteIndex = -1
    private var onsetIndex = -1

    @Volatile var suppressedPitches: Set<Int> = emptySet()

    private val activePitches = mutableSetOf<Int>()
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
            priority = Thread.MAX_PRIORITY
            start() 
        }
    }

    private fun runInferenceLoop() {
        val interp = interpreter ?: return

        val inShape = interp.getInputTensor(0).shape()
        val numInputSamples = if (inShape.isNotEmpty() && inShape.last() > 0) inShape.last() else INPUT_SAMPLES

        val noteShape = if (noteIndex != -1) interp.getOutputTensor(noteIndex).shape() else intArrayOf()
        val numOutputFrames = if (noteShape.size >= 2) noteShape[noteShape.size - 2] else OUTPUT_FRAMES
        val numMidiKeys = if (noteShape.isNotEmpty()) noteShape.last() else MIDI_KEYS

        Log.i(TAG, "Running Loop Config -> inputSamples: $numInputSamples, outputFrames: $numOutputFrames, midiKeys: $numMidiKeys")

        val inputBuffer = ByteBuffer.allocateDirect(numInputSamples * 4).order(ByteOrder.nativeOrder())
        
        val noteOutput = Array(1) { Array(numOutputFrames) { FloatArray(numMidiKeys) } }
        val onsetOutput = Array(1) { Array(numOutputFrames) { FloatArray(numMidiKeys) } }
        
        val outputs = mutableMapOf<Int, Any>()
        if (noteIndex != -1) outputs[noteIndex] = noteOutput
        if (onsetIndex != -1) outputs[onsetIndex] = onsetOutput

        // Resampling ratio: AAudio capture (44.1kHz) -> Magenta Onsets & Frames target (16kHz)
        val targetSampleRate = 16000.0f
        val nativeSampleRate = 44100.0f
        val resampleStep = nativeSampleRate / targetSampleRate
        
        val requiredNativeSamples = (numInputSamples * resampleStep).toInt() + 2
        val captureBuffer = ByteBuffer.allocateDirect(requiredNativeSamples * 4).order(ByteOrder.nativeOrder())
        val floatData = FloatArray(requiredNativeSamples)

        while (isRunning) {
            if (MidiInputManager.isMidiDeviceConnected()) break

            val available = NativeAudioEngine.getAvailableFrames()
            if (available < requiredNativeSamples) {
                Thread.sleep(10)
                continue
            }

            // Copy latest PCM samples from native audio engine
            captureBuffer.rewind()
            NativeAudioEngine.copyLatest(captureBuffer, requiredNativeSamples)
            captureBuffer.rewind()
            captureBuffer.asFloatBuffer().get(floatData)

            // Calculate peak for noise gate and soft gain
            var maxPeak = 0.0001f
            for (s in floatData) {
                val a = abs(s)
                if (a > maxPeak) maxPeak = a
            }
            // Mute below noise gate (0.005f), otherwise soft-gain normalize peak to ~0.5
            val gain = if (maxPeak < 0.005f) 0.0f else min(3.0f, 0.5f / maxPeak)

            // Linear interpolation resampling from 44.1kHz -> 16kHz
            inputBuffer.rewind()
            for (i in 0 until numInputSamples) {
                val srcIdx = i * resampleStep
                val i0 = srcIdx.toInt()
                val i1 = min(i0 + 1, requiredNativeSamples - 1)
                val frac = srcIdx - i0
                val rawSample = floatData[i0] * (1.0f - frac) + floatData[i1] * frac
                inputBuffer.putFloat(rawSample * gain)
            }

            interpreter?.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
            processOutputs(noteOutput[0], onsetOutput[0], numOutputFrames, numMidiKeys)
            
            Thread.sleep(30)
        }
    }

    private fun sigmoid(x: Float): Float {
        return 1.0f / (1.0f + kotlin.math.exp(-x))
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

            // Max onset probability over recent frames (converting logits -> probabilities via sigmoid)
            var peakOnset = 0.0f
            for (f in startFrame until outputFrames) {
                val onsetProb = sigmoid(onsetPosteriors[f][p])
                if (onsetProb > peakOnset) peakOnset = onsetProb
            }

            // Latest frame probability
            val frameProb = sigmoid(notePosteriors[latestFrame][p])
            val isActive = midiPitch in activePitches

            if (!isActive) {
                // Onset & Frames activation: require explicit onset + frame support
                if (peakOnset >= ONSET_THRESHOLD && frameProb >= FRAME_THRESHOLD) {
                    MidiInputManager.simulateExternalNoteOn(midiPitch)
                    activePitches.add(midiPitch)
                    noteOffConfidence[midiPitch] = 0
                }
            } else {
                // Sustain or note-off
                if (frameProb >= FRAME_THRESHOLD) {
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
