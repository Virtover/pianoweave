package com.example.ytpiano.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.ytpiano.midi.MidiInputManager
import org.jtransforms.fft.DoubleFFT_1D
import kotlin.math.*

/**
 * Advanced real-time acoustic piano note detector.
 * Uses Harmonic Product Spectrum (HPS) and Confidence Filtering to identify 
 * musical fundamental frequencies while rejecting speech and background noise.
 */
object AcousticNoteDetector {
    private const val SAMPLE_RATE = 44100
    private const val BUFFER_SIZE = 8192 // High resolution for low notes
    
    private var isRunning = false
    private var thread: Thread? = null
    
    // Stability tracking to prevent flickering and misdetection
    private val detectionConfidence = mutableMapOf<Int, Int>()
    private val activePitches = mutableSetOf<Int>()
    private const val CONFIDENCE_THRESHOLD = 3 // Frames of consistency before turning ON
    private const val DECAY_THRESHOLD = 5 // Frames of missing note before turning OFF

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        if (isRunning) return
        
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        isRunning = true
        thread = Thread {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize.coerceAtLeast(BUFFER_SIZE * 2)
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                isRunning = false
                return@Thread
            }

            audioRecord.startRecording()
            val audioBuffer = ShortArray(BUFFER_SIZE)
            val fftBuffer = DoubleArray(BUFFER_SIZE)
            val fft = DoubleFFT_1D(BUFFER_SIZE.toLong())

            while (isRunning) {
                val read = audioRecord.read(audioBuffer, 0, BUFFER_SIZE)
                if (read > 0) {
                    // 1. Apply Blackman-Harris window for sharp spectral peaks
                    for (i in 0 until BUFFER_SIZE) {
                        val a0 = 0.35875 ; val a1 = 0.48829 ; val a2 = 0.14128 ; val a3 = 0.01168
                        val t = 2 * PI * i / (BUFFER_SIZE - 1)
                        val window = a0 - a1 * cos(t) + a2 * cos(2 * t) - a3 * cos(3 * t)
                        fftBuffer[i] = (audioBuffer[i].toDouble() / Short.MAX_VALUE) * window
                    }

                    // 2. Compute Forward FFT
                    fft.realForward(fftBuffer)

                    // 3. Magnitude Spectrum with Noise Floor Estimation
                    val magnitudes = DoubleArray(BUFFER_SIZE / 2)
                    var totalEnergy = 0.0
                    for (k in 0 until BUFFER_SIZE / 2) {
                        val re = fftBuffer[2 * k]
                        val im = if (k == 0) 0.0 else fftBuffer[2 * k + 1]
                        magnitudes[k] = sqrt(re * re + im * im)
                        totalEnergy += magnitudes[k]
                    }
                    val noiseFloor = totalEnergy / (BUFFER_SIZE / 2)

                    // 4. Harmonic Product Spectrum (HPS)
                    // Isolates fundamental frequency by multiplying with its own harmonics
                    val hps = DoubleArray(BUFFER_SIZE / 12) // Scan up to ~3.6kHz
                    for (k in 1 until hps.size) {
                        // Multiply Fundamental * 2nd Harmonic * 3rd Harmonic
                        hps[k] = magnitudes[k] * magnitudes[min(k * 2, magnitudes.size - 1)] * magnitudes[min(k * 3, magnitudes.size - 1)]
                    }

                    // 5. Peak Finding with Adaptive Squelch (Reject speech/background)
                    val detectedThisFrame = mutableSetOf<Int>()
                    val squelchThreshold = max(25.0, noiseFloor * 12.0) 
                    
                    for (k in 2 until hps.size - 2) {
                        if (hps[k] > squelchThreshold && hps[k] > hps[k-1] && hps[k] > hps[k+1]) {
                            val freq = k.toDouble() * SAMPLE_RATE / BUFFER_SIZE
                            val pitch = frequencyToMidi(freq)
                            if (pitch in 21..108) detectedThisFrame.add(pitch)
                        }
                    }

                    // 6. Hysteresis State Machine (Fixes flickering and speech artifacts)
                    for (p in 21..108) {
                        val count = detectionConfidence[p] ?: 0
                        if (p in detectedThisFrame) {
                            detectionConfidence[p] = min(DECAY_THRESHOLD + 1, count + 1)
                        } else {
                            detectionConfidence[p] = max(0, count - 1)
                        }

                        val finalCount = detectionConfidence[p] ?: 0
                        if (finalCount >= CONFIDENCE_THRESHOLD && p !in activePitches) {
                            MidiInputManager.simulateNoteOn(p)
                            activePitches.add(p)
                        } else if (finalCount == 0 && p in activePitches) {
                            MidiInputManager.simulateNoteOff(p)
                            activePitches.remove(p)
                        }
                    }
                }
            }

            audioRecord.stop()
            audioRecord.release()
        }.apply { 
            name = "AcousticDetector"
            priority = Thread.MAX_PRIORITY
            start() 
        }
    }

    fun stop() {
        isRunning = false
        thread?.interrupt()
        thread = null
        activePitches.forEach { MidiInputManager.simulateNoteOff(it) }
        activePitches.clear()
        detectionConfidence.clear()
    }

    private fun frequencyToMidi(freq: Double): Int {
        if (freq <= 0) return -1
        return (69 + 12 * log2(freq / 440.0)).roundToInt()
    }
}
