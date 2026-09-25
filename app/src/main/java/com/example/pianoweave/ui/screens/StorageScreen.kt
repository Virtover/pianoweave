package com.example.pianoweave.ui.screens

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pianoweave.midi.StoredMidi
import com.example.pianoweave.ui.components.StoredSongCard

@Composable
fun StorageScreen(
    songs: List<StoredMidi>,
    onSongsChange: (List<StoredMidi>) -> Unit,
    context: Context,
    onSongSelect: (StoredMidi) -> Unit,
    onDeleteClick: (StoredMidi) -> Unit,
    onImportMidi: (Uri) -> Unit,
    importError: String? = null,
    onClearImportError: () -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    var searchQuery by remember { mutableStateOf("") }
    var songPendingDelete by remember { mutableStateOf<StoredMidi?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onImportMidi(it) }
    }

    // Invalid File Alert Dialog
    importError?.let { errorMsg ->
        AlertDialog(
            onDismissRequest = onClearImportError,
            title = { Text("Invalid File Format") },
            text = { Text(errorMsg) },
            confirmButton = {
                TextButton(onClick = onClearImportError) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.error,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // High-UX Confirmation Dialog overlay for large MIDI tracks (> 100 KB)
    songPendingDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { songPendingDelete = null },
            title = { Text("Delete MIDI Track?") },
            text = { 
                Text("You are about to remove '${song.metadata.title} - ${song.metadata.author}' (%.1f KB) from your local library cache. "
                    .format(song.file.length() / 1024.0)) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClick(song)
                        songPendingDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { songPendingDelete = null }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.primary,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val filteredSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) {
            songs
        } else {
            songs.filter {
                it.videoUrl.contains(searchQuery, ignoreCase = true)
                        || it.metadata.title.contains(searchQuery, ignoreCase = true)
                        || it.metadata.author.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Storage Header Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = "Local MIDI Library",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                Text(
                    text = if (songs.isEmpty()) "Your offline library is currently empty" else "${songs.size} file(s) cached locally",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .height(38.dp)
                    .then(if (isPortrait) Modifier.width(38.dp) else Modifier.wrapContentWidth())
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        RoundedCornerShape(10.dp)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable {
                        filePickerLauncher.launch(
                            arrayOf(
                                "audio/midi",
                                "audio/x-midi",
                                "application/x-midi",
                                "audio/mid",
                                "audio/sp-midi"
                            )
                        )
                    }
                    .padding(horizontal = if (isPortrait) 0.dp else 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.UploadFile,
                        contentDescription = "Add from device",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    if (!isPortrait) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Add from device",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // Enhanced Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search library...") },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.secondary,
                cursorColor = MaterialTheme.colorScheme.secondary
            )
        )

        // Conditional Layout for Song Entries
        if (filteredSongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (songs.isEmpty()) Icons.Default.LibraryMusic else Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = if (songs.isEmpty()) "No saved songs yet." else "No songs match your search query.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    filteredSongs,
                    key = { it.file.absolutePath }
                ) { song ->
                    StoredSongCard(
                        song = song,
                        onDelete = {
                            songPendingDelete = song
                        },
                        modifier = Modifier.clickable { onSongSelect(song) }
                    )
                }
            }
        }
    }
}
