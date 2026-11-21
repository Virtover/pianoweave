package com.lumenchord.pianoweave.ui.screens.pianoroll

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lumenchord.pianoweave.midi.Beat
import com.lumenchord.pianoweave.midi.MidiNoteEvent
import com.lumenchord.pianoweave.ui.theme.Ink
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme

/** One bar of the song map: when it starts and ends, and how many notes begin in it. */
class SongMapBar(val startMs: Long, val endMs: Long, val notes: Int)

/**
 * The song's bars for the song map: from its beat grid when it has one, else even two-second stretches (the same
 * fallback as the stage's lines), each with how many notes start in it.
 */
internal fun songMapBars(events: List<MidiNoteEvent>, beats: List<Beat>, durationMs: Long): List<SongMapBar> {
    val starts = beats.filter { it.isBarStart }.map { it.timeMs }.ifEmpty { (0L until durationMs step 2000L).toList() }
    if (starts.isEmpty()) return emptyList()
    val sorted = events.map { it.startMs }.sorted()
    return starts.mapIndexed { i, start ->
        val end = starts.getOrNull(i + 1) ?: durationMs
        val from = sorted.binarySearch(start).let { if (it < 0) -it - 1 else it }
        val to = sorted.binarySearch(end).let { if (it < 0) -it - 1 else it }
        SongMapBar(start, end, (to - from).coerceAtLeast(0))
    }
}

/** The bar number (from 1) the playhead [ms] is in, or null without bars. */
internal fun barAt(bars: List<SongMapBar>, ms: Long): Int? =
    bars.indexOfLast { it.startMs <= ms }.takeIf { it >= 0 }?.plus(1)

/**
 * The song map: a strip of the song's bars, each as tall as it is busy; what is played in the accent, the rest
 * muted, the loop marked, and a line where the song is. Tapping or dragging moves there.
 */
@Composable
internal fun SongMap(
    bars: List<SongMapBar>,
    durationMs: Long,
    playheadMs: Long,
    loop: LongRange?,
    onSeek: (Long) -> Unit,
    onSeekState: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    val muted = theme.palette.mutedContainer
    val seek by rememberUpdatedState(onSeek)
    val seekState by rememberUpdatedState(onSeekState)
    val maxNotes = (bars.maxOfOrNull { it.notes } ?: 1).coerceAtLeast(1)
    val dur = durationMs.coerceAtLeast(1L)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .semantics { contentDescription = "Song map: how busy each bar is. Tap or drag to move through the song" }
            .pointerInput(dur) { detectTapGestures { seek((it.x / size.width * dur).toLong().coerceIn(0L, dur)) } }
            .pointerInput(dur) {
                detectHorizontalDragGestures(
                    onDragStart = { seekState(true); seek((it.x / size.width * dur).toLong().coerceIn(0L, dur)) },
                    onDragEnd = { seekState(false) },
                    onDragCancel = { seekState(false) }
                ) { change, _ -> seek((change.position.x / size.width * dur).toLong().coerceIn(0L, dur)) }
            }
    ) {
        val x = { ms: Long -> ms.toFloat() / dur * size.width }
        loop?.let {
            drawRoundRect(
                theme.primaryColor.copy(alpha = 0.18f),
                Offset(x(it.first), 0f),
                Size((x(it.last) - x(it.first)).coerceAtLeast(2f), size.height),
                CornerRadius(4.dp.toPx())
            )
            drawRoundRect(
                theme.primaryColor,
                Offset(x(it.first), 0f),
                Size((x(it.last) - x(it.first)).coerceAtLeast(2f), size.height),
                CornerRadius(4.dp.toPx()),
                style = Stroke(1.5.dp.toPx())
            )
        }
        val gap = if (bars.size > 0 && size.width / bars.size > 3f) 1f else 0f
        for (bar in bars) {
            val left = x(bar.startMs)
            val width = (x(bar.endMs) - left - gap).coerceAtLeast(1f)
            val height = size.height * (0.18f + 0.72f * bar.notes / maxNotes)
            drawRoundRect(
                if (bar.startMs < playheadMs) theme.primaryColor else muted,
                Offset(left, size.height - height - 3.dp.toPx()),
                Size(width, height),
                CornerRadius(1.5f)
            )
        }
        val px = x(playheadMs.coerceIn(0L, dur))
        drawLine(theme.primaryColor.copy(alpha = 0.45f), Offset(px, 0f), Offset(px, size.height), 6.dp.toPx())
        drawLine(Ink, Offset(px, 0f), Offset(px, size.height), 2.dp.toPx())
    }
}
