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
 * Optimized for robustness against speech, self-audio feedback, and overtone misdetection.
 */
object AcousticNoteDetector {
    private const val SAMPLE_RATE = 44100
    private const val BUFFER_SIZE = 8192 
    
    private var isRunning = false
    private var thread: Thread? = null
    
    private val detectionConfidence = mutableMapOf<Int, Int>()
    private val activePitches = mutableSetOf<Int>()
    
    @Volatile
    var suppressedPitches: Set<Int> = emptySet()
    private val suppressionCooldowns = mutableMapOf<Int, Long>()
    private const val SUPPRESSION_MS = 300L 

    private const val CONFIDENCE_THRESHOLD = 5 

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
                    val now = System.currentTimeMillis()
                    
                    suppressedPitches.forEach { suppressionCooldowns[it] = now + SUPPRESSION_MS }

                    // Windowing
                    for (i in 0 until BUFFER_SIZE) {
                        val a0 = 0.35875 ; val a1 = 0.48829 ; val a2 = 0.14128 ; val a3 = 0.01168
                        val t = 2 * PI * i / (BUFFER_SIZE - 1)
                        val window = a0 - a1 * cos(t) + a2 * cos(2 * t) - a3 * cos(3 * t)
                        fftBuffer[i] = (audioBuffer[i].toDouble() / Short.MAX_VALUE) * window
                    }

                    fft.realForward(fftBuffer)

                    val magnitudes = DoubleArray(BUFFER_SIZE / 2)
                    var maxMag = 0.0
                    var sumMag = 0.0
                    for (k in 0 until BUFFER_SIZE / 2) {
                        val re = fftBuffer[2 * k]
                        val im = if (k == 0) 0.0 else fftBuffer[2 * k + 1]
                        magnitudes[k] = sqrt(re * re + im * im)
                        if (magnitudes[k] > maxMag) maxMag = magnitudes[k]
                        sumMag += magnitudes[k]
                    }
                    val noiseFloor = sumMag / (BUFFER_SIZE / 2)
                    
                    // 1. Harmonic Product Spectrum (HPS) with strict fundamental weighting
                    val hpsSize = BUFFER_SIZE / 12
                    val hps = DoubleArray(hpsSize)
                    for (k in 1 until hpsSize) {
                        // Weighted HPS: emphasize fundamental, de-emphasize high harmonics
                        hps[k] = magnitudes[k] * 
                                 sqrt(magnitudes[min(k * 2, magnitudes.size - 1)]) * 
                                 sqrt(magnitudes[min(k * 3, magnitudes.size - 1)])
                    }

                    // 2. Peak Finding with Adaptive Normalized Threshold
                    val detectedThisFrame = mutableSetOf<Int>()
                    // Peaks must be significantly above noise floor AND have a high HPS score relative to peak energy
                    val peakThreshold = max(20.0, noiseFloor * 15.0) 
                    
                    for (k in 2 until hpsSize - 2) {
                        if (hps[k] > peakThreshold && hps[k] > hps[k-1] && hps[k] > hps[k+1]) {
                            val freq = k.toDouble() * SAMPLE_RATE / BUFFER_SIZE
                            val pitch = (69 + 12 * log2(freq / 440.0)).roundToInt()
                            
                            val isSuppressed = (suppressionCooldowns[pitch] ?: 0L) > now
                            if (pitch in 21..108 && !isSuppressed) {
                                detectedThisFrame.add(pitch)
                            }
                        }
                    }

                    // 3. State Machine (uses simulateExternalNoteOn to prevent virtual key crosstalk)
                    for (p in 21..108) {
                        val count = detectionConfidence[p] ?: 0
                        if (p in detectedThisFrame) {
                            detectionConfidence[p] = min(CONFIDENCE_THRESHOLD + 2, count + 1)
                        } else {
                            detectionConfidence[p] = max(0, count - 1)
                        }

                        val finalCount = detectionConfidence[p] ?: 0
                        if (finalCount >= CONFIDENCE_THRESHOLD && p !in activePitches) {
                            MidiInputManager.simulateExternalNoteOn(p)
                            activePitches.add(p)
                        } else if (finalCount == 0 && p in activePitches) {
                            MidiInputManager.simulateExternalNoteOff(p)
                            activePitches.remove(p)
                        }
                    }
                }
            }

            audioRecord.stop()
            audioRecord.release()
        }.apply { 
            name = "PianoAcousticDetector"
            priority = Thread.MAX_PRIORITY
            start() 
        }
    }

    fun stop() {
        isRunning = false
        thread?.interrupt()
        thread = null
        activePitches.forEach { MidiInputManager.simulateExternalNoteOff(it) }
        activePitches.clear()
        detectionConfidence.clear()
        suppressionCooldowns.clear()
    }
}
