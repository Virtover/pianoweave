package com.lumenchord.pianoweave.ui.viewmodel

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ServerStatus {
    ONLINE,
    OFFLINE,
    CHECKING,
    UNKNOWN
}

val ServerStatus.statusColor: Color
    @Composable
    get() = when (this) {
        ServerStatus.ONLINE -> Color(0xFF4CAF50)
        ServerStatus.OFFLINE -> Color(0xFFEF5350)
        ServerStatus.CHECKING -> Color(0xFFFFA726)
        ServerStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }

val ServerStatus.displayText: String
    get() = when (this) {
        ServerStatus.ONLINE -> "Online"
        ServerStatus.OFFLINE -> "Offline"
        ServerStatus.CHECKING -> "Checking..."
        ServerStatus.UNKNOWN -> "Unknown"
    }
