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

        /** Slate with its slight cool tint turned warm: same brightness, warm instead of blue. */
        val WarmNight = NeutralPalette(
            text = Color(0xFFF1EFEC),
            onText = Color(0xFF141210),
            container = Color(0xFF332E2C),
            secondaryContainer = Color(0xFF2E2722),
            background = Color(0xFF13110F),
            surface = Color(0xFF1C1816),
            dialogSurface = Color(0xFF221B16),
            onSurface = Color(0xFFE8E4E3),
            onSurfaceVariant = Color(0xFF9F9490),
            muted = Color(0xFF9E948B),
            mutedContainer = Color(0xFF3D3630),
            errorContainer = Color(0xFF221B1C),
            stageTop = Color(0xFF140D09),
            stageMiddle = Color(0xFF17110D),
            stageBottom = Color(0xFF0F0A07),
            stageLane = Color(0xFF221B16)
        )
    }
}

object AppThemeManager {

    /** Matches the app icon: lamplight cream on warm night. */
    private val lamplightTheme = createTheme(
        id = "lamplight",
        name = "Lamplight",
        primary = Color(0xFFF7DDBA),
        customWaitTarget = Color(0xFF00D2FF),
        customSuccess = Color(0xFF2EA043),
        customBaseline = Color(0xFFFD9B4A),
        palette = NeutralPalette.WarmNight
    )

    private val classicGoldTheme = createTheme(
        id = "gold",
        name = "Classic Gold",
        primary = Color(0xFFD4AF37),
        customWaitTarget = Color(0xFF00D2FF),
        customSuccess = Color(0xFF2EA043),
        customBaseline = Color(0xFFFD9B4A)
    )

    val defaultTheme = classicGoldTheme

    val themes: List<AppTheme> = listOf(
        lamplightTheme,
        classicGoldTheme,
        createTheme("amber", "Warm Amber", Color(0xFFFFB300)),
        createTheme("sunburst", "Sunburst Yellow", Color(0xFFFFE812), isLightAccent = true),
        createTheme("orange", "Sunset Orange", Color(0xFFFF6D00)),
        createTheme("coral", "Coral Flame", Color(0xFFFF5722)),
        createTheme("crimson", "Crimson Red", Color(0xFFE53935)),
        createTheme("ruby", "Ruby Scarlet", Color(0xFFD81B60)),
        createTheme("rose", "Rose Pink", Color(0xFFEC407A)),
        createTheme("magenta", "Hot Magenta", Color(0xFFF06292)),
        createTheme("electric_purple", "Electric Purple", Color(0xFFAB47BC)),
        createTheme("royal_violet", "Royal Violet", Color(0xFF8E24AA)),
        createTheme("indigo", "Deep Indigo", Color(0xFF5C6BC0)),
        createTheme("sapphire", "Sapphire Blue", Color(0xFF1E88E5)),
        createTheme("electric_blue", "Electric Blue", Color(0xFF00B0FF)),
        createTheme("cyan", "Ocean Cyan", Color(0xFF00E5FF), isLightAccent = true),
        createTheme("aqua", "Deep Aqua", Color(0xFF00ACC1)),
        createTheme("teal", "Teal Breeze", Color(0xFF009688)),
        createTheme("emerald", "Emerald Green", Color(0xFF2ECC71)),
        createTheme("mint", "Vibrant Mint", Color(0xFF00E676), isLightAccent = true),
        createTheme("lime", "Neon Lime", Color(0xFF76FF03), isLightAccent = true),
        createTheme("chartreuse", "Chartreuse", Color(0xFFC0CA33), isLightAccent = true),
        createTheme("olive", "Olive Gold", Color(0xFFAFB42B)),
        createTheme("copper", "Copper Bronze", Color(0xFFD35400)),
        createTheme("rose_gold", "Rose Gold", Color(0xFFB76E79)),
        createTheme("silver", "Platinum Silver", Color(0xFFECEFF1), isLightAccent = true),
        createTheme("titanium", "Titanium Gray", Color(0xFF90A4AE)),
        createTheme("lavender", "Lavender Glow", Color(0xFFB388FF)),
        createTheme("cherry", "Cherry Blossom", Color(0xFFFF80AB)),
        createTheme("neon_cyan", "Neon Cyan", Color(0xFF18FFFF), isLightAccent = true),
        createTheme("toxic_green", "Toxic Green", Color(0xFF64DD17), isLightAccent = true),
        createTheme("solar", "Solar Flare", Color(0xFFFF3D00)),
        createTheme("deep_velvet", "Deep Velvet", Color(0xFF6A1B9A)),
        createTheme("cyber_turquoise", "Cyber Turquoise", Color(0xFF1DE9B6), isLightAccent = true),
        createTheme("champagne", "Champagne", Color(0xFFF7E7CE), isLightAccent = true),
        createTheme("midnight", "Midnight Blue", Color(0xFF3D5AFE)),
        createTheme("goldenrod", "Goldenrod", Color(0xFFDAA520))
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
        palette: NeutralPalette = NeutralPalette.Slate
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
            palette = palette
        )
    }
}

val LocalAppTheme = compositionLocalOf { AppThemeManager.defaultTheme }
