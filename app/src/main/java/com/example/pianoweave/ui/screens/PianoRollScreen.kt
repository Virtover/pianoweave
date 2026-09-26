package com.example.pianoweave.ui.screens

import android.content.res.Configuration
import android.graphics.Paint
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pianoweave.audio.AcousticNoteDetector
import com.example.pianoweave.audio.PianoPlayer
import com.example.pianoweave.midi.MidiInputManager
import com.example.pianoweave.midi.MidiNoteEvent
import com.example.pianoweave.midi.SimpleMidiReader
import com.example.pianoweave.midi.StoredMidi
import com.example.pianoweave.ui.components.AppThemeDialog
import com.example.pianoweave.ui.theme.LocalAppTheme
import com.example.pianoweave.ui.viewmodel.PianoWeaveViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

// --- Ultra Pro Piano Theme (Max Fidelity Visuals) ---
private val ColorBg = Color(0xFF0D1117)
private val ColorSurface = Color(0xFF161B22)
private val ColorSlate = Color(0xFF30363D)
private val ColorTextDim = Color(0xFF8B949E)
private val ColorKeyWhite = Color(0xFFE6E6E6) 
private val ColorKeyBlack = Color(0xFF030507)

@OptIn(ExperimentalMaterial3Api::class)
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

    if (parseError != null) MidiErrorScreen(song, parseError!!, onBack)
    else if (noteEvents == null) MidiLoadingScreen()
    else ModernPianoPlayerContent(song, noteEvents!!, viewModel, onBack)
}

