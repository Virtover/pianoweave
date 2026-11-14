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
 * The click and count-in. The metronome follows the song's own beats ([hasBeats]), and once on says so
 * when their tempo moves a lot ([isTempoUnstable]); without beats, or in wait mode, it stays greyed out
 * and says why. Count-in works in any song outside wait mode: with the
 * metronome on it's one bar of clicks, so it sits indented under it as part of it; otherwise it's a
 * 3-second countdown on screen and stands on its own.
 */
@Composable
fun MetronomeSection(viewModel: PianoWeaveViewModel, hasBeats: Boolean, isTempoUnstable: Boolean = false) {
    val context = LocalContext.current
    val accent = LocalAppTheme.current.primaryColor
    // Wait mode stops at every chord, so neither can play along with it (turning it on turns the metronome off)
    val isWaitMode = viewModel.isWaitModeEnabled
    val isMetronomeAvailable = hasBeats && !isWaitMode
    val isMetronomeOn = isMetronomeAvailable && viewModel.isMetronomeEnabled

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingToggleRow(
            title = "Metronome",
            subtitle = when {
                !hasBeats -> "No beat information for this piece"
                isWaitMode -> "Not available in wait mode"
                // Said once it's on, as the clicks start to follow it
                isMetronomeOn && isTempoUnstable ->
                    "Clicks on every beat of the recording. Warning: this recording has an unstable tempo, so the clicks will vary in speed"
                isMetronomeOn -> "Clicks on every beat of the recording"
                // The quick switch is worth knowing while it's off, which is when someone would want it
                else -> "Clicks on every beat of the recording. While playing, hold the speed button"
            },
            checked = isMetronomeOn,
            onCheckedChange = { viewModel.setMetronomeEnabled(context, it) },
            enabled = isMetronomeAvailable,
            accent = accent
        )
        if (isMetronomeOn) {
            SettingToggleRow(
                title = "Silent until the first note",
                subtitle = "No clicks over the quiet start of the recording",
                checked = viewModel.isMetronomeLeadInSilent,
                onCheckedChange = { viewModel.setMetronomeLeadInSilent(context, it) },
                accent = accent,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
        SettingToggleRow(
            title = "Count-in",
            subtitle = when {
                isWaitMode -> "Not available in wait mode"
                isMetronomeOn -> "One bar of clicks before playing, starting on a beat"
                else -> "A 3-second countdown before playing"
            },
            checked = !isWaitMode && viewModel.isCountInEnabled,
            onCheckedChange = { viewModel.setCountInEnabled(context, it) },
            enabled = !isWaitMode,
            accent = accent,
            modifier = if (isMetronomeOn) Modifier.padding(start = 16.dp) else Modifier
        )
    }
}
