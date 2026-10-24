package com.lumenchord.pianoweave.ui.screens.pianoroll.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel

private const val MAX_TRANSPOSE = 12

@Composable
fun TranspositionSection(viewModel: PianoWeaveViewModel) {
    val colorAccent = LocalAppTheme.current.primaryColor
    val offset = viewModel.transposeOffset
    val valueText = when {
        offset == 0 -> "Original key"
        else -> "${if (offset > 0) "+" else ""}$offset semitone${if (kotlin.math.abs(offset) == 1) "" else "s"}"
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SettingHeader(
            title = "Transposition",
            subtitle = "Shift every note up or down",
            value = valueText,
            isDefault = offset == 0,
            accent = colorAccent,
            onReset = { viewModel.transposeOffset = 0 }
        )
        StepSlider(
            stepCount = 2 * MAX_TRANSPOSE + 1,
            index = offset + MAX_TRANSPOSE,
            onIndexChange = { viewModel.transposeOffset = it - MAX_TRANSPOSE },
            originIndex = MAX_TRANSPOSE,
            // An octave each way, marked every half octave
            labels = listOf(-12, -6, 0, 6, 12).associate { it + MAX_TRANSPOSE to if (it > 0) "+$it" else "$it" },
            accent = colorAccent,
            valueDescription = "Transposition, $valueText"
        )
    }
}
