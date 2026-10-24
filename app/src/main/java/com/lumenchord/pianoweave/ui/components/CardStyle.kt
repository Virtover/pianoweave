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

/** A faint wash of [color] fading down from the top of a card, so cards of one screen read as a set. */
fun Modifier.cardWash(color: Color): Modifier =
    background(Brush.verticalGradient(listOf(color.copy(alpha = 0.07f), Color.Transparent)))

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
