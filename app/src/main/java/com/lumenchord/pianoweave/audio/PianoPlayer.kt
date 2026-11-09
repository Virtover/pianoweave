package com.lumenchord.pianoweave.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import dev.kotlinds.fluidsynthkmp.AudioConfig
import dev.kotlinds.fluidsynthkmp.FluidSynthPlayer
import dev.kotlinds.fluidsynthkmp.Interpolation
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
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

    // While a voice command may be being said, the song is 9 dB quieter: the phone's speaker is next to
    // its mic, and at full level it buried words that weren't spoken loudly. Ramped (no click), and
    // restored shortly after the words end so the decoder has them complete.
    private const val VOICE_DUCK_GAIN = MASTER_GAIN * 0.35f
    private const val DUCK_RAMP_STEPS = 4
    private const val DUCK_RAMP_STEP_MS = 8L
    private const val DUCK_RESTORE_DELAY_MS = 400L

    private val gainExecutor = Executors.newSingleThreadScheduledExecutor { Thread(it, "PianoGain").apply { isDaemon = true } }
    private var pendingRestore: ScheduledFuture<*>? = null
    @Volatile private var currentGain = MASTER_GAIN
    @Volatile private var targetGain = MASTER_GAIN

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

                // The metronome's woodblocks, on the drum channel; without them the piano still plays
                try {
                    if (loadSoundFont(MetronomeClick.soundFontFile(context).absolutePath) < 0) {
                        Log.w(TAG, "Failed to load the metronome SoundFont")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to prepare the metronome SoundFont", e)
                }

                programChange(CHANNEL, GRAND_PIANO)
                programChange(MetronomeClick.CHANNEL, 0)
                setGain(targetGain)
                currentGain = targetGain

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
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> stopAllNotes()
                        // Transient loss (notifications, short prompts): let notes ring out
                        // rather than cutting the player off mid-phrase.
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { /* no-op, or optionally duck gain */ }
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

    /**
     * Turns the song down while [on] (a possible voice command is being heard), and back up a moment
     * after it ends. Safe to call from any thread; never blocks.
     */
    fun duckForVoice(on: Boolean) {
        synchronized(gainExecutor) {
            pendingRestore?.cancel(false)
            pendingRestore = null
            if (on) {
                rampTo(VOICE_DUCK_GAIN)
            } else if (targetGain != MASTER_GAIN) {
                pendingRestore = gainExecutor.schedule({ rampTo(MASTER_GAIN) }, DUCK_RESTORE_DELAY_MS, TimeUnit.MILLISECONDS)
            }
        }
    }

    private fun rampTo(gain: Float) {
        if (targetGain == gain) return
        targetGain = gain
        gainExecutor.execute {
            val from = currentGain
            for (step in 1..DUCK_RAMP_STEPS) {
                // A newer ramp took over
                if (targetGain != gain) return@execute
                val g = from + (gain - from) * step / DUCK_RAMP_STEPS
                try {
                    player?.setGain(g)
                } catch (_: Exception) {}
                currentGain = g
                Thread.sleep(DUCK_RAMP_STEP_MS)
            }
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

    /** A metronome stroke on the drum channel; it rings out by itself, so it needs no note off. */
    @Synchronized
    fun click(key: Int, velocity: Int) {
        if (player == null || !isInitialized.get()) {
            appContext?.let { initialize(it) }
        }
        try {
            player?.noteOn(MetronomeClick.CHANNEL, key, velocity.coerceIn(1, 127))
        } catch (e: Exception) {
            Log.e(TAG, "Error playing click($key)", e)
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
