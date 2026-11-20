package com.lumenchord.pianoweave.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.lumenchord.pianoweave.ui.theme.AppThemeManager
import kotlin.math.max

/**
 * Soft beams of sunlight falling from above the top edge and fanning out, fading as they come down, like light
 * through a high window: "the path of the righteous is like the morning sun, shining ever brighter" (Proverbs
 * 4:18). Drawn behind everything by day ([lampGlow]) and over the stage's sky; [strength] scales them.
 */
fun DrawScope.drawSunBeams(color: Color, strength: Float) {
    val source = Offset(size.width * 0.5f, -size.height * 0.12f)
    val reach = size.height * 0.95f
    // (direction from straight down as a fraction of the width, half-width, alpha)
    val beams = listOf(
        Triple(-0.62f, 0.07f, 0.16f), Triple(-0.3f, 0.05f, 0.12f), Triple(-0.05f, 0.08f, 0.2f),
        Triple(0.24f, 0.05f, 0.13f), Triple(0.55f, 0.07f, 0.15f)
    )
    for ((dir, half, alpha) in beams) {
        val endX = source.x + dir * size.width
        val path = Path().apply {
            moveTo(source.x, source.y)
            lineTo(endX - half * size.width, source.y + reach)
            lineTo(endX + half * size.width, source.y + reach)
            close()
        }
        drawPath(
            path,
            Brush.verticalGradient(
                listOf(color.copy(alpha = alpha * strength), color.copy(alpha = alpha * strength * 0.35f), Color.Transparent),
                startY = 0f,
                endY = source.y + reach
            )
        )
    }
}

/** Sunlight through warm mist: the glory over the light themes ("the Lord will be your everlasting light", Isaiah 60:19). */
internal val SunGlow = Color(0xFFFFD98A)

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
                colors = listOf(SunGlow.copy(alpha = 0.6f), SunGlow.copy(alpha = 0.18f), Color.Transparent),
                center = Offset(size.width * 0.5f, -size.height * 0.08f),
                radius = max(size.width, size.height) * 0.8f
            )
        )
        drawSunBeams(SunGlow, strength = 1.7f)
    }
    drawRect(
        Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.08f), Color.Transparent),
            center = Offset(size.width * 0.5f, 0f),
            radius = max(size.width, size.height) * 0.7f
        )
    )
}
