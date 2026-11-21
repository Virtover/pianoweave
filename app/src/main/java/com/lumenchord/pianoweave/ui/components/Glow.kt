package com.lumenchord.pianoweave.ui.components

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
    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFFFFFBF0).copy(alpha = 0.75f),
            0.22f to Color(0xFFFFFBF0).copy(alpha = 0.25f),
            0.4f to Color.Transparent
        )
    )
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
