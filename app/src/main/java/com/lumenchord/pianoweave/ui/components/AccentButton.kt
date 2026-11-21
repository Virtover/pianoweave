package com.lumenchord.pianoweave.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.theme.goldLeaf
import com.lumenchord.pianoweave.ui.theme.onKeyButton

/**
 * A [Button] that, by day, turns a button filled with the theme's accent into gold leaf (the theme's
 * [com.lumenchord.pianoweave.ui.theme.AppTheme.fill], ink on it), so every accent button in the app shines the same
 * way. Any other button, and every button at night, is exactly a [Button].
 */
@Composable
fun AccentButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val theme = LocalAppTheme.current
    val isLeaf = enabled && theme.fill != null && colors.containerColor == theme.primaryColor
    Button(
        onClick = onClick,
        modifier = if (isLeaf) modifier.clip(shape).goldLeaf(theme, shape) else modifier,
        enabled = enabled,
        shape = shape,
        colors = if (isLeaf) colors.copy(containerColor = Color.Transparent, contentColor = theme.onKeyButton) else colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}
