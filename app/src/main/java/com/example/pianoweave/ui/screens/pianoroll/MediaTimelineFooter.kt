package com.example.pianoweave.ui.screens.pianoroll

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.pianoweave.ui.theme.LocalAppTheme
import com.example.pianoweave.ui.viewmodel.PianoWeaveViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaTimelineFooter(
    viewModel: PianoWeaveViewModel,
    dur: Long,
    onSeekState: (Boolean) -> Unit,
    onResetHead: () -> Unit
) {
    val appTheme = LocalAppTheme.current
    val ColorGold = appTheme.primaryColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorSurface)
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .pointerInput(Unit) { detectTapGestures { } }, // Consume gestures
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconButton(
                onClick = { viewModel.isPlaying = !viewModel.isPlaying },
                modifier = Modifier
                    .size(44.dp)
                    .background(ColorGold, CircleShape)
            ) {
                Icon(
                    imageVector = if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp)
                )
            }
            IconButton(
                onClick = { onResetHead() },
                modifier = Modifier
                    .size(44.dp)
                    .background(ColorSurface, CircleShape)
                    .border(1.dp, ColorSlate, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Replay,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(20.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val currentMs = viewModel.playheadMs.coerceIn(0L, dur.coerceAtLeast(1L))
                val fraction = if (dur > 0L) (currentMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f

                if (viewModel.isLoopingEnabled) {
                    RangeSlider(
                        value = viewModel.loopStartMs.toFloat()..viewModel.loopEndMs.toFloat(),
                        onValueChange = {
                            viewModel.loopStartMs = it.start.toLong()
                            viewModel.loopEndMs = it.endInclusive.toLong()
                        },
                        valueRange = 0f..dur.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = ColorGold,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .offset(y = (-12).dp),
                        startThumb = {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(ColorGold, CircleShape)
                                    .border(1.5.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ChevronLeft, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            }
                        },
                        endThumb = {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(ColorGold, CircleShape)
                                    .border(1.5.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ChevronRight, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                }

                Slider(
                    value = currentMs.toFloat(),
                    onValueChange = {
                        onSeekState(true)
                        viewModel.playheadMs = it.toLong()
                    },
                    onValueChangeFinished = { onSeekState(false) },
                    valueRange = 0f..dur.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(15.dp)
                                .background(Color.White, CircleShape)
                        )
                    },
                    track = { _ ->
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                        ) {
                            val totalWidth = size.width
                            val centerY = size.height / 2f
                            val thumbRadius = 7.5.dp.toPx()
                            val usableWidth = (totalWidth - 2 * thumbRadius).coerceAtLeast(0f)
                            val thumbCenterX = thumbRadius + fraction * usableWidth

                            // 1. Inactive Track
                            val inactiveTrackHeight = 2.5.dp.toPx()
                            drawRoundRect(
                                color = ColorSlate.copy(alpha = 0.5f),
                                topLeft = Offset(0f, centerY - inactiveTrackHeight / 2f),
                                size = Size(totalWidth, inactiveTrackHeight),
                                cornerRadius = CornerRadius(inactiveTrackHeight / 2f)
                            )

                            // 2. Loop Range Highlight (if looping enabled)
                            if (viewModel.isLoopingEnabled) {
                                val durLong = dur.coerceAtLeast(1L)
                                val sPerc = (viewModel.loopStartMs.toFloat() / durLong.toFloat()).coerceIn(0f, 1f)
                                val ePerc = (viewModel.loopEndMs.toFloat() / durLong.toFloat()).coerceIn(0f, 1f)
                                val startX = thumbRadius + sPerc * usableWidth
                                val endX = thumbRadius + ePerc * usableWidth
                                val rangeW = (endX - startX).coerceAtLeast(0f)
                                if (rangeW > 0f) {
                                    val loopTrackHeight = 7.dp.toPx()
                                    drawRoundRect(
                                        color = ColorGold.copy(alpha = 0.3f),
                                        topLeft = Offset(startX, centerY - loopTrackHeight / 2f),
                                        size = Size(rangeW, loopTrackHeight),
                                        cornerRadius = CornerRadius(loopTrackHeight / 2f)
                                    )
                                }
                            }

                            // 3. Active Progress Track
                            val activeTrackHeight = 6.5.dp.toPx()
                            val activeWidth = thumbCenterX.coerceIn(0f, totalWidth)
                            if (activeWidth > 0f) {
                                drawRoundRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(ColorGold, ColorGold),
                                        startX = 0f,
                                        endX = activeWidth.coerceAtLeast(1f)
                                    ),
                                    topLeft = Offset(0f, centerY - activeTrackHeight / 2f),
                                    size = Size(activeWidth, activeTrackHeight),
                                    cornerRadius = CornerRadius(activeTrackHeight / 2f)
                                )
                            }
                        }
                    }
                )
            }
        }
        Spacer(modifier = Modifier.width(20.dp))
        IconButton(
            onClick = { viewModel.isLoopingEnabled = !viewModel.isLoopingEnabled },
            modifier = Modifier
                .size(48.dp)
                .background(
                    if (viewModel.isLoopingEnabled) ColorGold else ColorSurface,
                    RoundedCornerShape(12.dp)
                )
                .border(
                    1.dp,
                    if (!viewModel.isLoopingEnabled) ColorSlate else Color.Transparent,
                    RoundedCornerShape(12.dp)
                )
        ) {
            Icon(
                imageVector = Icons.Default.Repeat,
                contentDescription = null,
                tint = if (viewModel.isLoopingEnabled) Color.Black else ColorGold,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
