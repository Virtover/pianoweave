package com.lumenchord.pianoweave.ui.screens.pianoroll.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lumenchord.pianoweave.ui.components.SettingToggleRow
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel

/**
 * The click and count-in. Both follow the song's own beats ([hasBeats]) and play along steadily; without
 * beats, or in wait mode, the switches stay greyed out and say why.
 */
@Composable
fun MetronomeSection(viewModel: PianoWeaveViewModel, hasBeats: Boolean) {
    val context = LocalContext.current
    val accent = LocalAppTheme.current.primaryColor

    // Wait mode stops at every chord, so neither can play along with it (turning it on turns the metronome off)
    val isWaitMode = viewModel.isWaitModeEnabled
    val isAvailable = hasBeats && !isWaitMode

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingToggleRow(
            title = "Metronome",
            subtitle = when {
                !hasBeats -> "No beat information for this piece."
                isWaitMode -> "Not available in wait mode. Turn wait mode off to use it."
                else -> "A soft click on every beat, following the recording's tempo. " +
                    "Hold the speed button to turn it on or off while playing."
            },
            checked = isAvailable && viewModel.isMetronomeEnabled,
            onCheckedChange = { viewModel.setMetronomeEnabled(context, it) },
            enabled = isAvailable,
            accent = accent
        )
        SettingToggleRow(
            title = "Count-in",
            subtitle = "One bar counted on screen before playing, and each time a loop starts over. " +
                "With the metronome on, you hear it too, starting on a beat.",
            checked = isAvailable && viewModel.isCountInEnabled,
            onCheckedChange = { viewModel.setCountInEnabled(context, it) },
            enabled = isAvailable,
            accent = accent
        )
    }
}
