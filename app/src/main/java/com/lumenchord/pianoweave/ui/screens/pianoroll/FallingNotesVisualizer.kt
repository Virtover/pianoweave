package com.lumenchord.pianoweave.ui.screens.pianoroll

import androidx.compose.ui.graphics.lerp
import com.lumenchord.pianoweave.ui.theme.litColor
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.runtime.remember
import android.graphics.Typeface
import android.graphics.Paint
import com.lumenchord.pianoweave.ui.components.drawStageDaylight
import androidx.compose.foundation.Canvas
import com.lumenchord.pianoweave.ui.theme.Ink
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.lumenchord.pianoweave.midi.Beat
import com.lumenchord.pianoweave.midi.MidiInputManager
import com.lumenchord.pianoweave.midi.MidiNoteEvent
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.theme.StageOrnament
import kotlin.math.PI
import kotlin.math.sin

/** The falling notes over the stage; [beats] (empty when the song has no beat grid) draw its bar and beat lines. */
@Composable
internal fun FallingNotesVisualizer(
    events: List<MidiNoteEvent>,
    beats: List<Beat> = emptyList(),
    head: Long,
    start: Int,
    /** The last key shown: notes beyond [start]..[end] get a marker at the stage's edge. */
    end: Int = 108,
    numWhiteKeys: Int,
    waitTargetPitches: Set<Int> = emptySet(),
    satisfiedPitches: Set<Int> = emptySet(),
    /** Names on the notes (see [NoteNameRules]); off draws none. */
    showNoteNames: Boolean = false,
    /** A tablet: larger names. */
    isLargeScreen: Boolean = false
) {
    val appTheme = LocalAppTheme.current
    val ColorGold = appTheme.litColor
    val ColorGoldLight = appTheme.lightColor
    val ColorUpcomingNote = appTheme.upcomingColor
    val ColorWaitTarget = appTheme.waitTargetColor
    val ColorWaitTargetLight = appTheme.waitTargetLightColor
    val ColorTarget = appTheme.targetColor
    val ColorBaseline = appTheme.baselineColor
    val ColorSuccess = appTheme.successColor
    val ColorSuccessLight = appTheme.successLightColor

    val scale = 0.25f
    val glowAlpha = (0.30f - 0.25f * ColorBaseline.luminance()).coerceIn(0.08f, 0.24f)
    // Drives the stage ornament; only runs for a theme that has one, so other stages don't redraw for it
    val ornament = appTheme.stageOrnament
    val ornamentTime = if (ornament != StageOrnament.None) {
        rememberInfiniteTransition(label = "stageOrnament").animateFloat(
            initialValue = 0f,
            targetValue = ORNAMENT_CYCLE,
            animationSpec = infiniteRepeatable(tween(durationMillis = (ORNAMENT_CYCLE * 1000).toInt(), easing = LinearEasing)),
            label = "stageOrnamentTime"
        )
    } else null
    // Bar numbers: each bar start's number in the song, counted once per beat grid
    val barNumbers = remember(beats) {
        var bar = 0
        beats.filter { it.isBarStart }.associate { it.timeMs to ++bar }
    }
    val inkArgb = Ink.toArgb()
    val labelPaint = remember { Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD } }
    val barPaint = remember { Paint().apply { isAntiAlias = true; textAlign = Paint.Align.LEFT; typeface = Typeface.DEFAULT_BOLD } }
    val tagColors = if (appTheme.isLight) Color(0xF2FFFAEC) to Color(0xFF4A300A) else Color(0xE0140E0A) to Color(0xFFFFE8BE)
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

        // By day, light from above: even across the stage, no sun or beams in it
        if (appTheme.isLight) drawStageDaylight()

        val t = ornamentTime?.value ?: 0f
        when (ornament) {
            StageOrnament.Stars -> drawStars(t)
            StageOrnament.None -> Unit
        }

        // 1. Grid lanes: an even line at every C (each octave), the other key edges only faintly
        val startWhite = getWhiteIndex(start)
        for (i in 0..numWhiteKeys) {
            val x = i * (tw / numWhiteKeys)
            val isOctave = (startWhite + i) % 7 == 0
            drawLine(
                color = appTheme.palette.stageLane.copy(alpha = if (isOctave) 0.9f else 0.22f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = if (isOctave) 1.dp.toPx() else 1f
            )
        }

        // 2. Bar Lines: the song's own bars, with fainter beats between them, when it has a beat grid;
        // otherwise an even line every two seconds, just to show the stage moving
        val lastMs = head + (size.height / scale).toLong()
        fun timeLine(ms: Long, alpha: Float, width: Float) {
            val y = size.height - ((ms - head) * scale)
            if (y in 0f..size.height) {
                drawLine(
                    color = Ink.copy(alpha = alpha),
                    start = Offset(0f, y),
                    end = Offset(tw, y),
                    strokeWidth = width
                )
            }
        }
        if (beats.isNotEmpty()) {
            var index = beats.binarySearchBy(head) { it.timeMs }.let { if (it < 0) -it - 1 else it }
            barPaint.textSize = 10.sp.toPx()
            barPaint.color = inkArgb
            barPaint.alpha = 115
            while (index < beats.size && beats[index].timeMs <= lastMs) {
                val beat = beats[index++]
                if (beat.isBarStart) {
                    timeLine(beat.timeMs, 0.1f, 1.dp.toPx())
                    // Its number at the left end, for finding your place and setting a loop
                    val y = size.height - ((beat.timeMs - head) * scale)
                    val number = barNumbers[beat.timeMs]
                    if (number != null && y in 14.dp.toPx()..size.height - 4.dp.toPx()) {
                        drawContext.canvas.nativeCanvas.drawText(number.toString(), 5.dp.toPx(), y - 3.dp.toPx(), barPaint)
                    }
                } else timeLine(beat.timeMs, 0.04f, 1f)
            }
        } else {
            val barIntervalMs = 2000L
            for (ms in (head / barIntervalMs) * barIntervalMs..lastMs step barIntervalMs) {
                timeLine(ms, 0.1f, 1.dp.toPx())
            }
        }

        // 3. Falling Notes
        val filtered = events.filter { (it.startMs + it.durationMs) >= head && it.startMs <= lastMs }
        val (blackEvents, whiteEvents) = filtered.partition { isPitchBlack(it.pitch) }

        val nameSize = (if (isLargeScreen) 12 else 10).sp.toPx()
        labelPaint.textSize = nameSize
        val labels = mutableListOf<NoteLabel>()

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
            // A press the previous chord already used doesn't count for a waiting note
            val isRecentOnset = (now - lastPress <= 350L) && (MidiInputManager.pressedKeys.contains(e.pitch) || now - lastPress <= 250L) &&
                    lastPress > (MidiInputManager.consumedPressTimestamps[e.pitch] ?: 0L)
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
                // By day the notes catch the light: their top in the theme's bright gold, not a faded copy
                appTheme.isLight -> lerp(ColorGoldLight, Color.White, 0.2f)
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
                    if (appTheme.isLight) listOf(Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.25f))
                    else listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.12f)),
                    startY = y,
                    endY = y + h
                ),
                topLeft = Offset(x1 + 1f, noteTop + 0.5f),
                size = Size(kw - 2f, h - 1f),
                cornerRadius = CornerRadius(corner),
                style = Stroke(width = 1.dp.toPx())
            )

            if (showNoteNames) {
                // Arriving: near the keys, or being played or waited for
                val arriving = isAtBaseline || isWaiting || y + h >= size.height * 0.7f
                labels += NoteLabel(e.pitch, x1, x2, noteTop, h, arriving, if (baseCol.luminance() > 0.4f) Color(0xFF2A1D10) else Color.White)
            }

            if (isHitting || isUserMatch || isWaiting) {
                val glowCol = if (isWaiting && !isSatisfied) ColorWaitTarget else baseCol
                drawRect(
                    brush = Brush.verticalGradient(listOf(glowCol.copy(alpha = 0.4f), Color.Transparent)),
                    topLeft = Offset(x1, y.coerceIn(-h, size.height) + h),
                    size = Size(kw, 35.dp.toPx())
                )
            }
        }

        // Hit line: a line of light with a soft glow rising from it, brightest in the middle.
        // Bright baseline colors (yellow, lime, cyan...) glow much harder at the same opacity, so
        // their glow is turned down to look about as strong as on darker colors.
        val baselineY = size.height - 1f
        val glowHeight = 28.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, ColorBaseline.copy(alpha = glowAlpha * 0.35f), ColorBaseline.copy(alpha = glowAlpha)),
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
        // Notes coming outside the keys shown: a marker at the edge they come from, at their height
        val outside = filtered.filter { it.pitch < start || it.pitch > end }
        if (outside.isNotEmpty()) drawEdgeMarkers(outside, start, head, scale, labelPaint, tagColors)
        if (showNoteNames) drawNoteNames(labels, labelPaint, nameSize, tagColors)
    }
}