@Composable
private fun MidiLoadingScreen() {
    val appTheme = LocalAppTheme.current
    val ColorGold = appTheme.primaryColor
    Box(modifier = Modifier.fillMaxSize().background(ColorBg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = ColorGold, strokeWidth = 4.dp, modifier = Modifier.size(56.dp))
            Text("Orchestrating performance...", color = ColorGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
    val appTheme = LocalAppTheme.current
    val ColorGold = appTheme.primaryColor
    val ColorGoldLight = appTheme.lightColor
    val ColorUpcomingNote = appTheme.upcomingColor
    val ColorWaitTarget = appTheme.waitTargetColor
    val ColorWaitTargetLight = appTheme.waitTargetLightColor
    val ColorTarget = appTheme.targetColor
    val ColorBaseline = appTheme.baselineColor
    val ColorBLGlow = appTheme.baselineGlowColor
    val ColorSuccess = appTheme.successColor
    val ColorSuccessLight = appTheme.successLightColor

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
    
    // --- Chord Collector State ---
    val chordHits = remember { mutableStateSetOf<Int>() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        PianoPlayer.initialize(context)
    }

    DisposableEffect(lifecycleOwner, viewModel.isWaitModeEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (viewModel.isWaitModeEnabled) {
                    AcousticNoteDetector.start(context)
                }
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                AcousticNoteDetector.stop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (viewModel.isWaitModeEnabled) {
            AcousticNoteDetector.start(context)
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
        if (nextRequiredOnset == null) emptySet<Int>()
        else noteEvents.filter { abs(it.startMs - nextRequiredOnset) <= 30L }.map { it.pitch }.toSet()
    }

    LaunchedEffect(notesToStrike, viewModel.isWaitModeEnabled, viewModel.isPlaying) {
        AcousticNoteDetector.targetPitches = if (viewModel.isWaitModeEnabled && viewModel.isPlaying) notesToStrike else emptySet()
    }

    // Lifecycle-aware capture managed above

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

                    // --- Simultaneous Chord Collector Logic ---
                    val currentPresses = required.mapNotNull { p ->
                        val lastPress = MidiInputManager.lastPressTimestamps[p] ?: 0L
                        val lastConsumed = MidiInputManager.consumedPressTimestamps[p] ?: 0L
                        if (lastPress >= arrivalAtWaitPointRealTime - 150L && lastPress > lastConsumed) {
                            p to lastPress
                        } else {
                            null
                        }
                    }.toMap()

                    // Highlight currently struck keys in UI
                    chordHits.clear()
                    chordHits.addAll(currentPresses.keys)

                    // Satisfied only when ALL required notes are struck within 1500ms
                    val isChordSatisfied = required.isNotEmpty() &&
                            currentPresses.size == required.size &&
                            (currentPresses.values.max() - currentPresses.values.min()) <= 1500L

                    if (!isChordSatisfied && required.isNotEmpty()) {
                        val now = System.currentTimeMillis()
                        currentPresses.filterValues { it < now - 1500L }.keys.forEach(MidiInputManager::simulateExternalNoteOff)
                        viewModel.playheadMs = upcoming
                        delay(10)
                        continue
                    } else {
                        // Success! Mark all notes as consumed and advance playhead
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
                viewModel.playheadMs = viewModel.loopStartMs ; lastTriggeredHeadMs = viewModel.loopStartMs ; PianoPlayer.stopAllNotes()
                val loopNotes = noteEvents.filter { viewModel.loopStartMs >= it.startMs && viewModel.loopStartMs < (it.startMs + it.durationMs) }
                loopNotes.forEach { PianoPlayer.noteOn(it.pitch, it.velocity) }
            } else if (!viewModel.isLoopingEnabled && next >= songDurationMs) {
                viewModel.playheadMs = songDurationMs ; viewModel.isPlaying = false
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

    Column(modifier = Modifier.fillMaxSize().background(ColorBg)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()
            .pointerInput(Unit) {
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
        ) {
            FallingNotesVisualizer(noteEvents, viewModel.playheadMs, startPitch, endPitch, totalWhiteKeys, waitTargetPitches, emptySet())

            ModernToolbar(viewModel.playheadMs, songDurationMs, viewModel, onBack, { viewModel.isPlaying = false ; showSettingsDialog = true })

            if (isWaitingAtBaseline && waitTargetPitches.isNotEmpty() && viewModel.isStrikeOverlayEnabled) {
                WaitModeOverlay(notes = waitTargetPitches)
            }

            SeekIndicatorOverlay(seekInfo)
        }

        Box(modifier = Modifier.fillMaxWidth().height(100.dp)
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
                startPitch, endPitch, totalWhiteKeys,
                sustainedPitches, waitTargetPitches, emptySet(),
                isWaitingAtBaseline
            )
        }

        MediaTimelineFooter(
            viewModel, songDurationMs, 
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

    if (showSettingsDialog) SettingsDialog(viewModel = viewModel, songDurationMs = songDurationMs, onDismiss = { showSettingsDialog = false })
}

private enum class SeekDirection { BACKWARD, FORWARD }
private data class SeekInfo(val direction: SeekDirection, val id: Long = System.currentTimeMillis())

@Composable
private fun SeekIndicatorOverlay(seekInfo: SeekInfo?) {
    val appTheme = LocalAppTheme.current
    val ColorGold = appTheme.primaryColor

    var visibleInfo by remember { mutableStateOf<SeekInfo?>(null) }
    var alpha by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(seekInfo) {
        if (seekInfo != null) {
            visibleInfo = seekInfo
            alpha = 1f
            delay(500)
            alpha = 0f
            delay(200)
            visibleInfo = null
        }
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = alpha,
        animationSpec = tween(durationMillis = 200),
        label = "seekAlpha"
    )

    visibleInfo?.let { info ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = animatedAlpha },
            contentAlignment = if (info.direction == SeekDirection.BACKWARD) Alignment.CenterStart else Alignment.CenterEnd
        ) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 48.dp)
                    .size(90.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.7f),
                border = BorderStroke(1.5.dp, ColorGold.copy(alpha = 0.8f))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (info.direction == SeekDirection.BACKWARD) Icons.Default.FastRewind else Icons.Default.FastForward,
                        contentDescription = null,
                        tint = ColorGold,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (info.direction == SeekDirection.BACKWARD) "-5s" else "+5s",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}



@Composable
private fun ModernToolbar(
    head: Long, dur: Long, viewModel: PianoWeaveViewModel, onBack: () -> Unit, onSetClick: () -> Unit
) {
    val appTheme = LocalAppTheme.current
    val ColorGold = appTheme.primaryColor
    val ColorSlate = Color(0xFF30363D)

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val current = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth().background(ColorSurface.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 4.dp)
            .pointerInput(Unit) { detectTapGestures { } }, // Consume gestures so UI toolbar doesn't pass taps through
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
        if (!isPortrait) Text(viewModel.activePracticeSong?.metadata?.title ?: "", color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.weight(1f), overflow = TextOverflow.Ellipsis)
        Text("${formatTime(head)} / ${formatTime(dur)}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
        if (isPortrait) Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (isPortrait) 6.dp else 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(if (isPortrait) 4.dp else 8.dp)) {
                listOf(0.25f, 0.5f, 1.0f).forEach { s ->
                    val isSelected = viewModel.speedMultiplier == s
                    Box(Modifier.size(if (isPortrait) 32.dp else 36.dp).background(if (isSelected) ColorGold else ColorSlate.copy(alpha = 0.8f), CircleShape).clickable { viewModel.speedMultiplier = s }, Alignment.Center) {
                        Text("${s}x", fontSize = 10.sp, fontWeight = FontWeight.Black, color = if (isSelected) Color.Black else Color.White)
                    }
                }
            }
            Box(modifier = Modifier.height(38.dp).then(if (isPortrait) Modifier.width(38.dp) else Modifier.wrapContentWidth()).background(if (viewModel.isWaitModeEnabled) ColorGold else ColorSurface.copy(alpha = 0.8f), RoundedCornerShape(10.dp)).border(1.dp, if (viewModel.isWaitModeEnabled) ColorGold else ColorSlate.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).clickable {
                if (viewModel.isWaitModeEnabled) AcousticNoteDetector.stop()
                else AcousticNoteDetector.start(current)
                viewModel.isWaitModeEnabled = !viewModel.isWaitModeEnabled
            }.padding(horizontal = if (isPortrait) 0.dp else 14.dp), Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Timer, null, tint = if (viewModel.isWaitModeEnabled) Color.Black else ColorGold, modifier = Modifier.size(18.dp))
                    if (!isPortrait) { Spacer(Modifier.width(8.dp)) ; Text(text = "Wait mode", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (viewModel.isWaitModeEnabled) Color.Black else ColorGold, maxLines = 1, softWrap = false) }
                }
            }
            Box(Modifier.size(38.dp).background(ColorSurface.copy(alpha = 0.8f), RoundedCornerShape(10.dp)).border(1.dp, ColorSlate.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).clickable { onSetClick() }, Alignment.Center) {
                Icon(Icons.Default.Tune, null, tint = ColorGold, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun getWhiteIndex(pitch: Int): Int {
    val octave = pitch / 12 ; val semitone = pitch % 12
    val offset = when (semitone) {
        0->0; 1->0; 2->1; 3->1; 4->2; 5->3; 6->3; 7->4; 8->4; 9->5; 10->5; 11->6; else->0
    }
    return (octave * 7) + offset
}

private fun getWhiteKeyXPx(whiteIndex: Int, numWhiteKeys: Int, totalWidth: Float): Float {
    val bw = totalWidth / numWhiteKeys
    return whiteIndex * bw
}

/**
 * Returns horizontal bounds for ANY pitch (black or white) in a fixed-width-white keyboard.
 */
private fun getPitchXRange(pitch: Int, startPitch: Int, totalWidth: Float, numWhiteKeys: Int): Pair<Float, Float> {
    val startWhite = getWhiteIndex(startPitch)
    val wkW = totalWidth / numWhiteKeys
    
    if (!isPitchBlack(pitch)) {
        val whiteIdx = getWhiteIndex(pitch) - startWhite
        return (whiteIdx * wkW) to ((whiteIdx + 1) * wkW)
    } else {
        // Black keys are centered on the line between their neighbors
        val leftWhiteIdx = getWhiteIndex(pitch - 1) - startWhite
        val center = (leftWhiteIdx + 1) * wkW
        val bkWidth = wkW * 0.65f
        return (center - bkWidth/2) to (center + bkWidth/2)
    }
}

@Composable
private fun FallingNotesVisualizer(
    events: List<MidiNoteEvent>, head: Long, start: Int, end: Int, numWhiteKeys: Int,
    waitTargetPitches: Set<Int> = emptySet(),
    satisfiedPitches: Set<Int> = emptySet()
) {
    val appTheme = LocalAppTheme.current
    val ColorGold = appTheme.primaryColor
    val ColorGoldLight = appTheme.lightColor
    val ColorUpcomingNote = appTheme.upcomingColor
    val ColorWaitTarget = appTheme.waitTargetColor
    val ColorWaitTargetLight = appTheme.waitTargetLightColor
    val ColorTarget = appTheme.targetColor
    val ColorBaseline = appTheme.baselineColor
    val ColorBLGlow = appTheme.baselineGlowColor
    val ColorSuccess = appTheme.successColor
    val ColorSuccessLight = appTheme.successLightColor

    val scale = 0.25f
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (size.width <= 0 || size.height <= 0) return@Canvas
        val tw = size.width

        // Rich vertical background gradient for falling notes section
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF090D14),
                    Color(0xFF0D1117),
                    Color(0xFF070A0F)
                )
            ),
            size = size
        )

        // 1. Grid Lanes
        for (i in 0..numWhiteKeys) {
            val x = i * (tw / numWhiteKeys)
            drawLine(color = Color(0xFF161B22).copy(alpha = 0.6f), start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 1f)
        }

        // 2. Bar Lines
        val barIntervalMs = 2000L ; val firstBar = (head / barIntervalMs) * barIntervalMs ; val lastMs = head + (size.height / scale).toLong()
        for (ms in firstBar..lastMs step barIntervalMs) {
            val y = size.height - ((ms - head) * scale)
            if (y in 0f..size.height) drawLine(color = Color.White.copy(alpha = 0.1f), start = Offset(0f, y), end = Offset(tw, y), strokeWidth = 1.dp.toPx())
        }

        // 3. Falling Notes
        val filtered = events.filter { (it.startMs + it.durationMs) >= head && it.startMs <= lastMs }
        val (blackEvents, whiteEvents) = filtered.partition { isPitchBlack(it.pitch) }

        fun drawNote(e: MidiNoteEvent) {
            val (x1, x2) = getPitchXRange(e.pitch, start, tw, numWhiteKeys)
            val kw = x2 - x1
            val h = (e.durationMs * scale).coerceAtLeast(12f)
            val y = size.height - ((e.startMs - head) * scale) - h
            
            val now = System.currentTimeMillis()
            val isAtBaseline = head >= e.startMs && head <= (e.startMs + e.durationMs)
            val isHitting = head >= e.startMs && head <= (e.startMs + 60L)
            val isWaiting = waitTargetPitches.contains(e.pitch) && head >= e.startMs - 50 && head <= e.startMs + 50
            val isSatisfied = satisfiedPitches.contains(e.pitch) && isWaiting
            
            val lastPress = MidiInputManager.lastPressTimestamps[e.pitch] ?: 0L
            val isRecentOnset = (now - lastPress <= 350L) && (MidiInputManager.pressedKeys.contains(e.pitch) || now - lastPress <= 250L)
            val isUserMatch = (isAtBaseline && MidiInputManager.pressedKeys.contains(e.pitch)) || (isWaiting && isRecentOnset)

            val baseCol = when {
                isSatisfied || isUserMatch -> ColorSuccess 
                isWaiting -> ColorWaitTarget
                isHitting -> ColorTarget
                isAtBaseline -> ColorGold
                else -> ColorUpcomingNote 
            }
            val highlightCol = when {
                isSatisfied || isUserMatch -> ColorSuccessLight
                isWaiting -> ColorWaitTargetLight
                isHitting -> ColorGoldLight
                isAtBaseline -> ColorGoldLight.copy(alpha = 0.8f)
                else -> baseCol.copy(alpha = 0.6f)
            }

            // Existing sharp note body on top
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(highlightCol, baseCol), startY = y, endY = y + h),
                topLeft = Offset(x1 + 0.5f, y.coerceIn(-h, size.height)),
                size = Size(kw - 1f, h),
                cornerRadius = CornerRadius(6.dp.toPx())
            )

            if (isHitting || isUserMatch || isWaiting) {
                val glowCol = if (isWaiting && !isSatisfied) ColorWaitTarget else baseCol
                drawRect(brush = Brush.verticalGradient(listOf(glowCol.copy(alpha = 0.4f), Color.Transparent)), topLeft = Offset(x1, y.coerceIn(-h, size.height) + h), size = Size(kw, 35.dp.toPx()))
            }
        }

        // Baseline glow aura drawn first, so falling notes are drawn OVER it (decays faster, 14.dp)
        val baselineY = size.height - 1f
