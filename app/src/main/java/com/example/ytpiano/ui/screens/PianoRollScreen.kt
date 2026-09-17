package com.example.ytpiano.ui.screens

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
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

// --- Ultra Pro Piano Theme ---
private val ColorBg = Color(0xFF0D1117)
private val ColorSurface = Color(0xFF161B22)
private val ColorGold = Color(0xFFD4AF37) // Signature Gold
private val ColorUpcomingNote = Color(0xFF424B5B) 
private val ColorSlate = Color(0xFF30363D) 
private val ColorSuccess = Color(0xFF2EA043) // Success Green
private val ColorTarget = Color(0xFFF39C12) // Vibrant On-Hit Gold
private val ColorBaseline = Color(0xFFF85149) // Neon Red Hitline
private val ColorTextDim = Color(0xFF8B949E)
private val ColorKeyWhite = Color(0xFFE6E6E6)
private val ColorKeyBlack = Color(0xFF1A1A1A)

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
            parseError = e.message?.takeIf { it.isNotBlank() } ?: "MIDI loading failed"
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
            Text("Building performance data...", color = ColorGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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

    // Silencing on pause/seek
    LaunchedEffect(isPlaying, isUserSeeking) {
        if (!isPlaying || isUserSeeking) {
            PianoPlayer.stopAllNotes()
        }
    }

    // Capture notes to strike for Wait Mode
    val nextOnset = remember(noteEvents, playheadMs) {
        noteEvents.filter { it.startMs >= playheadMs }.minOfOrNull { it.startMs }
    }
    val notesAtOnset = remember(noteEvents, nextOnset) {
        if (nextOnset == null) emptySet<Int>()
        else noteEvents.filter { abs(it.startMs - nextOnset) <= 30L }.map { it.pitch }.toSet()
    }

    // Playback Engine
    LaunchedEffect(isPlaying, speedMultiplier, isWaitModeEnabled, isLoopingEnabled, loopStartMs, loopEndMs, noteEvents) {
        if (!isPlaying) return@LaunchedEffect

        // Trigger notes under playhead when resuming
        val resumed = noteEvents.filter { playheadMs >= it.startMs && playheadMs < (it.startMs + it.durationMs) }
        resumed.forEach { PianoPlayer.noteOn(it.pitch) }
        
        lastTriggeredHeadMs = playheadMs
        var lastTime = System.nanoTime()

        while (isPlaying) {
            val now = System.nanoTime()
            val dt = (now - lastTime) / 1_000_000L
            lastTime = now

            if (isWaitModeEnabled && nextOnset != null) {
                val lookAhead = 10L
                if (playheadMs >= nextOnset - lookAhead) {
                    val notesAtOnsetNow = noteEvents.filter { abs(it.startMs - nextOnset) <= 30L }.map { it.pitch }.toSet()
                    val pressed = MidiInputManager.pressedKeys.toSet()
                    if (notesAtOnsetNow.any { it in startPitch..endPitch && it !in pressed }) {
                        delay(16)
                        continue
                    }
                }
            }

            val next = playheadMs + (dt * speedMultiplier).toLong()
            
            if (abs(next - lastTriggeredHeadMs) < 500L) {
                val triggered = noteEvents.filter { it.startMs > lastTriggeredHeadMs && it.startMs <= next }
                triggered.forEach { PianoPlayer.noteOn(it.pitch) }
            }
            lastTriggeredHeadMs = next

            if (isLoopingEnabled && next >= loopEndMs) {
                playheadMs = loopStartMs
                lastTriggeredHeadMs = loopStartMs
                PianoPlayer.stopAllNotes()
                val loopNotes = noteEvents.filter { loopStartMs >= it.startMs && loopStartMs < (it.startMs + it.durationMs) }
                loopNotes.forEach { PianoPlayer.noteOn(it.pitch) }
            } else if (!isLoopingEnabled && next >= songDurationMs) {
                playheadMs = songDurationMs 
                isPlaying = false
            } else {
                playheadMs = next
            }
            delay(12)
        }
    }

    val sustainedPitches = remember(noteEvents, playheadMs) {
        noteEvents.filter { playheadMs >= it.startMs && playheadMs <= (it.startMs + it.durationMs) }.map { it.pitch }.toSet()
    }

    Column(modifier = Modifier.fillMaxSize().background(ColorBg)) {
        ModernToolbar(
            title = song.metadata.title,
            head = playheadMs,
            dur = songDurationMs,
            isWait = isWaitModeEnabled,
            speed = speedMultiplier,
            onSpeedChange = { speedMultiplier = it },
            onWaitToggle = { isWaitModeEnabled = !isWaitModeEnabled },
            onSetClick = { showSettingsDialog = true },
            onBack = onBack
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            FallingNotesVisualizer(noteEvents, playheadMs, startPitch, endPitch, totalKeys)
            
            if (isWaitModeEnabled && notesAtOnset.isNotEmpty() && nextOnset != null && playheadMs >= nextOnset - 500) {
                WaitModeOverlay(notes = notesAtOnset)
            }
        }

        PianoKeyboardRow(startPitch, endPitch, sustainedPitches)

        MediaTimelineFooter(
            head = playheadMs,
            dur = songDurationMs,
            isPlaying = isPlaying,
            isLoop = isLoopingEnabled,
            lStart = loopStartMs,
            lEnd = loopEndMs,
            onPlay = { isPlaying = !isPlaying },
            onRewind = { 
                playheadMs = if (isLoopingEnabled) loopStartMs else 0L 
                lastTriggeredHeadMs = playheadMs
                PianoPlayer.stopAllNotes()
            },
            onSeek = { 
                playheadMs = it 
                lastTriggeredHeadMs = it
                PianoPlayer.stopAllNotes()
            },
            onSeekState = { isUserSeeking = it },
            onLoop = { isLoopingEnabled = !isLoopingEnabled },
            onRange = { s, e -> loopStartMs = s ; loopEndMs = e }
        )
    }

    if (showSettingsDialog) EditorSettingsDialog(transposeOffset, { transposeOffset = it }, { showSettingsDialog = false })
}

