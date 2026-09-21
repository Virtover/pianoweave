package com.example.pianoweave.ui.screens

import android.content.res.Configuration
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.pianoweave.audio.AcousticNoteDetector
import com.example.pianoweave.audio.PianoPlayer
import com.example.pianoweave.midi.MidiInputManager
import com.example.pianoweave.midi.MidiNoteEvent
import com.example.pianoweave.midi.SimpleMidiReader
import com.example.pianoweave.midi.StoredMidi
import com.example.pianoweave.ui.viewmodel.PianoWeaveViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

// --- Ultra Pro Piano Theme (Max Fidelity Visuals) ---
private val ColorBg = Color(0xFF0D1117)
private val ColorSurface = Color(0xFF161B22)
private val ColorGold = Color(0xFFD4AF37)
private val ColorGoldLight = Color(0xFFFFE082) 
private val ColorUpcomingNote = Color(0xFF1F2937) 
private val ColorSlate = Color(0xFF30363D)
private val ColorSuccess = Color(0xFF2EA043)
private val ColorSuccessLight = Color(0xFF69C67E)
private val ColorWaitTarget = Color(0xFF00D2FF) 
private val ColorWaitTargetLight = Color(0xFFB3F5FF)
private val ColorTarget = Color(0xFFF39C12) 
private val ColorBaseline = Color(0xFFF85149) 
private val ColorTextDim = Color(0xFF8B949E)
private val ColorKeyWhite = Color(0xFFE6E6E6) 
private val ColorKeyBlack = Color(0xFF121212)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PianoRollScreen(
    song: StoredMidi,
    viewModel: PianoWeaveViewModel,
    onBack: () -> Unit
) {
    var noteEvents by remember(song) { mutableStateOf<List<MidiNoteEvent>?>(null) }
    var parseError by remember(song) { mutableStateOf<String?>(null) }
    
    // Persistent tracking of the current song to avoid resets when coming back to the SAME song
    var lastSongPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(song) {
        if (lastSongPath != song.file.absolutePath) {
            // New song detected: perform full reset
            viewModel.playheadMs = 0L
            viewModel.isPlaying = true
            lastSongPath = song.file.absolutePath
        } else {
            viewModel.isPlaying = false
        }
        
        noteEvents = null
        parseError = null
        try {
            val parsed = withContext(Dispatchers.IO) {
                SimpleMidiReader.parse(song.file)
            }
            noteEvents = parsed
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
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isUserSeeking by remember { mutableStateOf(false) }

    val startPitch = 21
    val endPitch = 108
    val totalWhiteKeys = remember { (startPitch..endPitch).count { !isPitchBlack(it) } }

    val noteEvents = remember(rawEvents, viewModel.transposeOffset) {
        rawEvents.map { it.copy(pitch = it.pitch + viewModel.transposeOffset) }
    }

    val songDurationMs = remember(noteEvents) {
        noteEvents.maxOfOrNull { it.startMs + it.durationMs } ?: 10_000L
    }

    LaunchedEffect(songDurationMs) {
        if (viewModel.loopEndMs == 0L) viewModel.loopEndMs = songDurationMs
    }

    var lastTriggeredHeadMs by remember { mutableLongStateOf(-1L) }
    var currentWaitOnsetMs by remember { mutableLongStateOf(-1L) }
    var arrivalAtWaitPointRealTime by remember { mutableLongStateOf(0L) }
    
    // --- Chord Collector State ---
    // Tracks which pitches of the current required chord have been hit since arriving
    val chordHits = remember { mutableStateSetOf<Int>() }

    val context = LocalContext.current
    LaunchedEffect(Unit) { if (viewModel.isWaitModeEnabled) AcousticNoteDetector.start(context) }
    DisposableEffect(Unit) { onDispose { AcousticNoteDetector.stop() } }

    val sustainedPitches = remember(noteEvents, viewModel.playheadMs) {
        noteEvents.filter { viewModel.playheadMs >= it.startMs && viewModel.playheadMs <= (it.startMs + it.durationMs) }.map { it.pitch }.toSet()
    }
    
    LaunchedEffect(sustainedPitches) {
        AcousticNoteDetector.suppressedPitches = sustainedPitches
    }

    LaunchedEffect(viewModel.isPlaying, isUserSeeking) {
        if (!viewModel.isPlaying || isUserSeeking) PianoPlayer.stopAllNotes()
    }

    // Reset wait state if playback stops or mode toggles
    LaunchedEffect(viewModel.isPlaying, viewModel.isWaitModeEnabled) {
        if (!viewModel.isPlaying || !viewModel.isWaitModeEnabled) {
            currentWaitOnsetMs = -1L
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
                val upcoming = noteEvents.filter { it.startMs >= viewModel.playheadMs && it.startMs <= targetNext }.minOfOrNull { it.startMs }
                
                if (upcoming != null) {
                    val required = noteEvents.filter { abs(it.startMs - upcoming) <= 30L && it.pitch in startPitch..endPitch }.map { it.pitch }.toSet()

                    if (currentWaitOnsetMs != upcoming) {
                        currentWaitOnsetMs = upcoming
                        arrivalAtWaitPointRealTime = System.currentTimeMillis()
                        chordHits.clear()
                    }

                    // --- Robust Chord Collector Logic ---
                    // Instead of requiring all to be pressed simultaneously at this very instant,
                    // we collect "fresh" strikes that have happened since we decided to wait.
                    required.forEach { p ->
                        if (p !in chordHits) {
                            val lastPress = MidiInputManager.lastPressTimestamps[p] ?: 0L
                            val lastConsumed = MidiInputManager.consumedPressTimestamps[p] ?: 0L
                            
                            // A strike counts if it happened just before (400ms) or any time after reaching wait point
                            // AND it hasn't been consumed by a previous note.
                            if (lastPress >= arrivalAtWaitPointRealTime - 400L && lastPress > lastConsumed) {
                                chordHits.add(p)
                            }
                        }
                    }

                    val isChordSatisfied = required.isNotEmpty() && required.all { it in chordHits }

                    if (!isChordSatisfied && required.isNotEmpty()) {
                        viewModel.playheadMs = upcoming
                        delay(10)
                        continue
                    } else {
                        // Success! Mark all notes as consumed so they don't double-trigger the NEXT note
                        required.forEach { p ->
                            MidiInputManager.consumedPressTimestamps[p] = MidiInputManager.lastPressTimestamps[p] ?: 0L
                        }
                        currentWaitOnsetMs = -1L
                        chordHits.clear()
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
                viewModel.playheadMs = next
            }
            delay(10)
        }
    }

    val isWaitingAtBaseline = viewModel.isWaitModeEnabled && currentWaitOnsetMs != -1L
    val waitTargetPitches = if (isWaitingAtBaseline) notesToStrike else emptySet()

    Column(modifier = Modifier.fillMaxSize().background(ColorBg)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null 
            ) {
                viewModel.isPlaying = !viewModel.isPlaying
            }
        ) {
            FallingNotesVisualizer(noteEvents, viewModel.playheadMs, startPitch, endPitch, totalWhiteKeys, waitTargetPitches, chordHits.toSet())
            
            ModernToolbar(viewModel.playheadMs, songDurationMs, viewModel, onBack, { showSettingsDialog = true })

            if (isWaitingAtBaseline) {
                val remaining = waitTargetPitches.filter { it !in chordHits }.toSet()
                WaitModeOverlay(notes = remaining)
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(100.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                viewModel.isPlaying = !viewModel.isPlaying
            }
        ) {
            PianoKeyboardRow(startPitch, endPitch, totalWhiteKeys, sustainedPitches, waitTargetPitches, chordHits.toSet())
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

    if (showSettingsDialog) EditorSettingsDialog(viewModel.transposeOffset, { viewModel.transposeOffset = it }, { showSettingsDialog = false })
}

@Composable
private fun ModernToolbar(
    head: Long, dur: Long, viewModel: PianoWeaveViewModel, onBack: () -> Unit, onSetClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val current = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth().background(ColorSurface.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(enabled = false) {}, // Consume clicks so they don't toggle video
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
    val scale = 0.25f
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (size.width <= 0 || size.height <= 0) return@Canvas
        val tw = size.width

        // 1. Grid Lanes
        for (i in 0..numWhiteKeys) {
            val x = i * (tw / numWhiteKeys)
            drawLine(color = Color(0xFF161B22), start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 1f)
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
            
            val isAtBaseline = head >= e.startMs && head <= (e.startMs + e.durationMs)
            val isHitting = head >= e.startMs && head <= (e.startMs + 60L)
            val isWaiting = waitTargetPitches.contains(e.pitch) && head >= e.startMs - 50 && head <= e.startMs + 50
            val isSatisfied = satisfiedPitches.contains(e.pitch) && isWaiting
            val isUserMatch = isAtBaseline && MidiInputManager.pressedKeys.contains(e.pitch)

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

            drawRoundRect(brush = Brush.verticalGradient(listOf(highlightCol, baseCol), startY = y, endY = y + h), topLeft = Offset(x1 + 0.5f, y.coerceIn(-h, size.height)), size = Size(kw - 1f, h), cornerRadius = CornerRadius(6.dp.toPx()))
            
            if (isHitting || isUserMatch || isWaiting) {
                val glowCol = if (isWaiting && !isSatisfied) ColorWaitTarget else baseCol
                drawRect(brush = Brush.verticalGradient(listOf(glowCol.copy(alpha = 0.4f), Color.Transparent)), topLeft = Offset(x1, y.coerceIn(-h, size.height) + h), size = Size(kw, 35.dp.toPx()))
            }
        }

        whiteEvents.forEach { drawNote(it) }
        blackEvents.forEach { drawNote(it) }
        drawLine(ColorBaseline, Offset(0f, size.height - 1f), Offset(tw, size.height - 1f), 3.dp.toPx())
    }
}

@Composable
private fun WaitModeOverlay(notes: Set<Int>) {
    Box(Modifier.fillMaxWidth().padding(top = 64.dp), Alignment.TopCenter) {
        Surface(color = ColorWaitTarget.copy(alpha = 0.9f), shape = RoundedCornerShape(16.dp), shadowElevation = 20.dp, border = BorderStroke(2.dp, Color.White)) {
            Text(text = "STRIKE: " + notes.sorted().joinToString("   ") { midiPitchName(it) }, Modifier.padding(horizontal = 32.dp, vertical = 14.dp), fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.Black)
        }
    }
}

@Composable
private fun PianoKeyboardRow(
    start: Int, end: Int, numWhiteKeys: Int,
    sustainedPitches: Set<Int>,
    waitTargetPitches: Set<Int> = emptySet(),
    satisfiedPitches: Set<Int> = emptySet()
) {
    val pressed = MidiInputManager.pressedKeys.toSet()

    // Breathing pulse for waiting keys
    val infiniteTransition = rememberInfiniteTransition(label = "waitPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(animation = tween(600, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "pulse"
    )

    BoxWithConstraints(Modifier.fillMaxWidth().height(100.dp).background(ColorKeyBlack)) {
        val tw = constraints.maxWidth.toFloat()

        Canvas(Modifier.fillMaxSize()) {
            val wkW = tw / numWhiteKeys
            // 1. White Keys
            for (i in 0 until numWhiteKeys) {
                val x1 = i * wkW
                drawRoundRect(color = ColorKeyWhite, topLeft = Offset(x1 + 0.5f, 0f), size = Size(wkW - 1f, size.height), cornerRadius = CornerRadius(6.dp.toPx()))
                drawLine(Color.Black.copy(alpha = 0.15f), Offset(x1, 0f), Offset(x1, size.height), 1.2.dp.toPx())
            }

            // 2. Active White Key Highlights
            for (p in start..end) {
                if (isPitchBlack(p)) continue
                val (x1, x2) = getPitchXRange(p, start, tw, numWhiteKeys)
                val isPressed = pressed.contains(p)
                val isTarget = sustainedPitches.contains(p)
                val isWaiting = waitTargetPitches.contains(p)
                val isSatisfied = satisfiedPitches.contains(p)
                
                if (isPressed || isTarget || isWaiting || isSatisfied) {
                    val color = when {
                        isSatisfied -> ColorSuccess
                        isWaiting && isPressed -> ColorSuccess // Struck in chord window
                        isPressed -> ColorGold
                        isWaiting -> ColorWaitTarget.copy(alpha = pulseAlpha)
                        else -> ColorGold.copy(alpha = 0.35f)
                    }
                    drawRect(color, topLeft = Offset(x1 + 0.5f, 0f), size = Size(x2 - x1 - 1f, size.height))
                }
            }

            // 3. Black Keys
            for (p in start..end) {
                if (!isPitchBlack(p)) continue
                val (x1, x2) = getPitchXRange(p, start, tw, numWhiteKeys)
                val isPressed = pressed.contains(p)
                val isTarget = sustainedPitches.contains(p)
                val isWaiting = waitTargetPitches.contains(p)
                val isSatisfied = satisfiedPitches.contains(p)
                
                val highlightColor = when {
                    isSatisfied -> ColorSuccess
                    isWaiting && isPressed -> ColorSuccess
                    isPressed -> ColorGold
                    isWaiting -> ColorWaitTarget.copy(alpha = pulseAlpha)
                    isTarget -> ColorGold.copy(alpha = 0.4f)
                    else -> ColorKeyBlack
                }
                val h = size.height * 0.7f
                drawRoundRect(color = highlightColor, topLeft = Offset(x1 + 0.5f, 0f), size = Size(x2 - x1 - 1f, h), cornerRadius = CornerRadius(4.dp.toPx()))
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
    Row(modifier = Modifier.fillMaxWidth().background(ColorSurface).padding(horizontal = 20.dp, vertical = 10.dp).clickable(enabled = false) {}, // Consume clicks
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconButton(onClick = { viewModel.isPlaying = !viewModel.isPlaying }, Modifier.size(56.dp).background(ColorGold, CircleShape)) { Icon(if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(36.dp)) }
            IconButton(onClick = { onResetHead() }, modifier = Modifier.size(44.dp).background(ColorSurface, CircleShape).border(1.dp, ColorSlate, CircleShape)) { Icon(Icons.Default.Replay, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
        }
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val fullWidth = maxWidth
                Box(modifier = Modifier.fillMaxWidth(0.96f).height(8.dp).background(ColorSlate, CircleShape))
                if (viewModel.isLoopingEnabled) {
                    val sPerc = viewModel.loopStartMs.toFloat() / dur.coerceAtLeast(1) ; val ePerc = viewModel.loopEndMs.toFloat() / dur.coerceAtLeast(1)
                    Box(modifier = Modifier.fillMaxWidth(0.96f * (ePerc - sPerc)).align(Alignment.CenterStart).offset(x = (fullWidth * 0.02f) + (fullWidth * 0.96f * sPerc)).height(8.dp).background(ColorGold.copy(alpha = 0.5f)))
                    RangeSlider(value = viewModel.loopStartMs.toFloat()..viewModel.loopEndMs.toFloat(), onValueChange = { viewModel.loopStartMs = it.start.toLong() ; viewModel.loopEndMs = it.endInclusive.toLong() }, valueRange = 0f..dur.toFloat().coerceAtLeast(1f), colors = SliderDefaults.colors(thumbColor = ColorGold, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent), modifier = Modifier.fillMaxWidth().height(32.dp).offset(y = (-16).dp), startThumb = { Box(modifier = Modifier.size(32.dp).background(ColorGold, CircleShape).border(2.dp, Color.White.copy(0.6f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.ChevronLeft, null, tint = Color.Black, modifier = Modifier.size(20.dp)) } }, endThumb = { Box(modifier = Modifier.size(32.dp).background(ColorGold, CircleShape).border(2.0.dp, Color.White.copy(0.6f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.ChevronRight, null, tint = Color.Black, modifier = Modifier.size(20.dp)) } })
                }
                Slider(value = viewModel.playheadMs.toFloat().coerceIn(0f, dur.toFloat()), onValueChange = { onSeekState(true) ; viewModel.playheadMs = it.toLong() }, onValueChangeFinished = { onSeekState(false) }, valueRange = 0f..dur.toFloat().coerceAtLeast(1f), colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = ColorGold, inactiveTrackColor = Color.Transparent), modifier = Modifier.fillMaxWidth().height(32.dp))
            }
        }
        Spacer(modifier = Modifier.width(20.dp))
        IconButton(onClick = { viewModel.isLoopingEnabled = !viewModel.isLoopingEnabled }, modifier = Modifier.size(48.dp).background(if (viewModel.isLoopingEnabled) ColorGold else ColorSurface, RoundedCornerShape(12.dp)).border(1.dp, if(!viewModel.isLoopingEnabled) ColorSlate else Color.Transparent, RoundedCornerShape(12.dp))) { Icon(Icons.Default.Repeat, null, tint = if (viewModel.isLoopingEnabled) Color.Black else ColorGold, modifier = Modifier.size(26.dp)) }
    }
}

@Composable
private fun EditorSettingsDialog(offset: Int, onChange: (Int)->Unit, onDismiss: ()->Unit) {
    Dialog(onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = ColorSurface, contentColor = Color.White, border = BorderStroke(1.dp, ColorSlate)) {
            Column(modifier = Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                Text("Piano Editor", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = ColorGold)
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Transposition", color = ColorTextDim, fontWeight = FontWeight.Bold)
                        Text("${if (offset > 0) "+" else ""}$offset", color = ColorGold, fontWeight = FontWeight.Black)
                    }
                    Slider(value = offset.toFloat(), onValueChange = { onChange(it.toInt()) }, valueRange = -12f..12f, steps = 23, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = ColorGold))
                }
                Button(onClick = onDismiss, Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = ColorGold, contentColor = Color.Black), shape = RoundedCornerShape(14.dp)) { Text("DONE", fontWeight = FontWeight.Black, fontSize = 16.sp) }
            }
        }
    }
}

@Composable
private fun MidiErrorScreen(song: StoredMidi, error: String, onBack: () -> Unit) {
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
