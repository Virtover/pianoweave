package com.example.pianoweave.ui.theme

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
    val isLightAccent: Boolean = false
)

object AppThemeManager {

    val defaultTheme = createTheme(
        id = "gold",
        name = "Classic Gold",
        primary = Color(0xFFD4AF37),
        customWaitTarget = Color(0xFF00D2FF),
        customSuccess = Color(0xFF2EA043),
        customBaseline = Color(0xFFFD9B4A)
    )

    val themes: List<AppTheme> = listOf(
        defaultTheme,
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
        customBaseline: Color? = null
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
            isLightAccent = isLightAccent
        )
    }
}

val LocalAppTheme = compositionLocalOf { AppThemeManager.defaultTheme }
