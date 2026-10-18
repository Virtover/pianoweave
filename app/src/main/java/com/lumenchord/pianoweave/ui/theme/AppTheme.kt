package com.lumenchord.pianoweave.ui.theme

import android.graphics.Color as AndroidColor
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

data class AppTheme(
    val id: String,
    val name: String,
    val primaryColor: Color,
    val lightColor: Color,
    val upcomingColor: Color,
    val waitTargetColor: Color,
    val waitTargetLightColor: Color,
    val targetColor: Color,
    val baselineColor: Color,
    val baselineGlowColor: Color,
    val successColor: Color,
    val successLightColor: Color,
    val isLightAccent: Boolean = false,
    val palette: NeutralPalette = NeutralPalette.Slate
)

/** The non-accent colors: backgrounds, cards, text and the falling-notes stage. */
data class NeutralPalette(
    val text: Color,
    val onText: Color,
    val container: Color,
    val secondaryContainer: Color,
    val background: Color,
    val surface: Color,
    val dialogSurface: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val muted: Color,
    val mutedContainer: Color,
    val errorContainer: Color,
    val stageTop: Color,
    val stageMiddle: Color,
    val stageBottom: Color,
    val stageLane: Color
) {
    companion object {
        /** Cool piano black, used by every accent theme. */
        val Slate = NeutralPalette(
            text = Color(0xFFECEFF1),
            onText = Color(0xFF101214),
            container = Color(0xFF2C2E33),
            secondaryContainer = Color(0xFF22272E),
            background = Color(0xFF0F1113),
            surface = Color(0xFF16181C),
            dialogSurface = Color(0xFF161B22),
            onSurface = Color(0xFFE3E4E8),
            onSurfaceVariant = Color(0xFF90949F),
            muted = Color(0xFF8B949E),
            mutedContainer = Color(0xFF30363D),
            errorContainer = Color(0xFF221B1C),
            stageTop = Color(0xFF090D14),
            stageMiddle = Color(0xFF0D1117),
            stageBottom = Color(0xFF070A0F),
            stageLane = Color(0xFF161B22)
        )

        /**
         * Slate with its slight tint turned to [hue] (0-360), so the backgrounds quietly take on
         * the theme's own color while keeping Slate's brightness. [strength] scales how much tint
         * there is (1 = as much as Slate's blue) and [depth] below 1 makes the darks deeper.
         */
        fun tinted(hue: Float, strength: Float = 1f, depth: Float = 1f): NeutralPalette {
            fun Color.retint(isDark: Boolean = true): Color {
                val hsv = FloatArray(3)
                AndroidColor.colorToHSV(toArgb(), hsv)
                val value = if (isDark) hsv[2] * depth else hsv[2]
                return Color(AndroidColor.HSVToColor(floatArrayOf(hue, (hsv[1] * strength).coerceIn(0f, 1f), value.coerceIn(0f, 1f))))
            }
            return with(Slate) {
                NeutralPalette(
                    text = text.retint(isDark = false),
                    onText = onText.retint(),
                    container = container.retint(),
                    secondaryContainer = secondaryContainer.retint(),
                    background = background.retint(),
                    surface = surface.retint(),
                    dialogSurface = dialogSurface.retint(),
                    onSurface = onSurface.retint(isDark = false),
                    onSurfaceVariant = onSurfaceVariant.retint(isDark = false),
                    muted = muted.retint(isDark = false),
                    mutedContainer = mutedContainer.retint(),
                    errorContainer = errorContainer,
                    stageTop = stageTop.retint(),
                    stageMiddle = stageMiddle.retint(),
                    stageBottom = stageBottom.retint(),
                    stageLane = stageLane.retint()
                )
            }
        }
    }
}

object AppThemeManager {

    /**
     * "Your word is a lamp to my feet and a light to my path" (Psalm 119:105). Deep and warm, with
     * honey-gold light and lively companion colors: sky blue for notes to play, fresh green for
     * notes played right, a coral hit line.
     */
    private val lamplightTheme = createTheme(
        id = "lamplight",
        name = "Lamplight",
        primary = Color(0xFFFFC266),
        customWaitTarget = Color(0xFF5CCFFF),
        customSuccess = Color(0xFF4ADE80),
        customBaseline = Color(0xFFFF8A5C),
        palette = NeutralPalette.tinted(hue = 28f, strength = 1.35f, depth = 0.88f)
    )

