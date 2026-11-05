package com.lumenchord.pianoweave.ui.screens.learn.cards

import com.lumenchord.pianoweave.ui.theme.PillShape
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.ui.viewmodel.SelectedMediaFile
import com.lumenchord.pianoweave.ui.viewmodel.TranscriptionSource
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.lumenchord.pianoweave.ui.theme.Moods

/**
 * Link, file or microphone: what the card transcribes. Hand-built rather than a Material SegmentedButton,
 * which always reserves room for a checkmark and never shrinks its label, so "Record" ran into the
 * rounded end of a three-way row in portrait. Portrait says "Mic" instead, so the three segments share the width equally.
 */
@Composable
internal fun SourceSelector(
    selected: TranscriptionSource,
    enabled: Boolean,
    isCompact: Boolean,
    onSelect: (TranscriptionSource) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        Triple(TranscriptionSource.Link, "Link", Icons.Default.Link),
        Triple(TranscriptionSource.File, "File", Icons.Default.AudioFile),
        Triple(TranscriptionSource.Record, "Mic", Icons.Default.Mic)
    )
    val accent = MaterialTheme.colorScheme.secondary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val pill = RoundedCornerShape(percent = 50)
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // One size for every label, the largest at which all of them fit, so the segments read as a set
        // rather than each shrinking on its own.
        val segmentWidth = (maxWidth - SegmentDivider * (options.size - 1)) / options.size
        val labelFontSize = remember(segmentWidth, labelStyle, density) {
            val labelWidthsPx = options.indices.map { index ->
                val padding = segmentPadding(isFirst = index == 0, isLast = index == options.lastIndex)
                with(density) { (segmentWidth - padding - SegmentIconWithGap).toPx() }
            }
            var size = LabelMaxFontSize
            while (size > LabelMinFontSize && options.indices.any { index ->
                    textMeasurer.measure(options[index].second, labelStyle.copy(fontSize = size.sp), maxLines = 1)
                        .size.width > labelWidthsPx[index]
                }) {
                size -= 0.5f
            }
            size.sp
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isCompact) 42.dp else 36.dp)
                .clip(pill)
                .border(1.dp, outline, pill)
                .selectableGroup()
        ) {
            options.forEachIndexed { index, (source, label, icon) ->
                val isFirst = index == 0
                val isLast = index == options.lastIndex
                val isSelected = source == selected
                val shape = RoundedCornerShape(
                    topStartPercent = if (isFirst) 50 else 0,
                    bottomStartPercent = if (isFirst) 50 else 0,
                    topEndPercent = if (isLast) 50 else 0,
                    bottomEndPercent = if (isLast) 50 else 0
                )
                val contentColor = (if (isSelected) accent else MaterialTheme.colorScheme.onSurface)
                    .copy(alpha = if (enabled) 1f else 0.38f)

                if (!isFirst) VerticalDivider(thickness = SegmentDivider, color = outline)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(
                            if (isSelected) Modifier
                                .background(accent.copy(alpha = 0.18f), shape)
                                .border(1.dp, accent.copy(alpha = 0.6f), shape)
                            else Modifier
                        )
                        .selectable(
                            selected = isSelected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(source) }
                        )
                        // The rounded ends eat into the outer segments, so their content keeps clear of the curve.
                        .padding(start = if (isFirst) 12.dp else 6.dp, end = if (isLast) 12.dp else 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = label,
                        color = contentColor,
                        style = labelStyle.copy(fontSize = labelFontSize),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }
}
private val SegmentDivider = 1.dp
/** Icon (18dp) plus the gap before the label. */
private val SegmentIconWithGap = 24.dp
private const val LabelMaxFontSize = 14f
private const val LabelMinFontSize = 10f
/** Matches the segment padding: the outer ends keep clear of the pill's curve. */
private fun segmentPadding(isFirst: Boolean, isLast: Boolean): Dp =
    (if (isFirst) 12.dp else 6.dp) + (if (isLast) 12.dp else 6.dp)
