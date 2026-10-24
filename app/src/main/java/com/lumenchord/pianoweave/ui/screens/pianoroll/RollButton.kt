package com.lumenchord.pianoweave.ui.screens.pianoroll

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import com.lumenchord.pianoweave.ui.theme.PillShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme

/**
 * A control over the falling notes, round like the play controls: a circle for an icon, a pill once it
 * has text. Off, a dark tile with a faint edge and an accent icon; on, a soft tint and border of the
 * accent with a sheen across the top, which reads as "active" without a solid block of color. The
 * change between the two fades rather than snapping, and the button gives a little when pressed.
 * Content gets its color from [LocalContentColor].
 */
@Composable
internal fun RollButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isOn: Boolean = false,
    onClickLabel: String? = null,
    role: Role = Role.Button,
    size: Dp = 40.dp,
    shape: Shape = PillShape,
    horizontalPadding: Dp = 0.dp,
    content: @Composable RowScope.() -> Unit
) {
    val accent = LocalAppTheme.current.primaryColor
    val on by animateFloatAsState(if (isOn) 1f else 0f, tween(durationMillis = 180), label = "rollButtonOn")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "rollButtonPress")

    Row(
        modifier = modifier
            .height(size)
            .widthIn(min = size)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(shape)
            .background(lerp(ColorSurface.copy(alpha = 0.8f), accent.copy(alpha = 0.20f), on))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f * on), Color.Transparent)))
            .border(1.dp, lerp(Color.White.copy(alpha = 0.12f), accent.copy(alpha = 0.75f), on), shape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                onClickLabel = onClickLabel,
                role = role,
                onClick = onClick
            )
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides accent) {
            content()
        }
    }
}
