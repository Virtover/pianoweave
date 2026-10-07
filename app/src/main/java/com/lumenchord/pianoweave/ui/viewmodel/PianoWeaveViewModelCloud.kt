package com.lumenchord.pianoweave.ui.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.lumenchord.pianoweave.cloud.CloudAccountCache
import com.lumenchord.pianoweave.cloud.CloudMidi
import com.lumenchord.pianoweave.cloud.GoogleDriveManager
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
        val result = GoogleDriveManager.listCloudMidis(token)
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
            return@launch
        }

        var successCount = 0
        val existingCloudUrls = cloudSongs.map { it.videoUrl }.toSet()

        for ((index, storedMidi) in localList.withIndex()) {
            uploadProgressText = "Uploading ${index + 1} of ${localList.size}: ${storedMidi.metadata.title}"

            // Save to local account cache regardless of network
            CloudAccountCache.saveMidiContent(context, storedMidi.file.nameWithoutExtension, storedMidi.file)

            // Skip uploading to Drive if already present in cloud
            if (existingCloudUrls.contains(storedMidi.videoUrl)) {
                successCount++
                continue
            }

            val result = GoogleDriveManager.uploadMidi(token, storedMidi)
            result.fold(
                onSuccess = { cloudMidi ->
                    successCount++
                    CloudAccountCache.saveMidiContent(context, cloudMidi.id, storedMidi.file)
                },
                onFailure = { err ->
                    cloudError = "Error uploading ${storedMidi.metadata.title}: ${err.localizedMessage}"
                }
            )
        }

        // Reload cloud list and update cache
        loadCloudSongsImpl(context)

        // Remove uploaded local songs from local storage (MidiStorage)
        for (storedMidi in localList) {
            MidiStorage.delete(context, storedMidi)
        }
        loadSongsImpl(context)

        isUploadingToCloud = false
        uploadProgressText = ""
        showUploadDialog = false
        selectedStorageTab = StorageTab.GOOGLE_ACCOUNT
    }
}

internal fun PianoWeaveViewModel.deleteCloudSongImpl(context: Context, cloudMidi: CloudMidi) {
    val token = googleToken

    viewModelScope.launch {
        // Remove locally from state and account cache
        cloudSongs = cloudSongs.filterNot { it.id == cloudMidi.id }
        CloudAccountCache.deleteCloudSong(context, cloudMidi.id)

        if (token.isNotBlank()) {
            GoogleDriveManager.deleteCloudMidi(token, cloudMidi.id)
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
        val result = GoogleDriveManager.downloadMidiFile(context, token, cloudMidi)
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
