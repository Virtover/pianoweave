package com.example.pianoweave.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pianoweave.midi.StoredMidi
import com.example.pianoweave.ui.screens.learn.*
import com.example.pianoweave.ui.viewmodel.PianoWeaveViewModel

@Composable
fun LearnScreen(
    viewModel: PianoWeaveViewModel,
    context: Context,
    onSongSelect: (StoredMidi) -> Unit = {}
) {
    var showServerSettingsDialog by remember { mutableStateOf(false) }
    var showCancelConfirmDialog by remember { mutableStateOf(false) }

    fun handleCancelClick() {
        if (viewModel.progress > 0.05f) {
            showCancelConfirmDialog = true
        } else {
            viewModel.cancelTranscription(context)
        }
    }

    if (showCancelConfirmDialog) {
        CancelTranscriptionDialog(
            progressPercent = (viewModel.progress * 100).toInt(),
            onConfirmCancel = {
                showCancelConfirmDialog = false
                viewModel.cancelTranscription(context)
            },
            onDismiss = { showCancelConfirmDialog = false }
        )
    }

    if (showServerSettingsDialog) {
        ServerSettingsDialog(
            viewModel = viewModel,
            context = context,
            onDismiss = { showServerSettingsDialog = false }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. SUCCESS HERO - High visibility practicing prompt
            AnimatedVisibility(
                visible = !viewModel.isLoading && viewModel.readySong != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                viewModel.readySong?.let { song ->
                    TranscriptionSuccessCard(
                        song = song,
                        onSongSelect = onSongSelect
                    )
                }
            }

            // 2. INPUT CARD - Primary conversion entry
            TranscriptionInputCard(
                viewModel = viewModel,
                context = context,
                onOpenServerSettings = { showServerSettingsDialog = true },
                onCancelClick = { handleCancelClick() }
            )

            // 3. PROGRESS CARD - Active loading feedback
            AnimatedVisibility(visible = viewModel.isLoading) {
                TranscriptionProgressCard(
                    progress = viewModel.progress,
                    status = viewModel.status
                )
            }

            // 4. ERROR CARD - Persistent error feedback (dismissible)
            AnimatedVisibility(
                visible = !viewModel.isLoading && (viewModel.transcriptionError != null || viewModel.status.startsWith("Error")),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                val errorMsg = viewModel.transcriptionError ?: viewModel.status.removePrefix("Error: ")
                TranscriptionErrorCard(
                    errorMessage = errorMsg,
                    onErrorDismiss = { viewModel.clearTranscriptionError() }
                )
            }
        }
    }
}
