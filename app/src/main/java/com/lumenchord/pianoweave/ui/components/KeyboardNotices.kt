package com.lumenchord.pianoweave.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.lumenchord.pianoweave.midi.BluetoothMidi

/**
 * Says so when the remembered Bluetooth keyboard connects by itself, or goes away, wherever the person is
 * (the main screens and the piano roll each have their own snackbar host; only one is on screen). Each
 * notice shows once.
 */
@Composable
fun KeyboardNotices(host: SnackbarHostState) {
    val notice = BluetoothMidi.notice
    LaunchedEffect(notice?.id) {
        val current = notice ?: return@LaunchedEffect
        if (!BluetoothMidi.claimNotice(current.id)) return@LaunchedEffect
        host.showSnackbar(current.text, duration = SnackbarDuration.Short)
    }
}
