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
                    Color(0xFF090D14),
                    Color(0xFF0D1117),
                    Color(0xFF070A0F)
                )
            ),
            size = size
        )

        // 1. Grid Lanes
        for (i in 0..numWhiteKeys) {
            val x = i * (tw / numWhiteKeys)
            drawLine(
                color = Color(0xFF161B22).copy(alpha = 0.6f),
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

            drawRoundRect(
                brush = Brush.verticalGradient(listOf(highlightCol, baseCol), startY = y, endY = y + h),
                topLeft = Offset(x1 + 0.5f, y.coerceIn(-h, size.height)),
                size = Size(kw - 1f, h),
                cornerRadius = CornerRadius(6.dp.toPx())
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

        val baselineY = size.height - 1f
        drawLine(ColorBaseline, Offset(0f, baselineY), Offset(tw, baselineY), 3.dp.toPx())

        whiteEvents.forEach { drawNote(it) }
        blackEvents.forEach { drawNote(it) }
    }
}
