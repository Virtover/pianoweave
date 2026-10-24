package com.lumenchord.pianoweave.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.lumenchord.pianoweave.api.isPlaceholder
import com.lumenchord.pianoweave.cloud.CloudAccountCache
import com.lumenchord.pianoweave.cloud.CloudMidi
import com.lumenchord.pianoweave.cloud.GoogleDriveManager
import com.lumenchord.pianoweave.cloud.PendingDetailEdits
import com.lumenchord.pianoweave.cloud.isCloudFallback
import com.lumenchord.pianoweave.midi.hasEditableDetails
import com.lumenchord.pianoweave.midi.isSharedSourceKey
import com.lumenchord.pianoweave.midi.MidiStorage
import com.lumenchord.pianoweave.midi.StoredMidi
import com.lumenchord.pianoweave.midi.settleAddedAt
import com.lumenchord.pianoweave.util.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentLinkedQueue
import retrofit2.HttpException

private const val SYNC_CONCURRENCY = 6
private const val TAG = "CloudSync"

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
    refreshCloudSyncImpl(context, showModalProgress = true, uploadPreviousAccountSongs = true)
}

internal fun PianoWeaveViewModel.refreshCloudSyncImpl(
    context: Context,
    showModalProgress: Boolean = false,
    uploadPreviousAccountSongs: Boolean = false
) {
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
            GoogleDriveManager.libraryMutex.withLock {
                if (showModalProgress) {
                    withContext(Dispatchers.Main) {
                        cloudSyncProgressText = "Checking cloud storage..."
                    }
                }

                val initialCloudResult = GoogleDriveManager.listCloudMidis(context, token)
                if (initialCloudResult.isFailure) {
                    val err = initialCloudResult.exceptionOrNull()
                    Log.w(TAG, "Listing cloud library failed", err)
                    withContext(Dispatchers.Main) {
                        hasCloudSyncError = true
                        cloudSyncError = describeSyncError(err)
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

                // Only now that the new account's Drive is readable: a failed sign-in must not empty the library.
                adoptCloudAccount(context, replaceLibrary = !uploadPreviousAccountSongs)

                val initialCloudSongs = initialCloudResult.getOrDefault(emptyList()).toMutableList()
                // Corrupted cloud files: delete in parallel
                val corruptedCloudSongs = initialCloudSongs.filter { it.sizeBytes == 0L || it.name.isBlank() }
                corruptedCloudSongs.parallelMap { GoogleDriveManager.deleteCloudMidi(context, it.id, token) }
                initialCloudSongs.removeAll(corruptedCloudSongs.toSet())

                removeDuplicateCloudCopies(context, initialCloudSongs, MidiStorage.listAll(context), token)

                // Best effort, so every build reads them directly; the details were already read above
                initialCloudSongs.filter { it.hasLegacyDescription && !it.hasLostMetadata }
                    .parallelMap { GoogleDriveManager.rewriteLegacyDescription(context, it, token) }

                unlinkImportsSharingCloudCopy(context)

                // Keys shared by several songs (older imports) never identify a song; those match by cloud id only.
                val cloudFileIds = initialCloudSongs.map { it.id }.toSet()
                val cloudUrls = initialCloudSongs.map { it.videoUrl }.filterNot { it.isSharedSourceKey }.toSet()
                val allLocalSongs = MidiStorage.listAll(context)

                // 1. Remote deletions (local files only, no network). A song whose linked copy is gone but
                // which still has another cloud copy (e.g. a duplicate removed by another device) is relinked below.
                fun isDeletedRemotely(song: StoredMidi) =
                    song.cloudFileId.isNotBlank() && song.cloudFileId !in cloudFileIds && song.videoUrl !in cloudUrls
                if (!uploadPreviousAccountSongs) {
                    allLocalSongs.filter(::isDeletedRemotely).forEach { MidiStorage.hardDelete(context, it) }
                }

                // One snapshot instead of re-listing from disk several times
                val localSongs = if (!uploadPreviousAccountSongs) {
                    allLocalSongs.filterNot(::isDeletedRemotely)
                } else {
                    allLocalSongs
                }
                val deletedLocal = localSongs.filter { it.isDeleted }
                val activeLocalSongs = localSongs.filterNot { it.isDeleted }
                val deletedLocalCloudIds = deletedLocal.map { it.cloudFileId }.filter { it.isNotBlank() }.toSet()
                val deletedLocalUrls = deletedLocal.map { it.videoUrl }.filterNot { it.isSharedSourceKey }.toSet()

                // 2. Purge cloud copies of soft-deleted songs (parallel)
                val cloudSongsToDelete = initialCloudSongs.filter {
                    it.id in deletedLocalCloudIds || it.videoUrl in deletedLocalUrls
                }
                val stillInCloud = cloudSongsToDelete.parallelMap { cloud ->
                    cloud.takeIf { GoogleDriveManager.deleteCloudMidi(context, it.id, token).isFailure }
                }.filterNotNull()
                // Not downloaded again now either way; the deletion is retried on the next sync
                initialCloudSongs.removeAll(cloudSongsToDelete.toSet())
                // A deleted song is forgotten only once its cloud copy is gone. Forgotten earlier, a copy
                // whose deletion failed would come back as a new song on the next sync.
                deletedLocal.filterNot { song ->
                    stillInCloud.any { it.id == song.cloudFileId || (!song.videoUrl.isSharedSourceKey && it.videoUrl == song.videoUrl) }
                }.forEach { MidiStorage.hardDelete(context, it) }

                val remainingCloudByUrl = initialCloudSongs.filterNot { it.videoUrl.isSharedSourceKey }.associateBy { it.videoUrl }
                val remainingCloudById = initialCloudSongs.associateBy { it.id }

                // Work out BOTH directions up front from the same snapshot. They are disjoint,
                // so uploads and downloads can run at the same time.
                val localCloudIds = activeLocalSongs.map { it.cloudFileId }.filter { it.isNotBlank() }.toSet()
                val localUrls = activeLocalSongs.map { it.videoUrl }.filterNot { it.isSharedSourceKey }.toSet()

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

                repairLostMetadata(context, activeLocalSongs, remainingCloudById, remainingCloudByUrl, token)
                syncEditedDetails(context, remainingCloudById, token)
                syncAddedDates(context, remainingCloudById, token)

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
                                onSuccess = {
                                    MidiStorage.updateCloudFileId(context, localMidi, it.id)
                                    // The new cloud copy already carries any edited details.
                                    PendingDetailEdits.remove(context, localMidi)
                                },
                                onFailure = { err ->
                                    Log.w(TAG, "Upload failed", err)
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
                                    Log.w(TAG, "Download failed", err)
                                    failures.add("Download '${cloudSong.metadata.title}': ${err.localizedMessage ?: "Unknown error"}")
                                }
                            )
                            reportProgress("Syncing ${cloudSong.metadata.title}")
                        }
                    }
                    uploads.await()
                    downloads.await()
                }
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
            Log.w(TAG, "Cloud sync failed", e)
            withContext(Dispatchers.Main) {
                hasCloudSyncError = true
                cloudSyncError = describeSyncError(e)
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

/** A cloud copy whose metadata was lost (placeholder) or never readable (no description: "cloud://" URL). */
private val CloudMidi.hasLostMetadata: Boolean
    get() = metadata.isPlaceholder || metadata.isCloudFallback || videoUrl.startsWith("cloud://")

/**
 * A local song without real details: a placeholder, or the stand-in from the Drive file name given
 * when the cloud copy's description couldn't be read (release builds once stored it unreadably).
 */
private val StoredMidi.hasLostMetadata: Boolean
    get() = metadata.isPlaceholder || metadata.isCloudFallback

/**
 * Older versions linked imports with the same file name to one cloud copy by their shared key. Keeps one
 * link per cloud copy and unlinks the rest, so they upload as copies of their own; otherwise deleting one
 * would delete the others' cloud copy too.
 */
private fun unlinkImportsSharingCloudCopy(context: Context) {
    MidiStorage.list(context)
        .filter { it.videoUrl.isSharedSourceKey && it.cloudFileId.isNotBlank() }
        .groupBy { it.cloudFileId }
        .values
        .flatMap { it.drop(1) }
        .forEach { MidiStorage.updateCloudFileId(context, it, "") }
}

/**
 * Older versions uploaded every finished transcription twice, and sometimes a copy with its metadata
 * lost. Keeps one copy per transcription URL (real metadata first, then the one this device links to,
 * then the newest), moves local links over to it and deletes the rest. Updates [cloudSongs] in place.
 */
private suspend fun removeDuplicateCloudCopies(
    context: Context,
    cloudSongs: MutableList<CloudMidi>,
    localSongs: List<StoredMidi>,
    token: String
) {
    // Only transcriptions: imported files are keyed by file name, so two different imports can share a URL.
    val duplicateGroups = cloudSongs
        .filter { it.videoUrl.startsWith("http://") || it.videoUrl.startsWith("https://") }
        .groupBy { it.videoUrl }
        .values
        .filter { it.size > 1 }
    if (duplicateGroups.isEmpty()) return

    val linkedIds = localSongs.map { it.cloudFileId }.filter { it.isNotBlank() }.toSet()
    for (copies in duplicateGroups) {
        val kept = copies.sortedWith(
            compareBy<CloudMidi>({ it.hasLostMetadata }, { it.id !in linkedIds }).thenByDescending { it.modifiedTime }
        ).first()
        val extras = copies - kept
        val extraIds = extras.map { it.id }.toSet()

        localSongs.filter { it.cloudFileId in extraIds }.forEach { MidiStorage.updateCloudFileId(context, it, kept.id) }

        val deleted = extras.parallelMap { copy ->
            copy.takeIf { GoogleDriveManager.deleteCloudMidi(context, copy.id, token).isSuccess }
        }.filterNotNull()
        cloudSongs.removeAll(deleted.toSet())
    }
}

/**
 * Restores metadata that older versions lost: if only one side of a local/cloud pair still has real
 * metadata, it is copied to the other side.
 */
private suspend fun repairLostMetadata(
    context: Context,
    localSongs: List<StoredMidi>,
    cloudById: Map<String, CloudMidi>,
    cloudByUrl: Map<String, CloudMidi>,
    token: String
) {
    val cloudCopiesToFix = mutableListOf<Pair<CloudMidi, StoredMidi>>()
    for (local in localSongs) {
        val cloud = cloudById[local.cloudFileId] ?: cloudByUrl[local.videoUrl] ?: continue
        when {
            local.hasLostMetadata && !cloud.hasLostMetadata ->
                MidiStorage.updateMetadata(context, local, cloud.metadata)
            !local.hasLostMetadata && cloud.hasLostMetadata ->
                cloudCopiesToFix += cloud to local
        }
    }
    // Best effort: a failed repair is retried on the next sync.
    cloudCopiesToFix.parallelMap { (cloud, local) -> GoogleDriveManager.updateCloudMetadata(context, cloud.id, local, token) }
}

/**
 * Carries title/artist edits of songs without a source link both ways: details edited here (still
 * pending) are written to the cloud copy, otherwise the cloud copy's details (perhaps edited on
 * another device) replace the local ones. Re-reads the library, as linking above changes cloud ids.
 */
private suspend fun syncEditedDetails(
    context: Context,
    cloudById: Map<String, CloudMidi>,
    token: String
) {
    val localSongs = MidiStorage.list(context)
    PendingDetailEdits.retainOnly(context, localSongs)
    val toPush = mutableListOf<StoredMidi>()
    for (local in localSongs.filter { it.hasEditableDetails }) {
        val cloud = cloudById[local.cloudFileId] ?: continue
        when {
            PendingDetailEdits.contains(context, local) -> toPush += local
            !cloud.hasLostMetadata && !sameDetails(local, cloud) -> MidiStorage.updateMetadata(
                context, local, local.metadata.copy(title = cloud.metadata.title, author = cloud.metadata.author)
            )
        }
    }
    // Best effort: a song that fails stays pending and is retried on the next sync.
    toPush.parallelMap { song ->
        if (GoogleDriveManager.updateCloudMetadata(context, song.cloudFileId, song, token).isSuccess) {
            PendingDetailEdits.remove(context, song)
        }
    }
}

/**
 * Gives each song and its cloud copy the same "added" date, so the library is in the same order on
 * every device. Runs after [syncEditedDetails], so a copy rewritten here already has the same details.
 */
private suspend fun syncAddedDates(
    context: Context,
    cloudById: Map<String, CloudMidi>,
    token: String
) {
    val toPush = mutableListOf<StoredMidi>()
    for (local in MidiStorage.list(context)) {
        val cloud = cloudById[local.cloudFileId] ?: continue
        val settled = settleAddedAt(local.addedAt, cloud.addedAt, cloud.createdTime)
        if (local.addedAt != settled) MidiStorage.updateAddedAt(context, local, settled)
        if (cloud.addedAt != settled) toPush += local.copy(addedAt = settled)
    }
    // Best effort: a failed update is retried on the next sync.
    toPush.parallelMap { song -> GoogleDriveManager.updateCloudMetadata(context, song.cloudFileId, song, token) }
}

/** Compared the way the .meta file stores them, which turns line breaks into spaces. */
private fun sameDetails(local: StoredMidi, cloud: CloudMidi): Boolean {
    fun String.stored() = replace(Regex("[\r\n]+"), " ").trim()
    return local.metadata.title.stored() == cloud.metadata.title.stored() &&
        local.metadata.author.stored() == cloud.metadata.author.stored()
}

/**
 * The local library's cloud links belong to the account it was last synced with. After a switch to
 * another account they are dropped first: with [replaceLibrary] (switch while sync is on) the old
 * account's songs are removed so the new account's library replaces them; otherwise (sync turned on
 * after a switch) they are kept and uploaded to the new account.
 */
private fun PianoWeaveViewModel.adoptCloudAccount(context: Context, replaceLibrary: Boolean) {
    val prefs = AppPrefs.get(context)
    val libraryAccount = prefs.getString(AppPrefs.CLOUD_LIBRARY_ACCOUNT, null)
    val currentAccount = googleUserEmail
    if (!libraryAccount.isNullOrBlank() && !libraryAccount.equals(currentAccount, ignoreCase = true)) {
        MidiStorage.detachFromCloudAccount(context, removeSyncedSongs = replaceLibrary)
        CloudAccountCache.clearCache(context)
        cloudSongs = emptyList()
    }
    prefs.edit().putString(AppPrefs.CLOUD_LIBRARY_ACCOUNT, currentAccount).apply()
}

private fun PianoWeaveViewModel.describeSyncError(error: Throwable?): String =
    when ((error as? HttpException)?.code()) {
        401 -> "Google Drive access for $googleUserEmail has expired or was removed. Sign in again to restore it."
        403 -> "Google Drive access is not granted for $googleUserEmail. Sign in again and allow Piano Weave to use its Google Drive data."
        else -> "Sync error: ${error?.localizedMessage ?: "Failed to connect to Google Drive."}"
    }
