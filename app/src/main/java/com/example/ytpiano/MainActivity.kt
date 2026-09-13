package com.example.ytpiano

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    PianoLearnerScreen()
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun PianoLearnerScreen() {
    var youtubeUrl by remember { mutableStateOf("") }

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
            modifier = Modifier.padding(top = 24.dp)
        )

        Button(
            onClick = {
                // We'll connect this to the server next.
            },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Transcribe")
        }

        Text(
            text = "Ready",
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}