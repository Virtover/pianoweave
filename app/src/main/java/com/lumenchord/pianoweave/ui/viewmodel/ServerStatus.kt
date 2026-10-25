package com.lumenchord.pianoweave.ui.viewmodel

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.lumenchord.pianoweave.ui.theme.Moods

enum class ServerStatus {
    ONLINE,
    OFFLINE,
    /** Wi-Fi and mobile data are off, so no server can be reached. */
    NO_INTERNET,
    CHECKING,
    UNKNOWN,
    /** Reachable, but it has a password and the app has none or the wrong one. */
    PASSWORD_REQUIRED
}

val ServerStatus.statusColor: Color
    @Composable
    get() = when (this) {
        ServerStatus.ONLINE -> Moods.StillWaters.core
        ServerStatus.OFFLINE -> Moods.Lament.core
        ServerStatus.NO_INTERNET -> Moods.Lament.core
        ServerStatus.CHECKING -> Moods.Watch.core
        ServerStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
        ServerStatus.PASSWORD_REQUIRED -> Moods.Watch.core
    }

val ServerStatus.displayText: String
    get() = when (this) {
        ServerStatus.ONLINE -> "Online"
        ServerStatus.OFFLINE -> "Offline"
        ServerStatus.NO_INTERNET -> "No internet"
        ServerStatus.CHECKING -> "Checking..."
        ServerStatus.UNKNOWN -> "Unknown"
        ServerStatus.PASSWORD_REQUIRED -> "Password needed"
    }
