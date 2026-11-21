package com.lumenchord.pianoweave.ui.theme

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

import androidx.compose.runtime.CompositionLocalProvider

val PianoDarkColorScheme = darkColorScheme(
    primary = Color(0xFFECEFF1),      // Premium silver/off-white
    onPrimary = Color(0xFF101214),
    primaryContainer = Color(0xFF2C2E33),
    onPrimaryContainer = Color(0xFFECEFF1),
    secondary = Color(0xFFD4AF37),    // Classic golden accent
    onSecondary = Color(0xFF101214),
    secondaryContainer = Color(0xFF22272E),
    background = Color(0xFF0F1113),   // Deep piano black
    surface = Color(0xFF16181C),      // Sleek surface card
    onBackground = Color(0xFFE3E4E8),
    onSurface = Color(0xFFE3E4E8),
    onSurfaceVariant = Color(0xFF90949F),
    outline = Color(0xFF43474E),
    error = Color(0xFFE57373),
    errorContainer = Color(0xFF221B1C),
    onErrorContainer = Color(0xFFE57373),
    tertiary = Color(0xFF8B949E),
    tertiaryContainer = Color(0xFF30363D)
)

/** The light themes' base: [NeutralPalette.Sunlit], each theme then sets its own colors over it. */
val PianoLightColorScheme = lightColorScheme(
    primary = NeutralPalette.Sunlit.text,
    onPrimary = NeutralPalette.Sunlit.onText,
    background = NeutralPalette.Sunlit.background,
    surface = NeutralPalette.Sunlit.surface,
    onBackground = NeutralPalette.Sunlit.onSurface,
    onSurface = NeutralPalette.Sunlit.onSurface,
    onSurfaceVariant = NeutralPalette.Sunlit.onSurfaceVariant,
    outline = Color(0xFFC9B79C)
)

@Composable
fun PianoWeaveTheme(
    theme: AppTheme = AppThemeManager.defaultTheme,
    content: @Composable () -> Unit
) {
    val palette = theme.palette
    // By day the Material defaults (ripples, disabled states, scrims) must be the light ones too
    val base = if (theme.isLight) PianoLightColorScheme else PianoDarkColorScheme
    val dynamicColorScheme = base.copy(
        primary = palette.text,
        onPrimary = palette.onText,
        primaryContainer = palette.container,
        onPrimaryContainer = palette.text,
        secondaryContainer = palette.secondaryContainer,
        background = palette.background,
        surface = palette.surface,
        onBackground = palette.onSurface,
        onSurface = palette.onSurface,
        onSurfaceVariant = palette.onSurfaceVariant,
        // Errors are lament, not alarm (Psalm 56:8): see Moods.Lament
        error = Moods.Lament.core,
        errorContainer = Moods.Lament.deep,
        onErrorContainer = Moods.Lament.light,
        tertiary = palette.muted,
        tertiaryContainer = palette.mutedContainer,
        secondary = theme.primaryColor,
        onSecondary = if (theme.isLightAccent) Color(0xFF101214) else Color.White,
        outline = theme.primaryColor.copy(alpha = 0.4f)
    )

    CompositionLocalProvider(LocalAppTheme provides theme) {
        MaterialTheme(
            colorScheme = dynamicColorScheme,
            typography = PianoTypography,
            content = content
        )
    }
}

/**
 * The shape of every button: a full pill, so anything that can be pressed reads the same across cards
 * and dialogs. Cards, fields and tiles keep their rounded rectangles, which tells them apart.
 */
val PillShape = RoundedCornerShape(percent = 50)

/**
 * By day the key buttons (Transcribe, Done, the chosen Dark / Light) are gold leaf: the theme's [AppTheme.fill],
 * lit from within, with the ink on it. At night this adds nothing and the button keeps its accent fill.
 */
fun Modifier.goldLeaf(theme: AppTheme, shape: Shape = PillShape): Modifier =
    theme.fill?.let { background(Brush.verticalGradient(it), shape) } ?: this

/** A key button's container: clear over the gold leaf by day, the accent at night. */
val AppTheme.keyButtonContainer: Color get() = if (fill != null) Color.Transparent else primaryColor

/** What is written on a key button: the ink on gold leaf by day, black or white on the accent at night. */
val AppTheme.onKeyButton: Color get() = when {
    fill != null -> NeutralPalette.Sunlit.text
    isLightAccent -> Color.Black
    else -> Color.White
}
