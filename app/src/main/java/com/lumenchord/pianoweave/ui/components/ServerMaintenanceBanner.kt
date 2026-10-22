package com.lumenchord.pianoweave.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumenchord.pianoweave.api.MaintenanceNotice
import com.lumenchord.pianoweave.api.MaintenanceTexts
import com.lumenchord.pianoweave.ui.theme.Moods
import com.lumenchord.pianoweave.ui.viewmodel.formatClockTime
import kotlinx.coroutines.delay

/**
 * Announces the server's planned restart or shutdown. Counts down locally from the announced time,
 * so it stays exact between server polls. Drawn in [Moods.Watch]: the watchman's lamp, alert but
 * unafraid (Habakkuk 2:1, Matthew 25:4).
 */
@Composable
fun ServerMaintenanceBanner(notice: MaintenanceNotice, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val watch = Moods.Watch

    var nowSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(notice) {
        while (true) {
            nowSec = System.currentTimeMillis() / 1000
            delay(1000)
        }
    }
    val formatTime: (Long) -> String = { formatClockTime(context, it) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, watch.core.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (notice.isShutdown) Icons.Default.PowerSettingsNew else Icons.Default.RestartAlt,
                contentDescription = null,
                tint = watch.core,
                modifier = Modifier.size(24.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = MaintenanceTexts.title(notice, nowSec, formatTime),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = watch.light
                )
                Text(
                    text = MaintenanceTexts.detail(notice, nowSec, formatTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