/** The empty file slot: one large target that also says which files work and how big they may be. */
@Composable
internal fun ChooseFileArea(
    maxSizeMb: Float?,
    maxMinutes: Float?,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.secondary
    val limits = listOfNotNull(
        maxSizeMb?.let { "up to ${"%.0f".format(it)} MB" },
        maxMinutes?.let { "${"%.0f".format(it)} minutes" }
    ).joinToString(" · ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .drawBehind {
                drawRoundRect(
                    color = accent.copy(alpha = 0.55f),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = DrawStroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
                    )
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(Icons.Default.UploadFile, contentDescription = null, tint = accent, modifier = Modifier.size(30.dp))
        Text(
            text = "Choose a recording",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = accent
        )
        // No format list: the picker only offers files the server accepts.
        if (limits.isNotEmpty()) {
            Text(
                text = limits.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}
/** The picked file in place of the link field: name, length and size. Tapping it picks another; × removes it. */
@Composable
internal fun SelectedFileRow(
    file: SelectedMediaFile,
    enabled: Boolean,
    onChange: () -> Unit,
    onClear: () -> Unit,
    icon: ImageVector = Icons.Default.AudioFile,
    changeLabel: String = "Change",
    clearLabel: String = "Remove file"
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = if (enabled) 0.6f else 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClickLabel = changeLabel, onClick = onChange)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val details = listOfNotNull(
                    file.durationSeconds?.let { formatFileDuration(it) },
                    file.sizeBytes?.let { "%.1f MB".format(it / (1024.0 * 1024.0)) }
                ).joinToString(" · ")
                Text(
                    text = buildAnnotatedString {
                        if (details.isNotEmpty()) append("$details · ")
                        if (enabled) {
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)) {
                                append(changeLabel)
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (enabled) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, contentDescription = clearLabel)
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}
/** The empty recording slot: tapping it starts recording from the microphone. */
@Composable
internal fun StartRecordingArea(
    maxMinutes: Float?,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.secondary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .drawBehind {
                drawRoundRect(
                    color = accent.copy(alpha = 0.55f),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = DrawStroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
                    )
                )
            }
            .clickable(onClickLabel = "Start recording", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(RecordRed, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tap to record",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = accent
        )
        maxMinutes?.let {
            Text(
                text = "Up to ${"%.0f".format(it)} minutes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}
/** While recording: a pulsing dot, the elapsed time, a level meter, and stop / discard. */
@Composable
internal fun RecordingArea(
    elapsedMs: Long,
    level: Float,
    maxMinutes: Float?,
    onStop: () -> Unit,
    onDiscard: () -> Unit
) {
    val pulse by rememberInfiniteTransition(label = "recording").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 700), RepeatMode.Reverse),
        label = "recordingDot"
    )
    val elapsed = formatFileDuration(elapsedMs / 1000f)
    val limit = maxMinutes?.let { " / ${formatFileDuration(it * 60f)}" }.orEmpty()

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = RecordRed.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, RecordRed.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .graphicsLayer { alpha = pulse }
                        .background(RecordRed, CircleShape)
                )
                Text(
                    text = "Recording",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = elapsed + limit,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ProgressLine(fraction = level, color = RecordRed)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDiscard,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = PillShape,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Discard",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    onClick = onStop,
                    modifier = Modifier
                        .weight(1.4f)
                        .height(44.dp),
                    shape = PillShape,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RecordRed, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Stop",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
/**
 * Recording is courage, not danger: [Moods.Furnace], the fire that did not harm the three in it
 * (Daniel 3:27).
 */
private val RecordRed = Moods.Furnace.core
/** A rounded bar filled to [fraction] with [color]. */
@Composable
private fun ProgressLine(fraction: Float, color: Color, height: Dp = 6.dp) {
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(percent = 50)),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}
/** Takes longer than this ask before they are thrown away. */
internal const val DISCARD_CONFIRM_SECONDS = 10f
private fun formatFileDuration(seconds: Float): String {
    val total = seconds.toInt()
    return if (total >= 3600) "%d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
    else "%d:%02d".format(total / 60, total % 60)
}
