package com.lumenchord.pianoweave.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumenchord.pianoweave.api.VideoMetadata
import com.lumenchord.pianoweave.cloud.CloudMidi
import com.lumenchord.pianoweave.midi.StoredMidi
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import com.lumenchord.pianoweave.midi.ExportState

@Composable
fun StoredSongCard(
    song: StoredMidi,
    exportState: ExportState,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    SongCard(
        videoUrl = song.videoUrl,
        videoMetadata = song.metadata,
        sizeBytes = song.file.length(),
        exportState = exportState,
        onExport = onExport,
        onDelete = onDelete,
        modifier = modifier
    )
}

@Composable
fun CloudSongCard(
    cloudMidi: CloudMidi,
    exportState: ExportState,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    SongCard(
        videoUrl = cloudMidi.videoUrl,
        videoMetadata = cloudMidi.metadata,
        sizeBytes = cloudMidi.sizeBytes,
        exportState = exportState,
        onExport = onExport,
        onDelete = onDelete,
        modifier = modifier
    )
}

@Composable
private fun SongCard(
    videoUrl: String,
    videoMetadata: VideoMetadata,
    sizeBytes: Long,
    exportState: ExportState,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = videoMetadata.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "${videoMetadata.author} • ${videoUrl}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Text(
                        text = "%.1f KB".format(
                            sizeBytes / 1024.0
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Standard MIDI",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete from Library",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }

                when (exportState) {
                    ExportState.Exporting -> Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }

                    ExportState.Done -> Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Saved to Downloads",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    ExportState.Failed -> IconButton(
                        onClick = onExport,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = "Export failed, tap to retry",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }

                    ExportState.Idle -> ExportButton(onClick = onExport)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportButton(onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text("Save .mid file to Downloads") } },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.Download,
                contentDescription = "Save .mid file to Downloads",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}