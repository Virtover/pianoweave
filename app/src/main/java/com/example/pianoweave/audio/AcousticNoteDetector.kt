package com.example.pianoweave.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import com.example.pianoweave.midi.MidiInputManager
import org.jtransforms.fft.DoubleFFT_1D
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Professional acoustic piano note detector.
 * Optimized for high sensitivity to strikes and robustness against background noise.
 */
object AcousticNoteDetector {
    private const val SAMPLE_RATE = 44100
    private const val FFT_SIZE = 8192
    private const val HOP_SIZE = 2048 

    private var isRunning = false
    private var thread: Thread? = null
    
    @Volatile var suppressedPitches: Set<Int> = emptySet()
    private val suppressionMap = mutableMapOf<Int, Long>()
    private const val ECHO_WINDOW_MS = 450L 

    private val prevMagnitudes = DoubleArray(FFT_SIZE / 2)
    private var fluxAverage = 0.0
    private const val FLUX_ALPHA = 0.8 

    private val detectionConfidence = mutableMapOf<Int, Int>()
    private val activePitches = mutableSetOf<Int>()
    private const val ON_CONFIDENCE = 3  
    private const val OFF_CONFIDENCE = 5 

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        if (isRunning) return
        if (MidiInputManager.isMidiDeviceConnected()) return
        
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        isRunning = true
        if (!NativeAudioEngine.startCapture()) {
            isRunning = false
            return
        }

        thread = Thread {
            val fftData = DoubleArray(FFT_SIZE)
            val fft = DoubleFFT_1D(FFT_SIZE.toLong())
            val window = DoubleArray(FFT_SIZE) { i ->
                val a0 = 0.35875 ; val a1 = 0.48829 ; val a2 = 0.14128 ; val a3 = 0.01168
                val t = 2 * PI * i / (FFT_SIZE - 1)
                a0 - a1 * cos(t) + a2 * cos(2 * t) - a3 * cos(3 * t)
            }

            // Buffer for native capture
            val nativeBuffer = ByteBuffer.allocateDirect(FFT_SIZE * 4).order(ByteOrder.nativeOrder())
            val floatData = FloatArray(FFT_SIZE)

            var lastProcessedFrame = 0L

            while (isRunning) {
                if (MidiInputManager.isMidiDeviceConnected()) break

                val availableFrames = NativeAudioEngine.getAvailableFrames().toLong()
                if (availableFrames < lastProcessedFrame + HOP_SIZE) {
                    Thread.sleep(5)
                    continue
                }

                // Copy latest FFT_SIZE frames
                NativeAudioEngine.copyLatest(nativeBuffer, FFT_SIZE)
                nativeBuffer.rewind()
                nativeBuffer.asFloatBuffer().get(floatData)
                lastProcessedFrame = availableFrames

                val now = System.currentTimeMillis()
                suppressedPitches.forEach { suppressionMap[it] = now + ECHO_WINDOW_MS }

                for (i in 0 until FFT_SIZE) {
                    fftData[i] = floatData[i].toDouble() * window[i]
                }

                fft.realForward(fftData)

                val mags = DoubleArray(FFT_SIZE / 2)
                var currentEnergy = 0.0
                for (k in 0 until FFT_SIZE / 2) {
                    val re = fftData[2 * k] ; val im = if (k == 0) 0.0 else fftData[2 * k + 1]
                    mags[k] = sqrt(re * re + im * im)
                    currentEnergy += mags[k]
                }

                var flux = 0.0
                for (k in 0 until FFT_SIZE / 2) {
                    val diff = mags[k] - prevMagnitudes[k]
                    if (diff > 0) flux += diff
                    prevMagnitudes[k] = mags[k]
                }
                fluxAverage = FLUX_ALPHA * fluxAverage + (1 - FLUX_ALPHA) * flux
                val isOnset = flux > (fluxAverage * 2.5) && flux > 1.5

                val hpsSize = FFT_SIZE / 16 
                val hps = DoubleArray(hpsSize)
                var maxHps = 0.0
                for (k in 4 until hpsSize) { 
                    hps[k] = mags[k] * sqrt(mags[min(k * 2, mags.size - 1)]) * sqrt(mags[min(k * 3, mags.size - 1)])
                    if (hps[k] > maxHps) maxHps = hps[k]
                }

                val detectedThisFrame = mutableSetOf<Int>()
                val dynamicThreshold = if (isOnset) maxHps * 0.4 else maxHps * 0.75
                
                if (currentEnergy > 6.0 && maxHps > 0.5) {
                    for (k in 5 until hpsSize - 1) {
                        if (hps[k] > dynamicThreshold && hps[k] > hps[k-1] && hps[k] > hps[k+1]) {
                            val freq = k.toDouble() * SAMPLE_RATE / FFT_SIZE
                            val pitch = (69 + 12 * log2(freq / 440.0)).roundToInt()
                            
                            val isSuppressed = (suppressionMap[pitch] ?: 0L) > now
                            if (pitch in 21..108 && !isSuppressed) detectedThisFrame.add(pitch)
                        }
                    }
                }

                for (p in 21..108) {
                    val count = detectionConfidence[p] ?: 0
                    if (p in detectedThisFrame) {
                        detectionConfidence[p] = min(ON_CONFIDENCE + 2, count + 1)
                    } else {
                        detectionConfidence[p] = max(0, count - 1)
                    }

                    val finalCount = detectionConfidence[p] ?: 0
                    if (finalCount >= ON_CONFIDENCE && p !in activePitches) {
                        MidiInputManager.simulateExternalNoteOn(p)
                        activePitches.add(p)
                    } else if (finalCount == 0 && p in activePitches) {
                        MidiInputManager.simulateExternalNoteOff(p)
                        activePitches.remove(p)
                    }
                }
            }
        }.apply { 
            name = "AcousticPianoDetector"
            priority = Thread.MAX_PRIORITY
            start() 
        }
    }

    fun stop() {
        isRunning = false
        NativeAudioEngine.stopCapture()
        thread?.interrupt()
        thread = null
        activePitches.forEach { MidiInputManager.simulateExternalNoteOff(it) }
        activePitches.clear()
        detectionConfidence.clear()
        suppressionMap.clear()
    }
}
