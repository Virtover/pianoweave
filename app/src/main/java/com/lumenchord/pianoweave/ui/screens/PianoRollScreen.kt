package com.lumenchord.pianoweave.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.lumenchord.pianoweave.midi.MidiNoteEvent
import com.lumenchord.pianoweave.midi.SimpleMidiReader
import com.lumenchord.pianoweave.midi.StoredMidi
import com.lumenchord.pianoweave.midi.SustainPedalSpan
import com.lumenchord.pianoweave.ui.screens.pianoroll.*
import com.lumenchord.pianoweave.ui.components.RibbonLoader
import com.lumenchord.pianoweave.ui.theme.DisplayFontFamily
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PianoRollScreen(
    song: StoredMidi,
    viewModel: PianoWeaveViewModel,
    onBack: () -> Unit
) {
    var noteEvents by remember(song) { mutableStateOf<List<MidiNoteEvent>?>(null) }
    var sustainPedal by remember(song) { mutableStateOf<List<SustainPedalSpan>>(emptyList()) }
    var parseError by remember(song) { mutableStateOf<String?>(null) }

    // System back leaves the song like the toolbar's back arrow instead of closing the app. While the
    // UI bars are hidden, the player's own handler (registered later, so it wins) shows them first.
    BackHandler(onBack = onBack)

    LaunchedEffect(song.file.absolutePath) {
        viewModel.isWaitModeEnabled = false
        noteEvents = null
        parseError = null
        try {
            val parsedSong = withContext(Dispatchers.IO) {
                SimpleMidiReader.parseSong(song.file)
            }
            val parsed = parsedSong.notes
            sustainPedal = parsedSong.sustainPedal
            noteEvents = parsed
            val duration = (parsed.maxOfOrNull { it.startMs + it.durationMs } ?: 10_000L) + 5000L
            if (viewModel.loopEndMs == 0L) {
                viewModel.loopEndMs = duration
            }
        } catch (e: Exception) {
            parseError = e.message?.takeIf { it.isNotBlank() } ?: "MIDI loading failed"
        }
    }

    if (parseError != null) {
        MidiErrorScreen(song, parseError!!, onBack)
    } else if (noteEvents == null) {
        MidiLoadingScreen()
    } else {
        ModernPianoPlayerContent(song, noteEvents!!, sustainPedal, viewModel, onBack)
    }
}
@Composable
private fun MidiLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RibbonLoader(size = 180.dp)
            Text(
                text = "Orchestrating performance...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 0.08.em
                )
            )
        }
    }
}
@Composable
private fun MidiErrorScreen(song: StoredMidi, error: String, onBack: () -> Unit) {
    val appTheme = LocalAppTheme.current
    val ColorBaseline = appTheme.baselineColor
    val ColorTextDim = Color(0xFF8B949E)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = ColorBaseline,
                modifier = Modifier.size(72.dp)
            )
            Text(
                text = "Midi Compatibility Error",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = song.metadata.title,
                color = ColorTextDim,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = ColorSurface)
            ) {
                Text("Return to Library")
            }
        }
    }
}
