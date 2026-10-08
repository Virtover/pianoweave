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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.lumenchord.pianoweave.cloud.CloudMidi
import com.lumenchord.pianoweave.midi.ExportState
import com.lumenchord.pianoweave.midi.StoredMidi
import com.lumenchord.pianoweave.ui.components.CloudSongCard
import com.lumenchord.pianoweave.ui.components.DangerButton
import com.lumenchord.pianoweave.ui.components.StoredSongCard
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel

private enum class StorageScreenView {
    SELECTION,
    LOCAL_LIBRARY,
    CLOUD_LIBRARY
}

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
    var cloudSongPendingDelete by remember { mutableStateOf<CloudMidi?>(null) }

    // Screen navigation state - persisted across screen rotations via ViewModel
    var selectedView by remember(viewModel.selectedStorageViewName) {
        mutableStateOf(
            viewModel.selectedStorageViewName?.let {
                try { StorageScreenView.valueOf(it) } catch (_: Exception) { null }
            } ?: StorageScreenView.SELECTION
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ignore: the export is already running, the Toast fallback covers a denial */ }

    val askNotificationPermissionIfNeeded = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun setView(view: StorageScreenView) {
        selectedView = view
        viewModel.selectedStorageViewName = view.name
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onImportMidi(it) }
    }

    // Load cloud songs when screen mounts or sign in changes
    LaunchedEffect(viewModel.isGoogleSignedIn, viewModel.googleUserEmail) {
        if (viewModel.isGoogleSignedIn) {
            viewModel.loadCloudSongs(context)
        }
    }

    // Automatic Routing Logic:
    // 1. If NOT signed in -> show Local Library directly.
    // 2. If signed in & local songs empty -> show Google Account Library directly.
    // 3. If signed in & local songs exist -> user can browse between screens via SELECTION view.
    val activeView = when {
        !viewModel.isGoogleSignedIn -> StorageScreenView.LOCAL_LIBRARY
        songs.isEmpty() -> StorageScreenView.CLOUD_LIBRARY
        else -> selectedView
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

    // Delete confirmation dialog for local songs - Styled to match App Dialogs
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
                    contentColor = Color.White,
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
                                    text = "Delete Local Track?",
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
                            text = "You are about to remove '${song.metadata.title} - ${song.metadata.author}' (%.1f KB) from your local device library."
                                .format(song.file.length() / 1024.0),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = 22.sp
                        )

                        Spacer(Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { songPendingDelete = null },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorGold,
                                    contentColor = if (appTheme.isLightAccent) Color.Black else Color.White
                                )
                            ) {
                                Text(
                                    text = "Cancel",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            DangerButton(
                                onClick = {
                                    onDeleteClick(song)
                                    songPendingDelete = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
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

    // Delete confirmation dialog for cloud songs - Styled to match App Dialogs
    cloudSongPendingDelete?.let { cloudSong ->
        Dialog(onDismissRequest = { cloudSongPendingDelete = null }) {
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
                    contentColor = Color.White,
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
                                    text = "Delete Cloud Track?",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colorGold
                                )
                            }
                            IconButton(
                                onClick = { cloudSongPendingDelete = null },
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
                            text = "You are about to remove '${cloudSong.metadata.title}' from your Google Account storage.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = 22.sp
                        )

                        Spacer(Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { cloudSongPendingDelete = null },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorGold,
                                    contentColor = if (appTheme.isLightAccent) Color.Black else Color.White
                                )
                            ) {
                                Text(
                                    text = "Cancel",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            DangerButton(
                                onClick = {
                                    viewModel.deleteCloudSong(context, cloudSong)
                                    cloudSongPendingDelete = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
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

    // Upload Prompt Dialog - Styled to match App Dialogs (CancelTranscriptionDialog, NotEnoughCreditsDialog)
    if (viewModel.showUploadDialog) {
        Dialog(onDismissRequest = { viewModel.showUploadDialog = false }) {
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
                    contentColor = Color.White,
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
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    tint = colorGold,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "Cloud Backup",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colorGold
                                )
                            }
                            IconButton(
                                onClick = { viewModel.showUploadDialog = false },
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
                            text = "You have ${songs.size} MIDI file(s) stored on this device.\n\nWhat would you like to do with them?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
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
                                onClick = { viewModel.showUploadDialog = false },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .heightIn(min = 46.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, colorSlate),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text(
                                    text = "Keep on device",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    textAlign = TextAlign.Center,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Button(
                                onClick = { viewModel.uploadLocalSongsToCloud(context) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .heightIn(min = 46.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorGold,
                                    contentColor = if (appTheme.isLightAccent) Color.Black else Color.White
                                )
                            ) {
                                Text(
                                    text = "Add to Cloud",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    textAlign = TextAlign.Center,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Upload Progress Overlay
    if (viewModel.isUploadingToCloud) {
        Dialog(
            onDismissRequest = { },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
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
                    contentColor = Color.White,
                    border = BorderStroke(1.dp, colorSlate)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = colorGold,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "Uploading to Cloud",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = colorGold
                            )
                        }

                        HorizontalDivider(color = colorSlate.copy(alpha = 0.6f))

                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = colorGold,
                            trackColor = colorSlate
                        )

                        Text(
                            text = viewModel.uploadProgressText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = 22.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    // Upload Error Dialog
    if (viewModel.uploadErrorOccured) {
        val dismissUploadError = {
            viewModel.uploadErrorOccured = false
            viewModel.cloudError = null
        }

        Dialog(onDismissRequest = dismissUploadError) {
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
                    contentColor = Color.White,
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
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "Upload Failed",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            IconButton(
                                onClick = dismissUploadError,
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

                        // Scrollable: several failed songs make the message long
                        Text(
                            text = viewModel.cloudError ?: "Unknown error.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = 22.sp,
                            modifier = Modifier
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                        )

                        Spacer(Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = dismissUploadError,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, colorSlate),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text(
                                    text = "Close",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            // Failed songs stay local, so retrying uploads only those
                            if (songs.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        dismissUploadError()
                                        viewModel.uploadLocalSongsToCloud(context)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colorGold,
                                        contentColor = if (appTheme.isLightAccent) Color.Black else Color.White
                                    )
                                ) {
                                    Text(
                                        text = "Retry",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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

    val filteredCloudSongs = remember(viewModel.cloudSongs, searchQuery) {
        if (searchQuery.isBlank()) {
            viewModel.cloudSongs
        } else {
            viewModel.cloudSongs.filter {
                it.videoUrl.contains(searchQuery, ignoreCase = true)
                        || it.metadata.title.contains(searchQuery, ignoreCase = true)
                        || it.metadata.author.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Reusable "Add from device" Button (Restored previous rounded-square design in portrait)
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
        when (activeView) {
            StorageScreenView.SELECTION -> {
                // --- Separate Screen: Library Selection (NO import button here) ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MIDI Storage",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                        Text(
                            text = "Choose a library to browse",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Card 1: Local Library (Offline)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { setView(StorageScreenView.LOCAL_LIBRARY) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.LibraryMusic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Local Library",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${songs.size} track(s) stored on this device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Card 2: Google Account Library
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.loadCloudSongs(context)
                            setView(StorageScreenView.CLOUD_LIBRARY)
                        },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Cloud,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cloud Library",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${viewModel.cloudSongs.size} track(s) • ${viewModel.googleUserEmail}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            StorageScreenView.LOCAL_LIBRARY -> {
                // --- Screen: Local Library ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        if (viewModel.isGoogleSignedIn && songs.isNotEmpty()) {
                            IconButton(
                                onClick = { setView(StorageScreenView.SELECTION) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Libraries",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Local Library",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                            Text(
                                text = if (songs.isEmpty()) "Offline library is empty" else "${songs.size} track(s) on device",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    AddFromDeviceButton()
                }

                // Sleek, Subtle Offline Library Info Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isPortrait) "Stored on device" else "Offline files aren't synced to cloud.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    if (!viewModel.isGoogleSignedIn) {
                        TextButton(
                            onClick = { viewModel.signInWithGoogle(context) },
                            enabled = !viewModel.isGoogleAuthLoading,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            if (viewModel.isGoogleAuthLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = colorGold
                                )
                            } else {
                                Text(
                                    text = "Sign In",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = colorGold.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        TextButton(
                            onClick = { viewModel.showUploadDialog = true },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text(
                                text = "Move to cloud library",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorGold.copy(alpha = 0.7f)
                            )
                        }
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

            StorageScreenView.CLOUD_LIBRARY -> {
                // --- Screen: Google Account Library ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        if (viewModel.isGoogleSignedIn && songs.isNotEmpty()) {
                            IconButton(
                                onClick = { setView(StorageScreenView.SELECTION) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Libraries",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Cloud Library",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                            Text(
                                text = "${viewModel.cloudSongs.size} track(s) • ${viewModel.googleUserEmail}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.loadCloudSongs(context) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Sync Cloud Files",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        AddFromDeviceButton()
                    }
                }

                // Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search cloud tracks...") },
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

                if (viewModel.isCloudLoading && viewModel.cloudSongs.isEmpty()) {
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
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Syncing Google Account appDataFolder...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (filteredCloudSongs.isEmpty()) {
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
                                imageVector = Icons.Default.Cloud,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = if (viewModel.cloudSongs.isEmpty()) "No MIDI tracks stored in Google Account." else "No cloud tracks match your search.",
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
                            filteredCloudSongs,
                            key = { it.id }
                        ) { cloudSong ->
                            CloudSongCard(
                                cloudMidi = cloudSong,
                                exportState = viewModel.exportStates[cloudSong.id] ?: ExportState.Idle,
                                onExport = {
                                    askNotificationPermissionIfNeeded()
                                    viewModel.exportCloudSong(context, cloudSong)
                                },
                                onDelete = { cloudSongPendingDelete = cloudSong },
                                modifier = Modifier.clickable {
                                    viewModel.playCloudSong(context, cloudSong)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
