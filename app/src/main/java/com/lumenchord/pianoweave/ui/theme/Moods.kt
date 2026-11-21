package com.lumenchord.pianoweave.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

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
/**
 * The support heart's rose: a true rose rather than an alert red, "God loves a cheerful giver" (2 Corinthians 9:7).
 * [core] for the heart and the button, [ink] for words in it; deep by day, soft by night.
 */
object SupportRose {
    val core: Color get() = if (AppThemeManager.isLight) Color(0xFFC8506A) else Color(0xFFF2A0A8)
    val ink: Color get() = if (AppThemeManager.isLight) Color(0xFF8A2E44) else Color(0xFFF6D2D2)
    /** The Support button's fill: a rose that carries white text in both themes. */
    val button: Color get() = if (AppThemeManager.isLight) Color(0xFFC8506A) else Color(0xFFD86A7C)
}

object Moods {

    /**
     * Joy, a work completed: "The city has no need of sun or moon to shine on it, for the glory of God
     * gives it light" (Revelation 21:23). Gold light that does not fade. Used when a transcription is ready.
     */
    val Glory: Mood get() = if (AppThemeManager.isLight) GloryByDay else GloryByNight
    private val GloryByNight = Mood(
        light = Color(0xFFFFF1C2),
        core = Color(0xFFFFD45E),
        deep = Color(0xFF1F2412)
    )

    /**
     * Lament: "You keep track of all my sorrows. You have collected all my tears in your bottle"
     * (Psalm 56:8). Grief that is seen and held, not an alarm: a deep wine rose that keeps its warmth,
     * because the lament psalms end in trust. Used for errors and for undoing things.
     */
    val Lament: Mood get() = if (AppThemeManager.isLight) LamentByDay else LamentByNight
    private val LamentByNight = Mood(
        light = Color(0xFFF5BAC4),
        core = Color(0xFFD8687C),
        deep = Color(0xFF26141A)
    )

    /**
     * Watchfulness: "I will stand at my watch and station myself on the ramparts" (Habakkuk 2:1), and the
     * wise who "took oil in jars along with their lamps" (Matthew 25:4). Lamp-oil amber: alert, never
     * afraid. Used for announced server restarts and anything that asks for attention.
     */
    val Watch: Mood get() = if (AppThemeManager.isLight) WatchByDay else WatchByNight
    private val WatchByNight = Mood(
        light = Color(0xFFFFE0A3),
        core = Color(0xFFF5AE42),
        deep = Color(0xFF261D10)
    )

    /**
     * Peace: "He got up, rebuked the wind and said to the waves, 'Quiet! Be still!' Then the wind died
     * down and it was completely calm" (Mark 4:39); "he leads me beside quiet waters" (Psalm 23:2).
     * Clear, calm teal. Used when all is well, such as a server that is online.
     */
    val StillWaters: Mood get() = if (AppThemeManager.isLight) StillWatersByDay else StillWatersByNight
    private val StillWatersByNight = Mood(
        light = Color(0xFFA9F0E4),
        core = Color(0xFF3FD2BE),
        deep = Color(0xFF0D2A28)
    )

    /**
     * Courage: Shadrach, Meshach and Abednego walked in the furnace, and "the fire had not harmed their
     * bodies" (Daniel 3:27); Judas Maccabeus was "like a lion in his deeds" (1 Maccabees 3:4). Flame
     * that is brave rather than dangerous. Used while recording, when someone plays for the microphone.
     */
    val Furnace: Mood get() = if (AppThemeManager.isLight) FurnaceByDay else FurnaceByNight
    private val FurnaceByNight = Mood(
        light = Color(0xFFFFC27A),
        core = Color(0xFFFF5B3A),
        deep = Color(0xFF2A120D)
    )

    private val GloryByDay = GloryByNight.byDay()
    private val LamentByDay = LamentByNight.byDay()
    private val WatchByDay = WatchByNight.byDay()
    private val StillWatersByDay = StillWatersByNight.byDay()
    private val FurnaceByDay = FurnaceByNight.byDay()
}

/**
 * The same mood in sunlight: its core deepened to read on the mist, its text deeper still, and its
 * background a pale wash of the core over the warm mist instead of a dark tint.
 */
private fun Mood.byDay() = Mood(
    light = core.inDaylight(0.1f),
    core = core.inDaylight(0.22f),
    deep = lerp(NeutralPalette.Sunlit.surface, core, 0.14f)
)
