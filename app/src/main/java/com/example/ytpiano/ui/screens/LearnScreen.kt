package com.example.ytpiano.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ytpiano.midi.StoredMidi
import com.example.ytpiano.ui.viewmodel.PianoWeaveViewModel

@Composable
fun LearnScreen(
    viewModel: PianoWeaveViewModel,
    context: Context,
    onSongSelect: (StoredMidi) -> Unit = {}
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main New Conversion Widget Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "New Audio Conversion",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.secondary
                    )

                    OutlinedTextField(
                        value = viewModel.videoUrl,
                        onValueChange = {
                            viewModel.updateUrl(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text("Video URL")
                        },
                        placeholder = {
                            Text(
                                "https://www.video-platform.com/watch?v=..."
                            )
                        },
                        singleLine = true,
                        enabled = !viewModel.isLoading,
                        leadingIcon = {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        },
                        trailingIcon = {
                            if (
                                viewModel.videoUrl.isNotEmpty() &&
                                !viewModel.isLoading
                            ) {
                                IconButton(
                                    onClick = {
                                        viewModel.updateUrl("")
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear"
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor =
                                MaterialTheme.colorScheme.secondary,
                            cursorColor =
                                MaterialTheme.colorScheme.secondary
                        )
                    )

                    Button(
                        onClick = {
                            viewModel.startTranscription(context)
                        },
                        enabled =
                            viewModel.videoUrl.isNotBlank() &&
                                    !viewModel.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor =
                                MaterialTheme.colorScheme.secondary,
                            contentColor =
                                MaterialTheme.colorScheme.onSecondary,
                            disabledContainerColor =
                                MaterialTheme.colorScheme.primaryContainer,
                            disabledContentColor =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant
                                    .copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = if (viewModel.isLoading) {
                                "Transcribing..."
                            } else {
                                "Convert to MIDI"
                            },
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            // Real-Time Live Status/Progress Feed Card
            val showStatusCard =
                viewModel.isLoading ||
                        viewModel.progress > 0f ||
                        viewModel.status.contains(
                            "ready",
                            ignoreCase = true
                        ) ||
                        viewModel.status.contains(
                            "Error",
                            ignoreCase = true
                        ) ||
                        viewModel.status.contains(
                            "Loaded",
                            ignoreCase = true
                        )

            if (showStatusCard) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.spacedBy(12.dp)
                    ) {
                        if (
                            viewModel.isLoading &&
                            viewModel.progress < 1f &&
                            !viewModel.status.contains("Error")
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color =
                                    MaterialTheme.colorScheme.secondary,
                                strokeWidth = 3.dp
                            )
                        } else {
                            val (icon, color) = when {
                                viewModel.status.contains("Error") ||
                                        viewModel.status.contains("Failed") ->
                                    Icons.Default.Error to
                                            MaterialTheme.colorScheme.error

                                viewModel.progress >= 1f ->
                                    Icons.Default.CheckCircle to
                                            MaterialTheme.colorScheme.secondary

                                else ->
                                    Icons.Default.Info to
                                            MaterialTheme.colorScheme
                                                .onSurfaceVariant
                            }

                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.SpaceBetween,
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Processing Feed",
                                    style =
                                        MaterialTheme.typography
                                            .bodySmall
                                            .copy(
                                                fontWeight =
                                                    FontWeight.SemiBold
                                            ),
                                    color =
                                        MaterialTheme.colorScheme
                                            .onSurfaceVariant
                                )

                                if (viewModel.progress > 0f) {
                                    Text(
                                        text =
                                            "${(viewModel.progress * 100)
                                                .toInt()}%",
                                        style =
                                            MaterialTheme.typography
                                                .bodySmall
                                                .copy(
                                                    fontWeight =
                                                        FontWeight.Bold
                                                ),
                                        color =
                                            MaterialTheme.colorScheme
                                                .secondary
                                    )
                                }
                            }

                            Spacer(
                                modifier = Modifier.height(2.dp)
                            )

                            Text(
                                text = viewModel.status,
                                style =
                                    MaterialTheme.typography.bodyMedium
                                        .copy(
                                            fontWeight =
                                                FontWeight.Medium
                                        ),
                                color =
                                    MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Progress bar while processing.
                            if (
                                viewModel.isLoading &&
                                viewModel.progress < 1f
                            ) {
                                Spacer(
                                    modifier = Modifier.height(6.dp)
                                )

                                LinearProgressIndicator(
                                    progress = {
                                        viewModel.progress
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    color =
                                        MaterialTheme.colorScheme
                                            .secondary,
                                    trackColor =
                                        MaterialTheme.colorScheme
                                            .primaryContainer
                                )
                            }

                            // Replace the progress bar with the
                            // learning button when finished.
                            if (
                                !viewModel.isLoading &&
                                viewModel.progress >= 1f &&
                                viewModel.readySong != null
                            ) {
                                Spacer(
                                    modifier = Modifier.height(8.dp)
                                )

                                Button(
                                    onClick = {
                                        viewModel.readySong?.let { song ->
                                            onSongSelect(song)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor =
                                                MaterialTheme.colorScheme
                                                    .secondary,
                                            contentColor =
                                                MaterialTheme.colorScheme
                                                    .onSecondary
                                        )
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )

                                    Spacer(
                                        modifier =
                                            Modifier.width(8.dp)
                                    )

                                    Text(
                                        text = "Start learning",
                                        style =
                                            MaterialTheme.typography
                                                .labelLarge
                                                .copy(
                                                    fontWeight =
                                                        FontWeight.Bold
                                                )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}