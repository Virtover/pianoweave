package com.example.pianoweave.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import dev.kotlinds.fluidsynthkmp.AudioConfig
import dev.kotlinds.fluidsynthkmp.FluidSynthPlayer
import dev.kotlinds.fluidsynthkmp.Interpolation
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-quality Grand Piano player using FluidSynth and SoundFonts.
 * Optimized for pristine audio fidelity, low-latency playback, and acoustic realism.
 */
object PianoPlayer : AutoCloseable {

    private const val TAG = "PianoPlayer"
    private const val SOUND_FONT = "Full Grand Piano.sf2"
    private const val CHANNEL = 0
    private const val GRAND_PIANO = 0

    // Master gain configured to 0.82f to give headroom for polyphonic chords without digital clipping
    private const val MASTER_GAIN = 0.82f

    private var player: FluidSynthPlayer? = null
    private val isInitialized = AtomicBoolean(false)
    private var appContext: Context? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    @Synchronized
    fun initialize(context: Context) {
        appContext = context.applicationContext
        requestAudioFocus(context)

        if (isInitialized.get() && player != null) return

        try {
            val soundFontPath = copySoundFont(context)
            
            player?.close()
            player = null

            // Detect device native sample rate & buffer size for zero-resampling low latency audio
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val nativeSampleRate = audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
            val nativeBufferSize = audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 256

            // Clamp buffer size for stability across devices (between 128 and 512 frames)
            val periodSize = nativeBufferSize.coerceIn(128, 512)

            Log.i(TAG, "Initializing FluidSynth with SampleRate: $nativeSampleRate Hz, PeriodSize: $periodSize, Interpolation: HIGH")

            val newPlayer = FluidSynthPlayer(
                AudioConfig(
                    sampleRate = nativeSampleRate,
                    interpolation = Interpolation.HIGH, // 7th-order sinc interpolation for highest audio fidelity
                    periodSize = periodSize,
                    periods = 4 // Balanced 4-period buffer to prevent underruns while ensuring low latency
                )
            ).apply {
                val soundFontId = loadSoundFont(soundFontPath)
                if (soundFontId < 0) {
                    Log.e(TAG, "Failed to load SoundFont, id: $soundFontId")
                    isInitialized.set(false)
                    return
                }

                programChange(CHANNEL, GRAND_PIANO)
                setGain(MASTER_GAIN)

                // Concert-hall depth, pulled back so fast passages/pedal don't turn to mud
                setReverb(
                    roomSize = 0.55, // Concert hall spatial feel
                    damping = 0.40,  // Smooth acoustic wood reflection decay
                    width = 1.00,    // Full stereo panorama
                    level = 0.22     // Natural ambient depth without obscuring note attack
                )

                // Subtle warm chorus (subtle depth without unnatural acoustic modulation)
//                setChorus(
//                    voiceCount = 3,
//                    level = 0.15,
//                    speed = 0.30,
//                    depth = 1.50
//                )
            }
            player = newPlayer
            isInitialized.set(true)
            Log.i(TAG, "PianoPlayer initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PianoPlayer", e)
            player?.close()
            player = null
            isInitialized.set(false)
        }
    }

    fun requestAudioFocus(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                        focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        stopAllNotes()
                    }
                }
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting audio focus", e)
        }
    }

    fun abandonAudioFocus(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error abandoning audio focus", e)
        }
    }

    @Synchronized
    fun noteOn(pitch: Int, velocity: Int = 80) {
        if (pitch !in 0..127) return
        if (player == null || !isInitialized.get()) {
            appContext?.let { initialize(it) }
        }
        try {
            player?.noteOn(CHANNEL, pitch, velocity.coerceIn(1, 127))
        } catch (e: Exception) {
            Log.e(TAG, "Error playing noteOn($pitch)", e)
            player = null
            isInitialized.set(false)
        }
    }

    @Synchronized
    fun noteOff(pitch: Int) {
        if (pitch !in 0..127) return
        try {
            player?.noteOff(CHANNEL, pitch)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing noteOff($pitch)", e)
        }
    }

    /**
     * Stop all notes instantly.
     */
    @Synchronized
    fun stopAllNotes() {
        for (pitch in 0..127) {
            try {
                player?.noteOff(CHANNEL, pitch)
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    override fun close() {
        try {
            appContext?.let { abandonAudioFocus(it) }
            player?.close()
        } catch (_: Exception) {}
        player = null
        isInitialized.set(false)
    }

    private fun copySoundFont(context: Context): String {
        val destination = File(context.filesDir, SOUND_FONT)
        if (!destination.exists() || destination.length() == 0L) {
            context.assets.open(SOUND_FONT).use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return destination.absolutePath
    }
}
