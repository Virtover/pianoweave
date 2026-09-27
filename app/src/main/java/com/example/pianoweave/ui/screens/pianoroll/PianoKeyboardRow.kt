package com.example.pianoweave.ui.screens.pianoroll

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pianoweave.audio.PianoPlayer
import com.example.pianoweave.midi.MidiInputManager
import com.example.pianoweave.ui.theme.LocalAppTheme

@Composable
internal fun PianoKeyboardRow(
    start: Int,
    end: Int,
    numWhiteKeys: Int,
    sustainedPitches: Set<Int>,
    waitTargetPitches: Set<Int> = emptySet(),
    satisfiedPitches: Set<Int> = emptySet(),
    isInteractive: Boolean = false
) {
    val appTheme = LocalAppTheme.current
    val ColorGold = appTheme.primaryColor
    val ColorWaitTarget = appTheme.waitTargetColor
    val ColorSuccess = appTheme.successColor

    val pressed = MidiInputManager.pressedKeys.toSet()

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
            .height(100.dp)
            .background(Color(0xFF030507))
            .then(
                if (isInteractive) {
                    Modifier.pointerInput(start, end, numWhiteKeys, isInteractive) {
                        val activePointers = mutableMapOf<PointerId, Int>()
                        val tw = size.width.toFloat()
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { change ->
                                    val pId = change.id
                                    when {
                                        change.changedToDown() -> {
                                            val p = findPitchAt(change.position, start, end, tw, numWhiteKeys)
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
                                            val newP = findPitchAt(change.position, start, end, tw, numWhiteKeys)
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
                    }
                } else Modifier
            )
    ) {
        val tw = constraints.maxWidth.toFloat()
        val keyPath = remember { Path() }

        Canvas(modifier = Modifier.fillMaxSize()) {
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
            for (p in start..end) {
                if (isPitchBlack(p)) continue
                val (x1, x2) = getPitchXRange(p, start, tw, numWhiteKeys)
                val isPressed = pressed.contains(p)
                val isTarget = sustainedPitches.contains(p)
                val isWaiting = waitTargetPitches.contains(p)
                val isSatisfied = satisfiedPitches.contains(p)

                val lastPress = MidiInputManager.lastPressTimestamps[p] ?: 0L
                val isRecentOnset = (now - lastPress <= 350L) && (isPressed || now - lastPress <= 250L)

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
                val isRecentOnset = (now - lastPress <= 350L) && (isPressed || now - lastPress <= 250L)

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
    }
}
