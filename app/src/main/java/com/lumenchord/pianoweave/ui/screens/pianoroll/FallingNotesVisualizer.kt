package com.lumenchord.pianoweave.ui.screens.pianoroll

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.lumenchord.pianoweave.midi.MidiInputManager
import com.lumenchord.pianoweave.midi.MidiNoteEvent
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme

@Composable
internal fun FallingNotesVisualizer(
    events: List<MidiNoteEvent>,
    head: Long,
    start: Int,
    numWhiteKeys: Int,
    waitTargetPitches: Set<Int> = emptySet(),
    satisfiedPitches: Set<Int> = emptySet()
) {
    val appTheme = LocalAppTheme.current
    val ColorGold = appTheme.primaryColor
    val ColorGoldLight = appTheme.lightColor
    val ColorUpcomingNote = appTheme.upcomingColor
    val ColorWaitTarget = appTheme.waitTargetColor
    val ColorWaitTargetLight = appTheme.waitTargetLightColor
    val ColorTarget = appTheme.targetColor
    val ColorBaseline = appTheme.baselineColor
    val ColorSuccess = appTheme.successColor
    val ColorSuccessLight = appTheme.successLightColor

    val scale = 0.25f
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (size.width <= 0 || size.height <= 0) return@Canvas
        val tw = size.width

        // Rich vertical background gradient for falling notes section
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    appTheme.palette.stageTop,
                    appTheme.palette.stageMiddle,
                    appTheme.palette.stageBottom
                )
            ),
            size = size
        )

        // 1. Grid Lanes
        for (i in 0..numWhiteKeys) {
            val x = i * (tw / numWhiteKeys)
            drawLine(
                color = appTheme.palette.stageLane.copy(alpha = 0.6f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f
            )
        }

        // 2. Bar Lines
        val barIntervalMs = 2000L
        val firstBar = (head / barIntervalMs) * barIntervalMs
        val lastMs = head + (size.height / scale).toLong()
        for (ms in firstBar..lastMs step barIntervalMs) {
            val y = size.height - ((ms - head) * scale)
            if (y in 0f..size.height) {
                drawLine(
                    color = Color.White.copy(alpha = 0.1f),
                    start = Offset(0f, y),
                    end = Offset(tw, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        // 3. Falling Notes
        val filtered = events.filter { (it.startMs + it.durationMs) >= head && it.startMs <= lastMs }
        val (blackEvents, whiteEvents) = filtered.partition { isPitchBlack(it.pitch) }

        fun drawNote(e: MidiNoteEvent) {
            val (x1, x2) = getPitchXRange(e.pitch, start, tw, numWhiteKeys)
            val kw = x2 - x1
            val h = (e.durationMs * scale).coerceAtLeast(12f)
            val y = size.height - ((e.startMs - head) * scale) - h

            val now = System.currentTimeMillis()
            val isAtBaseline = head >= e.startMs && head <= (e.startMs + e.durationMs)
            val isHitting = head >= e.startMs && head <= (e.startMs + 60L)
            val isWaiting = waitTargetPitches.contains(e.pitch) && head >= e.startMs - 50 && head <= e.startMs + 50
            val isSatisfied = satisfiedPitches.contains(e.pitch) && isWaiting

            val lastPress = MidiInputManager.lastPressTimestamps[e.pitch] ?: 0L
            val isRecentOnset = (now - lastPress <= 350L) && (MidiInputManager.pressedKeys.contains(e.pitch) || now - lastPress <= 250L)
            val isUserMatch = (isAtBaseline && MidiInputManager.pressedKeys.contains(e.pitch)) || (isWaiting && isRecentOnset)

            val baseCol = when {
                isSatisfied || isUserMatch -> ColorSuccess
                isWaiting -> ColorWaitTarget
                isHitting -> ColorTarget
                isAtBaseline -> ColorGold
                else -> ColorUpcomingNote
            }
            val highlightCol = when {
                isSatisfied || isUserMatch -> ColorSuccessLight
                isWaiting -> ColorWaitTargetLight
                isHitting -> ColorGoldLight
                isAtBaseline -> ColorGoldLight.copy(alpha = 0.8f)
                else -> baseCol.copy(alpha = 0.6f)
            }

            val noteTop = y.coerceIn(-h, size.height)
            val corner = 6.dp.toPx()

            // Soft halo, like the glow around the logo's ribbons; stronger on notes being played
            val haloAlpha = if (isAtBaseline || isWaiting || isUserMatch) 0.22f else 0.08f
            for ((spread, alpha) in listOf(5.dp.toPx() to haloAlpha * 0.35f, 2.5.dp.toPx() to haloAlpha * 0.65f)) {
                drawRoundRect(
                    color = baseCol.copy(alpha = alpha),
                    topLeft = Offset(x1 + 0.5f - spread, noteTop - spread),
                    size = Size(kw - 1f + spread * 2, h + spread * 2),
                    cornerRadius = CornerRadius(corner + spread)
                )
            }

            drawRoundRect(
                brush = Brush.verticalGradient(listOf(highlightCol, baseCol), startY = y, endY = y + h),
                topLeft = Offset(x1 + 0.5f, noteTop),
                size = Size(kw - 1f, h),
                cornerRadius = CornerRadius(corner)
            )

            // Thin lit edge, like the highlight line along each ribbon
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.12f)),
                    startY = y,
                    endY = y + h
                ),
                topLeft = Offset(x1 + 1f, noteTop + 0.5f),
                size = Size(kw - 2f, h - 1f),
                cornerRadius = CornerRadius(corner),
                style = Stroke(width = 1.dp.toPx())
            )

            if (isHitting || isUserMatch || isWaiting) {
                val glowCol = if (isWaiting && !isSatisfied) ColorWaitTarget else baseCol
                drawRect(
                    brush = Brush.verticalGradient(listOf(glowCol.copy(alpha = 0.4f), Color.Transparent)),
                    topLeft = Offset(x1, y.coerceIn(-h, size.height) + h),
                    size = Size(kw, 35.dp.toPx())
                )
            }
        }

        // Hit line: a line of light with a soft glow rising from it, brightest in the middle
        val baselineY = size.height - 1f
        val glowHeight = 28.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, ColorBaseline.copy(alpha = 0.10f), ColorBaseline.copy(alpha = 0.28f)),
                startY = baselineY - glowHeight,
                endY = baselineY
            ),
            topLeft = Offset(0f, baselineY - glowHeight),
            size = Size(tw, glowHeight)
        )
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(ColorBaseline.copy(alpha = 0.55f), ColorBaseline, ColorGoldLight, ColorBaseline, ColorBaseline.copy(alpha = 0.55f))
            ),
            start = Offset(0f, baselineY),
            end = Offset(tw, baselineY),
            strokeWidth = 3.dp.toPx()
        )

        whiteEvents.forEach { drawNote(it) }
        blackEvents.forEach { drawNote(it) }
    }
}