    private val classicGoldTheme = createTheme(
        id = "gold",
        name = "Classic Gold",
        primary = Color(0xFFD4AF37),
        customWaitTarget = Color(0xFF00D2FF),
        customSuccess = Color(0xFF2EA043),
        customBaseline = Color(0xFFFD9B4A),
        tint = 0.8f
    )

    val defaultTheme = classicGoldTheme

    /**
     * Names come from the Bible's imagery of creation, worship and joy. Ids stay as they were, so
     * a saved choice keeps working.
     */
    val themes: List<AppTheme> = listOf(
        lamplightTheme,
        classicGoldTheme,
        createTheme("amber", "Honey", Color(0xFFFFB300)), // Psalm 19:10
        createTheme("sunburst", "Morning Joy", Color(0xFFFFD93D)), // Psalm 30:5
        createTheme("orange", "Dayspring", Color(0xFFFF7A1A)), // Luke 1:78
        createTheme("coral", "Hearth", Color(0xFFFF5722)),
        createTheme("solar", "Burning Bush", Color(0xFFFF3D00)), // Exodus 3:2
        createTheme("copper", "Potter's Clay", Color(0xFFD35400)), // Isaiah 64:8
        createTheme("goldenrod", "Harvest", Color(0xFFDAA520)), // Psalm 126:6
        createTheme("crimson", "Scarlet Cord", Color(0xFFE53935)), // Joshua 2:18
        createTheme("ruby", "Pomegranate", Color(0xFFD81B60)), // Song of Songs 4:3
        createTheme("rose", "Rose of Sharon", Color(0xFFEC407A)), // Song of Songs 2:1
        createTheme("magenta", "Desert Bloom", Color(0xFFF06292)), // Isaiah 35:1
        createTheme("cherry", "Almond Blossom", Color(0xFFFF8FB8)), // Numbers 17:8
        createTheme("rose_gold", "Rose Gold", Color(0xFFB76E79)),
        createTheme("lavender", "Lavender", Color(0xFFB388FF)),
        createTheme("electric_purple", "Iris", Color(0xFFAB47BC)),
        createTheme("royal_violet", "Royal Robe", Color(0xFF8E24AA)), // 1 Peter 2:9
        createTheme("deep_velvet", "Night Watch", Color(0xFF7B2CBF)), // Psalm 63:6
        createTheme("indigo", "Evening Prayer", Color(0xFF5C6BC0)), // Psalm 141:2
        createTheme("midnight", "Starry Host", Color(0xFF3D5AFE)), // Psalm 147:4
        createTheme("sapphire", "Sapphire Heaven", Color(0xFF1E88E5)), // Exodus 24:10
        createTheme("electric_blue", "Open Sky", Color(0xFF00B0FF)), // Psalm 19:1
        createTheme("cyan", "Living Water", Color(0xFF00E5FF)), // John 4:10
        createTheme("neon_cyan", "Morning Dew", Color(0xFF8BE9F5)), // Hosea 14:5
        createTheme("aqua", "Still Waters", Color(0xFF00ACC1)), // Psalm 23:2
        createTheme("cyber_turquoise", "Galilee", Color(0xFF1DE9B6)), // Matthew 4:18
        createTheme("teal", "Riverside", Color(0xFF009688)), // Psalm 1:3
        createTheme("mint", "Spring Rain", Color(0xFF2EE59D)), // Joel 2:23
        createTheme("emerald", "Green Pastures", Color(0xFF2ECC71)), // Psalm 23:2
        createTheme("toxic_green", "Evergreen", Color(0xFF43A047)), // Psalm 1:3, "its leaf does not wither"
        createTheme("lime", "New Growth", Color(0xFFA3E635)), // Isaiah 43:19
        createTheme("chartreuse", "Vineyard", Color(0xFFC0CA33)), // John 15:5
        createTheme("olive", "Olive Tree", Color(0xFFAFB42B)), // Psalm 52:8
        createTheme("champagne", "Fine Linen", Color(0xFFF7E7CE), tint = 0.6f), // Revelation 19:8
        createTheme("silver", "Refined Silver", Color(0xFFECEFF1)), // Psalm 12:6
        createTheme("titanium", "Rock of Refuge", Color(0xFF90A4AE)) // Psalm 18:2
    )

