package com.example.ytpiano

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ytpiano.api.CreateTranscriptionRequest
import com.example.ytpiano.api.PianoApiFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.ytpiano.midi.MidiStorage
import kotlin.time.Duration.Companion.milliseconds
import android.content.Context

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    PianoLearnerScreen(applicationContext)
                }
            }
        }
    }
}

@Composable
private fun PianoLearnerScreen(context: Context) {
    var youtubeUrl by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready") }
    var progress by remember { mutableStateOf(0f) }
    var isLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Piano Learner",
            style = MaterialTheme.typography.headlineMedium
        )

        OutlinedTextField(
            value = youtubeUrl,
            onValueChange = { youtubeUrl = it },
            label = {
                Text("YouTube URL")
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            singleLine = true,
            enabled = !isLoading
        )

        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    progress = 0f
                    status = "Submitting..."

                    try {
                        val response = PianoApiFactory.api
                            .createTranscription(
                                CreateTranscriptionRequest(
                                    youtube_url = youtubeUrl
                                )
                            )

                        val jobId = response.job_id

                        status = "Queued"

                        while (true) {
                            val job = PianoApiFactory.api
                                .getTranscription(jobId)

                            progress = job.progress

                            status = when (job.status) {
                                "queued" -> "Queued"
                                "running" -> "Transcribing..."
                                "completed" -> "Completed"
                                "failed" -> {
                                    "Failed: ${job.error ?: "Unknown error"}"
                                }
                                else -> job.status
                            }

                            if (job.status == "completed") {
                                status = "Downloading MIDI..."

                                val midiResponse = PianoApiFactory.api
                                    .downloadMidi(jobId)

                                val midiFile = MidiStorage.save(
                                    context,
                                    jobId,
                                    midiResponse
                                )

                                progress = 1f
                                status = "Ready: ${midiFile.name}"

                                break
                            }

                            if (job.status == "failed") {
                                break
                            }

                            delay(1000.milliseconds)
                        }
                    } catch (e: Exception) {
                        status = "Error: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier
                .padding(top = 16.dp),
            enabled = youtubeUrl.isNotBlank() && !isLoading
        ) {
            Text("Transcribe")
        }

        if (isLoading) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            )

            Text(
                text = "${(progress * 100).toInt()}%",
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Text(
            text = status,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}