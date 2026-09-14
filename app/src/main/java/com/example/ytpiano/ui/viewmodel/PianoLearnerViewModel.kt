package com.example.ytpiano.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytpiano.api.CreateTranscriptionRequest
import com.example.ytpiano.api.PianoApiFactory
import com.example.ytpiano.midi.MidiStorage
import com.example.ytpiano.midi.StoredMidi
import com.google.gson.JsonParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class PianoLearnerViewModel : ViewModel() {

    var youtubeUrl by mutableStateOf("")
        private set

    var status by mutableStateOf("Paste a YouTube piano video link above to begin.")
        private set

    var progress by mutableFloatStateOf(0f)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var songs by mutableStateOf<List<StoredMidi>>(emptyList())
        private set

    var activePracticeSong by mutableStateOf<StoredMidi?>(null)

    fun updateUrl(url: String) {
        if (!isLoading) {
            youtubeUrl = url
            status = "Ready to convert."
            progress = 0f
        }
    }

    fun loadSongs(context: Context) {
        songs = MidiStorage.list(context)
    }

    fun deleteSong(context: Context, song: StoredMidi) {
        MidiStorage.delete(context, song)
        loadSongs(context)
        if (activePracticeSong?.file?.absolutePath == song.file.absolutePath) {
            activePracticeSong = null
        }
    }

    fun startTranscription(context: Context) {
        val stableUrl = youtubeUrl.trim()
        if (stableUrl.isBlank() || isLoading) return

        viewModelScope.launch {
            isLoading = true
            progress = 0f
            status = "Checking local cache..."

            try {
                val storedFile = MidiStorage.find(context, stableUrl)
                if (storedFile != null) {
                    progress = 1f
                    status = "Loaded from local library cache!"
                    isLoading = false
                    loadSongs(context)
                    return@launch
                }

                status = "Submitting request to server..."
                val response = PianoApiFactory.api.createTranscription(
                    CreateTranscriptionRequest(youtube_url = stableUrl)
                )

                val jobId = response.job_id
                status = "Job successfully queued..."

                while (true) {
                    val job = PianoApiFactory.api.getTranscription(jobId)
                    progress = job.progress

                    val serverError = job.error
                    status = when (job.status) {
                        "queued" -> "Queued in server pipeline..."
                        "running", "processing" ->
                            if (job.title != null) {
                                "Transcribing \"${job.title}\"..."
                            } else {
                                "AI model transcribing notes..."
                            }
                        "completed" -> "Transcription completed."
                        "failed" -> {
                            if (!job.error.isNullOrBlank()) {
                                "Failed: ${job.error}"
                            } else {
                                "Failed: Server processing error"
                            }
                        }
                        else -> job.status
                    }

                    if (job.status == "completed") {
                        status = "Downloading completed MIDI file..."

                        withContext(Dispatchers.IO) {
                            val midiResponse =
                                PianoApiFactory.api.downloadMidi(jobId)

                            MidiStorage.save(
                                context = context,
                                youtubeUrl = stableUrl,
                                songTitle = job.title,
                                body = midiResponse
                            )
                        }

                        progress = 1f
                        status = "Transcription ready and saved!"
                        loadSongs(context)
                        break
                    }

                    if (job.status == "failed") {
                        break
                    }

                    delay(1000)
                }
            } catch (e: Exception) {
                val exceptionMessage = e.localizedMessage
                if (exceptionMessage != null && exceptionMessage.trim().lowercase() != "null" && exceptionMessage.isNotBlank()) {
                    status = "Error: $exceptionMessage"
                } else {
                    status = "Network or connection error occurred."
                }
            } finally {
                isLoading = false
            }
        }
    }
}
