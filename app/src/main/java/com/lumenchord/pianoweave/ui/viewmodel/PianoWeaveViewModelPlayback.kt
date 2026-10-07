package com.lumenchord.pianoweave.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.lumenchord.pianoweave.cloud.CloudAccountCache
import com.lumenchord.pianoweave.cloud.GoogleDriveManager
import com.lumenchord.pianoweave.midi.MidiStorage
import com.lumenchord.pianoweave.midi.StoredMidi
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun PianoWeaveViewModel.setSelectedThemeImpl(context: Context, themeId: String) {
    selectedThemeId = themeId
    val prefs = context.getSharedPreferences("piano_weave_prefs", Context.MODE_PRIVATE)
    prefs.edit().putString("selected_theme_id", themeId).apply()
}

internal fun PianoWeaveViewModel.clearTranscriptionErrorImpl() {
    transcriptionError = null
    status = if (videoUrl.isNotBlank()) "Ready to convert." else "Paste a video link above to begin."
    progress = 0f
}

internal fun PianoWeaveViewModel.setTopBarSpeedImpl(context: Context, speed: Float) {
    topBarSpeed = speed
    speedMultiplier = speed
    val prefs = context.getSharedPreferences("piano_weave_prefs", Context.MODE_PRIVATE)
    prefs.edit().putFloat("top_bar_speed", speed).apply()
}

internal fun PianoWeaveViewModel.setStrikeOverlayEnabledImpl(context: Context, enabled: Boolean) {
    isStrikeOverlayEnabled = enabled
    val prefs = context.getSharedPreferences("piano_weave_prefs", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("is_strike_overlay_enabled", enabled).apply()
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
        val prefs = context.getSharedPreferences("piano_weave_prefs", Context.MODE_PRIVATE)
        prefs.edit().putFloat("top_bar_speed", 1.0f).apply()
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
    MidiStorage.delete(context, song)
    loadSongsImpl(context)
    if (activePracticeSong?.file?.absolutePath == song.file.absolutePath) {
        activePracticeSong = null
    }
}

internal fun PianoWeaveViewModel.clearImportErrorImpl() {
    importError = null
}

internal fun PianoWeaveViewModel.importMidiFileImpl(context: Context, uri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val importedMidi = MidiStorage.importFile(context, uri)

            // Auto-upload imported track to Google Account if signed in and save ONLY to cloud library
            if (isGoogleSignedIn) {
                try {
                    val token = googleToken
                    if (token.isNotBlank()) {
                        GoogleDriveManager.uploadMidi(token, importedMidi)
                        CloudAccountCache.saveMidiContent(context, importedMidi.file.nameWithoutExtension, importedMidi.file)
                        importedMidi.file.delete()
                        File(importedMidi.file.parentFile, importedMidi.file.nameWithoutExtension + ".meta").delete()
                    }
                } catch (_: Exception) {
                    // Ignore background upload errors silently
                }
            }

            withContext(Dispatchers.Main) {
                loadSongsImpl(context)
                if (isGoogleSignedIn) {
                    loadCloudSongsImpl(context)
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                importError = e.message ?: "Failed to import file. Please select a valid MIDI file."
            }
        }
    }
}
