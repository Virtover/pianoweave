package com.lumenchord.pianoweave.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * Whether the settings dialogs size themselves (see [settingsDialogWidth]): on phones only. Their default
 * dialog width is the phone maker's choice, generous on Samsung's One UI but much narrower on stock
 * Android, where it left the settings in a thin column with every subtitle wrapping. Tablets keep the
 * platform's dialog width, which suits them.
 */
@Composable
fun isPhoneForSettings(): Boolean = LocalConfiguration.current.smallestScreenWidthDp < 600

/**
 * Width of the settings dialogs on a phone: what One UI does, everywhere: the screen's width less a small
 * margin held upright, about half the screen sideways, within a comfortable reading width.
 */
@Composable
fun settingsDialogWidth(): Dp {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return if (widthDp < 600) {
        (widthDp - 24).dp
    } else {
        (widthDp * 0.5f).dp.coerceIn(380.dp, 560.dp)
    }
}

/** On a phone the settings dialogs set their own width, so the platform's is off; tablets keep it. */
@Composable
fun settingsDialogProperties(): DialogProperties =
    DialogProperties(usePlatformDefaultWidth = !isPhoneForSettings())
