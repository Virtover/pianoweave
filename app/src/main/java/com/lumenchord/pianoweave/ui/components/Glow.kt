package com.lumenchord.pianoweave.ui.components

import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.lumenchord.pianoweave.ui.theme.AppThemeManager
import kotlin.math.max

/**
 * By day, an even soft light across the top of the falling-notes stage, fading within its upper part: the stage
 * is lit from above without any shape of a sun or beams in it.
 */
fun DrawScope.drawStageDaylight() {
    // As in the design: a wide ellipse of light just above the stage, warm white at its heart, gently; never a beam
    val center = Offset(size.width / 2f, 8.dp.toPx())
    val ry = 190.dp.toPx().coerceAtMost(size.height * 0.45f)
    val rx = size.width / 2f + 60.dp.toPx()
    scale(scaleX = rx / ry, scaleY = 1f, pivot = center) {
        drawCircle(
            Brush.radialGradient(
                0f to Color(0xFFFFF0C8).copy(alpha = 0.55f),
                0.35f to Color(0xFFFFDE8C).copy(alpha = 0.26f),
                0.7f to Color.Transparent,
                center = center,
                radius = ry
            ),
            radius = ry,
            center = center
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
    }
    drawRect(
        Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.08f), Color.Transparent),
            center = Offset(size.width * 0.5f, 0f),
            radius = max(size.width, size.height) * 0.7f
        )
    )
}
