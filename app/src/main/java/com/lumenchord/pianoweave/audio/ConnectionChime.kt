package com.lumenchord.pianoweave.audio

import android.os.Handler
import android.os.Looper

/**
 * The Bluetooth keyboard coming and going, heard as two soft, high piano notes a fifth apart (the app's own
 * grand piano, on a channel of their own): rising when it's back, falling when it's lost. Played gently and
 * high, like a grace note, so it doesn't read as part of the music; they ring out like real strings.
 */
object ConnectionChime {
    /** E6 and B6: high enough to stand apart from most playing, low enough to stay warm. */
    internal const val LOW = 88
    internal const val HIGH = 95
    internal const val VELOCITY = 30
    /** The second note follows the first like a grace note. */
    internal const val GAP_MS = 140L
    /** Then both are let go, so they fade as a released key does. */
    internal const val RING_MS = 900L

    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** The two notes, in order. */
    internal fun notes(rising: Boolean): List<Int> = if (rising) listOf(LOW, HIGH) else listOf(HIGH, LOW)

    fun play(rising: Boolean) {
        val (first, second) = notes(rising)
        PianoPlayer.cueOn(first, VELOCITY)
        main.postDelayed({ PianoPlayer.cueOn(second, VELOCITY) }, GAP_MS)
        main.postDelayed({ PianoPlayer.cueOff(first); PianoPlayer.cueOff(second) }, GAP_MS + RING_MS)
    }
}
