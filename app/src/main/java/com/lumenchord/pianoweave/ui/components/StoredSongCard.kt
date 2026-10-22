package com.lumenchord.pianoweave.ui.components

import androidx.compose.foundation.BorderStroke
import com.lumenchord.pianoweave.ui.theme.PillShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.res.Configuration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.SubcomposeAsyncImage
import com.lumenchord.pianoweave.api.VideoMetadata
import com.lumenchord.pianoweave.midi.ExportState
import com.lumenchord.pianoweave.midi.StoredMidi
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme

@Composable
fun StoredSongCard(
    song: StoredMidi,
    exportState: ExportState,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    /** Null for songs whose details come from their source link. */
    onEditDetails: (() -> Unit)? = null
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val metadata = song.metadata
    val details = remember(metadata.author, metadata.duration) { songDetails(metadata) }

    val shape = RoundedCornerShape(16.dp)
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Box {
            ArtBackdrop(thumbnailUrl = metadata.thumbnail, alpha = 0.22f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SongThumbnail(
                    thumbnailUrl = metadata.thumbnail,
                    size = if (isLandscape) 40.dp else 48.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = metadata.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isLandscape) 14.sp else 15.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = details ?: "MIDI file",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                ExportStatusIcon(exportState)

                SongMenu(
                    songTitle = metadata.title,
                    exportState = exportState,
                    onExport = onExport,
                    onDelete = onDelete,
                    onEditDetails = onEditDetails
                )
            }
        }
    }
}

/** "Artist · 3:45", leaving out whatever is unknown; null if nothing is known. */
internal fun songDetails(metadata: VideoMetadata): String? {
    val author = metadata.author.takeIf { it.isNotBlank() && it != "Unknown" }
    val duration = metadata.duration.takeIf { it > 0f }?.let { formatDuration(it) }
    return listOfNotNull(author, duration).joinToString(" · ").ifEmpty { null }
}

private fun formatDuration(seconds: Float): String {
    val total = seconds.toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
internal fun SongThumbnail(thumbnailUrl: String, size: Dp) {
    val shape = RoundedCornerShape(8.dp)
    val fallback: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(size * 0.45f)
            )
        }
    }

    Box(modifier = Modifier.size(size).clip(shape)) {
        if (thumbnailUrl.isBlank()) {
            fallback()
        } else {
            SubcomposeAsyncImage(
                model = smallThumbnailUrl(thumbnailUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = { fallback() },
                error = { fallback() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Shows export progress next to the menu; nothing while idle. */
@Composable
private fun ExportStatusIcon(exportState: ExportState) {
    when (exportState) {
        ExportState.Idle -> Unit
        ExportState.Exporting -> CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp
        )
        ExportState.Done -> Icon(
            Icons.Default.CheckCircle,
            contentDescription = "Saved to Downloads",
            tint = LocalAppTheme.current.successColor,
            modifier = Modifier.size(18.dp)
        )
        ExportState.Failed -> Icon(
            Icons.Default.ErrorOutline,
            contentDescription = "Export failed",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SongMenu(
    songTitle: String,
    exportState: ExportState,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onEditDetails: (() -> Unit)?
) {
    var expanded by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "More options for $songTitle",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val accent = MaterialTheme.colorScheme.secondary
        val itemColors = MenuDefaults.itemColors(
            textColor = MaterialTheme.colorScheme.onSurface,
            leadingIconColor = accent
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 220.dp, max = 280.dp),
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer),
            shadowElevation = 12.dp
        ) {
            // Which song the actions apply to, since the menu covers part of the list
            Text(
                text = songTitle,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f))
            if (onEditDetails != null) {
                DropdownMenuItem(
                    text = { Text("Edit details", fontWeight = FontWeight.Medium) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    colors = itemColors,
                    onClick = {
                        expanded = false
                        onEditDetails()
                    }
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        if (exportState == ExportState.Failed) "Retry saving to Downloads" else "Save to Downloads",
                        fontWeight = FontWeight.Medium
                    )
                },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, modifier = Modifier.size(20.dp)) },
                colors = itemColors,
                enabled = exportState != ExportState.Exporting,
                onClick = {
                    expanded = false
                    showExportDialog = true
                }
            )
            // Set apart, so it isn't hit while reaching for the others
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
            )
            DropdownMenuItem(
                text = { Text("Delete", fontWeight = FontWeight.Medium) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp)) },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error
                ),
                onClick = {
                    expanded = false
                    onDelete()
                }
            )
        }
    }

    if (showExportDialog) {
        ExportConfirmationDialog(
            songTitle = songTitle,
            onConfirm = {
                showExportDialog = false
                onExport()
            },
            onDismiss = { showExportDialog = false }
        )
    }
}

@Composable
fun ExportConfirmationDialog(
    songTitle: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val appTheme = LocalAppTheme.current
    val colorSurface = MaterialTheme.colorScheme.surface
    val colorPrimary = appTheme.primaryColor
    val colorSlate = MaterialTheme.colorScheme.tertiaryContainer
    val colorTextDim = MaterialTheme.colorScheme.tertiary

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.98f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = colorSurface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, colorSlate)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                                contentDescription = null,
                                tint = colorPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Export MIDI File",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = colorPrimary
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = colorTextDim
                            )
                        }
                    }

                    HorizontalDivider(color = colorSlate.copy(alpha = 0.6f))

                    val message = if (!songTitle.isNullOrBlank()) {
                        "Do you want to export \"$songTitle\" as a Standard MIDI file (.mid) to your device's Downloads folder?"
                    } else {
                        "Do you want to export this track as a Standard MIDI file (.mid) to your device's Downloads folder?"
                    }

                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 46.dp),
                            shape = PillShape,
                            border = BorderStroke(1.dp, colorSlate),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Text(
                                text = "Cancel",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }

                        Button(
                            onClick = onConfirm,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 46.dp),
                            shape = PillShape,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorPrimary,
                                contentColor = if (appTheme.isLightAccent) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = "Export",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
