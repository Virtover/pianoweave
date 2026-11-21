package com.lumenchord.pianoweave.ui.screens.pianoroll

import com.lumenchord.pianoweave.ui.theme.litColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.lerp
import android.graphics.Paint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.audio.PianoPlayer
import com.lumenchord.pianoweave.midi.MidiInputManager
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme

@Composable
internal fun PianoKeyboardRow(
    start: Int,
    end: Int,
    numWhiteKeys: Int,
    sustainedPitches: Set<Int>,
    waitTargetPitches: Set<Int> = emptySet(),
    satisfiedPitches: Set<Int> = emptySet(),
    isInteractive: Boolean = false,
    keyboardHeight: Dp = 100.dp,
    /** Names on the keys being played, where a key is wide enough to hold one. */
    showNoteNames: Boolean = false
) {
    val appTheme = LocalAppTheme.current
    val ColorGold = appTheme.litColor
    val ColorWaitTarget = appTheme.waitTargetColor
    val ColorSuccess = appTheme.successColor

    val pressed = MidiInputManager.pressedKeys.toSet()
    val isDay = appTheme.isLight
    // The bed the keys lie in, and the keys themselves: warm ivory and ebony, a parchment bed by day
    val keyBed = if (isDay) Color(0xFFD9C6A4) else Color(0xFF030507)
    val ivoryTop = if (isDay) Color(0xFFFFFDF6) else Color(0xFFF3EDE3)
    val ivoryBottom = if (isDay) Color(0xFFF0E3CA) else Color(0xFFD9D0C1)
    val ebonyTop = if (isDay) Color(0xFF3B2A19) else Color(0xFF1C1611)
    val ebonyBottom = if (isDay) Color(0xFF1E150C) else Color(0xFF050403)
    val ebonyTip = if (isDay) Color(0xFF5A4430) else Color(0xFF2E261E)
    val inkArgb = (if (isDay) Color(0xFF6B5A45) else Color(0xFF8A8076)).toArgb()

    // Breathing pulse for waiting keys
    val infiniteTransition = rememberInfiniteTransition(label = "waitPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(keyboardHeight)
            .background(keyBed)
            .then(
                if (isInteractive) {
                    Modifier.pointerInput(start, end, numWhiteKeys, isInteractive, keyboardHeight) {
                        val activePointers = mutableMapOf<PointerId, Int>()
                        val tw = size.width.toFloat()
                        val th = size.height.toFloat()
                        // Releases held keys if this handler stops mid-press (a layout change or leaving the
                        // screen restarts it); they would otherwise sound on and count as held in wait mode
                        try {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { change ->
                                        val pId = change.id
                                        when {
                                            change.changedToDown() -> {
                                                val p = findPitchAt(change.position, start, end, tw, numWhiteKeys, th)
                                                if (p != -1) {
                                                    activePointers[pId] = p
                                                    MidiInputManager.simulateNoteOn(p)
                                                    PianoPlayer.noteOn(p)
                                                }
                                                change.consume()
                                            }
                                            change.changedToUp() || !change.pressed -> {
                                                activePointers.remove(pId)?.let { oldP ->
                                                    MidiInputManager.simulateNoteOff(oldP)
                                                    PianoPlayer.noteOff(oldP)
                                                }
                                                change.consume()
                                            }
                                            change.positionChanged() -> {
                                                val newP = findPitchAt(change.position, start, end, tw, numWhiteKeys, th)
                                                val oldP = activePointers[pId]
                                                if (newP != oldP) {
                                                    if (oldP != null) {
                                                        MidiInputManager.simulateNoteOff(oldP)
                                                        PianoPlayer.noteOff(oldP)
                                                    }
                                                    if (newP != -1) {
                                                        activePointers[pId] = newP
                                                        MidiInputManager.simulateNoteOn(newP)
                                                        PianoPlayer.noteOn(newP)
                                                    } else {
                                                        activePointers.remove(pId)
                                                    }
                                                }
                                                change.consume()
                                            }
                                        }
                                    }
                                }
                            }
                        } finally {
                            activePointers.values.forEach { pitch ->
                                MidiInputManager.simulateNoteOff(pitch)
                                PianoPlayer.noteOff(pitch)
                            }
                            activePointers.clear()
                        }
                    }
                } else Modifier
            )
    ) {
        val tw = constraints.maxWidth.toFloat()
        val keyPath = remember { Path() }

        // At night the keyboard it always had (hairline gaps, cool keys); by day warm ivory and ebony
        if (!isDay) Canvas(modifier = Modifier.fillMaxSize()) {
            val wkW = tw / numWhiteKeys
            val whiteRadius = 6.dp.toPx()
            val blackRadius = 4.dp.toPx()

            // 1. Base White Keys
            for (i in 0 until numWhiteKeys) {
                val x1 = i * wkW
                keyPath.reset()
                keyPath.addRoundRect(
                    RoundRect(
                        rect = Rect(x1 + 0.5f, 0f, x1 + wkW - 0.5f, size.height),
                        bottomLeft = CornerRadius(whiteRadius, whiteRadius),
                        bottomRight = CornerRadius(whiteRadius, whiteRadius)
                    )
                )
                drawPath(path = keyPath, color = ColorKeyWhite)
                drawLine(Color.Black.copy(alpha = 0.15f), Offset(x1, 0f), Offset(x1, size.height), 1.2.dp.toPx())
            }

            // 2. Subtle Top Keyboard Shadow Gradient (drapes over idle keys)
            val shadowHeight = 24.dp.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.5f),
                        Color.Black.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = shadowHeight
                ),
                topLeft = Offset(0f, 0f),
                size = Size(tw, shadowHeight)
            )

            // 3. Active White Key Glow Auras & Highlights
            val now = System.currentTimeMillis()
            val playedWhite = mutableListOf<Int>()
            for (p in start..end) {
                if (isPitchBlack(p)) continue
                val (x1, x2) = getPitchXRange(p, start, tw, numWhiteKeys)
                val isPressed = pressed.contains(p)
                val isTarget = sustainedPitches.contains(p)
                val isWaiting = waitTargetPitches.contains(p)
                val isSatisfied = satisfiedPitches.contains(p)

                val lastPress = MidiInputManager.lastPressTimestamps[p] ?: 0L
                // A press the previous chord already used doesn't count here (wait mode wants it again)
                val isRecentOnset = (now - lastPress <= 350L) && (isPressed || now - lastPress <= 250L) &&
                        lastPress > (MidiInputManager.consumedPressTimestamps[p] ?: 0L)

                if (isPressed || isTarget || isWaiting || isSatisfied) {
                    val color = when {
                        isSatisfied -> ColorSuccess
                        isWaiting && isRecentOnset -> ColorSuccess
                        isPressed -> ColorGold
                        isWaiting -> ColorWaitTarget.copy(alpha = pulseAlpha)
                        else -> ColorGold.copy(alpha = 0.5f)
                    }

                    // Glow aura around white key
                    keyPath.reset()
                    keyPath.addRoundRect(
                        RoundRect(
                            rect = Rect(x1 - 1f, -1f, x2 + 1f, size.height + 1f),
                            bottomLeft = CornerRadius(whiteRadius + 1f, whiteRadius + 1f),
                            bottomRight = CornerRadius(whiteRadius + 1f, whiteRadius + 1f)
                        )
                    )
                    drawPath(path = keyPath, color = color.copy(alpha = 0.4f))

                    // Crisp white key highlight
                    keyPath.reset()
                    keyPath.addRoundRect(
                        RoundRect(
                            rect = Rect(x1 + 0.5f, 0f, x2 - 0.5f, size.height),
                            bottomLeft = CornerRadius(whiteRadius, whiteRadius),
                            bottomRight = CornerRadius(whiteRadius, whiteRadius)
                        )
                    )
                    drawPath(path = keyPath, color = color)
                    if (showNoteNames && x2 - x1 >= 14.dp.toPx()) playedWhite += p
                }
            }

            // 4. Black Keys
            for (p in start..end) {
                if (!isPitchBlack(p)) continue
                val (x1, x2) = getPitchXRange(p, start, tw, numWhiteKeys)
                val isPressed = pressed.contains(p)
                val isTarget = sustainedPitches.contains(p)
                val isWaiting = waitTargetPitches.contains(p)
                val isSatisfied = satisfiedPitches.contains(p)

                val lastPress = MidiInputManager.lastPressTimestamps[p] ?: 0L
                val isRecentOnset = (now - lastPress <= 350L) && (isPressed || now - lastPress <= 250L) &&
                        lastPress > (MidiInputManager.consumedPressTimestamps[p] ?: 0L)

                val highlightColor = when {
                    isSatisfied -> ColorSuccess
                    isWaiting && isRecentOnset -> ColorSuccess
                    isPressed -> ColorGold
                    isWaiting -> ColorWaitTarget.copy(alpha = pulseAlpha)
                    isTarget -> ColorGold.copy(alpha = 0.5f)
                    else -> null
                }

                val h = size.height * 0.7f
                keyPath.reset()
                keyPath.addRoundRect(
                    RoundRect(
                        rect = Rect(x1 + 0.5f, 0f, x2 - 0.5f, h),
                        bottomLeft = CornerRadius(blackRadius, blackRadius),
                        bottomRight = CornerRadius(blackRadius, blackRadius)
                    )
                )

                if (highlightColor != null) {
                    Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(x1 - 1.5f, -1.5f, x2 + 1.5f, h + 1.5f),
                                bottomLeft = CornerRadius(blackRadius + 1f, blackRadius + 1f),
                                bottomRight = CornerRadius(blackRadius + 1f, blackRadius + 1f)
                            )
                        )
                        drawPath(this, color = highlightColor.copy(alpha = 0.45f))
                    }
                    drawPath(path = keyPath, color = highlightColor)
                } else {
                    drawPath(path = keyPath, color = ColorKeyBlack)
                }
            }

            // 5. C Key Annotations
            val textPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#777777")
                textSize = 10.sp.toPx()
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            // Played keys carry their name above the C labels, in the ink of a lit key
            val namePaint = Paint().apply {
                color = android.graphics.Color.parseColor("#3A2410")
                textSize = 10.sp.toPx()
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            for (p in playedWhite) {
                val (x1, x2) = getPitchXRange(p, start, tw, numWhiteKeys)
                drawContext.canvas.nativeCanvas.drawText(noteName(p), (x1 + x2) / 2f, size.height - 24.dp.toPx(), namePaint)
            }
            for (p in start..end) {
                if (!isPitchBlack(p) && p % 12 == 0) {
                    val (x1, x2) = getPitchXRange(p, start, tw, numWhiteKeys)
                    val centerX = (x1 + x2) / 2f
                    val name = midiPitchName(p)
                    val y = size.height - 10.dp.toPx()
                    drawContext.canvas.nativeCanvas.drawText(name, centerX, y, textPaint)
                }
            }
        }
        else Canvas(modifier = Modifier.fillMaxSize()) {
            val wkW = tw / numWhiteKeys
            val whiteRadius = minOf(6.dp.toPx(), wkW * 0.3f)
            val blackRadius = minOf(4.dp.toPx(), wkW * 0.25f)
            val gap = if (wkW > 10.dp.toPx()) 1.dp.toPx() else 0.75f
            val now = System.currentTimeMillis()

            /** A key's state: what it is lit with (null when idle), and whether it is being played. */
            fun litColor(p: Int): Color? {
                val isPressed = pressed.contains(p)
                val isTarget = sustainedPitches.contains(p)
                val isWaiting = waitTargetPitches.contains(p)
                val isSatisfied = satisfiedPitches.contains(p)
                val lastPress = MidiInputManager.lastPressTimestamps[p] ?: 0L
                // A press the previous chord already used doesn't count here (wait mode wants it again)
                val isRecentOnset = (now - lastPress <= 350L) && (isPressed || now - lastPress <= 250L) &&
                    lastPress > (MidiInputManager.consumedPressTimestamps[p] ?: 0L)
                return when {
                    isSatisfied -> ColorSuccess
                    isWaiting && isRecentOnset -> ColorSuccess
                    isPressed -> ColorGold
                    isWaiting -> ColorWaitTarget.copy(alpha = pulseAlpha)
                    // The song's own notes softer than the ones you play
                    isTarget -> ColorGold.copy(alpha = 0.55f)
                    else -> null
                }
            }

            fun keyShape(x1: Float, x2: Float, bottom: Float, radius: Float) {
                keyPath.reset()
                keyPath.addRoundRect(
                    RoundRect(
                        rect = Rect(x1, 0f, x2, bottom),
                        bottomLeft = CornerRadius(radius, radius),
                        bottomRight = CornerRadius(radius, radius)
                    )
                )
            }

            // 1. White keys: ivory lit softly from above, a fine gap between them; a played key glows in its note's
            // colour, brightest at the top where the note lands
            val litWhite = mutableListOf<Int>()
            for (i in 0 until numWhiteKeys) {
                val x1 = i * wkW
                keyShape(x1 + gap / 2f, x1 + wkW - gap / 2f, size.height, whiteRadius)
                drawPath(keyPath, Brush.verticalGradient(listOf(ivoryTop, ivoryBottom)))
            }
            for (p in start..end) {
                if (isPitchBlack(p)) continue
                val lit = litColor(p) ?: continue
                val (x1, x2) = getPitchXRange(p, start, tw, numWhiteKeys)
                keyShape(x1 + gap / 2f, x2 - gap / 2f, size.height, whiteRadius)
                drawPath(keyPath, Brush.verticalGradient(listOf(lerp(lit, Color.White, 0.45f), lit, lit.copy(alpha = lit.alpha * 0.85f))))
                litWhite += p
            }

            // 2. A soft shade where the keys meet the stage: warm and faint by day, deeper at night
            val shadeHeight = 14.dp.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = if (isDay) listOf(Color(0xFF6B4A1E).copy(alpha = 0.18f), Color.Transparent)
                    else listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
                    startY = 0f,
                    endY = shadeHeight
                ),
                size = Size(tw, shadeHeight)
            )

            // 3. Black keys: ebony with a sheen at the tip; a played one lit like the white keys
            val blackHeight = size.height * 0.62f
            for (p in start..end) {
                if (!isPitchBlack(p)) continue
                val (x1, x2) = getPitchXRange(p, start, tw, numWhiteKeys)
                val lit = litColor(p)
                keyShape(x1, x2, blackHeight, blackRadius)
                if (lit != null) {
                    // Its light spilling onto the ivory around it
                    drawRect(
                        Brush.radialGradient(listOf(lit.copy(alpha = 0.35f), Color.Transparent), center = Offset((x1 + x2) / 2f, blackHeight * 0.5f), radius = (x2 - x1) * 1.4f),
                        topLeft = Offset(x1 - (x2 - x1), 0f), size = Size((x2 - x1) * 3f, blackHeight * 1.2f)
                    )
                    drawPath(keyPath, Brush.verticalGradient(listOf(lerp(lit, Color.White, 0.3f), lit), endY = blackHeight))
                } else {
                    drawPath(keyPath, Brush.verticalGradient(listOf(ebonyTop, ebonyBottom, ebonyTip), endY = blackHeight))
                }
            }

            // 4. Names: on played white keys where they fit, and every C labelled for orientation
            val canvas = drawContext.canvas.nativeCanvas
            val namePaint = Paint().apply {
                textSize = 10.sp.toPx()
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            if (showNoteNames && wkW >= 14.dp.toPx()) {
                namePaint.color = android.graphics.Color.parseColor("#2A1D10")
                for (p in litWhite) {
                    val (x1, x2) = getPitchXRange(p, start, tw, numWhiteKeys)
                    canvas.drawText(noteName(p), (x1 + x2) / 2f, size.height - 22.dp.toPx(), namePaint)
                }
            }
            val cPaint = Paint().apply {
                color = inkArgb
                textSize = minOf(10.sp.toPx(), wkW * 0.9f).coerceAtLeast(7.sp.toPx())
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            // Only where a key is wide enough to hold "C4"
            if (wkW >= 9.dp.toPx()) for (p in start..end) {
                if (!isPitchBlack(p) && p % 12 == 0) {
                    val (x1, x2) = getPitchXRange(p, start, tw, numWhiteKeys)
                    canvas.drawText(midiPitchName(p), (x1 + x2) / 2f, size.height - 8.dp.toPx(), cPaint)
                }
            }
        }
    }
}
