package com.lumenchord.pianoweave.ui.screens.learn

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lumenchord.pianoweave.midi.StoredMidi
import com.lumenchord.pianoweave.ui.screens.learn.cards.TranscriptionErrorCard as ExternalErrorCard
import com.lumenchord.pianoweave.ui.screens.learn.cards.TranscriptionInputCard as ExternalInputCard
import com.lumenchord.pianoweave.ui.screens.learn.cards.TranscriptionProgressCard as ExternalProgressCard
import com.lumenchord.pianoweave.ui.screens.learn.cards.TranscriptionSuccessCard as ExternalSuccessCard
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel

@Composable
internal fun TranscriptionSuccessCard(
    song: StoredMidi,
    onSongSelect: (StoredMidi) -> Unit
) = ExternalSuccessCard(song, onSongSelect)

@Composable
internal fun TranscriptionInputCard(
    viewModel: PianoWeaveViewModel,
    context: Context,
    onOpenServerSettings: () -> Unit
) = ExternalInputCard(viewModel, context, onOpenServerSettings)

@Composable
internal fun TranscriptionProgressCard(
    viewModel: PianoWeaveViewModel,
    progress: Float,
    status: String,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) = ExternalProgressCard(viewModel, progress, status, onCancelClick, modifier)

@Composable
internal fun TranscriptionErrorCard(
    errorMessage: String,
    onErrorDismiss: () -> Unit
) = ExternalErrorCard(errorMessage, onErrorDismiss)
