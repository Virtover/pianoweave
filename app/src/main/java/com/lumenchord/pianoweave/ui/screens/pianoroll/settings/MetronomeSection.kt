package com.lumenchord.pianoweave.ui.screens.pianoroll.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lumenchord.pianoweave.ui.components.SettingToggleRow
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel

/**
 * The click and count-in. Both follow the song's own beats ([hasBeats]) and play along steadily; without
 * beats, or in wait mode, the switches stay greyed out and say why. Count-in sits indented under the
 * metronome, as part of it, though it also works alone (counted on screen only).
 */
@Composable
fun MetronomeSection(viewModel: PianoWeaveViewModel, hasBeats: Boolean) {
    val context = LocalContext.current
    val accent = LocalAppTheme.current.primaryColor
    // Wait mode stops at every chord, so neither can play along with it (turning it on turns the metronome off)
    val isWaitMode = viewModel.isWaitModeEnabled
    val isAvailable = hasBeats && !isWaitMode
    val isMetronomeOn = isAvailable && viewModel.isMetronomeEnabled

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingToggleRow(
            title = "Metronome",
            subtitle = when {
                !hasBeats -> "No beat information for this piece"
                isWaitMode -> "Not available in wait mode"
                isMetronomeOn -> "Clicks on every beat of the recording"
                // The quick switch is worth knowing while it's off, which is when someone would want it
                else -> "Clicks on every beat of the recording. While playing, hold the speed button"
            },
            checked = isMetronomeOn,
            onCheckedChange = { viewModel.setMetronomeEnabled(context, it) },
            enabled = isAvailable,
            accent = accent
        )
        SettingToggleRow(
            title = "Count-in",
            subtitle = if (isMetronomeOn) "One bar of clicks before playing" else "One bar counted on screen before playing",
            checked = isAvailable && viewModel.isCountInEnabled,
            onCheckedChange = { viewModel.setCountInEnabled(context, it) },
            enabled = isAvailable,
            accent = accent,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
