package com.lumenchord.pianoweave.ui.screens.learn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumenchord.pianoweave.api.Clip
import com.lumenchord.pianoweave.api.formatClipTime
import com.lumenchord.pianoweave.api.parseClipTime
import com.lumenchord.pianoweave.ui.components.DialogHeader
import com.lumenchord.pianoweave.ui.components.DialogSurface
import com.lumenchord.pianoweave.ui.components.quietFieldColors
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.theme.PillShape
import kotlin.math.roundToInt

/**
 * Trim: the part of the song to transcribe, in whole seconds. The slider and the two times can't leave the song
 * ([totalSeconds], its length) or make a part shorter than [Clip.MIN_SECONDS]. While a link's length is still being
 * read ([totalSeconds] null) it waits, or shows [error] with a retry. "Whole song" clears the part.
 */
@Composable
internal fun TrimDialog(
    totalSeconds: Float?,
    initial: Clip?,
    isBilled: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onApply: (Clip?) -> Unit,
    onDismiss: () -> Unit
) {
    val appTheme = LocalAppTheme.current
    val gold = appTheme.primaryColor

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            DialogSurface(
                light = gold,
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    DialogHeader(title = "Trim", icon = Icons.Default.ContentCut, onClose = onDismiss, titleColor = gold)
                    Text(
                        text = if (isBilled) "Transcribe only a part of it. Only the part's length is charged."
                        else "Transcribe only a part of it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                    val total = totalSeconds?.toInt()?.takeIf { it > Clip.MIN_SECONDS }
                    if (total == null) {
                        LengthPending(error = error, onRetry = onRetry)
                    } else {
                        TrimRange(total = total, totalSeconds = totalSeconds, initial = initial, onApply = onApply)
                    }
                }
            }
        }
    }
}

@Composable
private fun LengthPending(error: String?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (error == null) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            Text("Reading the video's length…", style = MaterialTheme.typography.bodySmall)
        } else {
            Text(error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            OutlinedButton(onClick = onRetry, shape = PillShape) { Text("Try again") }
        }
    }
}

@Composable
private fun TrimRange(total: Int, totalSeconds: Float, initial: Clip?, onApply: (Clip?) -> Unit) {
    val appTheme = LocalAppTheme.current
    val gold = appTheme.primaryColor
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var start by remember(total) { mutableIntStateOf(initial?.startSeconds?.coerceIn(0, total - Clip.MIN_SECONDS) ?: 0) }
    var end by remember(total) { mutableIntStateOf((initial?.endSeconds ?: total).coerceIn(start + Clip.MIN_SECONDS, total)) }
    val isWhole = start == 0 && end == total

    // What will be sent, large: the part's length, or that it is the whole song
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatClipTime(end - start),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = gold
        )
        Text(
            text = if (isWhole) "the whole song" else "of ${formatClipTime(total)}",
            style = MaterialTheme.typography.labelMedium,
            color = muted
        )
    }

    Column {
        RangeSlider(
            value = start.toFloat()..end.toFloat(),
            onValueChange = { range ->
                val newStart = range.start.roundToInt()
                val newEnd = range.endInclusive.roundToInt()
                // The thumb that moved gives way at the minimum length, never the other one
                if (newStart != start) start = newStart.coerceIn(0, end - Clip.MIN_SECONDS)
                if (newEnd != end) end = newEnd.coerceIn(start + Clip.MIN_SECONDS, total)
            },
            valueRange = 0f..total.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = gold,
                activeTrackColor = gold,
                inactiveTrackColor = muted.copy(alpha = 0.22f)
            )
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("0:00", style = MaterialTheme.typography.labelSmall, color = muted)
            Spacer(Modifier.weight(1f))
            Text(formatClipTime(total), style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TimeField(
            label = "Start",
            value = start,
            range = 0..(end - Clip.MIN_SECONDS),
            onValue = { start = it },
            modifier = Modifier.weight(1f)
        )
        TimeField(
            label = "End",
            value = end,
            range = (start + Clip.MIN_SECONDS)..total,
            onValue = { end = it },
            modifier = Modifier.weight(1f)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = { onApply(null) },
            enabled = !isWhole,
            modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 46.dp),
            shape = PillShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Text("Whole song", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
        Button(
            onClick = { onApply(Clip.within(start, end, totalSeconds)) },
            modifier = Modifier.weight(1.2f).fillMaxHeight().heightIn(min = 46.dp),
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = gold,
                contentColor = if (appTheme.isLightAccent) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(if (isWhole) "Done" else "Use this part", fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1)
        }
    }
}

/**
 * A time to type ("1:35"), with a second's nudge either way below it, so the field keeps its whole width for the
 * time. Only a time within [range] is taken; anything else is marked and dropped when the field is left.
 */
@Composable
private fun TimeField(label: String, value: Int, range: IntRange, onValue: (Int) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(formatClipTime(value)) }
    var focused by remember { mutableStateOf(false) }
    // The slider and the nudges move the value; what is being typed is left alone
    LaunchedEffect(value) { if (!focused) text = formatClipTime(value) }
    val typed = parseClipTime(text)
    val focusManager = LocalFocusManager.current
    val nudge = { by: Int ->
        val next = (value + by).coerceIn(range)
        onValue(next)
        text = formatClipTime(next)
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { changed ->
                text = changed.take(9)
                parseClipTime(changed)?.takeIf { it in range }?.let(onValue)
            },
            label = { Text(label) },
            singleLine = true,
            isError = typed == null || typed !in range,
            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            shape = RoundedCornerShape(14.dp),
            colors = quietFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    focused = it.isFocused
                    if (!it.isFocused) text = formatClipTime(value)
                }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NudgeButton(Icons.Default.Remove, "$label a second earlier", enabled = value > range.first) { nudge(-1) }
            NudgeButton(Icons.Default.Add, "$label a second later", enabled = value < range.last) { nudge(1) }
        }
    }
}

@Composable
private fun NudgeButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp))
    }
}
