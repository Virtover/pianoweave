package com.lumenchord.pianoweave.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lumenchord.pianoweave.audio.AcousticNoteDetector
import com.lumenchord.pianoweave.audio.PianoPlayer
import com.lumenchord.pianoweave.midi.MidiInputManager
import com.lumenchord.pianoweave.midi.MidiNoteEvent
import com.lumenchord.pianoweave.midi.SimpleMidiReader
import com.lumenchord.pianoweave.midi.StoredMidi
import com.lumenchord.pianoweave.ui.screens.pianoroll.*
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
fun PianoRollScreen(
    song: StoredMidi,
    viewModel: PianoWeaveViewModel,
    onBack: () -> Unit
) {
    var noteEvents by remember(song) { mutableStateOf<List<MidiNoteEvent>?>(null) }
    var parseError by remember(song) { mutableStateOf<String?>(null) }

    LaunchedEffect(song.file.absolutePath) {
        viewModel.isWaitModeEnabled = false
        noteEvents = null
        parseError = null
        try {
            val parsed = withContext(Dispatchers.IO) {
                SimpleMidiReader.parse(song.file)
            }
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
        ModernPianoPlayerContent(song, noteEvents!!, viewModel, onBack)
    }
}

@Composable
private fun MidiLoadingScreen() {
    val appTheme = LocalAppTheme.current
    val ColorGold = appTheme.primaryColor
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = ColorGold,
                strokeWidth = 4.dp,
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = "Orchestrating performance...",
                color = ColorGold,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ModernPianoPlayerContent(
    song: StoredMidi,
    rawEvents: List<MidiNoteEvent>,
    viewModel: PianoWeaveViewModel,
    onBack: () -> Unit
) {
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isUserSeeking by remember { mutableStateOf(false) }

    val startPitch = 21
    val endPitch = 108
    val totalWhiteKeys = remember { (startPitch..endPitch).count { !isPitchBlack(it) } }

    val noteEvents = remember(rawEvents, viewModel.transposeOffset) {
        rawEvents.map { it.copy(pitch = it.pitch + viewModel.transposeOffset) }
    }

    val songDurationMs = remember(noteEvents) {
        (noteEvents.maxOfOrNull { it.startMs + it.durationMs } ?: 10_000L) + 5000L
    }

    LaunchedEffect(songDurationMs) {
        if (viewModel.loopEndMs == 0L) viewModel.loopEndMs = songDurationMs
    }

    var lastTriggeredHeadMs by remember { mutableLongStateOf(-1L) }
    var currentWaitOnsetMs by remember { mutableLongStateOf(-1L) }
    var arrivalAtWaitPointRealTime by remember { mutableLongStateOf(0L) }
    var lastCompletedWaitOnsetMs by remember { mutableLongStateOf(-1L) }

    val chordHits = remember { mutableStateSetOf<Int>() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        PianoPlayer.initialize(context)
    }

    DisposableEffect(lifecycleOwner, viewModel.isWaitModeEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (viewModel.isWaitModeEnabled && !MidiInputManager.isMidiDeviceConnected()) {
                    if (!AcousticNoteDetector.start(context)) {
                        viewModel.isWaitModeEnabled = false
                    }
                }
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                AcousticNoteDetector.stop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (viewModel.isWaitModeEnabled && !MidiInputManager.isMidiDeviceConnected()) {
            if (!AcousticNoteDetector.start(context)) {
                viewModel.isWaitModeEnabled = false
            }
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            AcousticNoteDetector.targetPitches = emptySet()
            AcousticNoteDetector.stop()
        }
    }

    val sustainedPitches = remember(noteEvents, viewModel.playheadMs, viewModel.isPlaying, viewModel.isWaitModeEnabled, currentWaitOnsetMs) {
        val waitTargetPitches = if (viewModel.isWaitModeEnabled && currentWaitOnsetMs != -1L) {
            noteEvents.filter { abs(it.startMs - currentWaitOnsetMs) <= 30L }.map { it.pitch }.toSet()
        } else {
            emptySet()
        }

        noteEvents.filter {
            viewModel.playheadMs >= it.startMs && viewModel.playheadMs <= (it.startMs + it.durationMs)
        }.map { it.pitch }.toSet() - waitTargetPitches
    }

    LaunchedEffect(sustainedPitches) {
        AcousticNoteDetector.suppressedPitches = emptySet()
    }

    LaunchedEffect(viewModel.isPlaying, isUserSeeking) {
        if (!viewModel.isPlaying || isUserSeeking) PianoPlayer.stopAllNotes()
    }

    // Reset wait state if mode toggles
    LaunchedEffect(viewModel.isWaitModeEnabled) {
        if (!viewModel.isWaitModeEnabled) {
            currentWaitOnsetMs = -1L
            lastCompletedWaitOnsetMs = -1L
            chordHits.clear()
        }
    }

    val nextRequiredOnset = remember(noteEvents, viewModel.playheadMs) {
        noteEvents.filter { it.startMs >= viewModel.playheadMs }.minOfOrNull { it.startMs }
    }

    val notesToStrike = remember(noteEvents, nextRequiredOnset) {
        if (nextRequiredOnset == null) emptySet()
        else noteEvents.filter { abs(it.startMs - nextRequiredOnset) <= 30L }.map { it.pitch }.toSet()
    }

    LaunchedEffect(notesToStrike, viewModel.isWaitModeEnabled, viewModel.isPlaying) {
        AcousticNoteDetector.targetPitches = if (viewModel.isWaitModeEnabled && viewModel.isPlaying) notesToStrike else emptySet()
    }

    LaunchedEffect(viewModel.isPlaying, viewModel.speedMultiplier, viewModel.isWaitModeEnabled, noteEvents) {
        if (!viewModel.isPlaying) return@LaunchedEffect

        val resumed = noteEvents.filter { viewModel.playheadMs >= it.startMs && viewModel.playheadMs < (it.startMs + it.durationMs) }
        resumed.forEach { PianoPlayer.noteOn(it.pitch, it.velocity) }

        lastTriggeredHeadMs = viewModel.playheadMs
        var lastTime = System.nanoTime()

        while (viewModel.isPlaying) {
            val now = System.nanoTime()
            val dt = (now - lastTime) / 1_000_000L
            lastTime = now

            val targetNext = viewModel.playheadMs + (dt * viewModel.speedMultiplier).toLong()

            if (viewModel.isWaitModeEnabled) {
                val upcoming = noteEvents.filter {
                    it.startMs >= viewModel.playheadMs &&
                            it.startMs <= targetNext &&
                            abs(it.startMs - lastCompletedWaitOnsetMs) > 30L
                }.minOfOrNull { it.startMs }

                if (upcoming != null) {
                    val required = noteEvents.filter { abs(it.startMs - upcoming) <= 30L && it.pitch in startPitch..endPitch }.map { it.pitch }.toSet()

                    if (currentWaitOnsetMs != upcoming) {
                        currentWaitOnsetMs = upcoming
                        arrivalAtWaitPointRealTime = System.currentTimeMillis()
                    }

                    val currentPresses = required.mapNotNull { p ->
                        val lastPress = MidiInputManager.lastPressTimestamps[p] ?: 0L
                        val lastConsumed = MidiInputManager.consumedPressTimestamps[p] ?: 0L
                        if (lastPress >= arrivalAtWaitPointRealTime - 300L && lastPress > lastConsumed) {
                            p to lastPress
                        } else {
                            null
                        }
                    }.toMap()

                    chordHits.clear()
                    chordHits.addAll(currentPresses.keys)

                    val isChordSatisfied = required.isNotEmpty() &&
                            currentPresses.size == required.size &&
                            (currentPresses.values.max() - currentPresses.values.min()) <= 1500L

                    if (!isChordSatisfied && required.isNotEmpty()) {
                        val currentTime = System.currentTimeMillis()
                        currentPresses.filterValues { it < currentTime - 1500L }.keys.forEach(MidiInputManager::simulateExternalNoteOff)
                        viewModel.playheadMs = upcoming
                        delay(10)
                        continue
                    } else {
                        required.forEach { p ->
                            MidiInputManager.consumedPressTimestamps[p] = MidiInputManager.lastPressTimestamps[p] ?: 0L
                        }
                        lastCompletedWaitOnsetMs = upcoming
                        currentWaitOnsetMs = -1L
                        chordHits.clear()
                        viewModel.playheadMs = upcoming + 35L
                        lastTriggeredHeadMs = upcoming + 35L
                    }
                }
            }

            val next = targetNext
            if (abs(next - lastTriggeredHeadMs) < 500L) {
                val triggered = noteEvents.filter { it.startMs > lastTriggeredHeadMs && it.startMs <= next }
                triggered.forEach { PianoPlayer.noteOn(it.pitch, it.velocity) }
            }
            lastTriggeredHeadMs = next

            if (viewModel.isLoopingEnabled && next >= viewModel.loopEndMs) {
                viewModel.playheadMs = viewModel.loopStartMs
                lastTriggeredHeadMs = viewModel.loopStartMs
                PianoPlayer.stopAllNotes()
                val loopNotes = noteEvents.filter { viewModel.loopStartMs >= it.startMs && viewModel.loopStartMs < (it.startMs + it.durationMs) }
                loopNotes.forEach { PianoPlayer.noteOn(it.pitch, it.velocity) }
            } else if (!viewModel.isLoopingEnabled && next >= songDurationMs) {
                viewModel.playheadMs = songDurationMs
                viewModel.isPlaying = false
            } else {
                if (viewModel.playheadMs < next) {
                    viewModel.playheadMs = next
                }
            }
            delay(10)
        }
    }

    val isWaitingAtBaseline = viewModel.isPlaying && viewModel.isWaitModeEnabled && currentWaitOnsetMs != -1L
    val waitTargetPitches = if (isWaitingAtBaseline) notesToStrike else emptySet()
    var seekInfo by remember { mutableStateOf<SeekInfo?>(null) }
    var isUiHidden by remember { mutableStateOf(false) }

    BackHandler(enabled = isUiHidden) {
        isUiHidden = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .pointerInput(songDurationMs, isUiHidden) {
                    val scale = 0.25f
                    val maxDist = 35.dp.toPx()
                    detectPianoRollGestures(
                        maxDistance = maxDist,
                        onTap = {
                            viewModel.isPlaying = !viewModel.isPlaying
                        },
                        onDoubleTap = { offset ->
                            val isLeft = offset.x < size.width / 2f
                            PianoPlayer.stopAllNotes()
                            currentWaitOnsetMs = -1L
                            lastCompletedWaitOnsetMs = -1L
                            chordHits.clear()
                            viewModel.playheadMs = calculateSeekPosition(
                                currentMs = viewModel.playheadMs,
                                deltaMs = if (isLeft) -5000L else 5000L,
                                songDurationMs = songDurationMs,
                                isLoopingEnabled = viewModel.isLoopingEnabled,
                                loopStartMs = viewModel.loopStartMs,
                                loopEndMs = viewModel.loopEndMs
                            )
                            seekInfo = SeekInfo(if (isLeft) SeekDirection.BACKWARD else SeekDirection.FORWARD)
                        },
                        onDragStart = {
                            viewModel.isPlaying = false
                            PianoPlayer.stopAllNotes()
                            currentWaitOnsetMs = -1L
                            chordHits.clear()
                        },
                        onVerticalDrag = { dy ->
                            val deltaMs = (dy / scale).toLong()
                            val newHead = (viewModel.playheadMs + deltaMs).coerceIn(0L, songDurationMs)
                            viewModel.playheadMs = newHead
                            lastTriggeredHeadMs = newHead
                        }
                    )
                }
        ) {
            FallingNotesVisualizer(
                events = noteEvents,
                head = viewModel.playheadMs,
                start = startPitch,
                numWhiteKeys = totalWhiteKeys,
                waitTargetPitches = waitTargetPitches,
                satisfiedPitches = emptySet()
            )

            if (!isUiHidden) {
                ModernToolbar(
                    head = viewModel.playheadMs,
                    dur = songDurationMs,
                    viewModel = viewModel,
                    onBack = onBack,
                    onSetClick = {
                        viewModel.isPlaying = false
                        showSettingsDialog = true
                    }
                )
            }

            if (isWaitingAtBaseline && waitTargetPitches.isNotEmpty() && viewModel.isStrikeOverlayEnabled) {
                WaitModeOverlay(notes = waitTargetPitches, isUiHidden = isUiHidden)
            }

            SeekIndicatorOverlay(seekInfo)

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp, top = if (isUiHidden) 12.dp else 58.dp)
                    .size(38.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                    .clickable { isUiHidden = !isUiHidden },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isUiHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (isUiHidden) "Show UI bars" else "Hide UI bars",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        val keyboardHeight = if (isUiHidden) 100.dp else 70.dp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(keyboardHeight)
                .then(
                    if (!viewModel.isWaitModeEnabled) {
                        Modifier.pointerInput(Unit) {
                            val maxDist = 35.dp.toPx()
                            detectTapAndDoubleTap(
                                maxDistance = maxDist,
                                onTap = {
                                    viewModel.isPlaying = !viewModel.isPlaying
                                },
                                onDoubleTap = { offset ->
                                    val isLeft = offset.x < size.width / 2f
                                    PianoPlayer.stopAllNotes()
                                    currentWaitOnsetMs = -1L
                                    lastCompletedWaitOnsetMs = -1L
                                    chordHits.clear()
                                    viewModel.playheadMs = calculateSeekPosition(
                                        currentMs = viewModel.playheadMs,
                                        deltaMs = if (isLeft) -5000L else 5000L,
                                        songDurationMs = songDurationMs,
                                        isLoopingEnabled = viewModel.isLoopingEnabled,
                                        loopStartMs = viewModel.loopStartMs,
                                        loopEndMs = viewModel.loopEndMs
                                    )
                                    seekInfo = SeekInfo(if (isLeft) SeekDirection.BACKWARD else SeekDirection.FORWARD)
                                }
                            )
                        }
                    } else Modifier
                )
        ) {
            PianoKeyboardRow(
                start = startPitch,
                end = endPitch,
                numWhiteKeys = totalWhiteKeys,
                sustainedPitches = sustainedPitches,
                waitTargetPitches = waitTargetPitches,
                satisfiedPitches = emptySet(),
                isInteractive = isWaitingAtBaseline,
                keyboardHeight = keyboardHeight
            )
        }

        if (!isUiHidden) {
            MediaTimelineFooter(
                viewModel = viewModel,
                dur = songDurationMs,
                onSeekState = { isUserSeeking = it },
                onResetHead = {
                    viewModel.playheadMs = if (viewModel.isLoopingEnabled) viewModel.loopStartMs else 0L
                    lastTriggeredHeadMs = viewModel.playheadMs
                    currentWaitOnsetMs = -1L
                    chordHits.clear()
                    PianoPlayer.stopAllNotes()
                }
            )
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            songDurationMs = songDurationMs,
            onDismiss = { showSettingsDialog = false }
        )
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
