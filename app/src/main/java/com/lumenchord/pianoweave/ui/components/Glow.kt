package com.lumenchord.pianoweave.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.max

/**
 * The app's background: [background] with a faint glow of the theme's [accent] from the top, like a
 * lamp above the page. "Your word is a lamp to my feet and a light to my path" (Psalm 119:105).
 */
fun Modifier.lampGlow(background: Color, accent: Color): Modifier = drawBehind {
    drawRect(background)
    drawRect(
        Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.08f), Color.Transparent),
            center = Offset(size.width * 0.5f, 0f),
            radius = max(size.width, size.height) * 0.7f
        )
    )
}
