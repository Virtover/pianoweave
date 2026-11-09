package com.lumenchord.pianoweave.ui.screens.pianoroll.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.audio.VoiceListeningMode
import com.lumenchord.pianoweave.audio.VoiceMicMode
import com.lumenchord.pianoweave.ui.screens.pianoroll.isDebuggableBuild
import com.lumenchord.pianoweave.ui.components.SettingToggleRow
import com.lumenchord.pianoweave.ui.screens.pianoroll.rememberVoiceCommandsToggle
import com.lumenchord.pianoweave.ui.screens.pianoroll.VoiceCommandGuide
import com.lumenchord.pianoweave.ui.screens.pianoroll.voiceModelProblemText
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.theme.PillShape
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import com.lumenchord.pianoweave.ui.viewmodel.VoiceModelState


/**
 * Voice commands in practice settings, kept compact: the switch says what it is (and, once on, that it
 * listens), the download shows only while it matters, the way of speaking is a two-way choice with a
 * one-line example, and the full list of words folds out on request.
 */
@Composable
fun VoiceCommandsSection(viewModel: PianoWeaveViewModel) {
    val context = LocalContext.current
    val accent = LocalAppTheme.current.primaryColor
    val toggle = rememberVoiceCommandsToggle(viewModel)
    val enabled = viewModel.isVoiceCommandsEnabled
    val mode = viewModel.voiceListeningMode

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingToggleRow(
            title = "Voice Commands",
            subtitle = if (enabled && viewModel.voiceModelState == VoiceModelState.READY) {
                "Listening in the piano roll. The mic in the toolbar brightens when it hears you."
            } else {
                "Control practice by speaking, so your hands stay on the keys. Works offline."
            },
            checked = enabled,
            onCheckedChange = toggle,
            accent = accent
        )

        if (!enabled) return@Column

        VoiceDownloadStatus(viewModel, accent)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ListeningModeSwitch(
                mode = mode,
                accent = accent,
                onSelect = { viewModel.setVoiceListeningMode(context, it) }
            )
            Text(
                text = when (mode) {
                    VoiceListeningMode.WORDS -> "Say a command on its own, like “pause”."
                    VoiceListeningMode.SELAH -> "Start with Selah, like “Selah, pause”. Other talking is ignored, for noisy rooms or lessons."
                },
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }

        CommandGuide(mode, accent)

        // Debug builds: compare how the phone processes the mic (see VoiceMicMode)
        if (isDebuggableBuild(context)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusText("Mic processing (debug)")
                PillChoice(
                    options = listOf(VoiceMicMode.CALL to "Call", VoiceMicMode.SPEECH to "Speech", VoiceMicMode.RAW to "Raw"),
                    selected = viewModel.voiceMicMode,
                    accent = accent,
                    onSelect = { viewModel.setVoiceMicMode(context, it) }
                )
            }
        }
    }
}

/**
 * Only while the model downloads, after it failed, or when it's missing (an interrupted download);
 * once ready, the switch's subtitle says so.
 */
@Composable
private fun VoiceDownloadStatus(viewModel: PianoWeaveViewModel, accent: Color) {
    val context = LocalContext.current
    val state = viewModel.voiceModelState
    when (state) {
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
        VoiceModelState.FAILED, VoiceModelState.MISSING -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusText(voiceModelProblemText(state, viewModel.voiceModelError), Modifier.weight(1f))
            OutlinedButton(
                onClick = { viewModel.downloadVoiceModel(context) },
                border = BorderStroke(1.dp, accent),
                shape = PillShape,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    if (state == VoiceModelState.FAILED) "Retry" else "Download",
                    color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp
                )
            }
        }
        VoiceModelState.READY -> Unit
    }
}

@Composable
private fun StatusText(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, lineHeight = 17.sp, modifier = modifier)
}

/** "Just the word" or "Selah first", as one pill split in two (labels fit a narrow portrait screen). */
@Composable
private fun ListeningModeSwitch(mode: VoiceListeningMode, accent: Color, onSelect: (VoiceListeningMode) -> Unit) =
    PillChoice(
        options = listOf(VoiceListeningMode.WORDS to "Just the word", VoiceListeningMode.SELAH to "“Selah” first"),
        selected = mode,
        accent = accent,
        onSelect = onSelect
    )

/** One pill split into equal options, the selected one lit in the accent. */
@Composable
private fun <T> PillChoice(options: List<Pair<T, String>>, selected: T, accent: Color, onSelect: (T) -> Unit) {
    val mode = selected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(PillShape)
            .border(1.dp, Color.White.copy(alpha = 0.14f), PillShape)
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEach { (option, label) ->
            val selected = option == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(PillShape)
                    .background(if (selected) accent.copy(alpha = 0.22f) else Color.Transparent)
                    .then(if (selected) Modifier.border(1.dp, accent.copy(alpha = 0.8f), PillShape) else Modifier)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.65f),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** "What can I say?": one line until opened, then every command and how to combine them. */
@Composable
private fun CommandGuide(mode: VoiceListeningMode, accent: Color) {
    var open by rememberSaveable { mutableStateOf(false) }
    val arrow by animateFloatAsState(if (open) 180f else 0f, label = "voiceGuideArrow")
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (open) "Hide commands" else "Show commands") { open = !open }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Text("What can I say?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(20.dp)
                    .rotate(arrow)
            )
        }

        AnimatedVisibility(
            visible = open,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                VoiceCommandGuide(mode)
            }
        }
    }
}