/**
 * A marker at the stage's edge for each note coming outside the keys shown, the side it is on, at the height its
 * note will reach the keys from: "◀ G2", "A6 ▶". Markers that would overlap keep the nearest note.
 */
private fun DrawScope.drawEdgeMarkers(
    outside: List<MidiNoteEvent>,
    start: Int,
    head: Long,
    scale: Float,
    paint: Paint,
    colors: Pair<Color, Color>
) {
    val canvas = drawContext.canvas.nativeCanvas
    val markerH = 22.dp.toPx()
    val pad = 6.dp.toPx()
    val placedLeft = mutableListOf<Float>()
    val placedRight = mutableListOf<Float>()
    for (e in outside.sortedBy { it.startMs }) {
        val bottom = size.height - (e.startMs - head) * scale
        val y = bottom.coerceIn(markerH, size.height)
        val left = e.pitch < start
        val placed = if (left) placedLeft else placedRight
        if (placed.any { kotlin.math.abs(it - y) < markerH + 2.dp.toPx() }) continue
        placed += y
        val text = if (left) "◀ ${noteName(e.pitch)}" else "${noteName(e.pitch)} ▶"
        val width = paint.measureText(text) + 2 * pad
        val x = if (left) 0f else size.width - width
        val corner = markerH / 2f
        drawRoundRect(colors.first, Offset(x - if (left) corner else 0f, y - markerH), Size(width + corner, markerH), CornerRadius(corner))
        drawRoundRect(colors.second.copy(alpha = 0.5f), Offset(x - if (left) corner else 0f, y - markerH), Size(width + corner, markerH), CornerRadius(corner), style = Stroke(1f))
        paint.color = colors.second.toArgb()
        canvas.drawText(text, x + width / 2f, y - markerH / 2f + paint.textSize * 0.35f, paint)
    }
}

