package com.lumenchord.pianoweave.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.math.hypot
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The round, softly tinted icon that leads a card's title, as on the Transcribe card. */
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(tint.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.57f))
    }
}

/**
 * A faint wash of [color] fading down from the top of a card, so cards of one screen read as a set.
 * [strength] from 0 to 1 dims it, e.g. to hand the light to another card.
 */
fun Modifier.cardWash(color: Color, strength: Float = 1f): Modifier =
    if (strength <= 0f) this
    else background(Brush.verticalGradient(listOf(color.copy(alpha = 0.07f * strength * glowScale(color)), Color.Transparent)))

/**
 * How much to dim a glow of [color] so every theme's glow looks about as strong. Two things make a glow
 * loud on a dark surface, and either is enough:
 * - brightness: a bright accent (yellow, cyan, silver) lays a whitish haze;
 * - saturation: a vivid one (scarlet, violet, electric blue) lays a strong color cast, and vivid colors
 *   also look brighter than they measure.
 * Saturation is OKLCH chroma, which tracks how colorful a color looks across hues. A glow is dimmed by
 * whichever of the two is stronger, so a color that is both isn't dimmed twice. Calm accents (rose gold,
 * slate) keep most of the glow; the loudest keep [GLOW_MIN_SCALE] of it.
 */
internal fun glowScale(color: Color): Float {
    val byBrightness = (GLOW_REFERENCE_LUMINANCE / color.luminance().coerceAtLeast(0.01f)).coerceIn(GLOW_MIN_SCALE, 1f)
    val oklab = color.convert(ColorSpaces.Oklab)
    val chroma = hypot(oklab.green, oklab.blue)
    val bySaturation = (GLOW_REFERENCE_CHROMA / chroma.coerceAtLeast(0.001f)).coerceIn(GLOW_MIN_SCALE, 1f)
    return minOf(byBrightness, bySaturation)
}

/** Relative luminance up to which a glow keeps its full strength, about that of a mid accent. */
private const val GLOW_REFERENCE_LUMINANCE = 0.3f

/** OKLCH chroma up to which a glow keeps its full strength, a little below that of rose gold, so even calm accents are softened a bit. */
private const val GLOW_REFERENCE_CHROMA = 0.08f

/** The loudest accents keep this much of the glow, so it never disappears. */
private const val GLOW_MIN_SCALE = 0.4f

/** A quiet well until focused, so the accent marks only where the user is typing. */
@Composable
fun quietFieldColors(): TextFieldColors {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.secondary
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = accent,
        unfocusedBorderColor = onSurface.copy(alpha = 0.14f),
        disabledBorderColor = onSurface.copy(alpha = 0.08f),
        focusedContainerColor = onSurface.copy(alpha = 0.04f),
        unfocusedContainerColor = onSurface.copy(alpha = 0.04f),
        cursorColor = accent
    )
}
