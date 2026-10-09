package com.lumenchord.pianoweave.ui.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.lumenchord.pianoweave.cloud.GoogleDriveManager
import com.lumenchord.pianoweave.midi.MidiStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun PianoWeaveViewModel.enableCloudSyncImpl(context: Context) {
    viewModelScope.launch(Dispatchers.Main) {
        showTurnOnCloudSyncConfirmDialog = false
        isCloudSyncEnabled = true
        hasCloudSyncError = false
        cloudSyncError = null
        saveCloudSyncState(context, enabled = true, hasError = false, errorMsg = null)
    }
    refreshCloudSyncImpl(context, showModalProgress = true)
}

internal fun PianoWeaveViewModel.refreshCloudSyncImpl(context: Context, showModalProgress: Boolean = false) {
    if (isRefreshingCloud || isCloudSyncing) return
    viewModelScope.launch(Dispatchers.IO) {
        withContext(Dispatchers.Main) {
            if (showModalProgress) {
                isCloudSyncing = true
                cloudSyncProgressText = "Preparing cloud sync..."
            } else {
                isRefreshingCloud = true
            }
            hasCloudSyncError = false
            cloudSyncError = null
        }

        val token = ensureGoogleAuthTokenImpl(context)
        if (token.isNullOrBlank()) {
            withContext(Dispatchers.Main) {
                isCloudSyncing = false
                isRefreshingCloud = false
                hasCloudSyncError = true
                cloudSyncError = "Google account sign-in required for sync."
                showCloudSyncErrorDialog = true
                saveCloudSyncState(context, enabled = true, hasError = true, errorMsg = cloudSyncError)
            }
            return@launch
        }

        val failures = mutableListOf<String>()

        try {
            if (showModalProgress) {
                withContext(Dispatchers.Main) {
                    cloudSyncProgressText = "Checking cloud storage..."
                }
            }

            val initialCloudResult = GoogleDriveManager.listCloudMidis(context, token)
            if (initialCloudResult.isFailure) {
                val err = initialCloudResult.exceptionOrNull()
                withContext(Dispatchers.Main) {
                    hasCloudSyncError = true
                    cloudSyncError = "Sync error: ${err?.localizedMessage ?: "Failed to connect to Google Drive."}"
                    isCloudSyncing = false
                    isRefreshingCloud = false
                    if (showModalProgress) {
                        showCloudSyncErrorDialog = true
                    }
                    saveCloudSyncState(context, enabled = true, hasError = true, errorMsg = cloudSyncError)
                    loadSongsImpl(context)
                }
                return@launch
            }

            val initialCloudSongs = initialCloudResult.getOrDefault(emptyList()).toMutableList()

            // Remove 0-byte or nameless corrupted files from cloud
            val corruptedCloudSongs = initialCloudSongs.filter { it.sizeBytes == 0L || it.name.isBlank() }
            for (corrupted in corruptedCloudSongs) {
                GoogleDriveManager.deleteCloudMidi(context, corrupted.id, token)
                initialCloudSongs.remove(corrupted)
            }

            val cloudFileIds = initialCloudSongs.map { it.id }.toSet()
            val allLocalSongs = MidiStorage.listAll(context)

            // 1. Detect remote deletions (local song has cloudFileId, but cloudFileId no longer on Drive)
            for (localMidi in allLocalSongs) {
                if (localMidi.cloudFileId.isNotBlank() && !cloudFileIds.contains(localMidi.cloudFileId)) {
                    MidiStorage.hardDelete(context, localMidi)
                }
            }

            // Refresh local songs list
            val updatedLocalSongs = MidiStorage.listAll(context)
            val deletedLocalCloudIds = updatedLocalSongs.filter { it.isDeleted && it.cloudFileId.isNotBlank() }.map { it.cloudFileId }.toSet()
            val deletedLocalUrls = updatedLocalSongs.filter { it.isDeleted }.map { it.videoUrl }.toSet()
            val activeLocalSongs = updatedLocalSongs.filterNot { it.isDeleted }

            // 2. Purge cloud copies for local soft-deleted songs
            val cloudSongsToDelete = initialCloudSongs.filter {
                deletedLocalCloudIds.contains(it.id) || deletedLocalUrls.contains(it.videoUrl)
            }
            for (cloudSong in cloudSongsToDelete) {
                GoogleDriveManager.deleteCloudMidi(context, cloudSong.id, token)
                initialCloudSongs.remove(cloudSong)
            }

            // Permanently hard delete local soft-deleted songs whose cloud copies are now removed
            updatedLocalSongs.filter { it.isDeleted }.forEach { MidiStorage.hardDelete(context, it) }

            val remainingCloudByUrl = initialCloudSongs.associateBy { it.videoUrl }
            val remainingCloudById = initialCloudSongs.associateBy { it.id }

            // 3. Upload active local songs missing cloudFileId or not on cloud
            val songsToUpload = activeLocalSongs.filter {
                !remainingCloudById.containsKey(it.cloudFileId) && !remainingCloudByUrl.containsKey(it.videoUrl)
            }

            songsToUpload.forEachIndexed { index, localMidi ->
                if (showModalProgress) {
                    withContext(Dispatchers.Main) {
                        cloudSyncProgressText = "Uploading ${index + 1} of ${songsToUpload.size}: ${localMidi.metadata.title}"
                    }
                }

                val existingCloud = remainingCloudByUrl[localMidi.videoUrl]
                if (existingCloud != null) {
                    MidiStorage.updateCloudFileId(context, localMidi, existingCloud.id)
                } else {
                    val uploadResult = GoogleDriveManager.uploadMidi(context, localMidi, token)
                    uploadResult.fold(
                        onSuccess = { uploadedCloudMidi ->
                            MidiStorage.updateCloudFileId(context, localMidi, uploadedCloudMidi.id)
                        },
                        onFailure = { err ->
                            failures.add("Upload '${localMidi.metadata.title}': ${err.localizedMessage ?: "Unknown error"}")
                        }
                    )
                }
            }

            // 4. Download cloud songs missing from local storage
            val currentLocalCloudIds = MidiStorage.listAll(context).map { it.cloudFileId }.toSet()
            val currentLocalUrls = MidiStorage.listAll(context).map { it.videoUrl }.toSet()
            val songsToDownload = initialCloudSongs.filter {
                !currentLocalCloudIds.contains(it.id) && !currentLocalUrls.contains(it.videoUrl)
            }

            songsToDownload.forEachIndexed { index, cloudSong ->
                if (showModalProgress) {
                    withContext(Dispatchers.Main) {
                        cloudSyncProgressText = "Downloading ${index + 1} of ${songsToDownload.size}: ${cloudSong.metadata.title}"
                    }
                }

                val downloadResult = GoogleDriveManager.downloadMidiFile(context, cloudSong, token)
                downloadResult.fold(
                    onSuccess = { storedMidi ->
                        if (storedMidi.file.exists() && storedMidi.file.length() > 0) {
                            MidiStorage.updateCloudFileId(context, storedMidi, cloudSong.id)
                        } else {
                            failures.add("Downloaded file '${cloudSong.metadata.title}' is empty.")
                        }
                    },
                    onFailure = { err ->
                        failures.add("Download '${cloudSong.metadata.title}': ${err.localizedMessage ?: "Unknown error"}")
                    }
                )
            }

            withContext(Dispatchers.Main) {
                hasCloudSyncError = failures.isNotEmpty()
                cloudSyncError = if (failures.isNotEmpty()) {
                    buildString {
                        append("Sync completed with errors:\n\n")
                        append(failures.joinToString("\n") { "• $it" })
                    }
                } else {
                    null
                }
                isCloudSyncing = false
                isRefreshingCloud = false
                if (hasCloudSyncError && showModalProgress) {
                    showCloudSyncErrorDialog = true
                }
                saveCloudSyncState(context, enabled = true, hasError = hasCloudSyncError, errorMsg = cloudSyncError)
                loadSongsImpl(context)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                hasCloudSyncError = true
                cloudSyncError = "Sync error: ${e.localizedMessage ?: "Unexpected error"}"
                isCloudSyncing = false
                isRefreshingCloud = false
                if (showModalProgress) {
                    showCloudSyncErrorDialog = true
                }
                saveCloudSyncState(context, enabled = true, hasError = true, errorMsg = cloudSyncError)
                loadSongsImpl(context)
            }
        }
    }
}

internal fun PianoWeaveViewModel.disableCloudSyncImpl(context: Context) {
    isCloudSyncEnabled = false
    hasCloudSyncError = false
    cloudSyncError = null
    showTurnOffCloudSyncConfirmDialog = false
    saveCloudSyncState(context, enabled = false, hasError = false, errorMsg = null)
}

internal fun saveCloudSyncState(context: Context, enabled: Boolean, hasError: Boolean, errorMsg: String?) {
    val prefs = context.getSharedPreferences("piano_weave_prefs", Context.MODE_PRIVATE)
    val editor = prefs.edit()
        .putBoolean("is_cloud_sync_enabled", enabled)
        .putBoolean("has_cloud_sync_error", hasError)

    if (errorMsg != null) {
        editor.putString("cloud_sync_error", errorMsg)
    } else {
        editor.remove("cloud_sync_error")
    }
    editor.apply()
}