/** One note's name, waiting to be drawn over the notes. */
private class NoteLabel(val pitch: Int, val x1: Float, val x2: Float, val top: Float, val height: Float, val arriving: Boolean, val ink: Color)

/**
 * The names on the notes, by these rules (the design's "Note names on each screen"):
 *  - inside a white-key note tall enough (34 dp) and wide enough to hold it, at its bottom, so it is read as it
 *    arrives; where keys are narrow (all 88 on a phone, landscape), only on arriving notes, so the stage stays clear;
 *  - otherwise, for an arriving note at least 14 dp tall (black keys always), a small tag just above it;
 *  - shorter notes get no name (fast runs): it lights on the key as it is played;
 *  - tags that would touch keep the lowest and highest of a chord.
 */
private fun DrawScope.drawNoteNames(labels: List<NoteLabel>, paint: Paint, textSize: Float, tagColors: Pair<Color, Color>) {
    val canvas = drawContext.canvas.nativeCanvas
    val minInside = 34.dp.toPx()
    val minTagged = 14.dp.toPx()
    val pad = 4.dp.toPx()
    val tags = mutableListOf<Pair<NoteLabel, android.graphics.RectF>>()
    for (label in labels) {
        val name = noteName(label.pitch)
        val width = label.x2 - label.x1
        val textWidth = paint.measureText(name)
        val narrowKeys = width < 18.dp.toPx()
        val fitsInside = !isPitchBlack(label.pitch) && label.height >= minInside && width >= textWidth + pad
        if (fitsInside && (label.arriving || !narrowKeys)) {
            if (label.top + label.height > size.height + textSize) continue
            paint.color = label.ink.toArgb()
            canvas.drawText(name, (label.x1 + label.x2) / 2f, label.top + label.height - 5.dp.toPx(), paint)
        } else if (label.arriving && label.height >= minTagged) {
            val tagW = textWidth + 2 * pad
            val tagH = textSize + pad
            val cx = (label.x1 + label.x2) / 2f
            val bottom = (label.top - 3.dp.toPx()).coerceAtLeast(tagH + 2.dp.toPx())
            tags += label to android.graphics.RectF(cx - tagW / 2f, bottom - tagH, cx + tagW / 2f, bottom)
        }
    }
    // A chord's tags would touch: the lowest and the highest first, the rest only where there is room
    val ordered = tags.sortedBy { it.first.pitch }.let { list ->
        if (list.size <= 2) list else listOf(list.first(), list.last()) + list.subList(1, list.size - 1)
    }
    val placed = mutableListOf<android.graphics.RectF>()
    for ((label, rect) in ordered) {
        if (placed.any { android.graphics.RectF.intersects(it, rect) }) continue
        placed += rect
        val corner = 6.dp.toPx()
        drawRoundRect(tagColors.first, Offset(rect.left, rect.top), Size(rect.width(), rect.height()), CornerRadius(corner))
        drawRoundRect(tagColors.second.copy(alpha = 0.45f), Offset(rect.left, rect.top), Size(rect.width(), rect.height()), CornerRadius(corner), style = Stroke(1f))
        paint.color = tagColors.second.toArgb()
        canvas.drawText(noteName(label.pitch), rect.centerX(), rect.bottom - pad * 0.75f - 1f, paint)
    }
}

