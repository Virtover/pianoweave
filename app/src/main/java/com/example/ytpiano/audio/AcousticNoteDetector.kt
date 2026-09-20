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
 * High-fidelity acoustic piano note detector.
 * 
 * Strategy:
 * 1. Spectral Flux Onset Detection: Identify sharp energy increases to trigger detection.
 * 2. Harmonic Product Spectrum (HPS): Identify fundamental frequencies.
 * 3. Hysteresis State Machine: Ensure stable note-on/off events and suppress speech/noise.
 * 4. Echo Cancellation: Ignore app-generated audio via time-windowed suppression.
 */
object AcousticNoteDetector {
    private const val SAMPLE_RATE = 44100
    private const val FFT_SIZE = 8192
    private const val HOP_SIZE = 2048 // 4x overlap for better time resolution

    private var isRunning = false
    private var thread: Thread? = null
    
    // Echo Cancellation
    @Volatile
    var suppressedPitches: Set<Int> = emptySet()
    private val suppressionMap = mutableMapOf<Int, Long>()
    private const val ECHO_WINDOW_MS = 450L // Increased for safety

    // Onset Detection State
    private val prevMagnitudes = DoubleArray(FFT_SIZE / 2)
    private var fluxAverage = 0.0
    private const val FLUX_ALPHA = 0.8 // Smoothing for noise floor estimation

    // State Tracking
    private val detectionConfidence = mutableMapOf<Int, Int>()
    private val activePitches = mutableSetOf<Int>()
    private const val ON_CONFIDENCE = 4  // Number of frames to confirm note-on
    private const val OFF_CONFIDENCE = 6 // Number of frames to confirm note-off

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        if (isRunning) return
        if (MidiInputManager.isMidiDeviceConnected()) return
        
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        isRunning = true
        thread = Thread {
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf.coerceAtLeast(FFT_SIZE * 2)
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                isRunning = false
                return@Thread
            }

            audioRecord.startRecording()
            
            // Use a circular buffer or shift-buffer to handle overlaps
            val processBuffer = ShortArray(FFT_SIZE)
            val fftData = DoubleArray(FFT_SIZE)
            val fft = DoubleFFT_1D(FFT_SIZE.toLong())
            val window = DoubleArray(FFT_SIZE) { i ->
                // Blackman-Harris window for sharp peak isolation
                val a0 = 0.35875 ; val a1 = 0.48829 ; val a2 = 0.14128 ; val a3 = 0.01168
                val t = 2 * PI * i / (FFT_SIZE - 1)
                a0 - a1 * cos(t) + a2 * cos(2 * t) - a3 * cos(3 * t)
            }

            while (isRunning) {
                if (MidiInputManager.isMidiDeviceConnected()) break
                // Read a hop's worth of data
                val samples = ShortArray(HOP_SIZE)
                val read = audioRecord.read(samples, 0, HOP_SIZE)
                if (read <= 0) continue

                // Shift and append to process buffer
                System.arraycopy(processBuffer, HOP_SIZE, processBuffer, 0, FFT_SIZE - HOP_SIZE)
                System.arraycopy(samples, 0, processBuffer, FFT_SIZE - HOP_SIZE, HOP_SIZE)

                val now = System.currentTimeMillis()
                
                // Update suppression list
                suppressedPitches.forEach { suppressionMap[it] = now + ECHO_WINDOW_MS }

                // Prepare FFT data with windowing
                for (i in 0 until FFT_SIZE) {
                    fftData[i] = (processBuffer[i].toDouble() / Short.MAX_VALUE) * window[i]
                }

                fft.realForward(fftData)

                // Calculate Magnitudes
                val mags = DoubleArray(FFT_SIZE / 2)
                var currentEnergy = 0.0
                for (k in 0 until FFT_SIZE / 2) {
                    val re = fftData[2 * k]
                    val im = if (k == 0) 0.0 else fftData[2 * k + 1]
                    mags[k] = sqrt(re * re + im * im)
                    currentEnergy += mags[k]
                }

                // 1. Spectral Flux (Onset Detection)
                var flux = 0.0
                for (k in 0 until FFT_SIZE / 2) {
                    val diff = mags[k] - prevMagnitudes[k]
                    if (diff > 0) flux += diff
                    prevMagnitudes[k] = mags[k]
                }
                
                // Adaptive Flux Threshold
                fluxAverage = FLUX_ALPHA * fluxAverage + (1 - FLUX_ALPHA) * flux
                val isOnset = flux > (fluxAverage * 2.5) && flux > 1.5

                // 2. Harmonic Product Spectrum (Frequency Detection)
                val hpsSize = FFT_SIZE / 16 // Scan up to ~2.7kHz
                val hps = DoubleArray(hpsSize)
                var maxHps = 0.0
                for (k in 4 until hpsSize) { // Start above ~21Hz (A0)
                    hps[k] = mags[k] * 
                             sqrt(mags[min(k * 2, mags.size - 1)]) * 
                             sqrt(mags[min(k * 3, mags.size - 1)])
                    if (hps[k] > maxHps) maxHps = hps[k]
                }

                val detectedThisFrame = mutableSetOf<Int>()
                if (isOnset || currentEnergy > 5.0) {
                    val peakThreshold = maxHps * 0.4
                    for (k in 5 until hpsSize - 1) {
                        if (hps[k] > peakThreshold && hps[k] > hps[k-1] && hps[k] > hps[k+1]) {
                            val freq = k.toDouble() * SAMPLE_RATE / FFT_SIZE
                            val pitch = (69 + 12 * log2(freq / 440.0)).roundToInt()
                            
                            val isSuppressed = (suppressionMap[pitch] ?: 0L) > now
                            if (pitch in 21..108 && !isSuppressed) {
                                detectedThisFrame.add(pitch)
                            }
                        }
                    }
                }

                // 3. State Machine & Debouncing
                for (p in 21..108) {
                    val currentConf = detectionConfidence[p] ?: 0
                    if (p in detectedThisFrame) {
                        detectionConfidence[p] = min(ON_CONFIDENCE + 2, currentConf + 1)
                    } else {
                        detectionConfidence[p] = max(0, currentConf - 1)
                    }

                    val finalConf = detectionConfidence[p] ?: 0
                    if (finalConf >= ON_CONFIDENCE && p !in activePitches) {
                        MidiInputManager.simulateExternalNoteOn(p)
                        activePitches.add(p)
                    } else if (finalConf == 0 && p in activePitches) {
                        MidiInputManager.simulateExternalNoteOff(p)
                        activePitches.remove(p)
                    }
                }
            }

            audioRecord.stop()
            audioRecord.release()
        }.apply { 
            name = "AcousticPianoDetector"
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
        suppressionMap.clear()
    }
}
