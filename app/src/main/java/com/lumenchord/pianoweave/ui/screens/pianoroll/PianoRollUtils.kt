package com.lumenchord.pianoweave.ui.screens.pianoroll

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlinx.coroutines.CancellationException
import kotlin.math.abs

internal fun isPitchBlack(p: Int): Boolean {
    val n = p % 12
    return n == 1 || n == 3 || n == 6 || n == 8 || n == 10
}

internal fun formatTime(ms: Long): String {
    val s = (ms / 1000L).coerceAtLeast(0L)
    return "%02d:%02d".format(s / 60, s % 60)
}

internal fun midiPitchName(p: Int): String {
    val n = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    return n[p % 12] + (p / 12 - 1)
}

internal fun parseTimeToMs(input: String): Long? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null
    return try {
        if (trimmed.contains(":")) {
            val parts = trimmed.split(":")
            if (parts.size != 2) return null
            val mins = parts[0].toLongOrNull() ?: return null
            val secs = parts[1].toDoubleOrNull() ?: return null
            ((mins * 60.0 + secs) * 1000.0).toLong()
        } else {
            val secs = trimmed.toDoubleOrNull() ?: return null
            (secs * 1000.0).toLong()
        }
    } catch (e: Exception) {
        null
    }
}

internal fun getWhiteIndex(pitch: Int): Int {
    val octave = pitch / 12
    val semitone = pitch % 12
    val offset = when (semitone) {
        0 -> 0; 1 -> 0; 2 -> 1; 3 -> 1; 4 -> 2; 5 -> 3; 6 -> 3; 7 -> 4; 8 -> 4; 9 -> 5; 10 -> 5; 11 -> 6; else -> 0
    }
    return (octave * 7) + offset
}

internal fun getWhiteKeyXPx(whiteIndex: Int, numWhiteKeys: Int, totalWidth: Float): Float {
    val bw = totalWidth / numWhiteKeys
    return whiteIndex * bw
}

/**
 * Returns horizontal bounds for ANY pitch (black or white) in a fixed-width-white keyboard.
 */
internal fun getPitchXRange(pitch: Int, startPitch: Int, totalWidth: Float, numWhiteKeys: Int): Pair<Float, Float> {
    val startWhite = getWhiteIndex(startPitch)
    val wkW = totalWidth / numWhiteKeys

    return if (!isPitchBlack(pitch)) {
        val whiteIdx = getWhiteIndex(pitch) - startWhite
        (whiteIdx * wkW) to ((whiteIdx + 1) * wkW)
    } else {
        val leftWhiteIdx = getWhiteIndex(pitch - 1) - startWhite
        val center = (leftWhiteIdx + 1) * wkW
        val bkWidth = wkW * 0.65f
        (center - bkWidth / 2) to (center + bkWidth / 2)
    }
}

internal fun findPitchAt(pos: Offset, start: Int, end: Int, tw: Float, numWhiteKeys: Int, containerHeight: Float): Int {
    for (p in start..end) {
        if (isPitchBlack(p)) {
            val (x1, x2) = getPitchXRange(p, start, tw, numWhiteKeys)
            if (pos.x in x1..x2 && pos.y <= containerHeight * 0.7f) return p
        }
    }
    for (p in start..end) {
        if (!isPitchBlack(p)) {
            val (x1, x2) = getPitchXRange(p, start, tw, numWhiteKeys)
            if (pos.x in x1..x2) return p
        }
    }
    return -1
}

internal fun calculateSeekPosition(
    currentMs: Long,
    deltaMs: Long,
    songDurationMs: Long,
    isLoopingEnabled: Boolean,
    loopStartMs: Long,
    loopEndMs: Long
): Long {
    val rawTarget = currentMs + deltaMs
    val loopLen = loopEndMs - loopStartMs

    if (!isLoopingEnabled || loopLen <= 0) {
        return rawTarget.coerceIn(0L, songDurationMs)
    }

    return if (deltaMs < 0) {
        val wasBeforeLoop = currentMs < loopStartMs
        if (wasBeforeLoop) {
            rawTarget.coerceAtLeast(0L)
        } else {
            rawTarget.coerceAtLeast(loopStartMs)
        }
    } else {
        if (rawTarget >= loopEndMs) {
            val offsetPastStart = rawTarget - loopStartMs
            val remainder = offsetPastStart % loopLen
            loopStartMs + remainder
        } else {
            rawTarget
        }
    }
}

