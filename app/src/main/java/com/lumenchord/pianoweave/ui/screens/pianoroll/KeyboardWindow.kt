package com.lumenchord.pianoweave.ui.screens.pianoroll

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.midi.MidiNoteEvent
import com.lumenchord.pianoweave.ui.theme.Ink
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.viewmodel.PIANO_HIGHEST
import com.lumenchord.pianoweave.ui.viewmodel.PIANO_LOWEST

/** The keys a window of the keyboard shows, from [low] to [high] (both white keys). */
data class KeyWindow(val low: Int, val high: Int) {
    val whiteKeys: Int get() = (low..high).count { !isPitchBlack(it) }
    val isWhole: Boolean get() = low <= PIANO_LOWEST && high >= PIANO_HIGHEST
}

/** Follow's window: two octaves, C to C. */
const val FOLLOW_SPAN = 24

/** The C at or below [pitch]. */
internal fun octaveFloor(pitch: Int) = pitch - pitch % 12

/** The B at or above [pitch]. */
internal fun octaveCeil(pitch: Int) = pitch + (11 - pitch % 12)

/** [range] widened to white keys at both ends, within the piano. */
internal fun whiteBounded(low: Int, high: Int): KeyWindow {
    val lo = (if (isPitchBlack(low)) low - 1 else low).coerceAtLeast(PIANO_LOWEST)
    val hi = (if (isPitchBlack(high)) high + 1 else high).coerceAtMost(PIANO_HIGHEST)
    return KeyWindow(lo, hi)
}

/** The song's range, in whole octaves, within the piano. */
internal fun songWindow(events: List<MidiNoteEvent>): KeyWindow {
    if (events.isEmpty()) return KeyWindow(PIANO_LOWEST, PIANO_HIGHEST)
    val low = octaveFloor(events.minOf { it.pitch }).coerceAtLeast(PIANO_LOWEST)
    val high = octaveCeil(events.maxOf { it.pitch }).coerceAtMost(PIANO_HIGHEST)
    return whiteBounded(low, high)
}

/**
 * Where Follow's window should start (a C) for the notes [upcoming] soon: unchanged while they all fit in it, else
 * the C whose two octaves hold the most of them, the nearest to [current] on a tie.
 */
internal fun bestFollowLow(upcoming: List<Int>, current: Int): Int {
    if (upcoming.isEmpty()) return current
    if (upcoming.all { it in current..current + FOLLOW_SPAN }) return current
    val lowestC = 24
    val highestC = PIANO_HIGHEST - FOLLOW_SPAN
    val candidates = (octaveFloor(upcoming.min()) - 12..octaveFloor(upcoming.max()) step 12)
        .map { it.coerceIn(lowestC, highestC) }.distinct()
    return candidates.maxWithOrNull(
        compareBy<Int> { c -> upcoming.count { it in c..c + FOLLOW_SPAN } }.thenBy { -kotlin.math.abs(it - current) }
    ) ?: current
}

/**
 * The whole keyboard in miniature under the keys, when only part of it is shown: the window in light, the song's
 * notes outside it in the accent, and the range in words. Tapping it moves the window there.
 */
@Composable
internal fun KeyRangeStrip(
    window: KeyWindow,
    songPitches: Set<Int>,
    label: String,
    onPick: (pitch: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    val whites = (PIANO_LOWEST..PIANO_HIGHEST).filter { !isPitchBlack(it) }
    val well = MaterialTheme.colorScheme.tertiaryContainer
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(well)
            .padding(horizontal = 6.dp)
            .semantics { contentDescription = "Keys shown: $label. Tap to move" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(vertical = 5.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .pointerInput(whites) {
                        detectTapGestures { offset ->
                            val index = (offset.x / size.width * whites.size).toInt().coerceIn(0, whites.lastIndex)
                            onPick(whites[index])
                        }
                    }
            ) {
                val w = size.width / whites.size
                whites.forEachIndexed { i, pitch ->
                    // A white key's own octave holds its black keys too, so a sharp outside the window is marked
                    val inWindow = pitch in window.low..window.high
                    val played = pitch in songPitches || (pitch + 1) in songPitches
                    val color = when {
                        inWindow -> theme.palette.surface
                        played -> theme.primaryColor
                        else -> theme.palette.mutedContainer
                    }
                    drawRoundRect(color, Offset(i * w + 0.5f, 0f), Size(w - 1f, size.height), CornerRadius(1.5f))
                }
                val x1 = whites.indexOfFirst { it >= window.low }.coerceAtLeast(0) * w
                val x2 = (whites.indexOfLast { it <= window.high } + 1) * w
                drawRoundRect(
                    theme.primaryColor,
                    Offset(x1 - 1.dp.toPx(), -2.dp.toPx()),
                    Size(x2 - x1 + 2.dp.toPx(), size.height + 4.dp.toPx()),
                    CornerRadius(3.dp.toPx()),
                    style = Stroke(1.5.dp.toPx())
                )
            }
        }
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Ink.copy(alpha = 0.7f), maxLines = 1)
    }
}