//        val glowUp = 20.dp.toPx()
//        val glowDown = 2.dp.toPx()
//
//        drawRect(
//            brush = Brush.verticalGradient(
//                colorStops = arrayOf(
//                    0.0f to Color.Transparent,
//                    0.6f to ColorBLGlow.copy(alpha = 0.15f),
//                    0.8f to ColorBLGlow.copy(alpha = 0.25f),
//                    0.9f to ColorBLGlow.copy(alpha = 0.6f),
//                    1.0f to ColorBaseline.copy(alpha = 0.6f),
//                ),
//                startY = baselineY - glowUp,
//                endY = baselineY
//            ),
//            topLeft = Offset(0f, baselineY - glowUp),
//            size = Size(tw, glowUp)
//        )

        drawLine(ColorBaseline, Offset(0f, baselineY), Offset(tw, baselineY), 3.dp.toPx())

        whiteEvents.forEach { drawNote(it) }
        blackEvents.forEach { drawNote(it) }
    }
}

@Composable
private fun WaitModeOverlay(notes: Set<Int>) {
    val appTheme = LocalAppTheme.current
    val ColorWaitTarget = appTheme.waitTargetColor
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 56.dp)
            .pointerInput(Unit) { detectTapGestures { } },
        Alignment.TopCenter
    ) {
        Surface(
            color = ColorWaitTarget.copy(alpha = 0.8f),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 10.dp,
            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.8f))
        ) {
            Text(
                text = "STRIKE: " + notes.sorted().joinToString("   ") { midiPitchName(it) },
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp),
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black
            )
        }
    }
}

