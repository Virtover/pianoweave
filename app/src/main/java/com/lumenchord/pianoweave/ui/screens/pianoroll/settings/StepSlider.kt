package com.lumenchord.pianoweave.ui.screens.pianoroll.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.ui.theme.PillShape
import com.lumenchord.pianoweave.ui.components.SettingLabel
import kotlin.math.roundToInt

/**
 * A setting's title, a short [subtitle], and its current [value] in a chip. Once the value differs from the
 * default ([isDefault] false) the chip shows a reset icon and tapping it calls [onReset].
 */
@Composable
internal fun SettingHeader(
    title: String,
    subtitle: String,
    value: String,
    isDefault: Boolean,
    accent: Color,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingLabel(
            title = title,
            subtitle = subtitle,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )
        Surface(
            onClick = onReset,
            enabled = !isDefault,
            shape = PillShape,
            color = accent.copy(alpha = if (isDefault) 0.08f else 0.16f),
            border = BorderStroke(1.dp, accent.copy(alpha = if (isDefault) 0.25f else 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(start = 10.dp, end = if (isDefault) 10.dp else 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(value, color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                if (!isDefault) {
                    Icon(
                        Icons.Default.RestartAlt,
                        contentDescription = "Reset $title",
                        tint = accent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * Picks one of [stepCount] evenly spaced stops, with − / + buttons for exact single steps. The fill runs from
 * [originIndex] (the setting's normal value) to the current stop, so it shows how far the setting is from
 * normal in either direction. Stops in [labels] are marked on the track and named underneath.
 */
@Composable
internal fun StepSlider(
    stepCount: Int,
    index: Int,
    onIndexChange: (Int) -> Unit,
    originIndex: Int,
    labels: Map<Int, String>,
    accent: Color,
    valueDescription: String,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val currentIndex by rememberUpdatedState(index)
    val currentOnChange by rememberUpdatedState(onIndexChange)
    val select = { newIndex: Int ->
        val clamped = newIndex.coerceIn(0, stepCount - 1)
        if (clamped != currentIndex) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            currentOnChange(clamped)
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        StepButton(Icons.Default.Remove, "Decrease", enabled = index > 0, accent = accent) { select(index - 1) }
        SliderTrack(
            stepCount = stepCount,
            index = index,
            originIndex = originIndex,
            labels = labels,
            accent = accent,
            valueDescription = valueDescription,
            onSelect = select,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        )
        StepButton(Icons.Default.Add, "Increase", enabled = index < stepCount - 1, accent = accent) { select(index + 1) }
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            // Centered on the track, which sits in the top TrackHeight of the slider
            .padding(top = (TrackHeight - StepButtonSize) / 2)
            .size(StepButtonSize),
        shape = CircleShape,
        border = BorderStroke(1.dp, accent.copy(alpha = if (enabled) 0.45f else 0.15f)),
        colors = IconButtonDefaults.outlinedIconButtonColors(
            contentColor = accent,
            disabledContentColor = accent.copy(alpha = 0.3f)
        )
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SliderTrack(
    stepCount: Int,
    index: Int,
    originIndex: Int,
    labels: Map<Int, String>,
    accent: Color,
    valueDescription: String,
    onSelect: (Int) -> Unit,
    modifier: Modifier
) {
    val density = LocalDensity.current
    val inset = with(density) { ThumbInset.toPx() }
    var dragging by remember { mutableStateOf(false) }
    val thumbScale by animateFloatAsState(if (dragging) 1.25f else 1f, label = "thumbScale")
    val trackColor = Color.White.copy(alpha = 0.12f)
    val tickColor = Color.White.copy(alpha = 0.3f)
    val ringColor = MaterialTheme.colorScheme.surface

    fun stopAt(x: Float, width: Int): Int {
        val usable = (width - 2 * inset).coerceAtLeast(1f)
        return (((x - inset) / usable) * (stepCount - 1)).roundToInt()
    }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(TrackHeight)
                .pointerInput(stepCount) {
                    detectTapGestures { onSelect(stopAt(it.x, size.width)) }
                }
                .pointerInput(stepCount) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragging = true
                            onSelect(stopAt(it.x, size.width))
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false }
                    ) { change, _ ->
                        change.consume()
                        onSelect(stopAt(change.position.x, size.width))
                    }
                }
                .semantics {
                    contentDescription = valueDescription
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        index.toFloat(), 0f..(stepCount - 1).toFloat(), steps = stepCount - 2
                    )
                    setProgress { target ->
                        onSelect(target.roundToInt())
                        true
                    }
                }
        ) {
            val cy = size.height / 2f
            val usable = size.width - 2 * inset
            fun x(i: Int) = inset + usable * i / (stepCount - 1)
            val bar = 4.dp.toPx()

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(inset, cy - bar / 2),
                size = Size(usable, bar),
                cornerRadius = CornerRadius(bar / 2)
            )
            val from = minOf(x(originIndex), x(index))
            val to = maxOf(x(originIndex), x(index))
            if (to > from) {
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(from, cy - bar / 2),
                    size = Size(to - from, bar),
                    cornerRadius = CornerRadius(bar / 2)
                )
            }
            labels.keys.forEach { i ->
                val passed = x(i) in from..to
                drawCircle(
                    color = if (passed) Color.Black.copy(alpha = 0.35f) else tickColor,
                    radius = if (i == originIndex) 3.dp.toPx() else 2.dp.toPx(),
                    center = Offset(x(i), cy)
                )
            }

            val thumb = x(index)
            if (dragging) {
                drawCircle(accent.copy(alpha = 0.18f), radius = 18.dp.toPx(), center = Offset(thumb, cy))
            }
            val r = 9.dp.toPx() * thumbScale
            drawCircle(ringColor, radius = r + 2.dp.toPx(), center = Offset(thumb, cy))
            drawCircle(accent, radius = r, center = Offset(thumb, cy))
        }

        // Labels centered under their stops, kept inside the track's width at the ends
        Layout(
            content = {
                labels.forEach { (i, text) ->
                    Text(
                        text = text,
                        fontSize = 10.sp,
                        fontWeight = if (i == index) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (i == index) accent else Color.White.copy(alpha = 0.5f),
                        maxLines = 1
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
            val width = constraints.maxWidth
            val height = placeables.maxOfOrNull { it.height } ?: 0
            val usable = width - 2 * inset
            layout(width, height) {
                labels.keys.zip(placeables).forEach { (i, p) ->
                    val center = inset + usable * i / (stepCount - 1)
                    val left = (center - p.width / 2f).roundToInt().coerceIn(0, (width - p.width).coerceAtLeast(0))
                    p.place(left, 0)
                }
            }
        }
    }
}

private val TrackHeight: Dp = 36.dp
private val StepButtonSize: Dp = 30.dp
/** Room at each end so the thumb at the first and last stop stays inside the track. */
private val ThumbInset: Dp = 13.dp
