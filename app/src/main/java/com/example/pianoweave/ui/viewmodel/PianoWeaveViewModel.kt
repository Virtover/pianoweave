package com.example.pianoweave.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pianoweave.api.CreateTranscriptionRequest
import com.example.pianoweave.api.PianoApiFactory
import com.example.pianoweave.midi.MidiStorage
import com.example.pianoweave.midi.StoredMidi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PianoWeaveViewModel : ViewModel() {

    // --- Conversion State ---
    var videoUrl by mutableStateOf("")
        private set

    var status by mutableStateOf("Paste a video link above to begin.")
        private set

    var progress by mutableFloatStateOf(0f)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var songs by mutableStateOf<List<StoredMidi>>(emptyList())
        private set

    var readySong by mutableStateOf<StoredMidi?>(null)
    var activePracticeSong by mutableStateOf<StoredMidi?>(null)
    private var lastPlayedSongPath: String? = null

    // --- Playback State (Orientation Survival) ---
    var isPlaying by mutableStateOf(false)
    var playheadMs by mutableLongStateOf(0L)
    var speedMultiplier by mutableFloatStateOf(1.0f)
    var isWaitModeEnabled by mutableStateOf(false)
    var isStrikeOverlayEnabled by mutableStateOf(true)
        private set
    var isLoopingEnabled by mutableStateOf(false)
    var loopStartMs by mutableLongStateOf(0L)
    var loopEndMs by mutableLongStateOf(0L)
    var transposeOffset by mutableIntStateOf(0)

    fun loadPreferences(context: Context) {
        val prefs = context.getSharedPreferences("piano_weave_prefs", Context.MODE_PRIVATE)
        isStrikeOverlayEnabled = prefs.getBoolean("is_strike_overlay_enabled", true)
    }

    fun setStrikeOverlayEnabled(context: Context, enabled: Boolean) {
        isStrikeOverlayEnabled = enabled
        val prefs = context.getSharedPreferences("piano_weave_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_strike_overlay_enabled", enabled).apply()
    }

    fun openPracticeSession(song: StoredMidi) {
        if (lastPlayedSongPath != song.file.absolutePath) {
            playheadMs = 0L
            isPlaying = true // Auto-play new songs
            isLoopingEnabled = false
            loopStartMs = 0L
            loopEndMs = 0L
            lastPlayedSongPath = song.file.absolutePath
        }
        activePracticeSong = song
    }

    fun updateUrl(url: String) {
        if (!isLoading) {
            videoUrl = url
            status = "Ready to convert."
            progress = 0f
        }
    }

    fun loadSongs(context: Context) {
        songs = MidiStorage.list(context)
        loadPreferences(context)
    }

    fun deleteSong(context: Context, song: StoredMidi) {
        MidiStorage.delete(context, song)
        loadSongs(context)
        if (activePracticeSong?.file?.absolutePath == song.file.absolutePath) {
            activePracticeSong = null
        }
    }

    fun startTranscription(context: Context) {
        val stableUrl = normalizeUrl(videoUrl)
        if (stableUrl.isBlank() || isLoading) return
        
        videoUrl = stableUrl

        viewModelScope.launch {
            isLoading = true
            progress = 0f
            status = "Checking local cache..."

            try {
                val storedFile = MidiStorage.find(context, stableUrl)
                if (storedFile != null) {
                    progress = 1f
                    status = "Loaded from local library cache!"
                    loadSongs(context)
                    readySong = songs.firstOrNull { it.file.absolutePath == storedFile.absolutePath }
                    isLoading = false
                    return@launch
                }

                status = "Submitting request to server..."
                val response = PianoApiFactory.api.createTranscription(
                    CreateTranscriptionRequest(source_url = stableUrl)
                )

                val jobId = response.job_id
                status = "Job successfully queued..."

                while (true) {
                    val job = PianoApiFactory.api.getTranscription(jobId)
                    progress = job.progress

                    status = when (job.status) {
                        "queued" -> "Queued in server pipeline..."
                        "running", "processing" ->
                            if (job.metadata != null) "Transcribing \"${job.metadata.title}\"..."
                            else "AI model transcribing notes..."
                        "completed" -> "Transcription completed."
                        "failed" -> job.error ?: "Failed: Server processing error"
                        else -> job.status
                    }

                    if (job.status == "completed") {
                        status = "Downloading completed MIDI file..."
                        withContext(Dispatchers.IO) {
                            val midiResponse = PianoApiFactory.api.downloadMidi(jobId)
                            MidiStorage.save(context, stableUrl, job.metadata, midiResponse)
                        }
                        progress = 1f
                        val updatedSongs = MidiStorage.list(context)
                        songs = updatedSongs
                        readySong = updatedSongs.firstOrNull { it.videoUrl == stableUrl }
                        status = "Ready to learn!"
                        loadSongs(context)
                        break
                    }
                    if (job.status == "failed") break
                    delay(1000)
                }
            } catch (e: Exception) {
                status = "Error: ${e.localizedMessage ?: "Connection error"}"
            } finally {
                isLoading = false
            }
        }
    }

    private fun normalizeUrl(url: String): String {
        var trimmed = url.trim()
        if (trimmed.isBlank()) return trimmed
        val ytDomain = "y" + "o" + "u" + "t" + "u" + "b" + "e.com"
        val ytShort = "y" + "o" + "u" + "t" + "u.be"
        val lowercase = trimmed.lowercase()
        if (!lowercase.startsWith("http://") && !lowercase.startsWith("https://")) {
            if (lowercase.contains(ytDomain) || lowercase.contains(ytShort)) trimmed = "https://$trimmed"
        }
        try {
            val uri = Uri.parse(trimmed)
            val host = uri.host?.lowercase() ?: ""
            if (host.contains(ytDomain)) {
                val videoId = uri.getQueryParameter("v")
                if (!videoId.isNullOrBlank()) return "https://www.$ytDomain/watch?v=$videoId"
            } else if (host.contains(ytShort)) {
                val videoId = uri.path?.trim('/')?.split('?')?.firstOrNull()?.split('&')?.firstOrNull()
                if (!videoId.isNullOrBlank()) return "https://$ytShort/$videoId"
            }
        } catch (_: Exception) {}
        return trimmed
    }
}