    fun getTheme(id: String?): AppTheme {
        return themes.find { it.id == id } ?: defaultTheme
    }

    private fun createTheme(
        id: String,
        name: String,
        primary: Color,
        isLightAccent: Boolean = false,
        customWaitTarget: Color? = null,
        customSuccess: Color? = null,
        customBaseline: Color? = null,
        /** How strongly the backgrounds take on the theme's color; 0 keeps plain Slate. */
        tint: Float = 0.9f,
        palette: NeutralPalette? = null
    ): AppTheme {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(primary.toArgb(), hsv)
        val hue = hsv[0]
        val sat = hsv[1]
        val valVal = hsv[2]

        val lightColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, maxOf(0f, sat * 0.4f), minOf(1f, valVal * 1.25f))))
        val upcomingColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, minOf(1f, sat * 1.05f), valVal * 0.85f)))

        val waitTargetColor = customWaitTarget ?: run {
            val targetHue = if (hue in 160f..220f) {
                45f
            } else if (hue in 40f..80f) {
                195f
            } else {
                (hue + 150f) % 360f
            }
            Color(AndroidColor.HSVToColor(floatArrayOf(targetHue, 0.85f, 1f)))
        }

        val waitTargetHsv = FloatArray(3)
        AndroidColor.colorToHSV(waitTargetColor.toArgb(), waitTargetHsv)
        val waitTargetLightColor = Color(AndroidColor.HSVToColor(floatArrayOf(waitTargetHsv[0], 0.3f, 1f)))

        val targetColor = Color(AndroidColor.HSVToColor(floatArrayOf((hue + 15f) % 360f, minOf(1f, sat * 1.1f), minOf(1f, valVal * 1.1f))))
        val baselineColor = customBaseline ?: Color(AndroidColor.HSVToColor(floatArrayOf((hue - 20f + 360f) % 360f, minOf(1f, sat * 1.2f), 1f)))
        val baselineGlowColor = Color(AndroidColor.HSVToColor(floatArrayOf((hue - 30f + 360f) % 360f, sat, valVal * 0.9f)))

        val successColor = customSuccess ?: run {
            if (hue in 80f..160f) {
                Color(0xFF00B0FF)
            } else {
                Color(0xFF2EA043)
            }
        }
        val successLightColor = if (hue in 80f..160f) Color(0xFF80D8FF) else Color(0xFF69C67E)

        val colorInt = primary.toArgb()
        val red = AndroidColor.red(colorInt) / 255f
        val green = AndroidColor.green(colorInt) / 255f
        val blue = AndroidColor.blue(colorInt) / 255f
        val luminance = 0.299f * red + 0.587f * green + 0.114f * blue
        val resolvedIsLightAccent = isLightAccent || (luminance > 0.55f)

        return AppTheme(
            id = id,
            name = name,
            primaryColor = primary,
            lightColor = lightColor,
            upcomingColor = upcomingColor,
            waitTargetColor = waitTargetColor,
            waitTargetLightColor = waitTargetLightColor,
            targetColor = targetColor,
            baselineColor = baselineColor,
            baselineGlowColor = baselineGlowColor,
            successColor = successColor,
            successLightColor = successLightColor,
            isLightAccent = resolvedIsLightAccent,
            // Near-gray accents have no real hue to borrow, so they keep Slate
            palette = palette ?: if (sat < 0.15f || tint <= 0f) NeutralPalette.Slate else NeutralPalette.tinted(hue, strength = tint)
        )
    }
}

val LocalAppTheme = compositionLocalOf { AppThemeManager.defaultTheme }
