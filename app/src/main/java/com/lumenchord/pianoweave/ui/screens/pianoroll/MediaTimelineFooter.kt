package com.lumenchord.pianoweave.ui.screens.pianoroll

import com.lumenchord.pianoweave.ui.theme.litColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.RoundedCornerShape
import com.lumenchord.pianoweave.ui.theme.onKeyButton
import com.lumenchord.pianoweave.ui.theme.goldLeaf
import androidx.compose.ui.platform.LocalContext
import com.lumenchord.pianoweave.ui.theme.PillShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.Canvas
import com.lumenchord.pianoweave.ui.theme.Ink
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.*
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaTimelineFooter(
    viewModel: PianoWeaveViewModel,
    dur: Long,
    onSeekState: (Boolean) -> Unit,
    onResetHead: () -> Unit,
    /** The song's bars, for the song map and the bar number; empty hides both. */
    songBars: List<SongMapBar> = emptyList(),
    /** A phone in portrait: no room for the back-to-start button, so the elapsed time does it instead. */
    isCompact: Boolean = false
) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val showSongMap = viewModel.isSongMapEnabled && songBars.isNotEmpty()
    val appTheme = LocalAppTheme.current
    val ColorGold = appTheme.litColor
    val ColorGoldLight = appTheme.lightColor
    val edgeColor = ColorSlate
    val timeStyle = TextStyle(
        color = Ink.copy(alpha = 0.6f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum" // Equal-width digits, so the time doesn't jitter while playing
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorSurface)
            .drawBehind {
                // Thin edge along the top, fading out towards the sides
                drawLine(
                    brush = Brush.horizontalGradient(listOf(Color.Transparent, edgeColor, edgeColor, Color.Transparent)),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .pointerInput(Unit) { detectTapGestures { } }, // Consume gestures
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(
                onClick = { viewModel.isPlaying = !viewModel.isPlaying },
                modifier = Modifier
                    .size(44.dp)
                    .drawBehind {
                        // Soft glow around the play button, like the light around the logo
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(ColorGold.copy(alpha = 0.35f), Color.Transparent),
                                center = center,
                                radius = size.minDimension * 0.85f
                            ),
                            radius = size.minDimension * 0.85f
                        )
                    }
                    // By day gold leaf lit from within, as the design has it; at night the gold it always had
                    .then(
                        if (appTheme.isLight) Modifier.goldLeaf(appTheme, CircleShape)
                        else Modifier.background(Brush.linearGradient(listOf(ColorGoldLight, ColorGold)), CircleShape)
                    )
            ) {
                Icon(
                    imageVector = if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (viewModel.isPlaying) "Pause" else "Play",
                    tint = if (appTheme.isLight) appTheme.onKeyButton else if (appTheme.isLightAccent) Color.Black else Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            if (!isCompact) {
                RollButton(
                    onClick = { onResetHead() },
                    size = 44.dp,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = "Back to start",
                        tint = Ink,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
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

                if (showSongMap) {
                    SongMap(
                        bars = songBars,
                        durationMs = dur,
                        playheadMs = currentMs,
                        loop = if (viewModel.isLoopingEnabled) minOf(viewModel.loopStartMs, viewModel.loopEndMs)..maxOf(viewModel.loopStartMs, viewModel.loopEndMs) else null,
                        onSeek = { viewModel.playheadMs = it },
                        onSeekState = onSeekState,
                        onLoopChange = { start, end ->
                            viewModel.loopStartMs = start
                            viewModel.loopEndMs = end
                        }
                    )
                } else {
                if (viewModel.isLoopingEnabled) {
                    RangeSlider(
                        // Ordered, since the loop settings can set the end before the start
                        value = minOf(viewModel.loopStartMs, viewModel.loopEndMs).toFloat()..
                            maxOf(viewModel.loopStartMs, viewModel.loopEndMs).toFloat(),
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
                            // A gold pill standing across the groove, by day and by night
                            Box(Modifier.size(width = 14.dp, height = 24.dp).goldLeaf(appTheme, RoundedCornerShape(7.dp)))
                        },
                        endThumb = {
                            // A gold pill standing across the groove, by day and by night
                            Box(Modifier.size(width = 14.dp, height = 24.dp).goldLeaf(appTheme, RoundedCornerShape(7.dp)))
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
                        thumbColor = Ink,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .drawBehind {
                                    val glowRadius = size.minDimension
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            listOf(ColorGold.copy(alpha = 0.35f), Color.Transparent),
                                            center = center,
                                            radius = glowRadius
                                        ),
                                        radius = glowRadius
                                    )
                                }
                                // An ivory disc ringed in gold, by day and by night as the design has it
                                .background(if (appTheme.isLight) Color(0xFFFBF3E2) else Color(0xFFFFF4E0), CircleShape)
                                .border(2.dp, ColorGold, CircleShape)
                        )
                    },
                    track = { _ ->
                        val trackInactiveColor = ColorSlate.copy(alpha = 0.5f)
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                        ) {
                            val totalWidth = size.width
                            val centerY = size.height / 2f
                            val thumbRadius = 9.dp.toPx()
                            val isDay = appTheme.isLight
                            val usableWidth = (totalWidth - 2 * thumbRadius).coerceAtLeast(0f)
                            val thumbCenterX = thumbRadius + fraction * usableWidth

                            // 1. Inactive Track
                            val inactiveTrackHeight = (if (isDay) 6.dp else 2.5.dp).toPx()
                            drawRoundRect(
                                color = if (isDay) appTheme.palette.mutedContainer else trackInactiveColor,
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
                                    val loopTrackHeight = (if (isDay) 12.dp else 7.dp).toPx()
                                    drawRoundRect(
                                        color = ColorGold.copy(alpha = if (isDay) 0.22f else 0.3f),
                                        topLeft = Offset(startX, centerY - loopTrackHeight / 2f),
                                        size = Size(rangeW, loopTrackHeight),
                                        cornerRadius = CornerRadius(if (isDay) 4.dp.toPx() else loopTrackHeight / 2f)
                                    )
                                    // By day the band is outlined in gold, as the design has it
                                    if (isDay) drawRoundRect(
                                        color = ColorGold,
                                        topLeft = Offset(startX, centerY - loopTrackHeight / 2f),
                                        size = Size(rangeW, loopTrackHeight),
                                        cornerRadius = CornerRadius(4.dp.toPx()),
                                        style = Stroke(1.dp.toPx())
                                    )
                                }
                            }

                            // 3. Active Progress Track
                            val activeTrackHeight = (if (isDay) 6.dp else 6.5.dp).toPx()
                            val activeWidth = thumbCenterX.coerceIn(0f, totalWidth)
                            if (activeWidth > 0f) {
                                drawRoundRect(
                                    brush = Brush.horizontalGradient(
                                        colors = if (isDay) appTheme.fill ?: listOf(ColorGoldLight, ColorGold) else listOf(ColorGold, ColorGoldLight),
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-4).dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val elapsed = formatTime(viewModel.playheadMs.coerceIn(0L, dur.coerceAtLeast(0L)))
                val bar = if (showSongMap) barAt(songBars, viewModel.playheadMs) else null
                val elapsedText = if (bar != null) "Bar $bar / ${songBars.size} · $elapsed" else elapsed
                if (isCompact) {
                    // Back to start, on the time itself: a small chip, as there is no room for a button of its own
                    Row(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(ColorSlate.copy(alpha = 0.6f))
                            .clickable(onClickLabel = "Back to start") { onResetHead() }
                            .padding(start = 4.dp, end = 7.dp, top = 1.dp, bottom = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = Ink.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
                        Text(elapsedText, style = timeStyle)
                    }
                } else {
                    Text(elapsedText, style = timeStyle)
                }
                Text(formatTime(dur), style = timeStyle)
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        RollButton(
            onClick = { viewModel.isLoopingEnabled = !viewModel.isLoopingEnabled },
            isOn = viewModel.isLoopingEnabled,
            // Its own gesture, as the speed button's for the metronome; the hint is in the Loop settings
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.setSongMapEnabled(context, !viewModel.isSongMapEnabled)
            },
            onLongClickLabel = if (viewModel.isSongMapEnabled) "Hide the song map" else "Show the song map",
            role = Role.Switch,
            size = 44.dp,
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.Repeat,
                contentDescription = if (viewModel.isLoopingEnabled) "Turn off loop" else "Loop a section",
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
