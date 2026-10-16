package com.lumenchord.pianoweave.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.lumenchord.pianoweave.cloud.GoogleDriveManager
import com.lumenchord.pianoweave.cloud.PendingDetailEdits
import com.lumenchord.pianoweave.midi.hasEditableDetails
import kotlinx.coroutines.sync.withLock
import com.lumenchord.pianoweave.midi.ExportNotifier
import com.lumenchord.pianoweave.midi.ExportState
import com.lumenchord.pianoweave.midi.MidiExporter
import com.lumenchord.pianoweave.midi.MidiStorage
import com.lumenchord.pianoweave.midi.StoredMidi
import com.lumenchord.pianoweave.util.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun PianoWeaveViewModel.setSelectedThemeImpl(context: Context, themeId: String) {
    selectedThemeId = themeId
    AppPrefs.get(context).edit().putString(AppPrefs.SELECTED_THEME_ID, themeId).apply()
}

internal fun PianoWeaveViewModel.clearTranscriptionErrorImpl() {
    transcriptionError = null
    status = if (videoUrl.isNotBlank()) "Ready to convert." else "Paste a video link above to begin."
    progress = 0f
}

/** The playback speeds offered in the player. */
val PLAYBACK_SPEEDS = listOf(0.25f, 0.5f, 1.0f, 1.25f, 1.5f, 2.0f)

/** Plays at [speed]; only normal-or-faster speeds are remembered, slow practice starts over at 1x. */
internal fun PianoWeaveViewModel.selectPlaybackSpeedImpl(context: Context, speed: Float) {
    setTopBarSpeedImpl(context, if (speed >= 1.0f) speed else 1.0f)
    speedMultiplier = speed
}

internal fun PianoWeaveViewModel.setTopBarSpeedImpl(context: Context, speed: Float) {
    topBarSpeed = speed
    speedMultiplier = speed
    AppPrefs.get(context).edit().putFloat(AppPrefs.TOP_BAR_SPEED, speed).apply()
}

internal fun PianoWeaveViewModel.setStrikeOverlayEnabledImpl(context: Context, enabled: Boolean) {
    isStrikeOverlayEnabled = enabled
    AppPrefs.get(context).edit().putBoolean(AppPrefs.IS_STRIKE_OVERLAY_ENABLED, enabled).apply()
}

internal fun PianoWeaveViewModel.openPracticeSessionImpl(context: Context, song: StoredMidi) {
    if (lastPlayedSongPath != song.file.absolutePath) {
        playheadMs = 0L
        isPlaying = true
        isLoopingEnabled = false
        loopStartMs = 0L
        loopEndMs = 0L
        speedMultiplier = 1.0f
        topBarSpeed = 1.0f
        AppPrefs.get(context).edit().putFloat(AppPrefs.TOP_BAR_SPEED, 1.0f).apply()
        lastPlayedSongPath = song.file.absolutePath
    }
    activePracticeSong = song
}

internal fun PianoWeaveViewModel.loadSongsImpl(context: Context) {
    songs = MidiStorage.list(context)
    loadPreferencesImpl(context)
    resumeActiveJobIfAnyImpl(context)
}

