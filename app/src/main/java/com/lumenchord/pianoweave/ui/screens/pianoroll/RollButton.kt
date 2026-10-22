package com.lumenchord.pianoweave.ui.screens.pianoroll

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * A control over the falling notes. Off, a dark tile with a slate edge and an accent icon; on, a soft
 * tint and border of the accent, which reads as "active" without a solid block of color. Content gets
 * its color from [LocalContentColor].
 */
@Composable
internal fun RollButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isOn: Boolean = false,
    onClickLabel: String? = null,
    role: Role = Role.Button,
    size: Dp = 40.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    horizontalPadding: Dp = 0.dp,
    content: @Composable RowScope.() -> Unit
) {
    val accent = LocalAppTheme.current.primaryColor
    Row(
        modifier = modifier
            .height(size)
            .widthIn(min = size)
            .clip(shape)
            .background(if (isOn) accent.copy(alpha = 0.20f) else ColorSurface.copy(alpha = 0.8f))
            .border(1.dp, if (isOn) accent.copy(alpha = 0.75f) else ColorSlate.copy(alpha = 0.5f), shape)
            .clickable(onClickLabel = onClickLabel, role = role, onClick = onClick)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides accent) {
            content()
        }
    }
}
