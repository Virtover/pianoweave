package com.lumenchord.pianoweave.ui.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.lumenchord.pianoweave.cloud.GoogleDriveManager
import com.lumenchord.pianoweave.midi.MidiStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentLinkedQueue

private const val SYNC_CONCURRENCY = 6

private suspend fun <T, R> List<T>.parallelMap(
    concurrency: Int = SYNC_CONCURRENCY,
    block: suspend (T) -> R
): List<R> {
    val semaphore = Semaphore(concurrency)
    return coroutineScope {
        map { item -> async { semaphore.withPermit { block(item) } } }.awaitAll()
    }
}

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

        val failures = ConcurrentLinkedQueue<String>()

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
            // Corrupted cloud files: delete in parallel
            val corruptedCloudSongs = initialCloudSongs.filter { it.sizeBytes == 0L || it.name.isBlank() }
            corruptedCloudSongs.parallelMap { GoogleDriveManager.deleteCloudMidi(context, it.id, token) }
            initialCloudSongs.removeAll(corruptedCloudSongs.toSet())

            val cloudFileIds = initialCloudSongs.map { it.id }.toSet()
            val allLocalSongs = MidiStorage.listAll(context)

            // 1. Remote deletions (local files only, no network)
            allLocalSongs
                .filter { it.cloudFileId.isNotBlank() && it.cloudFileId !in cloudFileIds }
                .forEach { MidiStorage.hardDelete(context, it) }

            // One snapshot instead of re-listing from disk several times
            val localSongs = allLocalSongs.filterNot {
                it.cloudFileId.isNotBlank() && it.cloudFileId !in cloudFileIds
            }
            val deletedLocal = localSongs.filter { it.isDeleted }
            val activeLocalSongs = localSongs.filterNot { it.isDeleted }
            val deletedLocalCloudIds = deletedLocal.map { it.cloudFileId }.filter { it.isNotBlank() }.toSet()
            val deletedLocalUrls = deletedLocal.map { it.videoUrl }.toSet()

            // 2. Purge cloud copies of soft-deleted songs (parallel)
            val cloudSongsToDelete = initialCloudSongs.filter {
                it.id in deletedLocalCloudIds || it.videoUrl in deletedLocalUrls
            }
            cloudSongsToDelete.parallelMap { GoogleDriveManager.deleteCloudMidi(context, it.id, token) }
            initialCloudSongs.removeAll(cloudSongsToDelete.toSet())
            deletedLocal.forEach { MidiStorage.hardDelete(context, it) }

            val remainingCloudByUrl = initialCloudSongs.associateBy { it.videoUrl }
            val remainingCloudById = initialCloudSongs.associateBy { it.id }

            // Work out BOTH directions up front from the same snapshot. They are disjoint,
            // so uploads and downloads can run at the same time.
            val localCloudIds = activeLocalSongs.map { it.cloudFileId }.filter { it.isNotBlank() }.toSet()
            val localUrls = activeLocalSongs.map { it.videoUrl }.toSet()

            val songsToUpload = activeLocalSongs.filter {
                it.cloudFileId !in remainingCloudById && it.videoUrl !in remainingCloudByUrl
            }
            val songsToLink = activeLocalSongs.filter {
                it.cloudFileId !in remainingCloudById && it.videoUrl in remainingCloudByUrl
            }
            val songsToDownload = initialCloudSongs.filter {
                it.id !in localCloudIds && it.videoUrl !in localUrls
            }

            // Linking is local-only metadata, no network
            songsToLink.forEach { MidiStorage.updateCloudFileId(context, it, remainingCloudByUrl.getValue(it.videoUrl).id) }

            val total = songsToUpload.size + songsToDownload.size
            val done = AtomicInteger(0)
            suspend fun reportProgress(label: String) {
                if (showModalProgress && total > 0) {
                    val n = done.incrementAndGet()
                    withContext(Dispatchers.Main) { cloudSyncProgressText = "$label ($n of $total)" }
                }
            }

            // 3 + 4. Upload and download concurrently
            coroutineScope {
                val uploads = async {
                    songsToUpload.parallelMap { localMidi ->
                        GoogleDriveManager.uploadMidi(context, localMidi, token).fold(
                            onSuccess = { MidiStorage.updateCloudFileId(context, localMidi, it.id) },
                            onFailure = { err ->
                                failures.add("Upload '${localMidi.metadata.title}': ${err.localizedMessage ?: "Unknown error"}")
                            }
                        )
                        reportProgress("Syncing ${localMidi.metadata.title}")
                    }
                }
                val downloads = async {
                    songsToDownload.parallelMap { cloudSong ->
                        GoogleDriveManager.downloadMidiFile(context, cloudSong, token).fold(
                            onSuccess = { stored ->
                                if (stored.file.exists() && stored.file.length() > 0) {
                                    MidiStorage.updateCloudFileId(context, stored, cloudSong.id)
                                } else {
                                    failures.add("Downloaded file '${cloudSong.metadata.title}' is empty.")
                                }
                            },
                            onFailure = { err ->
                                failures.add("Download '${cloudSong.metadata.title}': ${err.localizedMessage ?: "Unknown error"}")
                            }
                        )
                        reportProgress("Syncing ${cloudSong.metadata.title}")
                    }
                }
                uploads.await()
                downloads.await()
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
