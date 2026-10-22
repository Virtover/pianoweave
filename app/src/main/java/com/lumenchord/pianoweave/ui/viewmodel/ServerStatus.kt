package com.lumenchord.pianoweave.ui.viewmodel

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.lumenchord.pianoweave.ui.theme.Moods

enum class ServerStatus {
    ONLINE,
    OFFLINE,
    CHECKING,
    UNKNOWN
}

val ServerStatus.statusColor: Color
    @Composable
    get() = when (this) {
        ServerStatus.ONLINE -> Moods.StillWaters.core
        ServerStatus.OFFLINE -> Moods.Lament.core
        ServerStatus.CHECKING -> Moods.Watch.core
        ServerStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }

val ServerStatus.displayText: String
    get() = when (this) {
        ServerStatus.ONLINE -> "Online"
        ServerStatus.OFFLINE -> "Offline"
        ServerStatus.CHECKING -> "Checking..."
        ServerStatus.UNKNOWN -> "Unknown"
    }
