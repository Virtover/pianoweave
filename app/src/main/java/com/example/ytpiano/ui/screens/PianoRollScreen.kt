package com.example.ytpiano.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ytpiano.audio.PianoPlayer
import com.example.ytpiano.midi.MidiInputManager
import com.example.ytpiano.midi.MidiNoteEvent
import com.example.ytpiano.midi.SimpleMidiReader
import com.example.ytpiano.midi.StoredMidi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import androidx.compose.foundation.gestures.detectTapGestures

// --- Ultra Pro Piano Theme (Max Fidelity Visuals) ---
private val ColorBg = Color(0xFF0D1117)
private val ColorSurface = Color(0xFF161B22)
private val ColorGold = Color(0xFFD4AF37)
private val ColorGoldLight = Color(0xFFFFE082) 
private val ColorUpcomingNote = Color(0xFF1F2937) 
private val ColorSlate = Color(0xFF30363D) 
private val ColorSuccess = Color(0xFF2EA043) 
private val ColorSuccessLight = Color(0xFF69C67E) 
private val ColorTarget = Color(0xFFF39C12) 
private val ColorBaseline = Color(0xFFF85149) 
private val ColorTextDim = Color(0xFF8B949E)
private val ColorKeyWhite = Color(0xFF808E95) // Darker Blue-Grey base
private val ColorKeyBlack = Color(0xFF121212)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PianoRollScreen(
    song: StoredMidi,
    onBack: () -> Unit
) {
    var noteEvents by remember(song) { mutableStateOf<List<MidiNoteEvent>?>(null) }
    var parseError by remember(song) { mutableStateOf<String?>(null) }

    LaunchedEffect(song) {
        noteEvents = null
        parseError = null
        try {
            val parsed = withContext(Dispatchers.IO) {
                SimpleMidiReader.parse(song.file)
            }
            noteEvents = parsed
        } catch (e: Exception) {
            parseError = e.message?.takeIf { it.isNotBlank() } ?: "MIDI load failure"
        }
    }

    when {
        parseError != null -> MidiErrorScreen(song, parseError!!, onBack)
        noteEvents == null -> MidiLoadingScreen()
        else -> ModernPianoPlayerContent(song, noteEvents!!, onBack)
    }
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
    onBack: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(true) }
    var playheadMs by remember { mutableLongStateOf(0L) }
    var speedMultiplier by remember { mutableFloatStateOf(1.0f) }
    var isWaitModeEnabled by remember { mutableStateOf(false) }
    var isLoopingEnabled by remember { mutableStateOf(false) }
    var transposeOffset by remember { mutableIntStateOf(0) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    var isUserSeeking by remember { mutableStateOf(false) }

    val noteEvents = remember(rawEvents, transposeOffset) {
        rawEvents.map { it.copy(pitch = it.pitch + transposeOffset) }
    }

    val songDurationMs = remember(noteEvents) {
        noteEvents.maxOfOrNull { it.startMs + it.durationMs } ?: 10_000L
    }

    var loopStartMs by remember { mutableLongStateOf(0L) }
    var loopEndMs by remember { mutableLongStateOf(songDurationMs) }

    val startPitch = 36
    val endPitch = 96
    val totalKeys = endPitch - startPitch + 1

    var lastTriggeredHeadMs by remember { mutableLongStateOf(-1L) }

    // Logic to find the next required notes for Wait Mode UI overlay
    val currentNextOnset = remember(noteEvents, playheadMs) {
        noteEvents.filter { it.startMs >= playheadMs }.minOfOrNull { it.startMs }
    }
    val currentNotesToStrike = remember(noteEvents, currentNextOnset) {
        if (currentNextOnset == null) emptySet<Int>()
        else noteEvents.filter { abs(it.startMs - currentNextOnset) <= 30L }.map { it.pitch }.toSet()
    }

    LaunchedEffect(isPlaying, isUserSeeking) {
        if (!isPlaying || isUserSeeking) PianoPlayer.stopAllNotes()
    }

    // Main Playback Engine
    // loop bounds removed from keys to prevent "machine gun" re-launch during slider dragging
    LaunchedEffect(isPlaying, speedMultiplier, isWaitModeEnabled, noteEvents) {
        if (!isPlaying) return@LaunchedEffect

        // Resumed trigger logic: only once per start/jump
        val resumed = noteEvents.filter { playheadMs >= it.startMs && playheadMs < (it.startMs + it.durationMs) }
        resumed.forEach { PianoPlayer.noteOn(it.pitch, it.velocity) }
        
        lastTriggeredHeadMs = playheadMs
        var lastTime = System.nanoTime()

        while (isPlaying) {
            val now = System.nanoTime()
            val dt = (now - lastTime) / 1_000_000L
            lastTime = now

            // PRECISE WAIT MODE: Re-calculate upcoming onsets on every tick
            val targetNext = playheadMs + (dt * speedMultiplier).toLong()
            if (isWaitModeEnabled) {
                val upcoming = noteEvents.filter { it.startMs >= playheadMs && it.startMs <= targetNext }
                    .minOfOrNull { it.startMs }
                
                if (upcoming != null) {
                    val requiredPitches = noteEvents.filter { abs(it.startMs - upcoming) <= 30L }.map { it.pitch }.toSet()
                    val currentlyPressed = MidiInputManager.pressedKeys.toSet()
                    if (requiredPitches.any { it in startPitch..endPitch && it !in currentlyPressed }) {
                        playheadMs = upcoming
                        delay(10)
                        continue
                    }
                }
            }

            val next = targetNext
            
            // Audio Triggering
            if (abs(next - lastTriggeredHeadMs) < 500L) {
                val triggered = noteEvents.filter { it.startMs > lastTriggeredHeadMs && it.startMs <= next }
                triggered.forEach { PianoPlayer.noteOn(it.pitch, it.velocity) }
            }
            lastTriggeredHeadMs = next

            if (isLoopingEnabled && next >= loopEndMs) {
                playheadMs = loopStartMs ; lastTriggeredHeadMs = loopStartMs ; PianoPlayer.stopAllNotes()
                val loopNotes = noteEvents.filter { loopStartMs >= it.startMs && loopStartMs < (it.startMs + it.durationMs) }
                loopNotes.forEach { PianoPlayer.noteOn(it.pitch, it.velocity) }
            } else if (!isLoopingEnabled && next >= songDurationMs) {
                playheadMs = songDurationMs ; isPlaying = false
            } else {
                playheadMs = next
            }
            delay(10)
        }
    }

    val sustainedPitches = remember(noteEvents, playheadMs) {
        noteEvents.filter { playheadMs >= it.startMs && playheadMs <= (it.startMs + it.durationMs) }.map { it.pitch }.toSet()
    }

    Column(modifier = Modifier.fillMaxSize().background(ColorBg)) {
        ModernToolbar(song.metadata.title, playheadMs, songDurationMs, isWaitModeEnabled, speedMultiplier, { speedMultiplier = it }, { isWaitModeEnabled = !isWaitModeEnabled }, { showSettingsDialog = true }, onBack)

        Box(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            FallingNotesVisualizer(noteEvents, playheadMs, startPitch, endPitch, totalKeys)
            if (isWaitModeEnabled && currentNotesToStrike.isNotEmpty() && currentNextOnset != null && playheadMs >= currentNextOnset - 500) {
                WaitModeOverlay(notes = currentNotesToStrike)
            }
        }

        PianoKeyboardRow(startPitch, endPitch, sustainedPitches)

        MediaTimelineFooter(
            playheadMs, songDurationMs, isPlaying, isLoopingEnabled, loopStartMs, loopEndMs,
            { isPlaying = !isPlaying },
            { playheadMs = if (isLoopingEnabled) loopStartMs else 0L ; lastTriggeredHeadMs = playheadMs ; PianoPlayer.stopAllNotes() },
            { playheadMs = it ; lastTriggeredHeadMs = it ; PianoPlayer.stopAllNotes() },
            { isUserSeeking = it },
            { isLoopingEnabled = !isLoopingEnabled },
            { s, e -> 
                loopStartMs = s ; loopEndMs = e 
                lastTriggeredHeadMs = playheadMs // Prevents machine gun on drag
            }
        )
    }

    if (showSettingsDialog) EditorSettingsDialog(transposeOffset, { transposeOffset = it }, { showSettingsDialog = false })
}

