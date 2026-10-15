package com.lumenchord.pianoweave.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.lumenchord.pianoweave.R

/**
 * Playfair Display (SIL OFL, license in assets/licenses) for headings, matching the classic
 * gold-on-black piano look. It is a variable font, so each weight sets its own variation.
 */
@OptIn(ExperimentalTextApi::class)
val DisplayFontFamily = FontFamily(
    listOf(FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold, FontWeight.Black).map { weight ->
        Font(
            R.font.playfair_display,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
        )
    }
)

private fun TextStyle.display() = copy(fontFamily = DisplayFontFamily)

/** Material defaults, with headings in the display font; body and labels stay in the system sans-serif. */
val PianoTypography = Typography().run {
    copy(
        displayLarge = displayLarge.display(),
        displayMedium = displayMedium.display(),
        displaySmall = displaySmall.display(),
        headlineLarge = headlineLarge.display(),
        headlineMedium = headlineMedium.display(),
        headlineSmall = headlineSmall.display(),
        titleLarge = titleLarge.display()
    )
}