@Composable
private fun PianoKeyboardRow(
    start: Int, end: Int, numWhiteKeys: Int,
    sustainedPitches: Set<Int>,
    waitTargetPitches: Set<Int> = emptySet(),
    satisfiedPitches: Set<Int> = emptySet(),
    isInteractive: Boolean = false
) {
    val appTheme = LocalAppTheme.current
    val ColorGold = appTheme.primaryColor
    val ColorWaitTarget = appTheme.waitTargetColor
    val ColorSuccess = appTheme.successColor

    val pressed = MidiInputManager.pressedKeys.toSet()

    // Breathing pulse for waiting keys
    val infiniteTransition = rememberInfiniteTransition(label = "waitPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(animation = tween(600, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "pulse"
    )

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(100.dp)
            .background(Color(0xFF030507))
            .then(
                if (isInteractive) {
                    Modifier.pointerInput(start, end, numWhiteKeys, isInteractive) {
                        val activePointers = mutableMapOf<PointerId, Int>()
                        val tw = size.width.toFloat()
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { change ->
                                    val pId = change.id
                                    when {
                                        change.changedToDown() -> {
                                            val p = findPitchAt(change.position, start, end, tw, numWhiteKeys)
                                            if (p != -1) {
                                                activePointers[pId] = p
                                                MidiInputManager.simulateNoteOn(p)
                                                PianoPlayer.noteOn(p)
                                            }
                                            change.consume()
                                        }
                                        change.changedToUp() || !change.pressed -> {
                                            activePointers.remove(pId)?.let { oldP ->
                                                MidiInputManager.simulateNoteOff(oldP)
                                                PianoPlayer.noteOff(oldP)
                                            }
                                            change.consume()
                                        }
                                        change.positionChanged() -> {
                                            val newP = findPitchAt(change.position, start, end, tw, numWhiteKeys)
                                            val oldP = activePointers[pId]
                                            if (newP != oldP) {
                                                if (oldP != null) {
                                                    MidiInputManager.simulateNoteOff(oldP)
                                                    PianoPlayer.noteOff(oldP)
                                                }
                                                if (newP != -1) {
                                                    activePointers[pId] = newP
                                                    MidiInputManager.simulateNoteOn(newP)
                                                    PianoPlayer.noteOn(newP)
                                                } else {
                                                    activePointers.remove(pId)
                                                }
                                            }
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else Modifier
            )
    ) {
        val tw = constraints.maxWidth.toFloat()
        val keyPath = remember { Path() }

        Canvas(Modifier.fillMaxSize()) {
            val wkW = tw / numWhiteKeys
            val whiteRadius = 6.dp.toPx()
            val blackRadius = 4.dp.toPx()

            // 1. Base White Keys
            for (i in 0 until numWhiteKeys) {
                val x1 = i * wkW
                keyPath.reset()
                keyPath.addRoundRect(
                    RoundRect(
                        rect = Rect(x1 + 0.5f, 0f, x1 + wkW - 0.5f, size.height),
                        bottomLeft = CornerRadius(whiteRadius, whiteRadius),
                        bottomRight = CornerRadius(whiteRadius, whiteRadius)
                    )
                )
                drawPath(path = keyPath, color = ColorKeyWhite)
                drawLine(Color.Black.copy(alpha = 0.15f), Offset(x1, 0f), Offset(x1, size.height), 1.2.dp.toPx())
            }

            // 2. Subtle Top Keyboard Shadow Gradient (drapes over idle keys)
            val shadowHeight = 24.dp.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.5f),
                        Color.Black.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = shadowHeight
                ),
                topLeft = Offset(0f, 0f),
                size = Size(tw, shadowHeight)
            )

            // 3. Active White Key Glow Auras & Highlights (drawn AFTER shadow so shadow doesn't affect them)
            val now = System.currentTimeMillis()
            for (p in start..end) {
                if (isPitchBlack(p)) continue
                val (x1, x2) = getPitchXRange(p, start, tw, numWhiteKeys)
                val isPressed = pressed.contains(p)
                val isTarget = sustainedPitches.contains(p)
                val isWaiting = waitTargetPitches.contains(p)
                val isSatisfied = satisfiedPitches.contains(p)
                
                val lastPress = MidiInputManager.lastPressTimestamps[p] ?: 0L
                val isRecentOnset = (now - lastPress <= 350L) && (isPressed || now - lastPress <= 250L)

                if (isPressed || isTarget || isWaiting || isSatisfied) {
                    val color = when {
                        isSatisfied -> ColorSuccess
                        isWaiting && isRecentOnset -> ColorSuccess
                        isPressed -> ColorGold
                        isWaiting -> ColorWaitTarget.copy(alpha = pulseAlpha)
                        else -> ColorGold.copy(alpha = 0.5f)
                    }

                    // Glow aura around white key
                    keyPath.reset()
                    keyPath.addRoundRect(
                        RoundRect(
                            rect = Rect(x1 - 1f, -1f, x2 + 1f, size.height + 1f),
                            bottomLeft = CornerRadius(whiteRadius + 1f, whiteRadius + 1f),
                            bottomRight = CornerRadius(whiteRadius + 1f, whiteRadius + 1f)
                        )
                    )
                    drawPath(path = keyPath, color = color.copy(alpha = 0.4f))

                    // Crisp white key highlight
                    keyPath.reset()
                    keyPath.addRoundRect(
                        RoundRect(
                            rect = Rect(x1 + 0.5f, 0f, x2 - 0.5f, size.height),
                            bottomLeft = CornerRadius(whiteRadius, whiteRadius),
                            bottomRight = CornerRadius(whiteRadius, whiteRadius)
                        )
                    )
                    drawPath(path = keyPath, color = color)
                }
            }

            // 4. Black Keys (Base or Highlight replacing normal black completely, with glow aura)
            for (p in start..end) {
                if (!isPitchBlack(p)) continue
                val (x1, x2) = getPitchXRange(p, start, tw, numWhiteKeys)
                val isPressed = pressed.contains(p)
                val isTarget = sustainedPitches.contains(p)
                val isWaiting = waitTargetPitches.contains(p)
                val isSatisfied = satisfiedPitches.contains(p)
                
                val lastPress = MidiInputManager.lastPressTimestamps[p] ?: 0L
                val isRecentOnset = (now - lastPress <= 350L) && (isPressed || now - lastPress <= 250L)

                val highlightColor = when {
                    isSatisfied -> ColorSuccess
                    isWaiting && isRecentOnset -> ColorSuccess
                    isPressed -> ColorGold
                    isWaiting -> ColorWaitTarget.copy(alpha = pulseAlpha)
                    isTarget -> ColorGold.copy(alpha = 0.5f)
                    else -> null
                }

                val h = size.height * 0.7f
                keyPath.reset()
                keyPath.addRoundRect(
                    RoundRect(
                        rect = Rect(x1 + 0.5f, 0f, x2 - 0.5f, h),
                        bottomLeft = CornerRadius(blackRadius, blackRadius),
                        bottomRight = CornerRadius(blackRadius, blackRadius)
                    )
                )

                if (highlightColor != null) {
                    // Glow aura around black key
                    Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(x1 - 1.5f, -1.5f, x2 + 1.5f, h + 1.5f),
                                bottomLeft = CornerRadius(blackRadius + 1f, blackRadius + 1f),
                                bottomRight = CornerRadius(blackRadius + 1f, blackRadius + 1f)
                            )
                        )
                        drawPath(this, color = highlightColor.copy(alpha = 0.45f))
                    }

                    // Highlight replaces normal black completely (no base ColorKeyBlack drawn, no mixing with black)
                    drawPath(path = keyPath, color = highlightColor)
                } else {
                    // Normal idle black key
                    drawPath(path = keyPath, color = ColorKeyBlack)
                }
            }

            // 6. C Key Annotations
            val textPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#777777")
                textSize = 10.sp.toPx()
                textAlign = Paint.Align.CENTER
                setAntiAlias(true)
            }
            for (p in start..end) {
                if (!isPitchBlack(p) && p % 12 == 0) {
                    val (x1, x2) = getPitchXRange(p, start, tw, numWhiteKeys)
                    val centerX = (x1 + x2) / 2f
                    val name = midiPitchName(p)
                    val y = size.height - 10.dp.toPx()
                    drawContext.canvas.nativeCanvas.drawText(name, centerX, y, textPaint)
                }
            }
        }
    }
}

