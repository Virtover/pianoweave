package com.lumenchord.pianoweave.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * One emotion's colors, all drawn from a single image in Scripture: [light] for text, [core] for
 * icons and borders, [deep] for a tinted background.
 */
data class Mood(
    val light: Color,
    val core: Color,
    val deep: Color
)

/**
 * The app's emotional colors. The accent theme says who the app is; a mood says what is happening and
 * how it should feel. Every mood follows a passage, so the feeling stays truthful: sorrow that is held
 * rather than alarm, watchfulness without fear, joy as light that does not fade.
 */
object Moods {

    /**
     * Joy, a work completed: "The city has no need of sun or moon to shine on it, for the glory of God
     * gives it light" (Revelation 21:23). Gold light that does not fade. Used when a transcription is ready.
     */
    val Glory = Mood(
        light = Color(0xFFFFF1C2),
        core = Color(0xFFFFD45E),
        deep = Color(0xFF1F2412)
    )

    /**
     * Lament: "You keep track of all my sorrows. You have collected all my tears in your bottle"
     * (Psalm 56:8). Grief that is seen and held, not an alarm: a deep wine rose that keeps its warmth,
     * because the lament psalms end in trust. Used for errors and for undoing things.
     */
    val Lament = Mood(
        light = Color(0xFFF5BAC4),
        core = Color(0xFFD8687C),
        deep = Color(0xFF26141A)
    )

    /**
     * Watchfulness: "I will stand at my watch and station myself on the ramparts" (Habakkuk 2:1), and the
     * wise who "took oil in jars along with their lamps" (Matthew 25:4). Lamp-oil amber: alert, never
     * afraid. Used for announced server restarts and anything that asks for attention.
     */
    val Watch = Mood(
        light = Color(0xFFFFE0A3),
        core = Color(0xFFF5AE42),
        deep = Color(0xFF261D10)
    )

    /**
     * Peace: "He got up, rebuked the wind and said to the waves, 'Quiet! Be still!' Then the wind died
     * down and it was completely calm" (Mark 4:39); "he leads me beside quiet waters" (Psalm 23:2).
     * Clear, calm teal. Used when all is well, such as a server that is online.
     */
    val StillWaters = Mood(
        light = Color(0xFFA9F0E4),
        core = Color(0xFF3FD2BE),
        deep = Color(0xFF0D2A28)
    )

    /**
     * Courage: Shadrach, Meshach and Abednego walked in the furnace, and "the fire had not harmed their
     * bodies" (Daniel 3:27); Judas Maccabeus was "like a lion in his deeds" (1 Maccabees 3:4). Flame
     * that is brave rather than dangerous. Used while recording, when someone plays for the microphone.
     */
    val Furnace = Mood(
        light = Color(0xFFFFC27A),
        core = Color(0xFFFF5B3A),
        deep = Color(0xFF2A120D)
    )
}
