package com.lumenchord.pianoweave.audio

import android.os.Handler
import android.os.Looper

/**
 * The Bluetooth keyboard coming and going, heard as soft chords rolled upward like a harp on the app's own
 * grand piano (a channel of its own, so the song never cuts it), in open, hymn-like spacing:
 * - back: one C-major chord spread wide (C4 G4 E5 C6): a quiet "here it is";
 * - lost: a whole "Amen", the plagal cadence that closes hymns (F major opening into a wide C major).
 *   Never falling, never sad: when the keyboard goes, the music rises and widens, "I am with you always"
 *   (Matthew 28:20), more than ever.
 * Played lightly, the top voice carrying, and left to ring like a held pedal in a church. Everything else
 * the app plays is turned down meanwhile ([PianoPlayer.duckForCue]) and swells back after; the touch is
 * firmer than it sounds to make up for the synth being turned down with it (one synth, one volume).
 */
object ConnectionChime {
    /** Notes rolled upward from [atMs] on. */
    internal class Chord(val notes: List<Int>, val atMs: Long)

    internal val BACK = listOf(Chord(listOf(60, 67, 76, 84), 0))
    internal val LOST = listOf(
        Chord(listOf(53, 60, 65, 69, 77), 0),        // "A-": F major (F3 C4 F4 A4 F5)
        Chord(listOf(48, 55, 64, 72, 79, 84), 900),  // "-men": C major, wider and higher (C3 G3 E4 C5 G5 C6)
    )
    /** Between the notes of a roll. */
    internal const val ROLL_MS = 85L
    /** How long the last chord rings before it's let go (it then fades as released keys do). */
    internal const val RING_MS = 2_600L
    internal const val TOP_VELOCITY = 56
    internal const val INNER_VELOCITY = 42
    /** The music is down before the first note sounds. */
    internal const val DUCK_LEAD_MS = 60L

    private val main by lazy { Handler(Looper.getMainLooper()) }
    /** Cues still sounding; the music comes back only when the last one ends. */
    private var playing = 0

    internal fun phrase(rising: Boolean): List<Chord> = if (rising) BACK else LOST

    internal fun velocity(pitch: Int, chord: Chord): Int =
        if (pitch == chord.notes.max()) TOP_VELOCITY else INNER_VELOCITY

    fun play(rising: Boolean) {
        val chords = phrase(rising)
        playing++
        PianoPlayer.duckForCue(true)
        chords.forEachIndexed { index, chord ->
            chord.notes.forEachIndexed { i, pitch ->
                main.postDelayed({ PianoPlayer.cueOn(pitch, velocity(pitch, chord)) }, DUCK_LEAD_MS + chord.atMs + i * ROLL_MS)
            }
            // Each chord gives way to the next (or rings out at the end), like changing harmony under the pedal
            val endMs = chords.getOrNull(index + 1)?.atMs?.plus(ROLL_MS * 2)
                ?: (chord.atMs + chord.notes.size * ROLL_MS + RING_MS)
            main.postDelayed({ chord.notes.forEach(PianoPlayer::cueOff) }, DUCK_LEAD_MS + endMs)
        }
        main.postDelayed({ if (--playing == 0) PianoPlayer.duckForCue(false) }, DUCK_LEAD_MS + totalMs(chords))
    }

    /** From the first note until the last chord is let go. */
    internal fun totalMs(chords: List<Chord>): Long =
        chords.last().let { it.atMs + it.notes.size * ROLL_MS + RING_MS }
}
