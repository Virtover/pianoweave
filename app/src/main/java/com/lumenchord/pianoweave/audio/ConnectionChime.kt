package com.lumenchord.pianoweave.audio

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import dev.kotlinds.fluidsynthkmp.AudioConfig
import dev.kotlinds.fluidsynthkmp.FluidSynthPlayer
import dev.kotlinds.fluidsynthkmp.Interpolation

/**
 * The Bluetooth keyboard coming and going, heard as chords rolled upward like a harp, in open, hymn-like
 * spacing, on the same grand piano the app plays:
 * - back: one C-major chord spread wide (C4 G4 E5 C6): a quiet "here it is";
 * - lost: one F-major chord, spread wide and rolled upward the same way: warm and open, the "A-" of an
 *   "Amen" left hanging in the air. Never falling, never sad: "I am with you always" (Matthew 28:20).
 *
 * It has a synth of its own, so the app's music can be turned well down underneath it
 * ([PianoPlayer.duckForCue]) without turning the cue down too: it has to be heard over a playing song.
 * That synth (the piano SoundFont, ~17 MB) is made when a cue may come ([prepare], at the start of the
 * 3 s wait before "lost") and let go a minute after the last cue.
 */
object ConnectionChime {
    private const val TAG = "ConnectionChime"

    /** Notes rolled upward from [atMs] on; each chord gives way to the next, the last rings out. */
    internal class Chord(val notes: List<Int>, val atMs: Long)

    internal val BACK = listOf(Chord(listOf(60, 67, 76, 84), 0))
    internal val LOST = listOf(Chord(listOf(53, 60, 65, 69, 77), 0)) // F major: F3 C4 F4 A4 F5
    /** Between the notes of a roll. */
    internal const val ROLL_MS = 85L
    /** How long the last chord rings before it's let go (it then fades as released keys do). */
    internal const val RING_MS = 2_600L
    /** A clear but gentle touch: the top voice carries, the rest sit under it. */
    internal const val TOP_VELOCITY = 64
    internal const val INNER_VELOCITY = 50
    /** The music is down before the first note sounds. */
    internal const val DUCK_LEAD_MS = 80L
    private const val GAIN = 0.9f
    private const val IDLE_CLOSE_MS = 60_000L

    private val thread by lazy { HandlerThread("ConnectionChime").apply { start() } }
    private val handler by lazy { Handler(thread.looper) }
    private var synth: FluidSynthPlayer? = null
    private var appContext: Context? = null
    /** Cues still sounding; the music comes back only when the last one ends. */
    private var playing = 0
    private val closeIdle = Runnable {
        if (playing == 0) {
            runCatching { synth?.close() }
            synth = null
        }
    }

    internal fun phrase(rising: Boolean): List<Chord> = if (rising) BACK else LOST

    internal fun velocity(pitch: Int, chord: Chord): Int =
        if (pitch == chord.notes.max()) TOP_VELOCITY else INNER_VELOCITY

    /** From the first note until the last chord is let go. */
    internal fun totalMs(chords: List<Chord>): Long =
        chords.last().let { it.atMs + it.notes.size * ROLL_MS + RING_MS }

    /** Gets the synth ready in the background, so a cue soon after plays at once. */
    fun prepare(context: Context) {
        appContext = context.applicationContext
        handler.post { ensureSynth() }
    }

    fun play(context: Context, rising: Boolean) {
        appContext = context.applicationContext
        handler.post {
            val player = ensureSynth() ?: return@post
            handler.removeCallbacks(closeIdle)
            val chords = phrase(rising)
            playing++
            PianoPlayer.duckForCue(true)
            chords.forEachIndexed { index, chord ->
                chord.notes.forEachIndexed { i, pitch ->
                    handler.postDelayed({
                        runCatching { player.noteOn(0, pitch, velocity(pitch, chord)) }
                    }, DUCK_LEAD_MS + chord.atMs + i * ROLL_MS)
                }
                // Each gives way to the next (or rings out at the end), like changing harmony under the pedal
                val endMs = chords.getOrNull(index + 1)?.atMs?.plus(ROLL_MS * 2) ?: totalMs(chords)
                handler.postDelayed({
                    chord.notes.forEach { runCatching { player.noteOff(0, it) } }
                }, DUCK_LEAD_MS + endMs)
            }
            handler.postDelayed({
                if (--playing == 0) {
                    PianoPlayer.duckForCue(false)
                    handler.postDelayed(closeIdle, IDLE_CLOSE_MS)
                }
            }, DUCK_LEAD_MS + totalMs(chords))
        }
    }

    /** On the chime thread. Null if the synth couldn't be made (then no cue; the app goes on as before). */
    private fun ensureSynth(): FluidSynthPlayer? {
        synth?.let { return it }
        val context = appContext ?: return null
        return runCatching {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val rate = audio?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48_000
            val period = (audio?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 256)
                .coerceIn(128, 512)
            FluidSynthPlayer(
                AudioConfig(sampleRate = rate, interpolation = Interpolation.HIGH, periodSize = period, periods = 4)
            ).apply {
                check(loadSoundFont(PianoPlayer.copySoundFont(context)) >= 0) { "SoundFont did not load" }
                programChange(0, 0)
                setGain(GAIN)
                // More space than the practice piano: it should sound like it fills a church
                setReverb(roomSize = 0.85, damping = 0.30, width = 1.0, level = 0.45)
            }
        }.onFailure { Log.w(TAG, "no chime synth", it) }.getOrNull().also { synth = it }
    }
}