internal fun PianoWeaveViewModel.deleteSongImpl(context: Context, song: StoredMidi) {
    MidiStorage.delete(context, song) // Soft delete locally immediately
    loadSongsImpl(context)
    if (activePracticeSong?.file?.absolutePath == song.file.absolutePath) {
        activePracticeSong = null
    }

    if (isGoogleSignedIn) {
        viewModelScope.launch(Dispatchers.IO) {
            val token = googleToken
            if (token.isNotBlank()) {
                try {
                    val fileId = if (song.cloudFileId.isNotBlank()) {
                        song.cloudFileId
                    } else {
                        val cloudSongs = GoogleDriveManager.listCloudMidis(context, token).getOrDefault(emptyList())
                        cloudSongs.firstOrNull { it.videoUrl == song.videoUrl }?.id
                    }

                    if (!fileId.isNullOrBlank()) {
                        val deleteResult = GoogleDriveManager.deleteCloudMidi(context, fileId, token)
                        if (deleteResult.isSuccess) {
                            MidiStorage.hardDelete(context, song)
                            withContext(Dispatchers.Main) {
                                hasCloudSyncError = false
                                cloudSyncError = null
                                saveCloudSyncState(context, enabled = isCloudSyncEnabled, hasError = false, errorMsg = null)
                                loadSongsImpl(context)
                            }
                        } else {
                            throw Exception(deleteResult.exceptionOrNull()?.localizedMessage ?: "Failed to delete from Drive")
                        }
                    } else {
                        MidiStorage.hardDelete(context, song)
                        withContext(Dispatchers.Main) {
                            loadSongsImpl(context)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        hasCloudSyncError = true
                        cloudSyncError = "Failed to delete file from cloud: ${e.localizedMessage ?: "Network error"}"
                        saveCloudSyncState(context, enabled = isCloudSyncEnabled, hasError = true, errorMsg = cloudSyncError)
                        loadSongsImpl(context)
                    }
                }
            }
        }
    }
}

internal fun PianoWeaveViewModel.clearImportErrorImpl() {
    importError = null
}

internal fun PianoWeaveViewModel.importMidiFileImpl(context: Context, uri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val importedMidi = MidiStorage.importFile(context, uri)

            if (isCloudSyncEnabled && isGoogleSignedIn) {
                try {
                    val token = googleToken
                    if (token.isNotBlank()) {
                        GoogleDriveManager.backUpNewSong(context, importedMidi, token).getOrThrow()
                        refreshCloudSyncImpl(context, false)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        hasCloudSyncError = true
                        cloudSyncError = "Failed to sync '${importedMidi.metadata.title}' to cloud: ${e.localizedMessage ?: "Network error"}"
                        saveCloudSyncState(context, enabled = isCloudSyncEnabled, hasError = true, errorMsg = cloudSyncError)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                loadSongsImpl(context)
                Toast.makeText(
                    context,
                    "Added to library: ${importedMidi.metadata.title}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                importError = e.message ?: "Failed to import file. Please select a valid MIDI file."
            }
        }
    }
}

/**
 * Renames a song that has no source link (see [hasEditableDetails]). A blank artist is stored as
 * "Unknown", like any other song whose artist isn't known. Synced songs update their cloud copy now
 * if possible; otherwise the edit stays pending and the next sync writes it.
 */
internal fun PianoWeaveViewModel.updateSongDetailsImpl(context: Context, song: StoredMidi, title: String, artist: String) {
    val newTitle = title.replace(Regex("[\r\n]+"), " ").trim()
    if (newTitle.isBlank() || !song.hasEditableDetails) return
    // Left blank, an import keeps showing "Device Storage"; a cleared real artist becomes "Unknown".
    val newArtist = artist.replace(Regex("[\r\n]+"), " ").trim().ifBlank {
        song.metadata.author.takeIf { it == "Device Storage" } ?: "Unknown"
    }
    val metadata = song.metadata.copy(title = newTitle, author = newArtist)
    if (metadata == song.metadata) return

    MidiStorage.updateMetadata(context, song, metadata)
    PendingDetailEdits.add(context, song)
    songs = MidiStorage.list(context)
    if (readySong?.file == song.file) readySong = readySong?.copy(metadata = metadata)
    if (activePracticeSong?.file == song.file) activePracticeSong = activePracticeSong?.copy(metadata = metadata)

    if (!isCloudSyncEnabled || !isGoogleSignedIn) return
    val token = googleToken.takeIf { it.isNotBlank() } ?: return
    viewModelScope.launch(Dispatchers.IO) {
        // Under the library lock so a running sync can't read the pending flag between this write and its own.
        GoogleDriveManager.libraryMutex.withLock {
            val current = MidiStorage.list(context).firstOrNull { it.file == song.file } ?: return@withLock
            // Not uploaded yet: the upload in the next sync carries the new details.
            if (current.cloudFileId.isBlank()) return@withLock
            if (GoogleDriveManager.updateCloudMetadata(context, current.cloudFileId, current, token).isSuccess) {
                PendingDetailEdits.remove(context, current)
            }
        }
    }
}

internal fun PianoWeaveViewModel.exportLocalSongImpl(context: Context, song: StoredMidi) {
    launchExport(context, song.file.absolutePath, song.metadata.title) {
        MidiExporter.exportToDownloads(context, song.metadata.title) { song.file.inputStream() }
    }
}

internal fun PianoWeaveViewModel.launchExport(
    context: Context,
    key: String,
    title: String,
    work: suspend () -> Result<String>
) {
    val current = exportStates[key]
    if (current == ExportState.Exporting || current == ExportState.Done) return // ignore double taps

    viewModelScope.launch {
        exportStates[key] = ExportState.Exporting
        ExportNotifier.started(context, key, title)

        val result = withContext(Dispatchers.IO) { work() }

        result.fold(
            onSuccess = { ExportNotifier.finished(context, key, title, it) },
            onFailure = { ExportNotifier.failed(context, key, title, it.localizedMessage) }
        )

        Toast.makeText(
            context,
            result.fold(
                onSuccess = { "Saved to Downloads: $it" },
                onFailure = { "Export failed: ${it.localizedMessage}" }
            ),
            Toast.LENGTH_LONG
        ).show()
        exportStates[key] = if (result.isSuccess) ExportState.Done else ExportState.Failed

        delay(10000)
        // Reset, unless the user already retried
        if (exportStates[key] != ExportState.Exporting) exportStates.remove(key)
    }
}