private fun findPitchAt(pos: Offset, start: Int, end: Int, tw: Float, numWhiteKeys: Int): Int {
    for (p in start..end) {
        if (isPitchBlack(p)) {
            val (x1, x2) = getPitchXRange(p, start, tw, numWhiteKeys)
            if (pos.x in x1..x2 && pos.y <= 100.dp.value * 0.7f * 2.5f) return p
        }
    }
    for (p in start..end) {
        if (!isPitchBlack(p)) {
            val (x1, x2) = getPitchXRange(p, start, tw, numWhiteKeys)
            if (pos.x in x1..x2) return p
        }
    }
    return -1
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaTimelineFooter(
    viewModel: PianoWeaveViewModel, dur: Long, onSeekState: (Boolean) -> Unit, onResetHead: () -> Unit
) {
    val appTheme = LocalAppTheme.current
    val ColorGold = appTheme.primaryColor
    val ColorSlate = Color(0xFF30363D)
    Row(modifier = Modifier.fillMaxWidth().background(ColorSurface).padding(horizontal = 20.dp, vertical = 10.dp).pointerInput(Unit) { detectTapGestures { } }, // Consume gestures
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconButton(onClick = { viewModel.isPlaying = !viewModel.isPlaying }, Modifier.size(44.dp).background(ColorGold, CircleShape)) { Icon(if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(32.dp)) }
            IconButton(onClick = { onResetHead() }, modifier = Modifier.size(44.dp).background(ColorSurface, CircleShape).border(1.dp, ColorSlate, CircleShape)) { Icon(Icons.Default.Replay, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
        }
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val currentMs = viewModel.playheadMs.coerceIn(0L, dur.coerceAtLeast(1L))
                val fraction = if (dur > 0L) (currentMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f

                if (viewModel.isLoopingEnabled) {
                    RangeSlider(
                        value = viewModel.loopStartMs.toFloat()..viewModel.loopEndMs.toFloat(),
                        onValueChange = { viewModel.loopStartMs = it.start.toLong() ; viewModel.loopEndMs = it.endInclusive.toLong() },
                        valueRange = 0f..dur.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(thumbColor = ColorGold, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent),
                        modifier = Modifier.fillMaxWidth().height(32.dp).offset(y = (-12).dp),
                        startThumb = {
                            Box(modifier = Modifier.size(24.dp).background(ColorGold, CircleShape).border(1.5.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.ChevronLeft, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            }
                        },
                        endThumb = {
                            Box(modifier = Modifier.size(24.dp).background(ColorGold, CircleShape).border(1.5.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.ChevronRight, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                }

                Slider(
                    value = currentMs.toFloat(),
                    onValueChange = { onSeekState(true) ; viewModel.playheadMs = it.toLong() },
                    onValueChangeFinished = { onSeekState(false) },
                    valueRange = 0f..dur.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(15.dp)
                                .background(Color.White, CircleShape)
                        )
                    },
                    track = { _ ->
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                        ) {
                            val totalWidth = size.width
                            val centerY = size.height / 2f
                            val thumbRadius = 7.5.dp.toPx()
                            val usableWidth = (totalWidth - 2 * thumbRadius).coerceAtLeast(0f)
                            val thumbCenterX = thumbRadius + fraction * usableWidth

                            // 1. Inactive Track (thin dark line across the whole slider width)
                            val inactiveTrackHeight = 2.5.dp.toPx()
                            drawRoundRect(
                                color = ColorSlate.copy(alpha = 0.5f),
                                topLeft = Offset(0f, centerY - inactiveTrackHeight / 2f),
                                size = Size(totalWidth, inactiveTrackHeight),
                                cornerRadius = CornerRadius(inactiveTrackHeight / 2f)
                            )

                            // 2. Loop Range Highlight (if looping enabled)
                            if (viewModel.isLoopingEnabled) {
                                val durLong = dur.coerceAtLeast(1L)
                                val sPerc = (viewModel.loopStartMs.toFloat() / durLong.toFloat()).coerceIn(0f, 1f)
                                val ePerc = (viewModel.loopEndMs.toFloat() / durLong.toFloat()).coerceIn(0f, 1f)
                                val startX = thumbRadius + sPerc * usableWidth
                                val endX = thumbRadius + ePerc * usableWidth
                                val rangeW = (endX - startX).coerceAtLeast(0f)
                                if (rangeW > 0f) {
                                    val loopTrackHeight = 7.dp.toPx()
                                    drawRoundRect(
                                        color = ColorGold.copy(alpha = 0.3f),
                                        topLeft = Offset(startX, centerY - loopTrackHeight / 2f),
                                        size = Size(rangeW, loopTrackHeight),
                                        cornerRadius = CornerRadius(loopTrackHeight / 2f)
                                    )
                                }
                            }

                            // 3. Active Progress Track (thicker pill bar from start to thumb center)
                            val activeTrackHeight = 6.5.dp.toPx()
                            val activeWidth = thumbCenterX.coerceIn(0f, totalWidth)
                            if (activeWidth > 0f) {
                                drawRoundRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(ColorGold, ColorGold),
                                        startX = 0f,
                                        endX = activeWidth.coerceAtLeast(1f)
                                    ),
                                    topLeft = Offset(0f, centerY - activeTrackHeight / 2f),
                                    size = Size(activeWidth, activeTrackHeight),
                                    cornerRadius = CornerRadius(activeTrackHeight / 2f)
                                )
                            }
                        }
                    }
                )
            }
        }
        Spacer(modifier = Modifier.width(20.dp))
        IconButton(onClick = { viewModel.isLoopingEnabled = !viewModel.isLoopingEnabled }, modifier = Modifier.size(48.dp).background(if (viewModel.isLoopingEnabled) ColorGold else ColorSurface, RoundedCornerShape(12.dp)).border(1.dp, if(!viewModel.isLoopingEnabled) ColorSlate else Color.Transparent, RoundedCornerShape(12.dp))) { Icon(Icons.Default.Repeat, null, tint = if (viewModel.isLoopingEnabled) Color.Black else ColorGold, modifier = Modifier.size(26.dp)) }
    }
}

@Composable
private fun SettingsDialog(
    viewModel: PianoWeaveViewModel,
    songDurationMs: Long,
    onDismiss: () -> Unit
) {
    val currentContext = LocalContext.current
    var showThemeDialog by remember { mutableStateOf(false) }
    val appTheme = LocalAppTheme.current
    val ColorGold = appTheme.primaryColor
    val ColorGoldLight = appTheme.lightColor
    val ColorUpcomingNote = appTheme.upcomingColor
    val ColorWaitTarget = appTheme.waitTargetColor
    val ColorWaitTargetLight = appTheme.waitTargetLightColor
    val ColorTarget = appTheme.targetColor
    val ColorBaseline = appTheme.baselineColor
    val ColorBLGlow = appTheme.baselineGlowColor
    val ColorSuccess = appTheme.successColor
    val ColorSuccessLight = appTheme.successLightColor
    val ColorSlate = Color(0xFF30363D)
    val ColorTextDim = Color(0xFF8B949E)

    LaunchedEffect(Unit) {
        viewModel.isPlaying = false
    }

    var startText by remember { mutableStateOf(formatTime(viewModel.loopStartMs)) }
    var endText by remember { mutableStateOf(formatTime(viewModel.loopEndMs)) }
    var isLooping by remember { mutableStateOf(viewModel.isLoopingEnabled) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(max = 650.dp),
                shape = RoundedCornerShape(20.dp),
                color = ColorSurface,
                contentColor = Color.White,
                border = BorderStroke(1.dp, ColorSlate)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = ColorGold
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = ColorTextDim)
                        }
                    }

                    HorizontalDivider(color = ColorSlate.copy(alpha = 0.6f))

                    // Transposition Section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Transposition", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Surface(
                                color = ColorSlate.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, ColorGold.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "${if (viewModel.transposeOffset > 0) "+" else ""}${viewModel.transposeOffset} semi",
                                    color = ColorGold,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Slider(
                            value = viewModel.transposeOffset.toFloat(),
                            onValueChange = { viewModel.transposeOffset = it.toInt() },
                            valueRange = -12f..12f,
                            steps = 23,
                            colors = SliderDefaults.colors(
                                thumbColor = ColorGold,
                                activeTrackColor = ColorGold,
                                inactiveTrackColor = ColorSlate
                            )
                        )
                    }

                    HorizontalDivider(color = ColorSlate.copy(alpha = 0.6f))

                    // Loop Section
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f).padding(end = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text("Loop Playback", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Repeat section during practice", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 16.sp)
                            }
                            Switch(
                                checked = isLooping,
                                onCheckedChange = {
                                    isLooping = it
                                    viewModel.isLoopingEnabled = it
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = ColorGold,
                                    uncheckedThumbColor = ColorTextDim,
                                    uncheckedTrackColor = ColorSlate
                                )
                            )
                        }

                        AnimatedVisibility(
                            visible = isLooping,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = startText,
                                        onValueChange = { newText ->
                                            startText = newText
                                            val parsed = parseTimeToMs(newText)
                                            if (parsed != null) {
                                                viewModel.loopStartMs = parsed.coerceIn(0L, songDurationMs)
                                            }
                                        },
                                        label = { Text("Start (mm:ss)") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = ColorGold,
                                            unfocusedBorderColor = ColorSlate,
                                            focusedLabelColor = ColorGold,
                                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                                            cursorColor = ColorGold,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        )
                                    )
                                    OutlinedTextField(
                                        value = endText,
                                        onValueChange = { newText ->
                                            endText = newText
                                            val parsed = parseTimeToMs(newText)
                                            if (parsed != null) {
                                                viewModel.loopEndMs = parsed.coerceIn(0L, songDurationMs)
                                            }
                                        },
                                        label = { Text("End (mm:ss)") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = ColorGold,
                                            unfocusedBorderColor = ColorSlate,
                                            focusedLabelColor = ColorGold,
                                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                                            cursorColor = ColorGold,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        )
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val currentFormatted = formatTime(viewModel.playheadMs)
                                            startText = currentFormatted
                                            viewModel.loopStartMs = viewModel.playheadMs
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                        border = BorderStroke(1.dp, ColorGold.copy(alpha = 0.6f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorGold)
                                    ) {
                                        Text("Start = Current", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val currentFormatted = formatTime(viewModel.playheadMs)
                                            endText = currentFormatted
                                            viewModel.loopEndMs = viewModel.playheadMs
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                        border = BorderStroke(1.dp, ColorGold.copy(alpha = 0.6f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorGold)
                                    ) {
                                        Text("End = Current", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = ColorSlate.copy(alpha = 0.6f))

                    // Strike Overlay Section
                    val currentContext = LocalContext.current
                    var isStrikeOverlay by remember { mutableStateOf(viewModel.isStrikeOverlayEnabled) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("Strike Overlay", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Show STRIKE instruction banner in wait mode", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 16.sp)
                        }
                        Switch(
                            checked = isStrikeOverlay,
                            onCheckedChange = {
                                isStrikeOverlay = it
                                viewModel.setStrikeOverlayEnabled(currentContext, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = ColorGold,
                                uncheckedThumbColor = ColorTextDim,
                                uncheckedTrackColor = ColorSlate
                            )
                        )
                    }


                    HorizontalDivider(color = ColorSlate.copy(alpha = 0.6f))

                    // App Color Theme Option
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f).padding(end = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text("Theme", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Current: ${appTheme.name}", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { showThemeDialog = true },
                                border = BorderStroke(1.dp, ColorGold),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .background(ColorGold, CircleShape)
                                            .border(1.dp, Color.White, CircleShape)
                                    )
                                    Text("Change", color = ColorGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    if (showThemeDialog) {
                        AppThemeDialog(
                            currentThemeId = viewModel.selectedThemeId,
                            onSelectTheme = { themeId ->
                                viewModel.setSelectedTheme(currentContext, themeId)
                            },
                            onDismiss = { showThemeDialog = false }
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    Button(
                        onClick = {
                            val parsedStart = parseTimeToMs(startText)
                            val parsedEnd = parseTimeToMs(endText)

                            if (parsedStart != null) {
                                viewModel.loopStartMs = parsedStart.coerceIn(0L, songDurationMs)
                            }
                            if (parsedEnd != null) {
                                viewModel.loopEndMs = parsedEnd.coerceIn(0L, songDurationMs)
                            }
                            viewModel.isLoopingEnabled = isLooping
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorGold, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("DONE", fontWeight = FontWeight.Black, fontSize = 14.sp)
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun MidiErrorScreen(song: StoredMidi, error: String, onBack: () -> Unit) {
    val appTheme = LocalAppTheme.current
    val ColorBaseline = appTheme.baselineColor
    val ColorTextDim = Color(0xFF8B949E)
    Box(Modifier.fillMaxSize().background(ColorBg).padding(32.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Default.Warning, null, tint = ColorBaseline, modifier = Modifier.size(72.dp))
            Text("Midi Compatibility Error", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
            Text(song.metadata.title, color = ColorTextDim, textAlign = TextAlign.Center)
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = ColorSurface)) { Text("Return to Library") }
        }
    }
}

private fun isPitchBlack(p: Int): Boolean { val n = p % 12 ; return n == 1 || n == 3 || n == 6 || n == 8 || n == 10 }
private fun formatTime(ms: Long): String { val s = (ms / 1000L).coerceAtLeast(0L) ; return "%02d:%02d".format(s / 60, s % 60) }
private fun midiPitchName(p: Int): String { val n = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B") ; return n[p % 12] + (p / 12 - 1) }

private fun parseTimeToMs(input: String): Long? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null
    return try {
        if (trimmed.contains(":")) {
            val parts = trimmed.split(":")
            if (parts.size != 2) return null
            val mins = parts[0].toLongOrNull() ?: return null
            val secs = parts[1].toDoubleOrNull() ?: return null
            ((mins * 60.0 + secs) * 1000.0).toLong()
        } else {
            val secs = trimmed.toDoubleOrNull() ?: return null
            (secs * 1000.0).toLong()
        }
    } catch (e: Exception) {
        null
    }
}

private suspend fun PointerInputScope.detectTapAndDoubleTap(
    maxDistance: Float,
    onTap: (Offset) -> Unit,
    onDoubleTap: (Offset) -> Unit
) {
    val doubleTapTimeout = 300L
    var lastUpTime = 0L
    var lastUpPos = Offset.Zero

    while (true) {
        awaitPointerEventScope {
            val down = awaitFirstDown(requireUnconsumed = true)
            val downPos = down.position
            val pointer = down
            var upPos: Offset? = null
            var exceeded = false

            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointer.id } ?: break
                    if (!change.pressed) {
                        upPos = change.position
                        change.consume()
                        break
                    }
                    if ((change.position - downPos).getDistance() > maxDistance) {
                        exceeded = true
                        break
                    }
                }
            } catch (e: CancellationException) {
                throw e
            }

            if (!exceeded && upPos != null) {
                val distance = (upPos - downPos).getDistance()
                if (distance <= maxDistance) {
                    val now = System.currentTimeMillis()
                    if (now - lastUpTime <= doubleTapTimeout && (upPos - lastUpPos).getDistance() <= maxDistance * 2f) {
                        onDoubleTap(upPos)
                        lastUpTime = 0L
                    } else {
                        val secondDown = withTimeoutOrNull(doubleTapTimeout) {
                            awaitFirstDown(requireUnconsumed = true)
                        }
                        if (secondDown != null) {
                            val secondDownPos = secondDown.position
                            val secondPointer = secondDown
                            var secondUpPos: Offset? = null
                            var secondExceeded = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == secondPointer.id } ?: break
                                    if (!change.pressed) {
                                        secondUpPos = change.position
                                        change.consume()
                                        break
                                    }
                                    if ((change.position - secondDownPos).getDistance() > maxDistance) {
                                        secondExceeded = true
                                        break
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            }
                            if (!secondExceeded && secondUpPos != null && (secondUpPos - secondDownPos).getDistance() <= maxDistance) {
                                onDoubleTap(secondUpPos)
                                lastUpTime = 0L
                            } else {
                                onTap(upPos)
                                lastUpTime = now
                                lastUpPos = upPos
                            }
                        } else {
                            onTap(upPos)
                            lastUpTime = now
                            lastUpPos = upPos
                        }
                    }
                }
            }
        }
    }
}

private fun calculateSeekPosition(
    currentMs: Long,
    deltaMs: Long,
    songDurationMs: Long,
    isLoopingEnabled: Boolean,
    loopStartMs: Long,
    loopEndMs: Long
): Long {
    val rawTarget = currentMs + deltaMs
    val loopLen = loopEndMs - loopStartMs

    if (!isLoopingEnabled || loopLen <= 0) {
        return rawTarget.coerceIn(0L, songDurationMs)
    }

    if (deltaMs < 0) {
        val wasBeforeLoop = currentMs < loopStartMs
        return if (wasBeforeLoop) {
            rawTarget.coerceAtLeast(0L)
        } else {
            rawTarget.coerceAtLeast(loopStartMs)
        }
    } else {
        return if (rawTarget >= loopEndMs) {
            val offsetPastStart = rawTarget - loopStartMs
            val remainder = offsetPastStart % loopLen
            loopStartMs + remainder
        } else {
            rawTarget
        }
    }
}
