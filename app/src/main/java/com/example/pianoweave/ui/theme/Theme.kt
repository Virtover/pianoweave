package com.example.pianoweave.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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
    background = Color(0xFF0F1113),   // Deep piano black
    surface = Color(0xFF16181C),      // Sleek surface card
    onBackground = Color(0xFFE3E4E8),
    onSurface = Color(0xFFE3E4E8),
    onSurfaceVariant = Color(0xFF90949F),
    outline = Color(0xFF43474E),
    error = Color(0xFFFFB4AB)
)

@Composable
fun PianoWeaveTheme(
    theme: AppTheme = AppThemeManager.defaultTheme,
    content: @Composable () -> Unit
) {
    val dynamicColorScheme = PianoDarkColorScheme.copy(
        secondary = theme.primaryColor,
        onSecondary = if (theme.isLightAccent) Color(0xFF101214) else Color.White,
        outline = theme.primaryColor.copy(alpha = 0.4f)
    )

    CompositionLocalProvider(LocalAppTheme provides theme) {
        MaterialTheme(
            colorScheme = dynamicColorScheme,
            content = content
        )
    }
}
