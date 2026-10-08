package com.lumenchord.pianoweave.ui.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.lumenchord.pianoweave.cloud.CloudAccountCache
import com.lumenchord.pianoweave.cloud.CloudMidi
import com.lumenchord.pianoweave.cloud.GoogleDriveManager
import com.lumenchord.pianoweave.midi.MidiExporter
import com.lumenchord.pianoweave.midi.MidiStorage
import com.lumenchord.pianoweave.midi.StoredMidi
import kotlinx.coroutines.launch

enum class StorageTab {
    MY_LIBRARY,
    GOOGLE_ACCOUNT
}

internal fun PianoWeaveViewModel.loadCloudSongsImpl(context: Context) {
    if (!isGoogleSignedIn && googleUserEmail.isBlank()) {
        cloudSongs = emptyList()
        return
    }

    // 1. Load immediately from local account cache for fast offline access
    val cachedSongs = CloudAccountCache.loadCloudSongs(context)
    if (cachedSongs.isNotEmpty() || cloudSongs.isEmpty()) {
        cloudSongs = cachedSongs
    }

    // 2. Fetch fresh list from Google Drive if token available
    val token = googleToken
    if (token.isBlank()) return

    viewModelScope.launch {
        isCloudLoading = true
        cloudError = null
        val result = GoogleDriveManager.listCloudMidis(context, token)
        result.fold(
            onSuccess = { onlineList ->
                cloudSongs = onlineList
                CloudAccountCache.saveCloudSongs(context, onlineList)
                isCloudLoading = false
            },
            onFailure = { err ->
                isCloudLoading = false
                // On failure (e.g. offline), retain cached songs
                if (cloudSongs.isEmpty()) {
                    cloudSongs = CloudAccountCache.loadCloudSongs(context)
                }
                cloudError = err.localizedMessage ?: "Failed to sync with Google Account."
            }
        )
    }
}

internal fun PianoWeaveViewModel.uploadLocalSongsToCloudImpl(context: Context) {
    val localList = songs
    if (localList.isEmpty()) return

    viewModelScope.launch {
        isUploadingToCloud = true
        uploadProgressText = "Preparing upload..."
        cloudError = null

        val token = ensureGoogleAuthToken(context)
        if (token.isNullOrBlank()) {
            isUploadingToCloud = false
            cloudError = "Not signed in to Google."
            return@launch // dialog stays open
        }

        val cloudIdByUrl = cloudSongs.associate { it.videoUrl to it.id }
        val synced = mutableListOf<StoredMidi>()
        val failures = mutableListOf<String>()

        localList.forEachIndexed { index, midi ->
            uploadProgressText = "Uploading ${index + 1} of ${localList.size}: ${midi.metadata.title}"

            val cloudId = cloudIdByUrl[midi.videoUrl]
                ?: GoogleDriveManager.uploadMidi(context, midi, token)
                    .onFailure { failures += "${midi.metadata.title}: ${it.localizedMessage}" }
                    .getOrNull()
                    ?.id

            if (cloudId != null) {
                CloudAccountCache.saveMidiContent(context, cloudId, midi.file)
                synced += midi
            }
        }

        loadCloudSongsImpl(context)

        synced.forEach { MidiStorage.delete(context, it) }
        loadSongsImpl(context)

        uploadProgressText = ""
        showUploadDialog = false
        isUploadingToCloud = false

        if (failures.isEmpty()) {
            selectedStorageTab = StorageTab.GOOGLE_ACCOUNT
        } else {
            cloudError = buildString {
                append("${synced.size} of ${localList.size} uploaded. ${failures.size} failed:\n")
                append(failures.joinToString("\n") { "• $it" })
            }
            uploadErrorOccured = true
        }
    }
}

internal fun PianoWeaveViewModel.deleteCloudSongImpl(context: Context, cloudMidi: CloudMidi) {
    val token = googleToken

    viewModelScope.launch {
        // Remove locally from state and account cache
        cloudSongs = cloudSongs.filterNot { it.id == cloudMidi.id }
        CloudAccountCache.deleteCloudSong(context, cloudMidi.id)

        if (token.isNotBlank()) {
            GoogleDriveManager.deleteCloudMidi(context, cloudMidi.id, token)
        }
    }
}

internal fun PianoWeaveViewModel.playCloudSongImpl(context: Context, cloudMidi: CloudMidi) {
    // 1. Check if cached in account cache
    val cachedFile = CloudAccountCache.getCachedMidiFile(context, cloudMidi.id)
    if (cachedFile != null) {
        val stored = StoredMidi(
            file = cachedFile,
            videoUrl = cloudMidi.videoUrl,
            metadata = cloudMidi.metadata
        )
        openPracticeSession(context, stored)
        return
    }

    // 2. Check if cached in MidiStorage
    val existingFile = MidiStorage.find(context, cloudMidi.videoUrl)
    if (existingFile != null) {
        val stored = StoredMidi(
            file = existingFile,
            videoUrl = cloudMidi.videoUrl,
            metadata = cloudMidi.metadata
        )
        openPracticeSession(context, stored)
        return
    }

    // 3. Otherwise download from Google Drive API
    viewModelScope.launch {
        isCloudLoading = true
        val token = ensureGoogleAuthToken(context)
        if (token.isNullOrBlank()) {
            isCloudLoading = false
            cloudError = "Please sign in to Google to download this track."
            return@launch
        }
        val result = GoogleDriveManager.downloadMidiFile(context, cloudMidi, token)
        result.fold(
            onSuccess = { storedMidi ->
                isCloudLoading = false
                openPracticeSession(context, storedMidi)
            },
            onFailure = { err ->
                isCloudLoading = false
                cloudError = "Failed to download track: ${err.localizedMessage}"
            }
        )
    }
}

internal fun PianoWeaveViewModel.exportCloudSongImpl(context: Context, song: CloudMidi) {
    launchExport(context, song.id, song.metadata.title) {
        val cached = CloudAccountCache.getCachedMidiFile(context, song.id)
        if (cached != null && cached.exists()) {
            MidiExporter.exportToDownloads(context, song.metadata.title) { cached.inputStream() }
        } else {
            val token = ensureGoogleAuthToken(context)
                ?: return@launchExport Result.failure(IllegalStateException("Not signed in to Google."))
            GoogleDriveManager.downloadMidiFile(context, song, token)
                .mapCatching { storedSong ->
                    MidiExporter.exportToDownloads(context, storedSong.metadata.title) {
                        storedSong.file.inputStream()
                    }.getOrThrow()
                }
        }
    }
}
