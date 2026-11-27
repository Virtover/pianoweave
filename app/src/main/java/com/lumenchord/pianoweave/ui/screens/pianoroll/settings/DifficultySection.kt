package com.lumenchord.pianoweave.ui.screens.pianoroll.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.midi.MidiVariants
import com.lumenchord.pianoweave.midi.StoredMidi
import com.lumenchord.pianoweave.ui.components.AppDropdownMenu
import com.lumenchord.pianoweave.ui.components.AppMenuItem
import com.lumenchord.pianoweave.ui.screens.pianoroll.ColorSurface
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.theme.PillShape
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import kotlinx.coroutines.launch

/** What each choice is, in the menu and under the title. */
private fun difficultyHint(variant: String): String = when (variant) {
    MidiVariants.FULL -> "Every note, for two hands"
    MidiVariants.ADVANCED -> "A little lighter, for two hands"
    MidiVariants.INTERMEDIATE -> "Fewer notes, for two hands"
    MidiVariants.EASY -> "Simpler, for two hands"
    MidiVariants.BEGINNER -> "The simplest, for two hands"
    MidiVariants.RAW -> "Every instrument, as transcribed"
    else -> ""
}

/**
 * The difficulty of an ensemble song, as one row like Speed: the piano versions from Full to Beginner, and Raw,
 * the multi-track as transcribed. Shown only for a song with several results; the choice switches the notes at
 * once and is kept with the song.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DifficultySection(viewModel: PianoWeaveViewModel, song: StoredMidi, context: Context) {
    val variants = viewModel.songVariants
    val selected = viewModel.selectedVariant ?: return
    if (variants.size < 2) return
    val colorAccent = LocalAppTheme.current.primaryColor
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var expanded by remember { mutableStateOf(false) }
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("Difficulty", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (MidiVariants.RAW in variants) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                    tooltip = {
                        RichTooltip(title = { Text("Raw") }) {
                            Text(rawExplanation(viewModel.includeDrumNotes), lineHeight = 18.sp)
                        }
                    },
                    state = tooltipState
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClickLabel = "Explain Raw") { scope.launch { tooltipState.show() } }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(difficultyHint(selected), fontSize = 12.sp, lineHeight = 16.sp, color = muted, modifier = Modifier.weight(1f, fill = false))
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = muted, modifier = Modifier.size(14.dp))
                    }
                }
            } else {
                Text(difficultyHint(selected), fontSize = 12.sp, lineHeight = 16.sp, color = muted)
            }
        }
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                border = BorderStroke(1.dp, colorAccent),
                shape = PillShape,
                contentPadding = PaddingValues(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
            ) {
                Text(MidiVariants.label(selected), color = colorAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Difficulty", tint = colorAccent, modifier = Modifier.size(20.dp))
            }
            AppDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                heading = "Difficulty",
                containerColor = ColorSurface
            ) {
                variants.forEach { option ->
                    AppMenuItem(
                        text = MidiVariants.label(option),
                        hint = difficultyHint(option),
                        selected = option == selected,
                        tint = colorAccent,
                        role = Role.RadioButton,
                        onClick = {
                            expanded = false
                            viewModel.selectSongVariant(context, song, option)
                        }
                    )
                }
            }
        }
    }
}

/** Drums are mentioned only where they can be on: a debug build with its drum switch on. */
internal fun rawExplanation(withDrums: Boolean): String =
    "The transcription as it is: every instrument as its own track" +
        (if (withDrums) ", drums included, played as piano notes" else "") +
        ". It isn't arranged for two hands, so it can be hard or impossible to play."
