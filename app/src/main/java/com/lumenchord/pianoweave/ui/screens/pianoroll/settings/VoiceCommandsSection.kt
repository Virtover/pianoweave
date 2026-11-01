package com.lumenchord.pianoweave.ui.screens.pianoroll.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.audio.VoiceListeningMode
import com.lumenchord.pianoweave.ui.components.SettingToggleRow
import com.lumenchord.pianoweave.ui.screens.pianoroll.rememberVoiceCommandsToggle
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.theme.PillShape
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import com.lumenchord.pianoweave.ui.viewmodel.VoiceModelState

/** What to say and what it does, for the reference list. */
private val COMMAND_GUIDE = listOf(
    "play · go" to "Play",
    "pause · stop" to "Pause",
    "wait" to "Wait mode on / off",
    "loop" to "Loop on / off",
    "full screen" to "Hide / show the bars",
    "loop start … loop end" to "Loop the part in between",
    "again" to "Start over (or from the loop start)",
    "back · rewind" to "Back 5 seconds",
    "forward · skip" to "Forward 5 seconds",
    "slower · faster" to "Change speed",
    "normal" to "Normal speed",
    "no · undo" to "Take back the last command"
)

/**
 * Voice commands in practice settings. Off, one line on what it is; on, everything a first-time user
 * needs: whether it's ready, the two ways of speaking to it (with an example each), and what every
 * word does.
 */
@Composable
fun VoiceCommandsSection(viewModel: PianoWeaveViewModel) {
    val context = LocalContext.current
    val accent = LocalAppTheme.current.primaryColor
    val toggle = rememberVoiceCommandsToggle(viewModel)
    val enabled = viewModel.isVoiceCommandsEnabled
    val mode = viewModel.voiceListeningMode

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingToggleRow(
            title = "Voice Commands",
            subtitle = "Control practice by speaking, so your hands stay on the keys. Speech is recognized on your device, offline.",
            checked = enabled,
            onCheckedChange = toggle,
            accent = accent
        )

        if (!enabled) return@Column

        VoiceStatus(viewModel, accent)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("How to speak")
            ListeningModeOption(
                selected = mode == VoiceListeningMode.WORDS,
                title = "Just the command",
                example = "“pause”",
                description = "Quickest. Say it on its own, with a short pause before and after.",
                accent = accent,
                onSelect = { viewModel.setVoiceListeningMode(context, VoiceListeningMode.WORDS) }
            )
            ListeningModeOption(
                selected = mode == VoiceListeningMode.SELAH,
                title = "“Selah” first",
                example = "“Selah, pause”",
                description = "For noisy rooms or lessons: only what starts with Selah counts. “Selah” alone pauses.",
                accent = accent,
                onSelect = { viewModel.setVoiceListeningMode(context, VoiceListeningMode.SELAH) }
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("What to say")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                COMMAND_GUIDE.forEach { (words, action) ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = words,
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.width(118.dp)
                        )
                        Text(action, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
            val prefix = if (mode == VoiceListeningMode.SELAH) "Selah, " else ""
            Text(
                text = "Chain them in one go: “${prefix}full screen, play” or “${prefix}back back” for 10 seconds. " +
                        "Say “no” within 4 seconds to undo, or tap the note that shows what was done.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

/** Ready, downloading or failed, so a first-time user knows whether to start talking. */
@Composable
private fun VoiceStatus(viewModel: PianoWeaveViewModel, accent: Color) {
    val context = LocalContext.current
    when (viewModel.voiceModelState) {
        VoiceModelState.DOWNLOADING -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusText("Getting the voice model ready… ${(viewModel.voiceModelProgress * 100).toInt()}%")
            LinearProgressIndicator(
                progress = { viewModel.voiceModelProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PillShape),
                color = accent,
                trackColor = Color.White.copy(alpha = 0.1f)
            )
        }
        VoiceModelState.FAILED -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusText("The voice model couldn't be downloaded. Check your connection.", Modifier.weight(1f))
            OutlinedButton(
                onClick = { viewModel.downloadVoiceModel(context) },
                border = BorderStroke(1.dp, accent),
                shape = PillShape,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Retry", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        else -> StatusText("Ready. The mic in the toolbar brightens when it hears you.")
    }
}

@Composable
private fun StatusText(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, lineHeight = 17.sp, modifier = modifier)
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
}

@Composable
private fun ListeningModeOption(
    selected: Boolean,
    title: String,
    example: String,
    description: String,
    accent: Color,
    onSelect: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, if (selected) accent.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.12f), shape)
            .background(if (selected) accent.copy(alpha = 0.10f) else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = accent),
            modifier = Modifier.padding(12.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(example, color = accent, fontSize = 13.sp)
            }
            Text(description, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}
