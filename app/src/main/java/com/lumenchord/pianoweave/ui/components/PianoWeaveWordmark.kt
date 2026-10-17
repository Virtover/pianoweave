package com.lumenchord.pianoweave.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import com.lumenchord.pianoweave.ui.theme.DisplayFontFamily
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme

/**
 * "PIANO WEAVE" in the style of the logo: thin, widely spaced capitals with a lamplight gradient
 * and a soft glow, tinted by the current theme. [stacked] puts the two words on separate lines.
 */
@Composable
fun PianoWeaveWordmark(
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    stacked: Boolean = false
) {
    val theme = LocalAppTheme.current
    val style = remember(theme, fontSize) {
        val start = theme.primaryColor
        val end = theme.palette.text
        TextStyle(
            brush = Brush.horizontalGradient(listOf(start, lerp(start, end, 0.55f), end)),
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Light,
            fontSize = fontSize,
            letterSpacing = 0.26.em,
            shadow = Shadow(color = start.copy(alpha = 0.3f), offset = Offset.Zero, blurRadius = 18f)
        )
    }

    if (stacked) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            WordmarkLine("PIANO", style)
            WordmarkLine("WEAVE", style)
        }
    } else {
        WordmarkLine("PIANO WEAVE", style, modifier)
    }
}

@Composable
private fun WordmarkLine(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier
    )
}
