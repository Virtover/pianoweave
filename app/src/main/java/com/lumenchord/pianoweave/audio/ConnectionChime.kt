package com.lumenchord.pianoweave.audio

import android.os.Handler
import android.os.Looper

/**
 * The Bluetooth keyboard coming and going, heard as a soft chord rolled like a harp on the app's own grand
 * piano (a channel of its own, so the song never cuts it). Open, hymn-like spacing, in keeping with the
 * app's spirit:
 * - back: C major spread wide (C4 G4 E5 C6), rolled upward, like the "Amen" closing a hymn;
 * - lost: the same C major, higher and rolled downward (E6 C6 G5 C5), softer still, coming to rest on the
 *   root: never sad. Whatever went away, the light stays ("I am with you always", Matthew 28:20); only
 *   the direction says the keyboard is gone.
 * Played very lightly, the lower voices softer still, and left to ring long, like a held pedal in a church.
 */
object ConnectionChime {
    internal val BACK = listOf(60, 67, 76, 84)
    internal val LOST = listOf(88, 84, 79, 72)
    /** Between the notes of the roll. */
    internal const val ROLL_MS = 90L
    /** How long the chord rings before it's let go (it then fades as released keys do). */
    internal const val RING_MS = 2_400L
    /** Very light touch; the top voice carries, the rest sit under it. */
    internal const val TOP_VELOCITY = 34
    internal const val INNER_VELOCITY = 26
    /** "Lost" is quieter: a farewell, not an alarm. */
    internal const val LOST_SOFTER_BY = 6

    private val main by lazy { Handler(Looper.getMainLooper()) }

    internal fun notes(rising: Boolean): List<Int> = if (rising) BACK else LOST

    internal fun velocity(pitch: Int, chord: List<Int>): Int =
        (if (pitch == chord.max()) TOP_VELOCITY else INNER_VELOCITY) - if (chord == LOST) LOST_SOFTER_BY else 0

    fun play(rising: Boolean) {
        val chord = notes(rising)
        chord.forEachIndexed { i, pitch ->
            main.postDelayed({ PianoPlayer.cueOn(pitch, velocity(pitch, chord)) }, i * ROLL_MS)
        }
        main.postDelayed({ chord.forEach(PianoPlayer::cueOff) }, chord.size * ROLL_MS + RING_MS)
    }
}
