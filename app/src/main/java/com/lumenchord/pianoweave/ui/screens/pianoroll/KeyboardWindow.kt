package com.lumenchord.pianoweave.ui.screens.pianoroll

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
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

/** The piano's white keys, A0 to C8: a window is a run of them. */
internal val PIANO_WHITES: List<Int> = (PIANO_LOWEST..PIANO_HIGHEST).filter { !isPitchBlack(it) }

/** The fewest white keys a window shows: two octaves and the next C. */
const val MIN_WINDOW_WHITES = 15

/** Where a window starts and how wide it is, in white keys (fractional while a gesture is under way). */
data class WhiteSpan(val low: Float, val count: Float) {
    fun toWindow(): KeyWindow {
        val n = count.coerceIn(MIN_WINDOW_WHITES.toFloat(), PIANO_WHITES.size.toFloat()).let { kotlin.math.round(it).toInt() }
        val lo = kotlin.math.round(low).toInt().coerceIn(0, PIANO_WHITES.size - n)
        return KeyWindow(PIANO_WHITES[lo], PIANO_WHITES[lo + n - 1])
    }
}

internal fun KeyWindow.toSpan(): WhiteSpan {
    val lo = PIANO_WHITES.indexOfFirst { it >= low }.coerceAtLeast(0)
    val hi = PIANO_WHITES.indexOfLast { it <= high }.coerceAtLeast(lo)
    return WhiteSpan(lo.toFloat(), (hi - lo + 1).toFloat())
}

/**
 * A two-finger gesture on the window: [zoom] above 1 shows fewer keys around [focusFraction] (where the fingers are,
 * 0 at the left edge), [panFraction] slides the keys (a fraction of the width, positive to the right).
 */
internal fun WhiteSpan.pinched(zoom: Float, panFraction: Float, focusFraction: Float): WhiteSpan {
    // [zoom]: how much farther apart the fingers are than when they touched (2 = twice: half the keys)
    val max = PIANO_WHITES.size.toFloat()
    val newCount = (count / zoom).coerceIn(MIN_WINDOW_WHITES.toFloat(), max)
    val focus = low + focusFraction * count
    val newLow = (focus - focusFraction * newCount - panFraction * newCount).coerceIn(0f, max - newCount)
    return WhiteSpan(newLow, newCount)
}

/** The same width, moved so it is centred on [pitch]. */
internal fun KeyWindow.centredOn(pitch: Int): KeyWindow {
    val span = toSpan()
    val centre = PIANO_WHITES.indexOfLast { it <= pitch }.coerceAtLeast(0)
    return WhiteSpan(centre - span.count / 2f + 0.5f, span.count).toWindow()
}

/**
 * Two fingers on the stage or the keys: pinch to show more or fewer keys, slide to move them. Measured from where
 * the fingers first touched (their spread then is the base, whatever it was), so bringing them back undoes the
 * change rather than adding to it. [onChange] gets the spread now over the spread then, how far their middle has
 * moved and where it started (fractions of the width). Seen before the area's own gestures and taken from them
 * once a second finger is down.
 */
internal fun Modifier.twoFingerKeys(
    onStart: () -> Unit,
    onChange: (spreadRatio: Float, panFraction: Float, focusFraction: Float) -> Unit,
    onEnd: () -> Unit
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var startSpread = 0f
        var startX = 0f
        var active = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val down = event.changes.filter { it.pressed }
            if (down.size >= 2) {
                val a = down[0].position
                val b = down[1].position
                val spread = (a - b).getDistance()
                val middle = (a.x + b.x) / 2f
                val width = size.width.toFloat().coerceAtLeast(1f)
                if (!active) {
                    active = true
                    startSpread = spread.coerceAtLeast(24.dp.toPx())
                    startX = middle
                    onStart()
                }
                onChange(spread.coerceAtLeast(1f) / startSpread, (middle - startX) / width, startX / width)
                event.changes.forEach { it.consume() }
            } else if (active) {
                // The last finger still down after a pinch stays the pinch's, not a scrub or a tap
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
        if (active) onEnd()
    }
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
 * notes outside it in the accent, and the range in words. Tapping or dragging moves the window there, its width
 * and the keyboard's mode kept.
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
                    .pointerInput(whites) {
                        detectHorizontalDragGestures { change, _ ->
                            val index = (change.position.x / size.width * whites.size).toInt().coerceIn(0, whites.lastIndex)
                            onPick(whites[index])
                        }
                    }
            ) {
                val w = size.width / whites.size
                val ivory = if (theme.isLight) Color(0xFFFFFBF0) else Color(0xFFE9E2D6)
                val ebony = if (theme.isLight) Color(0xFF3B2A19) else Color(0xFF0B0907)
                whites.forEachIndexed { i, pitch ->
                    drawRect(ivory, Offset(i * w + 0.5f, 0f), Size(w - 1f, size.height))
                    // The song's notes on this key, or the black key after it: a mark at the foot
                    if (pitch in songPitches || (pitch + 1) in songPitches) {
                        drawRect(theme.primaryColor, Offset(i * w + 0.5f, size.height * 0.72f), Size(w - 1f, size.height * 0.28f))
                    }
                    if (isPitchBlack(pitch + 1) && pitch + 1 <= PIANO_HIGHEST) {
                        drawRect(ebony, Offset((i + 1) * w - w * 0.32f, 0f), Size(w * 0.64f, size.height * 0.58f))
                    }
                }
                // The keys not shown, dimmed; the ones shown framed
                val x1 = whites.indexOfFirst { it >= window.low }.coerceAtLeast(0) * w
                val x2 = (whites.indexOfLast { it <= window.high } + 1) * w
                val veil = (if (theme.isLight) Color(0xFF6B5A45) else Color.Black).copy(alpha = 0.45f)
                if (x1 > 0f) drawRect(veil, Offset.Zero, Size(x1, size.height))
                if (x2 < size.width) drawRect(veil, Offset(x2, 0f), Size(size.width - x2, size.height))
                drawRoundRect(
                    theme.primaryColor,
                    Offset(x1 - 1.dp.toPx(), -2.dp.toPx()),
                    Size(x2 - x1 + 2.dp.toPx(), size.height + 4.dp.toPx()),
                    CornerRadius(3.dp.toPx()),
                    style = Stroke(2.dp.toPx())
                )
            }
        }
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Ink.copy(alpha = 0.7f), maxLines = 1)
    }
}
