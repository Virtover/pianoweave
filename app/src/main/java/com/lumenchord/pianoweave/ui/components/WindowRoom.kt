package com.lumenchord.pianoweave.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

/**
 * How much room the app's window has, so layouts follow its size rather than the orientation alone: a tablet in
 * portrait is wider than a phone in landscape, and a tablet in landscape is taller than a phone in portrait is wide.
 * The thresholds keep phones exactly as they were: portrait is narrow and tall, landscape wide and short.
 */
@Immutable
data class WindowRoom(val widthDp: Int, val heightDp: Int) {
    val isLandscape: Boolean get() = widthDp > heightDp

    /** Little height (a phone in landscape): tighter spacing so the main cards fit without scrolling. */
    val isShort: Boolean get() = heightDp < 480

    /** Room for full-length labels, side-by-side controls and a navigation rail. */
    val isWide: Boolean get() = widthDp >= 600

    /** Tablet-sized both ways (also an unfolded foldable): room for larger controls and two panes. */
    val isLarge: Boolean get() = minOf(widthDp, heightDp) >= 600
}

@Composable
fun rememberWindowRoom(): WindowRoom {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        WindowRoom(configuration.screenWidthDp, configuration.screenHeightDp)
    }
}
