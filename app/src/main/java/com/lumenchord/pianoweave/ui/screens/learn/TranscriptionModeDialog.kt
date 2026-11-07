package com.lumenchord.pianoweave.ui.screens.learn

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumenchord.pianoweave.api.TranscriptionMode
import com.lumenchord.pianoweave.ui.components.DialogHeroHeader
import com.lumenchord.pianoweave.ui.components.DialogSurface
import com.lumenchord.pianoweave.ui.components.IconBadge
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.theme.PillShape

private data class ModeOption(
    val mode: TranscriptionMode,
    val icon: ImageVector,
    val title: String,
    /** A few words on what it is best for, shown as a tag beside the title. */
    val bestFor: String,
    val body: String
)

private val modeOptions = listOf(
    ModeOption(
        mode = TranscriptionMode.Transcription,
        icon = Icons.Default.Piano,
        title = "Piano transcription",
        bestFor = "Piano videos",
        body = "The piano notes, as played. Vocals and other instruments are left out."
    ),
    ModeOption(
        mode = TranscriptionMode.Arrangement,
        icon = Icons.AutoMirrored.Filled.QueueMusic,
        title = "Piano arrangement",
        bestFor = "Any song",
        body = "The whole song, as it might sound on piano. An approximation, so it may not be what you expect."
    )
)

/**
 * Asked on every Transcribe while the server makes arrangements: a transcription of the piano in the song,
 * or an arrangement of all of it. Opens on the last choice; a tap on an option only selects ([onSelect])
 * it, since starting costs minutes, and the button below names what will be made. [cost] (credits, on a
 * billed server) is the same either way, so it is said once rather than per option.
 */
@Composable
internal fun TranscriptionModeDialog(
    selected: TranscriptionMode,
    onSelect: (TranscriptionMode) -> Unit,
    cost: Int?,
    onConfirm: (TranscriptionMode) -> Unit,
    onDismiss: () -> Unit
) {
    val appTheme = LocalAppTheme.current
    val colorGold = appTheme.primaryColor
    val colorSlate = MaterialTheme.colorScheme.tertiaryContainer

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            // Landscape puts the two options side by side, so the dialog fits without scrolling
            val isWide = maxWidth >= 560.dp
            DialogSurface(
                light = colorGold,
                modifier = Modifier
                    .widthIn(max = if (isWide) 680.dp else 440.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, colorSlate)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(if (isWide) 16.dp else 20.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isWide) 12.dp else 16.dp)
                ) {
                    DialogHeroHeader(
                        title = "Transcribe or Arrange?",
                        subtitle = "Choose what the AI makes from this song.",
                        icon = Icons.Default.AutoAwesome,
                        onClose = onDismiss,
                        titleColor = colorGold
                    )

                    val options = @Composable { modifier: Modifier ->
                        modeOptions.forEach { option ->
                            ModeOptionCard(
                                option = option,
                                isSelected = option.mode == selected,
                                onSelect = { onSelect(option.mode) },
                                modifier = modifier
                            )
                        }
                    }
                    if (isWide) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .selectableGroup(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) { options(Modifier.weight(1f).fillMaxHeight()) }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectableGroup(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) { options(Modifier.fillMaxWidth()) }
                    }

                    if (cost != null) CostNote(cost)

                    ModeDialogButtons(selected = selected, onConfirm = { onConfirm(selected) }, onDismiss = onDismiss)
                }
            }
        }
    }
}

/** One option as a card: badge, title with its "best for" tag, and what it makes. Lit in gold when selected. */
@Composable
private fun ModeOptionCard(
    option: ModeOption,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gold = LocalAppTheme.current.primaryColor
    val shape = RoundedCornerShape(16.dp)
    val light by animateFloatAsState(if (isSelected) 1f else 0f, tween(220), label = "modeOptionLight")
    val borderColor by animateColorAsState(
        if (isSelected) gold.copy(alpha = 0.85f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        tween(220),
        label = "modeOptionBorder"
    )
    val onSurface = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f))
            // The selected card catches the light from its top, like the dialog itself
            .background(Brush.verticalGradient(listOf(gold.copy(alpha = 0.16f * light), gold.copy(alpha = 0.04f * light))))
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, shape)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(option.icon, tint = if (isSelected) gold else onSurface.copy(alpha = 0.75f), size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = option.title,
                    color = if (isSelected) gold else onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = option.bestFor,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.4.sp,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(8.dp))
            SelectionMark(isSelected = isSelected, light = light)
        }
        Text(
            text = option.body,
            style = MaterialTheme.typography.bodySmall,
            color = onSurface.copy(alpha = 0.8f),
            lineHeight = 18.sp
        )
    }
}

/** A radio-like ring that fills with gold and a check when its card is selected. */
@Composable
private fun SelectionMark(isSelected: Boolean, light: Float) {
    val gold = LocalAppTheme.current.primaryColor
    val onGold = if (LocalAppTheme.current.isLightAccent) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(gold.copy(alpha = light))
            .border(
                1.5.dp,
                if (isSelected) gold else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = onGold, modifier = Modifier.size(15.dp))
        }
    }
}

/** On a billed server: both options take the same credits, so the choice is only about the result. */
@Composable
private fun CostNote(cost: Int) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = muted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            text = "Either one uses $cost ${if (cost == 1) "credit" else "credits"}",
            style = MaterialTheme.typography.labelMedium,
            color = muted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ModeDialogButtons(selected: TranscriptionMode, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val appTheme = LocalAppTheme.current
    val colorGold = appTheme.primaryColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .heightIn(min = 46.dp),
            shape = PillShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Text("Cancel", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Button(
            onClick = onConfirm,
            modifier = Modifier
                .weight(1.4f)
                .fillMaxHeight()
                .heightIn(min = 46.dp),
            shape = PillShape,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colorGold,
                contentColor = if (appTheme.isLightAccent) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (selected == TranscriptionMode.Arrangement) "Arrange" else "Transcribe",
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                maxLines = 1
            )
        }
    }
}
