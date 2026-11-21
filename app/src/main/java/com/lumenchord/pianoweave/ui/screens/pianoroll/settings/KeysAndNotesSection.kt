package com.lumenchord.pianoweave.ui.screens.pianoroll.settings

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.ui.components.SettingLabel
import com.lumenchord.pianoweave.ui.components.SettingToggleRow
import com.lumenchord.pianoweave.ui.screens.pianoroll.noteName
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.theme.PillShape
import com.lumenchord.pianoweave.ui.theme.goldLeaf
import com.lumenchord.pianoweave.ui.theme.onKeyButton
import com.lumenchord.pianoweave.ui.viewmodel.KeyboardRange
import com.lumenchord.pianoweave.ui.viewmodel.PIANO_HIGHEST
import com.lumenchord.pianoweave.ui.viewmodel.PIANO_LOWEST
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel

/**
 * The song map's switch, under the loop it shows. Its subtitle carries the loop button's gesture, as the speed
 * menu carries the metronome's.
 */
@Composable
fun SongMapToggle(viewModel: PianoWeaveViewModel, context: Context) {
    SettingToggleRow(
        title = "Song map",
        subtitle = "Instead of the progress bar: how busy each bar is, and your loop. Or hold the loop button",
        checked = viewModel.isSongMapEnabled,
        onCheckedChange = { viewModel.setSongMapEnabled(context, it) },
        accent = LocalAppTheme.current.primaryColor
    )
}

/** One line for the Keyboard & notes row, so the page needn't be opened to check it. */
fun keysAndNotesSummary(viewModel: PianoWeaveViewModel): String =
    viewModel.keyboardRange.label + if (viewModel.isNoteNamesEnabled) " · names on" else " · names off"

/** The Keyboard & notes page: which keys are shown, and names on the notes and keys. */
@Composable
fun KeysAndNotesSection(viewModel: PianoWeaveViewModel, context: Context) {
    val theme = LocalAppTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingLabel(title = "Keyboard", subtitle = rangeHelp(viewModel.keyboardRange))
        RangeChoice(selected = viewModel.keyboardRange, onSelect = { viewModel.setKeyboardRange(context, it) })

        AnimatedVisibility(
            visible = viewModel.keyboardRange == KeyboardRange.Custom,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            // Dragged locally, saved when let go, so the roll doesn't redraw its keyboard on every step
            var range by remember(viewModel.customRangeLow, viewModel.customRangeHigh) {
                mutableStateOf(viewModel.customRangeLow.toFloat()..viewModel.customRangeHigh.toFloat())
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    onValueChangeFinished = {
                        viewModel.setCustomRange(context, range.start.toInt(), range.endInclusive.toInt())
                    },
                    valueRange = PIANO_LOWEST.toFloat()..PIANO_HIGHEST.toFloat(),
                    steps = PIANO_HIGHEST - PIANO_LOWEST - 1,
                    colors = SliderDefaults.colors(
                        thumbColor = theme.primaryColor,
                        activeTrackColor = theme.primaryColor,
                        inactiveTrackColor = MaterialTheme.colorScheme.tertiaryContainer,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    )
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("From ${noteName(range.start.toInt())}", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("To ${noteName(range.endInclusive.toInt())}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        SettingToggleRow(
            title = "Note names",
            subtitle = "On the falling notes and on the keys being played",
            checked = viewModel.isNoteNamesEnabled,
            onCheckedChange = { viewModel.setNoteNamesEnabled(context, it) },
            accent = theme.primaryColor
        )
    }
}

private fun rangeHelp(range: KeyboardRange): String = when (range) {
    KeyboardRange.All -> "The whole keyboard"
    KeyboardRange.Song -> "Just the keys this song uses, in whole octaves"
    KeyboardRange.Follow -> "Two octaves that move with the music; notes outside show at the edge"
    KeyboardRange.Custom -> "Your own range; notes outside show at the edge"
}

/** The four choices as a recessed well with the chosen one lifted, like Link / File / Mic. */
@Composable
private fun RangeChoice(selected: KeyboardRange, onSelect: (KeyboardRange) -> Unit) {
    val theme = LocalAppTheme.current
    val well = if (theme.isLight) MaterialTheme.colorScheme.tertiaryContainer else Color.Black.copy(alpha = 0.28f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(PillShape)
            .background(well, PillShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), PillShape)
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        KeyboardRange.entries.forEach { range ->
            val isSelected = range == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (isSelected) Modifier.goldLeaf(theme) else Modifier)
                    .clip(PillShape)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(range) }),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = range.label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) theme.onKeyButton else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