@Composable
private fun ModernToolbar(
    title: String, head: Long, dur: Long, isWait: Boolean, speed: Float,
    onSpeedChange: (Float) -> Unit, onWaitToggle: () -> Unit, onSetClick: () -> Unit, onBack: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    Row(
        modifier = Modifier.fillMaxWidth().background(ColorSurface).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
        
        if (!isPortrait) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.weight(1f), overflow = TextOverflow.Ellipsis)
        }

        Text("${formatTime(head)} / ${formatTime(dur)}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))

        if (isPortrait) Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (isPortrait) 6.dp else 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(if (isPortrait) 4.dp else 8.dp)) {
                listOf(0.25f, 0.5f, 1.0f).forEach { s ->
                    val isSelected = speed == s
                    Box(Modifier.size(if (isPortrait) 32.dp else 36.dp).background(if (isSelected) ColorGold else ColorSlate, CircleShape).clickable { onSpeedChange(s) }, Alignment.Center) {
                        Text("${s}x", fontSize = 10.sp, fontWeight = FontWeight.Black, color = if (isSelected) Color.Black else Color.White)
                    }
                }
            }
            
            Box(
                modifier = Modifier
                    .height(38.dp)
                    .then(if (isPortrait) Modifier.width(38.dp) else Modifier.wrapContentWidth())
                    .background(if (isWait) ColorGold else ColorSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, if (isWait) ColorGold else ColorSlate, RoundedCornerShape(10.dp))
                    .clickable { onWaitToggle() }
                    .padding(horizontal = if (isPortrait) 0.dp else 14.dp),
                Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Timer, null, tint = if (isWait) Color.Black else ColorGold, modifier = Modifier.size(18.dp))
                    if (!isPortrait) {
                        Spacer(Modifier.width(8.dp))
                        Text(text = "Wait mode", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (isWait) Color.Black else ColorGold, maxLines = 1, softWrap = false)
                    }
                }
            }

            Box(Modifier.size(38.dp).background(ColorSurface, RoundedCornerShape(10.dp)).border(1.dp, ColorSlate, RoundedCornerShape(10.dp)).clickable { onSetClick() }, Alignment.Center) {
                Icon(Icons.Default.Tune, null, tint = ColorGold, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun getXPx(index: Int, count: Int, totalWidth: Float): Float {
    val bw = (totalWidth / count).toInt()
    val rem = (totalWidth % count).toInt()
    return (index * bw + minOf(index, rem)).toFloat()
}

@Composable
private fun FallingNotesVisualizer(
    events: List<MidiNoteEvent>, head: Long, start: Int, end: Int, count: Int
) {
    val scale = 0.25f
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (size.width <= 0 || size.height <= 0) return@Canvas
        val tw = size.width

        for (p in start..end) {
            val x = getXPx(p - start, count, tw)
            drawLine(color = if (isPitchBlack(p)) Color(0xFF161B22) else Color(0xFF0D1117), start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 1f)
        }

        val barIntervalMs = 2000L ; val firstBar = (head / barIntervalMs) * barIntervalMs ; val lastMs = head + (size.height / scale).toLong()
        for (ms in firstBar..lastMs step barIntervalMs) {
            val y = size.height - ((ms - head) * scale)
            if (y in 0f..size.height) drawLine(color = Color.White.copy(alpha = 0.1f), start = Offset(0f, y), end = Offset(tw, y), strokeWidth = 1.dp.toPx())
        }

        events.filter { (it.startMs + it.durationMs) >= head && it.startMs <= lastMs }.forEach { e ->
            val index = e.pitch - start
            val x1 = getXPx(index, count, tw) ; val x2 = getXPx(index + 1, count, tw) ; val kw = x2 - x1
            val h = (e.durationMs * scale).coerceAtLeast(12f)
            val y = size.height - ((e.startMs - head) * scale) - h
            val isAtBaseline = head >= e.startMs && head <= (e.startMs + e.durationMs)
            val isHitting = head >= e.startMs && head <= (e.startMs + 60L)
            val isUserMatch = isAtBaseline && MidiInputManager.pressedKeys.contains(e.pitch)

            val baseCol = when { isUserMatch -> ColorSuccess ; isHitting -> ColorTarget ; isAtBaseline -> ColorGold ; else -> ColorUpcomingNote }
            val highlightCol = when { isUserMatch -> ColorSuccessLight ; isHitting -> ColorGoldLight ; isAtBaseline -> ColorGoldLight.copy(alpha = 0.8f) ; else -> baseCol.copy(alpha = 0.6f) }
            
            // Standardized width match for semitone lanes
            drawRoundRect(brush = Brush.verticalGradient(listOf(highlightCol, baseCol), startY = y, endY = y + h), topLeft = Offset(x1 + 0.5f, y.coerceIn(-h, size.height)), size = Size(kw - 1f, h), cornerRadius = CornerRadius(6.dp.toPx()))
            if (isHitting || isUserMatch) {
                drawRect(brush = Brush.verticalGradient(listOf(baseCol.copy(alpha = 0.4f), Color.Transparent)), topLeft = Offset(x1, y.coerceIn(-h, size.height) + h), size = Size(kw, 35.dp.toPx()))
                drawCircle(brush = Brush.radialGradient(listOf(baseCol.copy(alpha = 0.3f), Color.Transparent), center = Offset(x1 + kw/2, size.height), radius = 18.dp.toPx()), center = Offset(x1 + kw/2, size.height), radius = 18.dp.toPx())
            }
        }
        drawLine(ColorBaseline, Offset(0f, size.height - 1f), Offset(tw, size.height - 1f), 3.dp.toPx())
    }
}

@Composable
private fun WaitModeOverlay(notes: Set<Int>) {
    Box(Modifier.fillMaxWidth().padding(top = 32.dp), Alignment.TopCenter) {
        Surface(color = ColorGold, shape = RoundedCornerShape(16.dp), shadowElevation = 20.dp, border = BorderStroke(2.dp, Color.White.copy(0.7f))) {
            Text(text = "STRIKE: " + notes.sorted().joinToString("   ") { midiPitchName(it) }, Modifier.padding(horizontal = 32.dp, vertical = 14.dp), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
        }
    }
}

@Composable
private fun PianoKeyboardRow(start: Int, end: Int, sustainedPitches: Set<Int>) {
    val count = end - start + 1
    BoxWithConstraints(Modifier.fillMaxWidth().height(100.dp).background(ColorKeyWhite)) {
        val tw = constraints.maxWidth.toFloat() ; val pressed = MidiInputManager.pressedKeys.toSet()
        Canvas(Modifier.fillMaxSize()) {
            for (p in start..end) {
                if (isPitchBlack(p)) continue
                val index = p - start
                val x1 = getXPx(index, count, tw) ; val x2 = getXPx(index + 1, count, tw)
                var dx1 = x1 ; var dx2 = x2
                if (p > start && isPitchBlack(p - 1)) dx1 = (x1 + getXPx(index - 1, count, tw)) / 2f
                if (isPitchBlack(p + 1)) dx2 = (x2 + getXPx(index + 2, count, tw)) / 2f
                val isPressed = pressed.contains(p) ; val isTarget = sustainedPitches.contains(p)
                val highlightColor = when { isPressed && isTarget -> ColorSuccess ; isPressed -> ColorGold ; isTarget -> ColorGold.copy(alpha = 0.35f) ; else -> ColorKeyWhite }
                val shineColor = when { isPressed && isTarget -> ColorSuccessLight ; isPressed -> ColorGoldLight ; isTarget -> highlightColor.copy(alpha = 0.5f) ; else -> ColorKeyWhite }
                drawRoundRect(brush = Brush.verticalGradient(listOf(shineColor, highlightColor), startY = 0f, endY = size.height), topLeft = Offset(dx1 + 0.5f, 0f), size = Size(dx2 - dx1 - 1f, size.height), cornerRadius = CornerRadius(6.dp.toPx()))
                if (isPressed) drawRect(Color.Black.copy(alpha = 0.1f), topLeft = Offset(dx1, 0f), size = Size(dx2 - dx1, size.height))
                drawLine(Color.Black.copy(alpha = 0.25f), Offset(dx1, 0f), Offset(dx1, size.height), 1.2.dp.toPx())
                if (p == end) drawLine(Color.Black.copy(alpha = 0.25f), Offset(dx2, 0f), Offset(dx2, size.height), 1.2.dp.toPx())
            }
            for (p in start..end) {
                if (!isPitchBlack(p)) continue
                val index = p - start
                val x1 = getXPx(index, count, tw) ; val x2 = getXPx(index + 1, count, tw)
                val isPressed = pressed.contains(p) ; val isTarget = sustainedPitches.contains(p)
                val highlightColor = when { isPressed && isTarget -> ColorSuccess ; isPressed -> ColorGold ; isTarget -> ColorGold.copy(alpha = 0.4f) ; else -> ColorKeyBlack }
                val shineColor = when { isPressed && isTarget -> ColorSuccessLight ; isPressed -> ColorGoldLight ; isTarget -> highlightColor.copy(alpha = 0.7f) ; else -> ColorKeyBlack }
                val h = size.height * 0.7f
                drawRoundRect(brush = Brush.verticalGradient(listOf(shineColor, highlightColor), startY = 0f, endY = h), topLeft = Offset(x1 + 0.5f, 0f), size = Size(x2 - x1 - 1f, h), cornerRadius = CornerRadius(4.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxSize()) {
            for (p in start..end) {
                Box(Modifier.weight(1f).fillMaxHeight().pointerInput(p) { detectTapGestures(onPress = { try { MidiInputManager.simulateNoteOn(p) ; PianoPlayer.noteOn(p) ; awaitRelease() } finally { MidiInputManager.simulateNoteOff(p) ; PianoPlayer.noteOff(p) } }) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaTimelineFooter(
    head: Long, dur: Long, isPlaying: Boolean, isLoop: Boolean, lStart: Long, lEnd: Long,
    onPlay: () -> Unit, onRewind: () -> Unit, onSeek: (Long) -> Unit, onSeekState: (Boolean) -> Unit,
    onLoop: () -> Unit, onRange: (Long, Long) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(ColorSurface).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconButton(onClick = onPlay, Modifier.size(56.dp).background(ColorGold, CircleShape)) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(36.dp)) }
            IconButton(onClick = onRewind, modifier = Modifier.size(44.dp).background(ColorSurface, CircleShape).border(1.dp, ColorSlate, CircleShape)) { Icon(Icons.Default.Replay, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val fullWidth = maxWidth
                Box(modifier = Modifier.fillMaxWidth(0.96f).height(8.dp).background(ColorSlate, CircleShape))
                if (isLoop) {
                    val sPerc = lStart.toFloat() / dur.coerceAtLeast(1) ; val ePerc = lEnd.toFloat() / dur.coerceAtLeast(1)
                    Box(modifier = Modifier.fillMaxWidth(0.96f * (ePerc - sPerc)).align(Alignment.CenterStart).offset(x = (fullWidth * 0.02f) + (fullWidth * 0.96f * sPerc)).height(8.dp).background(ColorGold.copy(alpha = 0.5f)))
                    RangeSlider(value = lStart.toFloat()..lEnd.toFloat(), onValueChange = { onRange(it.start.toLong(), it.endInclusive.toLong()) }, valueRange = 0f..dur.toFloat().coerceAtLeast(1f), colors = SliderDefaults.colors(thumbColor = ColorGold, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent), modifier = Modifier.fillMaxWidth().height(32.dp).offset(y = (-16).dp), startThumb = { Box(modifier = Modifier.size(32.dp).background(ColorGold, CircleShape).border(2.dp, Color.White.copy(0.6f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.ChevronLeft, null, tint = Color.Black, modifier = Modifier.size(20.dp)) } }, endThumb = { Box(modifier = Modifier.size(32.dp).background(ColorGold, CircleShape).border(2.0.dp, Color.White.copy(0.6f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.ChevronRight, null, tint = Color.Black, modifier = Modifier.size(20.dp)) } })
                }
                Slider(value = head.toFloat().coerceIn(0f, dur.toFloat()), onValueChange = { onSeekState(true) ; onSeek(it.toLong()) }, onValueChangeFinished = { onSeekState(false) }, valueRange = 0f..dur.toFloat().coerceAtLeast(1f), colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = ColorGold, inactiveTrackColor = Color.Transparent), modifier = Modifier.fillMaxWidth().height(32.dp))
            }
        }
        Spacer(modifier = Modifier.width(20.dp))
        IconButton(onClick = onLoop, modifier = Modifier.size(48.dp).background(if (isLoop) ColorGold else ColorSurface, RoundedCornerShape(12.dp)).border(1.dp, if(!isLoop) ColorSlate else Color.Transparent, RoundedCornerShape(12.dp))) { Icon(Icons.Default.Repeat, null, tint = if (isLoop) Color.Black else ColorGold, modifier = Modifier.size(26.dp)) }
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
                        Text("${if (offset > 0) "+" else ""}$offset", color = ColorGold, fontWeight = FontWeight.Black, style = TextStyle(shadow = Shadow(Color.Black, blurRadius = 4f)))
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
