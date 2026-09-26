package com.example.pianoweave.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pianoweave.api.CreateTranscriptionRequest
import com.example.pianoweave.api.PianoApiFactory
import com.example.pianoweave.api.config.AppConfig
import com.example.pianoweave.midi.MidiStorage
import com.example.pianoweave.midi.StoredMidi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

enum class ServerStatus {
    ONLINE,
    OFFLINE,
    CHECKING,
    UNKNOWN
}

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

    var currentJobId by mutableStateOf<String?>(null)
        private set

    private var transcriptionJob: Job? = null

    var readySong by mutableStateOf<StoredMidi?>(null)
    var activePracticeSong by mutableStateOf<StoredMidi?>(null)
    private var lastPlayedSongPath: String? = null

    var transcriptionError by mutableStateOf<String?>(null)
        private set

    var selectedThemeId by mutableStateOf("gold")
        private set

    fun setSelectedTheme(context: Context, themeId: String) {
        selectedThemeId = themeId
        val prefs = context.getSharedPreferences("piano_weave_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("selected_theme_id", themeId).apply()
    }

    fun clearTranscriptionError() {
        transcriptionError = null
        status = if (videoUrl.isNotBlank()) "Ready to convert." else "Paste a video link above to begin."
        progress = 0f
    }

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

    // --- Server Settings State ---
    var defaultServerUrl by mutableStateOf("")
        private set
    var customServerUrl by mutableStateOf("")
        private set
    var isCustomServer by mutableStateOf(false)
        private set
    var serverStatus by mutableStateOf(ServerStatus.UNKNOWN)
        private set

    val activeServerUrl: String
        get() {
            if (isCustomServer && customServerUrl.isNotBlank()) {
                return if (customServerUrl.endsWith("/")) customServerUrl else "$customServerUrl/"
            }
            return if (defaultServerUrl.isNotBlank()) {
                if (defaultServerUrl.endsWith("/")) defaultServerUrl else "$defaultServerUrl/"
            } else {
                "http://localhost:8000/"
            }
        }

    val isUsingDefaultServer: Boolean
        get() = !isCustomServer || customServerUrl.isBlank() || activeServerUrl == (if (defaultServerUrl.endsWith("/")) defaultServerUrl else "$defaultServerUrl/")

    suspend fun testServerConnection(url: String): ServerStatus = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext ServerStatus.OFFLINE
        try {
            val api = PianoApiFactory.getApi(url)
            val response = api.checkHealth()
            if (response.code() > 0) {
                ServerStatus.ONLINE
            } else {
                ServerStatus.OFFLINE
            }
        } catch (_: Exception) {
            ServerStatus.OFFLINE
        }
    }

    fun checkServerHealth() {
        val url = activeServerUrl
        viewModelScope.launch {
            serverStatus = ServerStatus.CHECKING
            serverStatus = testServerConnection(url)
        }
    }

    fun loadPreferences(context: Context) {
        val prefs = context.getSharedPreferences("piano_weave_prefs", Context.MODE_PRIVATE)
        isStrikeOverlayEnabled = prefs.getBoolean("is_strike_overlay_enabled", true)
        selectedThemeId = prefs.getString("selected_theme_id", "gold") ?: "gold"

        defaultServerUrl = try {
            AppConfig.initialize(context)
            AppConfig.getConfig().baseUrl
        } catch (_: Exception) {
            ""
        }
        isCustomServer = prefs.getBoolean("use_custom_server", false)
        customServerUrl = prefs.getString("custom_server_url", "") ?: ""

        checkServerHealth()
    }

    fun updateServerSettings(context: Context, useCustom: Boolean, customUrl: String) {
        var formattedUrl = customUrl.trim()
        if (formattedUrl.isNotEmpty()) {
            if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
                formattedUrl = "http://$formattedUrl"
            }
            if (!formattedUrl.endsWith("/")) {
                formattedUrl = "$formattedUrl/"
            }
        }
        isCustomServer = useCustom
        customServerUrl = formattedUrl

        val prefs = context.getSharedPreferences("piano_weave_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("use_custom_server", useCustom)
            .putString("custom_server_url", formattedUrl)
            .apply()

        checkServerHealth()
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
            if (url.isBlank()) {
                status = "Paste a video link above to begin."
                progress = 0f
            } else if (!status.startsWith("Error") && !status.startsWith("Failed")) {
                status = "Ready to convert."
            }
        }
    }

    fun loadSongs(context: Context) {
        songs = MidiStorage.list(context)
        loadPreferences(context)
        resumeActiveJobIfAny(context)
    }

    fun deleteSong(context: Context, song: StoredMidi) {
        MidiStorage.delete(context, song)
        loadSongs(context)
        if (activePracticeSong?.file?.absolutePath == song.file.absolutePath) {
            activePracticeSong = null
        }
    }

    var importError by mutableStateOf<String?>(null)
        private set

    fun clearImportError() {
        importError = null
    }

    fun importMidiFile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                MidiStorage.importFile(context, uri)
                withContext(Dispatchers.Main) {
                    loadSongs(context)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    importError = e.message ?: "Failed to import file. Please select a valid MIDI file."
                }
            }
        }
    }

    private fun saveActiveJob(context: Context, jobId: String, url: String) {
        val prefs = context.getSharedPreferences("piano_weave_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("active_job_id", jobId)
            .putString("active_job_url", url)
            .apply()
    }

    private fun clearActiveJob(context: Context) {
        val prefs = context.getSharedPreferences("piano_weave_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("active_job_id")
            .remove("active_job_url")
            .apply()
    }

    fun resumeActiveJobIfAny(context: Context) {
        if (isLoading || transcriptionJob?.isActive == true) return
        val prefs = context.getSharedPreferences("piano_weave_prefs", Context.MODE_PRIVATE)
        val savedJobId = prefs.getString("active_job_id", null)
        val savedUrl = prefs.getString("active_job_url", null)

        if (!savedJobId.isNullOrBlank() && !savedUrl.isNullOrBlank()) {
            videoUrl = savedUrl
            currentJobId = savedJobId
            isLoading = true
            status = "Resuming transcription job..."

            transcriptionJob = viewModelScope.launch {
                pollTranscriptionJob(context, savedJobId, savedUrl)
            }
        }
    }

    fun cancelTranscription(context: Context) {
        val jobId = currentJobId
        transcriptionJob?.cancel()
        transcriptionJob = null

        if (jobId != null) {
            val serverUrl = activeServerUrl
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val api = PianoApiFactory.getApi(serverUrl)
                    api.deleteTranscription(jobId)
                } catch (_: Exception) {}
            }
        }

        currentJobId = null
        isLoading = false
        progress = 0f
        status = "Transcription cancelled."
        transcriptionError = null
        clearActiveJob(context)
    }

    private suspend fun pollTranscriptionJob(context: Context, jobId: String, stableUrl: String) {
        val api = PianoApiFactory.getApi(activeServerUrl)
        try {
            while (true) {
                val job = api.getTranscription(jobId)
                progress = job.progress

                status = when (job.status) {
                    "queued" -> "Queued in server pipeline..."
                    "running", "processing" ->
                        if (job.metadata != null) "Transcribing \"${job.metadata.title}\"..."
                        else "AI model transcribing notes..."
                    "completed" -> "Transcription completed."
                    "failed" -> {
                        val errMsg = job.error ?: "Failed: Server processing error"
                        transcriptionError = errMsg
                        "Error: $errMsg"
                    }
                    else -> job.status
                }

                if (job.status == "completed") {
                    status = "Downloading completed MIDI file..."
                    withContext(Dispatchers.IO) {
                        val midiResponse = api.downloadMidi(jobId)
                        MidiStorage.save(context, stableUrl, job.metadata, midiResponse)
                    }
                    progress = 1f
                    val updatedSongs = MidiStorage.list(context)
                    songs = updatedSongs
                    readySong = updatedSongs.firstOrNull { it.videoUrl == stableUrl }
                    status = "Ready to learn!"
                    transcriptionError = null
                    clearActiveJob(context)
                    currentJobId = null
                    break
                }
                if (job.status == "failed") {
                    clearActiveJob(context)
                    currentJobId = null
                    break
                }
                delay(1000)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val errMsg = e.localizedMessage ?: "Connection error"
            transcriptionError = errMsg
            status = "Error: $errMsg"
            clearActiveJob(context)
            currentJobId = null
        } finally {
            isLoading = false
        }
    }

    fun startTranscription(context: Context) {
        val stableUrl = normalizeUrl(videoUrl)
        if (stableUrl.isBlank() || isLoading) return
        
        videoUrl = stableUrl
        transcriptionError = null

        transcriptionJob = viewModelScope.launch {
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
                val api = PianoApiFactory.getApi(activeServerUrl)
                val response = api.createTranscription(
                    CreateTranscriptionRequest(source_url = stableUrl)
                )

                val jobId = response.job_id
                currentJobId = jobId
                saveActiveJob(context, jobId, stableUrl)
                status = "Job successfully queued..."

                pollTranscriptionJob(context, jobId, stableUrl)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val errMsg = e.localizedMessage ?: "Connection error"
                transcriptionError = errMsg
                status = "Error: $errMsg"
                clearActiveJob(context)
                currentJobId = null
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
