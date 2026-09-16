package com.example.ytpiano.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/**
 * Advanced real-time additive synthesizer providing a rich, percussive piano tone.
 * Optimized for low latency and high stability to prevent audio artifacts.
 */
object PianoSynthesizer {

    private const val SAMPLE_RATE = 44100
    private const val BUFFER_SIZE = 2048 // Increased for better stability
    
    private val audioTrack = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build())
        .setAudioFormat(AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build())
        .setBufferSizeInBytes(BUFFER_SIZE * 4)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    private val activeVoices = ConcurrentHashMap<Int, Voice>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var sampleCounter = 0L

    init {
        scope.launch {
            audioTrack.play()
            val buffer = ShortArray(BUFFER_SIZE)

            while (isActive) {
                buffer.fill(0)
                val voices = activeVoices.values.toList()

                if (voices.isEmpty()) {
                    audioTrack.write(buffer, 0, buffer.size)
                    sampleCounter += buffer.size
                    continue
                }

                for (i in 0 until BUFFER_SIZE) {
                    var sum = 0.0
                    val t = (sampleCounter + i).toDouble() / SAMPLE_RATE

                    for (voice in voices) {
                        val vt = t - voice.startTime
                        if (vt < 0) continue
                        
                        // Maximum ring duration
                        if (vt > 3.0) {
                            activeVoices.remove(voice.pitch)
                            continue
                        }

                        // --- Acoustic Piano Synthesis Model ---
                        // A more complex harmonic series for "Real Piano" timbre
                        var s = sin(2.0 * PI * voice.freq * vt)
                        s += 0.60 * sin(2.0 * PI * (voice.freq * 2.001) * vt + 0.1) // Slightly detuned octaves
                        s += 0.30 * sin(2.0 * PI * (voice.freq * 3.0) * vt + 0.2)
                        s += 0.15 * sin(2.0 * PI * (voice.freq * 4.002) * vt + 0.3)
                        s += 0.08 * sin(2.0 * PI * (voice.freq * 5.0) * vt + 0.4)
                        s += 0.04 * sin(2.0 * PI * (voice.freq * 6.0) * vt + 0.5)

                        // Hammer Strike Noise (percussive "thump" at onset)
                        val noiseEnv = exp(-100.0 * vt)
                        val hammerThump = (Math.random() * 2.0 - 1.0) * 0.2 * noiseEnv
                        s += hammerThump

                        // ADSR - Percussive Envelope
                        val attack = 0.003
                        val env = if (vt < attack) {
                            vt / attack 
                        } else {
                            // Classic piano decay: sharp initial drop then slow sustain
                            0.7 * exp(-8.0 * (vt - attack)) + 0.3 * exp(-1.5 * (vt - attack))
                        }
                        
                        sum += s * voice.amp * env
                    }
                    
                    // Limiter to prevent digital clipping
                    val limited = sum.coerceIn(-1.0, 1.0)
                    buffer[i] = (limited * Short.MAX_VALUE).toInt().toShort()
                }

                audioTrack.write(buffer, 0, buffer.size)
                sampleCounter += buffer.size
            }
        }
    }

    fun noteOn(pitch: Int, velocity: Int = 80) {
        val freq = 440.0 * 2.0.pow((pitch - 69).toDouble() / 12.0)
        val amp = (velocity / 127.0) * 0.22
        
        activeVoices[pitch] = Voice(
            pitch = pitch,
            freq = freq,
            amp = amp,
            startTime = sampleCounter.toDouble() / SAMPLE_RATE
        )
    }

    fun noteOff(pitch: Int) {
        // Natural string vibration decay
    }

    private data class Voice(
        val pitch: Int,
        val freq: Double,
        val amp: Double,
        val startTime: Double
    )
}
