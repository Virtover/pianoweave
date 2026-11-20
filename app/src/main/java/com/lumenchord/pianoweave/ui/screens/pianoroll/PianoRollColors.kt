package com.lumenchord.pianoweave.ui.screens.pianoroll

import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val ColorBg: Color
    @Composable get() = MaterialTheme.colorScheme.background

internal val ColorSurface: Color
    @Composable get() = MaterialTheme.colorScheme.surface

internal val ColorSlate: Color
    @Composable get() = MaterialTheme.colorScheme.tertiaryContainer

internal val ColorTextDim: Color
    @Composable get() = MaterialTheme.colorScheme.tertiary

/** Behind a short notice over the stage: a dark veil at night, a sunlit card by day. */
internal val ColorOverlay: Color
    @Composable get() = if (LocalAppTheme.current.isLight) MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    else Color.Black.copy(alpha = 0.7f)

internal val ColorKeyWhite = Color(0xFFE6E6E6)
internal val ColorKeyBlack = Color(0xFF030507)
