package com.example.ytpiano.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/**
 * High-quality real-time Grand Piano synthesizer.
 * Uses additive synthesis with 8 harmonics, hammer-strike emulation, 
 * and a soft-knee limiter for clear, undistorted polyphonic playback.
 */
object PianoSynthesizer {

    private const val SAMPLE_RATE = 44100
    private const val BUFFER_SIZE = 1024
    
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
        .setBufferSizeInBytes(BUFFER_SIZE * 8) // Ample buffer for high polyphony
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

                // Dynamic gain compensation to avoid distortion during chords
                val gain = 1.0 / sqrt(max(1.0, voices.size.toDouble()))

                for (i in 0 until BUFFER_SIZE) {
                    var mixed = 0.0
                    val t = (sampleCounter + i).toDouble() / SAMPLE_RATE

                    for (voice in voices) {
                        val vt = t - voice.startTime
                        if (vt < 0) continue
                        
                        // Grand Piano Sustain: ring for up to 4 seconds
                        if (vt > 4.0) {
                            activeVoices.remove(voice.pitch)
                            continue
                        }

                        // --- Grand Piano Harmonic Model ---
                        // Rich overtone series with detuned octaves for "body"
                        var s = sin(2.0 * PI * voice.freq * vt)
                        s += 0.55 * sin(2.0 * PI * (voice.freq * 2.001) * vt + 0.1)
                        s += 0.25 * sin(2.0 * PI * (voice.freq * 3.0) * vt + 0.2)
                        s += 0.12 * sin(2.0 * PI * (voice.freq * 4.002) * vt + 0.3)
                        s += 0.06 * sin(2.0 * PI * (voice.freq * 5.0) * vt + 0.4)
                        s += 0.03 * sin(2.0 * PI * (voice.freq * 6.0) * vt + 0.5)

                        // Hammer Impact (Percussive onset noise)
                        val attackEnv = exp(-120.0 * vt)
                        s += (Math.random() * 2.0 - 1.0) * 0.2 * attackEnv

                        // ADSR - Piano Hammer-on-String Envelope
                        val attack = 0.005
                        val env = if (vt < attack) {
                            vt / attack 
                        } else {
                            // Compound decay: fast initial drop then a slow "singing" sustain
                            0.7 * exp(-7.0 * (vt - attack)) + 0.3 * exp(-1.2 * (vt - attack))
                        }
                        
                        mixed += s * voice.amp * env
                    }
                    
                    // --- Soft-Knee Limiter ---
                    // Smoothly saturates instead of hard-clipping
                    val scaled = mixed * gain
                    val output = if (abs(scaled) > 0.8) {
                        sign(scaled) * (0.8 + (abs(scaled) - 0.8) / (1 + (abs(scaled) - 0.8).pow(2)))
                    } else {
                        scaled
                    }
                    
                    buffer[i] = (output.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
                }

                audioTrack.write(buffer, 0, buffer.size)
                sampleCounter += buffer.size
            }
        }
    }

    /**
     * Start a piano note.
     */
    fun noteOn(pitch: Int, velocity: Int = 80) {
        val freq = 440.0 * 2.0.pow((pitch - 69).toDouble() / 12.0)
        // High notes have less energy
        val brilliance = 1.0 / (1.0 + 0.0005 * freq)
        val amp = (velocity / 127.0) * 0.35 * brilliance
        
        activeVoices[pitch] = Voice(
            pitch = pitch,
            freq = freq,
            amp = amp,
            startTime = sampleCounter.toDouble() / SAMPLE_RATE
        )
    }

    /**
     * Stop a note (unused as natural decay sounds better for piano)
     */
    fun noteOff(pitch: Int) {
        // String continues to vibrate
    }

    private data class Voice(
        val pitch: Int,
        val freq: Double,
        val amp: Double,
        val startTime: Double
    )
}