/** One star of a night-sky stage, placed by fractions of the stage's width and height. */
private class StageStar(
    val x: Float,
    val y: Float,
    val radiusDp: Float,
    val brightness: Float,
    /** Twinkle speed in radians per second of the ornament clock, and where in its twinkle the star starts. */
    val speed: Float,
    val phase: Float
)

/** Seconds of the ornament clock before it loops; long, so the loop is never noticed. */
private const val ORNAMENT_CYCLE = 60f

/** Whole cycles of [count] per loop of the clock, as radians per second, so everything loops seamlessly. */
private fun cyclesPerLoop(count: Int) = (2 * PI * count / ORNAMENT_CYCLE).toFloat()

/** How far down the stage the stars reach before they have faded out (fraction of its height). */
private const val STAR_SKY_DEPTH = 0.8f

/** A fixed scatter, the same every time, so the sky doesn't shift between songs or rotations. */
private val STAGE_STARS: List<StageStar> = kotlin.random.Random(147).let { random ->
    List(56) {
        // Most stars faint and small, a few brighter ones
        val bright = random.nextFloat() < 0.12f
        StageStar(
            x = random.nextFloat(),
            y = random.nextFloat() * STAR_SKY_DEPTH,
            radiusDp = if (bright) 1.3f + random.nextFloat() * 0.4f else 0.6f + random.nextFloat() * 0.5f,
            brightness = if (bright) 0.45f + random.nextFloat() * 0.2f else 0.12f + random.nextFloat() * 0.2f,
            // Whole cycles per loop of the clock, so every star is back where it started when it loops
            speed = cyclesPerLoop(6 + random.nextInt(18)),
            phase = random.nextFloat() * (2 * PI).toFloat()
        )
    }
}

/** Night sky: still stars, each breathing on its own slow rhythm, fading toward the horizon above the keys. */
private fun DrawScope.drawStars(t: Float) {
    STAGE_STARS.forEach { star ->
        val fade = 1f - (star.y / STAR_SKY_DEPTH).coerceIn(0f, 1f)
        val twinkle = 0.65f + 0.35f * sin(t * star.speed + star.phase)
        drawCircle(
            color = Color.White.copy(alpha = star.brightness * fade * twinkle),
            radius = star.radiusDp.dp.toPx(),
            center = Offset(star.x * size.width, star.y * size.height)
        )
    }
}
