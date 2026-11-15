package com.lumenchord.pianoweave.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * Width of the settings dialogs (the app's and the piano roll's). Not the platform's default dialog width:
 * that one is the phone maker's choice, generous on Samsung's One UI but much narrower on stock Android,
 * where it left the settings in a thin column with every subtitle wrapping. Instead, what One UI does
 * everywhere: the screen's width less a small margin on a phone held upright, about half the screen
 * sideways (and on tablets), within a comfortable reading width.
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

/** The settings dialogs set their own width (see [settingsDialogWidth]), so the platform's is off. */
val SettingsDialogProperties = DialogProperties(usePlatformDefaultWidth = false)
