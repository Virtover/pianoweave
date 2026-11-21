package com.lumenchord.pianoweave.ui.screens.learn

import com.lumenchord.pianoweave.ui.theme.onKeyButton
import com.lumenchord.pianoweave.ui.theme.goldLeaf
import com.lumenchord.pianoweave.ui.components.AccentButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
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
                        text = if (isBilled) "Transcribe only a part. Only its length is charged." else "Transcribe only a part.",
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
    var start by remember(total) { mutableIntStateOf(initial?.startSeconds?.coerceIn(0, total - Clip.MIN_SECONDS) ?: 0) }
    var end by remember(total) { mutableIntStateOf((initial?.endSeconds ?: total).coerceIn(start + Clip.MIN_SECONDS, total)) }
    val isWhole = start == 0 && end == total

    PartSlider(
        start = start,
        end = end,
        total = total,
        onChange = { newStart, newEnd ->
            // The thumb that moved gives way at the minimum length, never the other one
            if (newStart != start) start = newStart.coerceIn(0, end - Clip.MIN_SECONDS)
            if (newEnd != end) end = newEnd.coerceIn(start + Clip.MIN_SECONDS, total)
        }
    )

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TimeBox(
            label = "Start",
            value = start,
            range = 0..(end - Clip.MIN_SECONDS),
            onValue = { start = it },
            modifier = Modifier.weight(1f)
        )
        TimeBox(
            label = "End",
            value = end,
            range = (start + Clip.MIN_SECONDS)..total,
            onValue = { end = it },
            modifier = Modifier.weight(1f)
        )
    }

    Text(
        text = "Part: ${formatClipTime(end - start)} of ${formatClipTime(total)}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )

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
            Text("Whole song", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
        }
        AccentButton(
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
 * The part on the song: a slim track lit between two round gold thumbs. The default range slider draws tall bar
 * thumbs with gaps in the track, which looked out of place in the app's dialogs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartSlider(start: Int, end: Int, total: Int, onChange: (Int, Int) -> Unit) {
    val gold = LocalAppTheme.current.primaryColor
    val rail = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
    val surface = MaterialTheme.colorScheme.surface
    val thumb = @Composable { _: RangeSliderState ->
        Box(
            modifier = Modifier
                .size(22.dp)
                .shadow(3.dp, CircleShape)
                .goldLeaf(LocalAppTheme.current, CircleShape)
                .border(3.dp, surface, CircleShape)
        )
    }
    RangeSlider(
        value = start.toFloat()..end.toFloat(),
        onValueChange = { range -> onChange(range.start.roundToInt(), range.endInclusive.roundToInt()) },
        valueRange = 0f..total.toFloat(),
        startThumb = thumb,
        endThumb = thumb,
        track = { state ->
            Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
                val span = (state.valueRange.endInclusive - state.valueRange.start).takeIf { it > 0f } ?: 1f
                val from = size.width * (state.activeRangeStart - state.valueRange.start) / span
                val to = size.width * (state.activeRangeEnd - state.valueRange.start) / span
                val y = size.height / 2
                drawLine(rail, Offset(0f, y), Offset(size.width, y), strokeWidth = size.height, cap = StrokeCap.Round)
                drawLine(gold, Offset(from, y), Offset(to, y), strokeWidth = size.height, cap = StrokeCap.Round)
            }
        }
    )
}

/**
 * A time to type ("1:35") between a second's nudge either way. A plain field rather than an outlined text field,
 * whose icon slots left too little room for a time like 13:51 on a phone. Only a time within [range] is taken;
 * anything else is marked and dropped when the field is left.
 */
@Composable
private fun TimeBox(label: String, value: Int, range: IntRange, onValue: (Int) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(formatClipTime(value)) }
    var focused by remember { mutableStateOf(false) }
    // The slider and the nudges move the value; what is being typed is left alone
    LaunchedEffect(value) { if (!focused) text = formatClipTime(value) }
    val typed = parseClipTime(text)
    val isError = typed == null || typed !in range
    val focusManager = LocalFocusManager.current
    val gold = LocalAppTheme.current.primaryColor
    val onSurface = MaterialTheme.colorScheme.onSurface
    val nudge = { by: Int ->
        val next = (value + by).coerceIn(range)
        onValue(next)
        text = formatClipTime(next)
    }
    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        focused -> gold
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                .border(1.dp, borderColor, RoundedCornerShape(14.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NudgeButton(Icons.Default.Remove, "$label a second earlier", enabled = value > range.first) { nudge(-1) }
            BasicTextField(
                value = text,
                onValueChange = { changed ->
                    text = changed.take(9)
                    parseClipTime(changed)?.takeIf { it in range }?.let(onValue)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    color = if (isError) MaterialTheme.colorScheme.error else onSurface,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(gold),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged {
                        focused = it.isFocused
                        if (!it.isFocused) text = formatClipTime(value)
                    }
            )
            NudgeButton(Icons.Default.Add, "$label a second later", enabled = value < range.last) { nudge(1) }
        }
    }
}

@Composable
private fun NudgeButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp))
    }
}
