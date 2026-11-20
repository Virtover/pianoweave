package com.lumenchord.pianoweave.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.lumenchord.pianoweave.ui.theme.AppThemeManager
import kotlin.math.max

/** Sunlight through warm mist: the glory over the light themes ("the Lord will be your everlasting light", Isaiah 60:19). */
private val SunGlow = Color(0xFFFFE2A6)

/**
 * The app's background: [background] with a faint glow of the theme's [accent] from the top, like a
 * lamp above the page. "Your word is a lamp to my feet and a light to my path" (Psalm 119:105).
 */
fun Modifier.lampGlow(background: Color, accent: Color): Modifier = drawBehind {
    drawRect(background)
    if (AppThemeManager.isLight) {
        // By day the light is the sun's, not a lamp's: a wide golden radiance from above, scattered
        // through the mist, with a second, softer pool lower down where it settles
        drawRect(
            Brush.radialGradient(
                colors = listOf(SunGlow.copy(alpha = 0.45f), SunGlow.copy(alpha = 0.15f), Color.Transparent),
                center = Offset(size.width * 0.5f, -size.height * 0.05f),
                radius = max(size.width, size.height) * 0.85f
            )
        )
        drawRect(
            Brush.radialGradient(
                // Warm, not white: light that rests the eyes
                colors = listOf(Color(0xFFFFF0D0).copy(alpha = 0.25f), Color.Transparent),
                center = Offset(size.width * 0.3f, size.height * 0.6f),
                radius = max(size.width, size.height) * 0.6f
            )
        )
    }
    drawRect(
        Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.08f), Color.Transparent),
            center = Offset(size.width * 0.5f, 0f),
            radius = max(size.width, size.height) * 0.7f
        )
    )
}
