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
 * Commissioner (SIL OFL, license in assets/licenses), the typeface of the Lumenchord wordmark, for
 * the wordmark and headings. The bundled file is a variable font cut down to Latin, with weights
 * 300 to 800. The heaviest weights asked for are drawn a step lighter, which keeps headings crisp
 * instead of blocky, in the spirit of the light wordmark.
 */
@OptIn(ExperimentalTextApi::class)
val DisplayFontFamily = FontFamily(
    listOf(
        FontWeight.Light to 300,
        FontWeight.Normal to 400,
        FontWeight.Medium to 500,
        FontWeight.SemiBold to 560,
        FontWeight.Bold to 600,
        FontWeight.ExtraBold to 640,
        FontWeight.Black to 700
    ).map { (weight, drawn) ->
        Font(
            R.font.commissioner,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(drawn))
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
