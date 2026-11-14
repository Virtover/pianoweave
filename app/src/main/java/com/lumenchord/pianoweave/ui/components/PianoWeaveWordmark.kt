package com.lumenchord.pianoweave.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.font.FontWeight
import com.lumenchord.pianoweave.ui.theme.DisplayFontFamily
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme

/** One pass of the shimmer, there or back. Much slower than the website's 4 s: a lamp's glow, not a marquee. */
private const val SHIMMER_MILLIS = 20000

/** The gradient is this many times wider than the text, like the website's `background-size: 250%`. */
private const val GRADIENT_WIDTH = 2.5f

/**
 * "PIANO WEAVE" in the style of the logo: thin, widely spaced capitals with a soft glow, lit by a
 * shimmer that slowly slides back and forth across it, as on pianoweave.com (`gold-shimmer`). The
 * website's gold is the theme's accent here and its white the theme's text colour.
 * [stacked] puts the two words on separate lines.
 */
@Composable
fun PianoWeaveWordmark(
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    stacked: Boolean = false
) {
    val theme = LocalAppTheme.current
    val accent = theme.primaryColor
    val light = theme.palette.text
    // Website: gold, white 50 %, silver #d8dce3 65 %, gold.
    val stops = remember(accent, light) {
        arrayOf(
            0f to accent,
            0.5f to light,
            0.65f to lerp(light, Color(0xFFD8DCE3), 0.85f),
            1f to accent
        )
    }
    val style = remember(accent, fontSize) {
        TextStyle(
            color = Color.White, // only its alpha matters: the gradient is painted over it
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Light,
            fontSize = fontSize,
            letterSpacing = 0.26.em,
            shadow = Shadow(color = Color.White.copy(alpha = 0.3f), offset = Offset.Zero, blurRadius = 18f)
        )
    }
    val phase = rememberInfiniteTransition(label = "wordmark").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SHIMMER_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wordmarkShimmer"
    )

    if (stacked) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            WordmarkLine("PIANO", style, stops, phase)
            WordmarkLine("WEAVE", style, stops, phase)
        }
    } else {
        WordmarkLine("PIANO WEAVE", style, stops, phase, modifier)
    }
}

/**
 * One line of the wordmark. The gradient is drawn over the text with SrcIn, so it also tints the glow,
 * and [phase] is read only while drawing: the shimmer never recomposes or re-measures the text.
 */
@Composable
private fun WordmarkLine(
    text: String,
    style: TextStyle,
    stops: Array<Pair<Float, Color>>,
    phase: State<Float>,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val width = size.width * GRADIENT_WIDTH
                val travel = width - size.width
                onDrawWithContent {
                    drawContent()
                    // background-position 0 % → 100 %: the gradient's left edge moves from the text's
                    // left edge until its right edge reaches the text's right edge.
                    val startX = -phase.value * travel
                    drawRect(
                        brush = Brush.horizontalGradient(*stops, startX = startX, endX = startX + width),
                        blendMode = BlendMode.SrcIn
                    )
                }
            }
    )
}
