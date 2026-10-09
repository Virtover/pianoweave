package com.lumenchord.pianoweave.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.lumenchord.pianoweave.midi.ExportState
import com.lumenchord.pianoweave.midi.StoredMidi
import com.lumenchord.pianoweave.ui.components.CloudSyncDialogs
import com.lumenchord.pianoweave.ui.components.DangerButton
import com.lumenchord.pianoweave.ui.components.StoredSongCard
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel

@Composable
fun StorageScreen(
    viewModel: PianoWeaveViewModel,
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
    val appTheme = LocalAppTheme.current

    val colorSurface = MaterialTheme.colorScheme.surface
    val colorGold = appTheme.primaryColor
    val colorSlate = MaterialTheme.colorScheme.tertiaryContainer
    val colorTextDim = MaterialTheme.colorScheme.tertiary

    var searchQuery by remember { mutableStateOf("") }
    var songPendingDelete by remember { mutableStateOf<StoredMidi?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val askNotificationPermissionIfNeeded = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onImportMidi(it) }
    }

    val listState = rememberLazyListState()
    val previousSongsSize = remember { mutableIntStateOf(songs.size) }

    LaunchedEffect(songs.size) {
        if (songs.size > previousSongsSize.intValue) {
            listState.scrollToItem(0)
        }
        previousSongsSize.intValue = songs.size
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

    // Delete confirmation dialog
    songPendingDelete?.let { song ->
        Dialog(onDismissRequest = { songPendingDelete = null }) {
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
                    border = BorderStroke(1.dp, colorSlate)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = colorGold,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "Delete Track?",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colorGold
                                )
                            }
                            IconButton(
                                onClick = { songPendingDelete = null },
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

                        Text(
                            text = "Are you sure you want to delete '${song.metadata.title}'? The track will be removed from your library.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp
                        )

                        Spacer(Modifier.height(4.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { songPendingDelete = null },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .heightIn(min = 46.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, colorSlate),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Cancel",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            DangerButton(
                                onClick = {
                                    onDeleteClick(song)
                                    songPendingDelete = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .heightIn(min = 46.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Delete",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Cloud Sync Dialogs (confirmation, progress, error)
    CloudSyncDialogs(viewModel = viewModel, context = context)

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

    @Composable
    fun AddFromDeviceButton() {
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
                            "audio/midi", "audio/x-midi", "application/x-midi",
                            "audio/mid", "audio/sp-midi"
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

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- Header Section ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MIDI Library",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    val (statusIcon, statusTint, statusText) = when {
                        !viewModel.isCloudSyncEnabled -> Triple(
                            Icons.Default.CloudOff,
                            MaterialTheme.colorScheme.secondary,
                            "Stored on device"
                        )
                        viewModel.hasCloudSyncError -> Triple(
                            Icons.Default.ErrorOutline,
                            MaterialTheme.colorScheme.error,
                            "Sync error"
                        )
                        else -> Triple(
                            Icons.Default.Cloud,
                            MaterialTheme.colorScheme.secondary,
                            "Online"
                        )
                    }

                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusTint,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = if (viewModel.isCloudSyncEnabled && viewModel.hasCloudSyncError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (viewModel.isCloudSyncEnabled && viewModel.hasCloudSyncError) {
                            Modifier.clickable { viewModel.showCloudSyncErrorDialog = true }
                        } else {
                            Modifier
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (viewModel.isCloudSyncEnabled) {
                    IconButton(
                        onClick = { viewModel.refreshCloudSync(context) },
                        enabled = !viewModel.isRefreshingCloud,
                        modifier = Modifier.size(34.dp)
                    ) {
                        if (viewModel.isRefreshingCloud) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Sync Cloud Files",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                AddFromDeviceButton()
            }
        }

        // --- Info & Quick Action Bar (Exact position & style of former "Move to cloud") ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${songs.size} track(s) in library",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            TextButton(
                onClick = {
                    if (viewModel.isCloudSyncEnabled) {
                        viewModel.requestTurnOffCloudSync(context)
                    } else {
                        viewModel.requestTurnOnCloudSync(context)
                    }
                },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                modifier = Modifier.height(26.dp)
            ) {
                Text(
                    text = if (viewModel.isCloudSyncEnabled) "Turn off cloud sync" else "Turn on cloud sync",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorGold.copy(alpha = 0.85f)
                )
            }
        }

        // Search Field
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

        // Songs List
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
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    filteredSongs,
                    key = { it.file.absolutePath }
                ) { song ->
                    StoredSongCard(
                        song = song,
                        exportState = viewModel.exportStates[song.file.absolutePath] ?: ExportState.Idle,
                        onExport = {
                            askNotificationPermissionIfNeeded()
                            viewModel.exportLocalSong(context, song)
                        },
                        onDelete = { songPendingDelete = song },
                        modifier = Modifier.clickable { onSongSelect(song) }
                    )
                }
            }
        }
    }
}
