package com.lumenchord.pianoweave.ui.screens

import androidx.activity.compose.BackHandler
import com.lumenchord.pianoweave.ui.theme.Ink
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.lumenchord.pianoweave.midi.Beat
import com.lumenchord.pianoweave.midi.MidiNoteEvent
import com.lumenchord.pianoweave.midi.SimpleMidiReader
import androidx.compose.ui.platform.LocalContext
import com.lumenchord.pianoweave.midi.withoutDrumsUnless
import com.lumenchord.pianoweave.midi.StoredMidi
import com.lumenchord.pianoweave.midi.SustainPedalSpan
import com.lumenchord.pianoweave.ui.screens.pianoroll.*
import com.lumenchord.pianoweave.ui.components.RibbonLoader
import com.lumenchord.pianoweave.ui.theme.DisplayFontFamily
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import kotlinx.coroutines.Dispatchers
import com.lumenchord.pianoweave.midi.MidiStorage
import com.lumenchord.pianoweave.midi.MidiVariants
import kotlinx.coroutines.withContext

@Composable
fun PianoRollScreen(
    song: StoredMidi,
    viewModel: PianoWeaveViewModel,
    onBack: () -> Unit
) {
    var allNotes by remember(song) { mutableStateOf<List<MidiNoteEvent>?>(null) }
    // The debug drum switch applies to the open song at once, without reading the file again
    val includeDrums = viewModel.includeDrumNotes
    val noteEvents = remember(allNotes, includeDrums) { allNotes?.let { withoutDrumsUnless(it, includeDrums) } }
    var sustainPedal by remember(song) { mutableStateOf<List<SustainPedalSpan>>(emptyList()) }
    var beats by remember(song) { mutableStateOf<List<Beat>>(emptyList()) }
    var parseError by remember(song) { mutableStateOf<String?>(null) }

    // System back leaves the song like the toolbar's back arrow instead of closing the app. While the
    // UI bars are hidden, the player's own handler (registered later, so it wins) shows them first.
    BackHandler(onBack = onBack)

    // Hands are on the piano, not the screen: keep it awake for as long as the roll is open (also while
    // paused or waiting for a note in wait mode). Cleared on leaving, so other screens sleep normally.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // The song's results by name (an ensemble song's difficulty levels); empty for a plain MIDI file
    var variants by remember(song) { mutableStateOf<Map<String, ByteArray>?>(null) }
    val context = LocalContext.current

    LaunchedEffect(song.file.absolutePath) {
        viewModel.isWaitModeEnabled = false
        allNotes = null
        parseError = null
        viewModel.songVariants = emptyList()
        viewModel.selectedVariant = null
        try {
            val (found, stored) = withContext(Dispatchers.IO) {
                MidiVariants.read(song.file) to MidiStorage.readSelectedVariant(context, song)
            }
            viewModel.songVariants = found.keys.toList()
            viewModel.selectedVariant = MidiVariants.choose(stored, found.keys.toList())
            variants = found
        } catch (e: Exception) {
            parseError = e.message?.takeIf { it.isNotBlank() } ?: "MIDI loading failed"
        }
    }

    // Opening the song, and each change of difficulty: the notes are swapped in place, so the playhead stays
    // where it is (within the new length) and the settings dialog stays open
    val variant = viewModel.selectedVariant
    LaunchedEffect(variants, variant) {
        val found = variants ?: return@LaunchedEffect
        try {
            val parsedSong = withContext(Dispatchers.IO) {
                if (found.isEmpty()) SimpleMidiReader.parseSong(song.file)
                else SimpleMidiReader.parseSong(found.getValue(MidiVariants.choose(variant, found.keys.toList())!!).inputStream())
            }
            val parsed = parsedSong.notes
            val duration = (parsed.maxOfOrNull { it.startMs + it.durationMs } ?: 10_000L) + 5000L
            val previous = allNotes?.let { (it.maxOfOrNull { n -> n.startMs + n.durationMs } ?: 10_000L) + 5000L }
            sustainPedal = parsedSong.sustainPedal
            beats = parsedSong.beats
            allNotes = parsed
            // A loop end left at the old song's end moves to the new one's; a loop set by hand stays
            if (viewModel.loopEndMs == 0L || (previous != null && viewModel.loopEndMs == previous)) {
                viewModel.loopEndMs = duration
            }
            viewModel.loopStartMs = viewModel.loopStartMs.coerceAtMost(duration)
            viewModel.playheadMs = viewModel.playheadMs.coerceIn(0L, duration)
        } catch (e: Exception) {
            parseError = e.message?.takeIf { it.isNotBlank() } ?: "MIDI loading failed"
        }
    }

    if (parseError != null) {
        MidiErrorScreen(song, parseError!!, onBack)
    } else if (noteEvents == null) {
        MidiLoadingScreen()
    } else {
        ModernPianoPlayerContent(song, noteEvents!!, sustainPedal, beats, viewModel, onBack)
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
                color = Ink,
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