internal suspend fun PointerInputScope.detectTapAndDoubleTap(
    maxDistance: Float,
    onTap: (Offset) -> Unit,
    onDoubleTap: (Offset) -> Unit
) {
    val doubleTapTimeout = 300L
    var lastUpTime = 0L
    var lastUpPos = Offset.Zero

    while (true) {
        awaitPointerEventScope {
            val down = awaitFirstDown(requireUnconsumed = true)
            val downPos = down.position
            val pointer = down
            var upPos: Offset? = null
            var exceeded = false

            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointer.id } ?: break
                    if (!change.pressed) {
                        upPos = change.position
                        change.consume()
                        break
                    }
                    if ((change.position - downPos).getDistance() > maxDistance) {
                        exceeded = true
                        break
                    }
                }
            } catch (e: CancellationException) {
                throw e
            }

            if (!exceeded && upPos != null) {
                val distance = (upPos - downPos).getDistance()
                if (distance <= maxDistance) {
                    val now = System.currentTimeMillis()
                    if (now - lastUpTime <= doubleTapTimeout && (upPos - lastUpPos).getDistance() <= maxDistance * 2f) {
                        onDoubleTap(upPos)
                        lastUpTime = 0L
                    } else {
                        val secondDown = withTimeoutOrNull(doubleTapTimeout) {
                            awaitFirstDown(requireUnconsumed = true)
                        }
                        if (secondDown != null) {
                            val secondDownPos = secondDown.position
                            val secondPointer = secondDown
                            var secondUpPos: Offset? = null
                            var secondExceeded = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == secondPointer.id } ?: break
                                    if (!change.pressed) {
                                        secondUpPos = change.position
                                        change.consume()
                                        break
                                    }
                                    if ((change.position - secondDownPos).getDistance() > maxDistance) {
                                        secondExceeded = true
                                        break
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            }
                            if (!secondExceeded && secondUpPos != null && (secondUpPos - secondDownPos).getDistance() <= maxDistance) {
                                onDoubleTap(secondUpPos)
                                lastUpTime = 0L
                            } else {
                                onTap(upPos)
                                lastUpTime = now
                                lastUpPos = upPos
                            }
                        } else {
                            onTap(upPos)
                            lastUpTime = now
                            lastUpPos = upPos
                        }
                    }
                }
            }
        }
    }
}

internal suspend fun PointerInputScope.detectPianoRollGestures(
    maxDistance: Float,
    onTap: () -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onDragStart: () -> Unit,
    onVerticalDrag: (Float) -> Unit
) {
    val doubleTapTimeout = 300L
    var lastUpTime = 0L
    var lastUpPos = Offset.Zero

    while (true) {
        awaitPointerEventScope {
            val down = awaitFirstDown(requireUnconsumed = true)
            val downPos = down.position
            val pointer = down
            var upPos: Offset? = null
            var isDragging = false
            var lastY = downPos.y

            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointer.id } ?: break
                    if (!change.pressed) {
                        upPos = change.position
                        change.consume()
                        break
                    }
                    val currentPos = change.position
                    val offsetDelta = currentPos - downPos
                    if (offsetDelta.getDistance() > maxDistance) {
                        if (!isDragging) {
                            if (abs(offsetDelta.y) > abs(offsetDelta.x)) {
                                isDragging = true
                                onDragStart()
                            }
                        }
                        if (isDragging) {
                            val dy = currentPos.y - lastY
                            lastY = currentPos.y
                            onVerticalDrag(dy)
                            change.consume()
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            }

            if (!isDragging && upPos != null) {
                val distance = (upPos - downPos).getDistance()
                if (distance <= maxDistance) {
                    val now = System.currentTimeMillis()
                    if (now - lastUpTime <= doubleTapTimeout && (upPos - lastUpPos).getDistance() <= maxDistance * 2f) {
                        onDoubleTap(upPos)
                        lastUpTime = 0L
                    } else {
                        val secondDown = withTimeoutOrNull(doubleTapTimeout) {
                            awaitFirstDown(requireUnconsumed = true)
                        }
                        if (secondDown != null) {
                            val secondDownPos = secondDown.position
                            val secondPointer = secondDown
                            var secondUpPos: Offset? = null
                            var secondExceeded = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == secondPointer.id } ?: break
                                    if (!change.pressed) {
                                        secondUpPos = change.position
                                        change.consume()
                                        break
                                    }
                                    if ((change.position - secondDownPos).getDistance() > maxDistance) {
                                        secondExceeded = true
                                        break
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            }
                            if (!secondExceeded && secondUpPos != null && (secondUpPos - secondDownPos).getDistance() <= maxDistance) {
                                onDoubleTap(secondUpPos)
                                lastUpTime = 0L
                            } else {
                                onTap()
                                lastUpTime = now
                                lastUpPos = upPos
                            }
                        } else {
                            onTap()
                            lastUpTime = now
                            lastUpPos = upPos
                        }
                    }
                }
            }
        }
    }
}
