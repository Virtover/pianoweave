package com.lumenchord.pianoweave.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

/**
 * A dialog's panel, lit from above by [light]: a wide, soft pool of it falling from the top edge, and a
 * thin bright rim along that edge, fading out toward the corners. Kin to the cards' flat top wash, but
 * reads as light shining into the dialog. Drawn on the panel itself, so it reaches the very top
 * whatever padding or scrolling the content has.
 */
@Composable
fun DialogSurface(
    light: Color,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    color: Color,
    contentColor: Color = contentColorFor(color),
    border: BorderStroke? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = contentColor,
        border = border
    ) {
        Box(Modifier.dialogLight(light)) {
            content()
        }
    }
}

private fun Modifier.dialogLight(light: Color): Modifier = drawBehind {
    // Bright accents are dimmed so the light looks the same in every theme
    val scale = glowScale(light)
    val top = Offset(size.width / 2f, 0f)
    // A circle squashed into a wide oval: the pool is broad but doesn't reach far down tall dialogs
    val depth = 170.dp.toPx()
    val stretch = (size.width * 0.65f) / depth
    scale(scaleX = stretch, scaleY = 1f, pivot = top) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(light.copy(alpha = 0.09f * scale), light.copy(alpha = 0.03f * scale), Color.Transparent),
                center = top,
                radius = depth
            ),
            radius = depth,
            center = top
        )
    }
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(Color.Transparent, light.copy(alpha = 0.4f * scale), Color.Transparent)
        ),
        size = Size(size.width, 1.5.dp.toPx())
    )
}
