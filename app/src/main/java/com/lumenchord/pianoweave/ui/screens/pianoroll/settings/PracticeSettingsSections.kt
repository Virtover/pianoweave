package com.lumenchord.pianoweave.ui.screens.pianoroll.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.midi.MidiInputManager
import com.lumenchord.pianoweave.ui.components.AppDropdownMenu
import com.lumenchord.pianoweave.ui.components.AppMenuItem
import com.lumenchord.pianoweave.ui.components.SettingLabel
import com.lumenchord.pianoweave.ui.components.SettingToggleRow
import com.lumenchord.pianoweave.ui.screens.pianoroll.ColorSurface
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.theme.PillShape
import com.lumenchord.pianoweave.ui.viewmodel.PLAYBACK_SPEEDS
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import java.util.Locale

/**
 * Playback speed as one row: the current speed in a pill that opens the same list as the toolbar's speed
 * button, so it takes a line instead of a slider.
 */
@Composable
fun SpeedSection(viewModel: PianoWeaveViewModel, context: Context) {
    val colorAccent = LocalAppTheme.current.primaryColor
    var expanded by remember { mutableStateOf(false) }
    val speed = viewModel.speedMultiplier

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingLabel(
            title = "Speed",
            subtitle = "Slow down to learn, speed up to challenge",
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                border = BorderStroke(1.dp, colorAccent),
                shape = PillShape,
                contentPadding = PaddingValues(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
            ) {
                Text(
                    text = if (speed == 1.0f) "Normal" else formatSpeed(speed),
                    color = colorAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "Playback speed",
                    tint = colorAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            AppDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                heading = "Playback speed",
                containerColor = ColorSurface
            ) {
                PLAYBACK_SPEEDS.forEach { option ->
                    AppMenuItem(
                        text = formatSpeed(option),
                        hint = if (option == 1.0f) "Normal" else null,
                        selected = option == speed,
                        tint = colorAccent,
                        role = Role.RadioButton,
                        onClick = {
                            expanded = false
                            viewModel.selectPlaybackSpeed(context, option)
                        }
                    )
                }
            }
        }
    }
}

/** Wait mode's own settings: the STRIKE banner, and the keyboard's sound while a MIDI keyboard is plugged in. */
@Composable
fun WaitModeSection(viewModel: PianoWeaveViewModel, context: Context) {
    val colorAccent = LocalAppTheme.current.primaryColor
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingToggleRow(
            title = "Strike Overlay",
            subtitle = "Show the notes to play while waiting",
            // Read straight from the view model: the banner can also be hidden from the toolbar or by swiping it away
            checked = viewModel.isStrikeOverlayEnabled,
            onCheckedChange = { viewModel.setStrikeOverlayEnabled(context, it) },
            accent = colorAccent
        )

        // Only meaningful with a MIDI keyboard plugged in, so it appears with one
        if (MidiInputManager.isConnected) {
            SettingToggleRow(
                title = "Keyboard Sound",
                subtitle = "Play your MIDI keyboard through the app. Turn off if it has its own speakers",
                checked = viewModel.isKeyboardSoundEnabled,
                onCheckedChange = { viewModel.setKeyboardSoundEnabled(context, it) },
                accent = colorAccent
            )
        }
    }
}

/** "0.25x", "0.5x", "1x", "1.25x": no trailing zeros, so speeds stay short. */
private fun formatSpeed(speed: Float): String =
    String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.') + "x"
