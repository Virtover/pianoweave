package com.lumenchord.pianoweave.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ripple
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/**
 * Like `clickable`, but holding the press for [holdMillis] calls [onHold] instead (with a haptic tick).
 * Releasing any time before that is a normal click.
 */
fun Modifier.clickableWithLongHold(
    holdMillis: Long,
    onHold: () -> Unit,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnHold by rememberUpdatedState(onHold)

    this
        .semantics {
            role = Role.Button
            onClick { currentOnClick(); true }
        }
        .indication(interactionSource, ripple())
        .pointerInput(holdMillis) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val press = PressInteraction.Press(down.position)
                interactionSource.tryEmit(press)

                var released = false
                var cancelled = false
                withTimeoutOrNull(holdMillis) {
                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        up.consume()
                        released = true
                    } else {
                        cancelled = true
                    }
                }

                when {
                    released -> {
                        interactionSource.tryEmit(PressInteraction.Release(press))
                        currentOnClick()
                    }
                    cancelled -> interactionSource.tryEmit(PressInteraction.Cancel(press))
                    else -> {
                        interactionSource.tryEmit(PressInteraction.Cancel(press))
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnHold()
                        // Swallow the rest of this press so letting go doesn't also click
                        waitForUpOrCancellation()?.consume()
                    }
                }
            }
        }
}