@Composable
private fun ModernToolbar(
    title: String, head: Long, dur: Long, isWait: Boolean, speed: Float,
    onSpeedChange: (Float) -> Unit, onWaitToggle: () -> Unit, onSetClick: () -> Unit, onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(ColorSurface).padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
        Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
        
        Text(
            text = "${formatTime(head)} / ${formatTime(dur)}",
            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp)
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.25f, 0.5f, 1.0f).forEach { s ->
                    val isSelected = speed == s
                    Box(
                        modifier = Modifier.size(35.dp).background(if (isSelected) ColorGold else ColorSlate, CircleShape).clickable { onSpeedChange(s) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${s}x", fontSize = 10.sp, fontWeight = FontWeight.Black, color = if (isSelected) Color.Black else Color.White)
                    }
                }
            }

            Box(
                modifier = Modifier.height(36.dp).background(if (isWait) ColorGold else ColorSurface, RoundedCornerShape(8.dp)).border(1.dp, if (isWait) ColorGold else ColorSlate, RoundedCornerShape(8.dp)).clickable { onWaitToggle() }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, null, tint = if (isWait) Color.Black else ColorGold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Wait mode", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isWait) Color.Black else ColorGold)
                }
            }

            Box(
                modifier = Modifier.size(36.dp).background(ColorSurface, RoundedCornerShape(8.dp)).border(1.dp, ColorSlate, RoundedCornerShape(8.dp)).clickable { onSetClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Tune, null, tint = ColorGold, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun FallingNotesVisualizer(
    events: List<MidiNoteEvent>, head: Long, start: Int, end: Int, count: Int
) {
    val scale = 0.25f
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (size.width <= 0 || size.height <= 0) return@Canvas
        val kw = size.width / count
        
        for (p in start..end) {
            val x = (p - start) * kw
            drawLine(color = if (isPitchBlack(p)) Color(0xFF161B22) else Color(0xFF0D1117), start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 1f)
        }

        val barIntervalMs = 2000L 
        val firstVisibleBar = (head / barIntervalMs) * barIntervalMs
        val lastVisibleMs = head + (size.height / scale).toLong()
        
        for (ms in firstVisibleBar..lastVisibleMs step barIntervalMs) {
            val y = size.height - ((ms - head) * scale)
            if (y in 0f..size.height) {
                drawLine(color = Color.White.copy(alpha = 0.15f), start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1.5.dp.toPx())
            }
        }

        events.forEach { e ->
            if (e.pitch !in start..end) return@forEach
            val endMs = e.startMs + e.durationMs
            if (endMs < head || e.startMs > lastVisibleMs) return@forEach
            
            val x = (e.pitch - start) * kw
            val h = (e.durationMs * scale).coerceAtLeast(10f)
            val y = size.height - ((e.startMs - head) * scale) - h
            
            val isAtBaseline = head >= e.startMs && head <= endMs
            val isHittingOnset = head >= e.startMs && head <= (e.startMs + 60L)
            val isUserMatch = isAtBaseline && MidiInputManager.pressedKeys.contains(e.pitch)

            val color = when {
                isUserMatch -> ColorSuccess
                isHittingOnset -> ColorTarget
                isAtBaseline -> ColorGold.copy(alpha = 0.75f)
                else -> ColorUpcomingNote 
            }
            
            drawRoundRect(color = color, topLeft = Offset(x + 3f, y.coerceIn(-h, size.height)), size = Size(kw - 6f, h), cornerRadius = CornerRadius(4.dp.toPx()))
            
            if (isHittingOnset || isUserMatch) {
                drawRect(brush = Brush.verticalGradient(listOf(color.copy(0.4f), Color.Transparent)), topLeft = Offset(x, y.coerceIn(-h, size.height) + h), size = Size(kw, 40.dp.toPx()))
            }
        }
        drawLine(ColorBaseline, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 2f)
    }
}

@Composable
private fun WaitModeOverlay(notes: Set<Int>) {
    Box(Modifier.fillMaxWidth().padding(top = 24.dp), Alignment.TopCenter) {
        Surface(color = ColorGold, shape = RoundedCornerShape(12.dp), shadowElevation = 12.dp, border = BorderStroke(2.dp, Color.White.copy(0.5f))) {
            Text(text = "STRIKE: " + notes.sorted().joinToString("  ") { midiPitchName(it) }, Modifier.padding(horizontal = 24.dp, vertical = 10.dp), fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.Black)
        }
    }
}

@Composable
private fun PianoKeyboardRow(start: Int, end: Int, sustainedPitches: Set<Int>) {
    Row(Modifier.fillMaxWidth().height(80.dp).background(ColorBg)) {
        val pressed = MidiInputManager.pressedKeys.toSet()
        for (p in start..end) {
            val isBlack = isPitchBlack(p)
            val isPressed = pressed.contains(p)
            val isTarget = sustainedPitches.contains(p)
            val baseColor = if (isBlack) ColorKeyBlack else ColorKeyWhite
            
            // Refined keyboard highlighting
            val highlightColor = when {
                isPressed && isTarget -> ColorSuccess // Correct hit
                isPressed -> ColorGold // Pressed but not target
                isTarget -> ColorGold.copy(alpha = 0.4f) // Target note (soft highlight, not dark grey)
                else -> baseColor
            }
            
            val gradient = Brush.verticalGradient(
                colors = if (isPressed || isTarget) {
                    listOf(highlightColor, highlightColor.copy(alpha = 0.7f))
                } else {
                    listOf(baseColor, baseColor)
                },
                startY = 0f,
                endY = 200f
            )

            Box(
                Modifier.weight(1f).fillMaxHeight().padding(0.5.dp)
                    .background(gradient, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                    .clickable { 
                        if (MidiInputManager.pressedKeys.contains(p)) { 
                            MidiInputManager.simulateNoteOff(p) ; PianoPlayer.noteOff(p) 
                        } else { 
                            MidiInputManager.simulateNoteOn(p) ; PianoPlayer.noteOn(p) 
                        } 
                    }, 
                Alignment.BottomCenter
            ) {
                val label = when (p) { 36->"C1";48->"C2";60->"C3";72->"C4";84->"C5";96->"C6"; else->"" }
                if (label.isNotEmpty()) Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isPressed) Color.Black else ColorTextDim, modifier = Modifier.padding(bottom = 6.dp))
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
        modifier = Modifier.fillMaxWidth().background(ColorSurface).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton(onClick = onPlay, Modifier.size(48.dp).background(ColorGold, CircleShape)) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(32.dp)) }
            IconButton(onClick = onRewind, modifier = Modifier.size(36.dp).background(ColorSurface, CircleShape).border(1.dp, ColorSlate, CircleShape)) { Icon(Icons.Default.Replay, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val fullWidth = maxWidth
                Box(modifier = Modifier.fillMaxWidth(0.96f).height(6.dp).background(ColorSlate, CircleShape))
                if (isLoop) {
                    val sPerc = lStart.toFloat() / dur.coerceAtLeast(1)
                    val ePerc = lEnd.toFloat() / dur.coerceAtLeast(1)
                    Box(modifier = Modifier.fillMaxWidth(0.96f * (ePerc - sPerc)).align(Alignment.CenterStart).offset(x = (fullWidth * 0.02f) + (fullWidth * 0.96f * sPerc)).height(6.dp).background(ColorGold.copy(alpha = 0.45f)))
                    RangeSlider(value = lStart.toFloat()..lEnd.toFloat(), onValueChange = { onRange(it.start.toLong(), it.endInclusive.toLong()) }, valueRange = 0f..dur.toFloat().coerceAtLeast(1f), colors = SliderDefaults.colors(thumbColor = ColorGold, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent), modifier = Modifier.fillMaxWidth().height(32.dp).offset(y = (-16).dp), startThumb = { Box(modifier = Modifier.size(28.dp).background(ColorGold, CircleShape).border(1.5.dp, Color.White.copy(0.4f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.ChevronLeft, null, tint = Color.Black, modifier = Modifier.size(18.dp)) } }, endThumb = { Box(modifier = Modifier.size(28.dp).background(ColorGold, CircleShape).border(1.5.dp, Color.White.copy(0.4f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.ChevronRight, null, tint = Color.Black, modifier = Modifier.size(18.dp)) } })
                }
                Slider(value = head.toFloat().coerceIn(0f, dur.toFloat()), onValueChange = { onSeekState(true) ; onSeek(it.toLong()) }, onValueChangeFinished = { onSeekState(false) }, valueRange = 0f..dur.toFloat().coerceAtLeast(1f), colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = ColorGold, inactiveTrackColor = Color.Transparent), modifier = Modifier.fillMaxWidth().height(32.dp))
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        IconButton(onClick = onLoop, modifier = Modifier.size(40.dp).background(if (isLoop) ColorGold else ColorSurface, RoundedCornerShape(10.dp)).border(1.dp, if(!isLoop) ColorSlate else Color.Transparent, RoundedCornerShape(10.dp))) { Icon(Icons.Default.Repeat, null, tint = if (isLoop) Color.Black else ColorGold, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
private fun EditorSettingsDialog(offset: Int, onChange: (Int)->Unit, onDismiss: ()->Unit) {
    Dialog(onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = ColorSurface, contentColor = Color.White, border = BorderStroke(1.dp, ColorSlate)) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("Piano Editor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = ColorGold)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Transposition", color = ColorTextDim, fontWeight = FontWeight.Bold)
                        Text("${if (offset > 0) "+" else ""}$offset", color = ColorGold, fontWeight = FontWeight.Black, style = TextStyle(shadow = Shadow(Color.Black, blurRadius = 4f)))
                    }
                    Slider(value = offset.toFloat(), onValueChange = { onChange(it.toInt()) }, valueRange = -12f..12f, steps = 23, colors = SliderDefaults.colors(thumbColor = ColorKeyWhite, activeTrackColor = ColorGold))
                }
                Button(onClick = onDismiss, Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = ColorGold, contentColor = Color.Black), shape = RoundedCornerShape(12.dp)) { Text("DONE", fontWeight = FontWeight.Black) }
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
