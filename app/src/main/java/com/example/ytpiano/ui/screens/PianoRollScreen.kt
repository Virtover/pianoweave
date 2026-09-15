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
import com.example.ytpiano.midi.MidiInputManager
import com.example.ytpiano.midi.MidiNoteEvent
import com.example.ytpiano.midi.SimpleMidiReader
import com.example.ytpiano.midi.StoredMidi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

// --- Ultra Pro Piano Theme (Updated for Visual Excellence) ---
private val ColorBg = Color(0xFF0D1117)
private val ColorSurface = Color(0xFF161B22)
private val ColorGold = Color(0xFFD4AF37) // Signature Premium Gold
private val ColorGoldDim = Color(0xFF3E351A)
private val ColorSlate = Color(0xFF30363D) // Muted Dark Slate
private val ColorSuccess = Color(0xFF2EA043) // Success Green
private val ColorTarget = Color(0xFFF39C12) // Vibrant On-Hit Gold
private val ColorBaseline = Color(0xFFF85149) // Neon Red Hitline
private val ColorTextDim = Color(0xFF8B949E)
private val ColorKeyWhite = Color(0xFFE6E6E6)
private val ColorKeyBlack = Color(0xFF1A1A1A)

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
            CircularProgressIndicator(color = ColorGold, strokeWidth = 3.dp, modifier = Modifier.size(52.dp))
            Text("Building performance data...", color = ColorGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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

    // Robust Wait Mode Logic: Pause timeline if the NEXT note hasn't been hit
    val nextOnsetMs = remember(noteEvents, playheadMs) {
        noteEvents.filter { it.startMs >= playheadMs }.minOfOrNull { it.startMs }
    }
    val notesAtOnset = remember(noteEvents, nextOnsetMs) {
        if (nextOnsetMs == null) emptySet<Int>()
        else {
            val tolerance = 30L
            noteEvents.filter { abs(it.startMs - nextOnsetMs) <= tolerance }.map { it.pitch }.toSet()
        }
    }

    LaunchedEffect(isPlaying, speedMultiplier, isWaitModeEnabled, isLoopingEnabled, loopStartMs, loopEndMs) {
        var lastTime = System.nanoTime()
        while (isPlaying) {
            val now = System.nanoTime()
            val dt = (now - lastTime) / 1_000_000L
            lastTime = now

            if (isWaitModeEnabled && nextOnsetMs != null) {
                val lookAhead = 20L
                if (playheadMs >= nextOnsetMs - lookAhead) {
                    val pressed = MidiInputManager.pressedKeys.toSet()
                    if (notesAtOnset.any { it in startPitch..endPitch && it !in pressed }) {
                        delay(16)
                        continue
                    }
                }
            }

            var next = playheadMs + (dt * speedMultiplier).toLong()
            if (isLoopingEnabled && next >= loopEndMs) next = loopStartMs
            else if (!isLoopingEnabled && next >= songDurationMs) {
                next = songDurationMs ; isPlaying = false
            }
            playheadMs = next
            delay(10)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(ColorBg)) {
        ModernToolbar(song.songTitle, isWaitModeEnabled, speedMultiplier, { speedMultiplier = it }, { isWaitModeEnabled = !isWaitModeEnabled }, { showSettingsDialog = true }, onBack)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            FallingNotesVisualizer(noteEvents, playheadMs, startPitch, endPitch, totalKeys)
            if (isWaitModeEnabled && nextOnsetMs != null && playheadMs >= nextOnsetMs - 500) {
                WaitModeOverlay(notesAtOnset)
            }
        }

        PianoKeyboardRow(startPitch, endPitch, noteEvents, playheadMs)

        MediaTimelineFooter(
            head = playheadMs,
            dur = songDurationMs,
            isPlaying = isPlaying,
            isLoop = isLoopingEnabled,
            lStart = loopStartMs,
            lEnd = loopEndMs,
            onPlay = { isPlaying = !isPlaying },
            onReset = { isPlaying = false ; playheadMs = if (isLoopingEnabled) loopStartMs else 0L },
            onSeek = { playheadMs = it },
            onLoop = { isLoopingEnabled = !isLoopingEnabled },
            onRange = { s, e -> loopStartMs = s ; loopEndMs = e }
        )
    }

    if (showSettingsDialog) EditorSettingsDialog(transposeOffset, { transposeOffset = it }, { showSettingsDialog = false })
}

@Composable
private fun ModernToolbar(title: String, isWait: Boolean, speed: Float, onSpeedChange: (Float) -> Unit, onWaitToggle: () -> Unit, onSetClick: () -> Unit, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(ColorSurface).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
        Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
        
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Speed Circle Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(0.25f, 0.5f, 1.0f).forEach { s ->
                    val isSelected = speed == s
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(if (isSelected) ColorGold else ColorSlate, CircleShape)
                            .clickable { onSpeedChange(s) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${s}x", fontSize = 10.sp, fontWeight = FontWeight.Black, color = if (isSelected) Color.Black else Color.White)
                    }
                }
            }

            Button(
                onClick = onWaitToggle,
                colors = ButtonDefaults.buttonColors(containerColor = if (isWait) ColorGold else ColorSurface, contentColor = if (isWait) Color.Black else ColorGold),
                shape = RoundedCornerShape(8.dp), 
                border = if (!isWait) BorderStroke(1.dp, ColorSlate) else null,
                contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.Timer, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Wait mode", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(onClick = onSetClick, modifier = Modifier.size(34.dp).background(ColorSurface, RoundedCornerShape(8.dp)).border(1.dp, ColorSlate, RoundedCornerShape(8.dp))) {
                Icon(Icons.Default.Tune, null, tint = ColorGold, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun FallingNotesVisualizer(events: List<MidiNoteEvent>, head: Long, start: Int, end: Int, count: Int) {
    val scale = 0.25f
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (size.width <= 0 || size.height <= 0) return@Canvas
        val kw = size.width / count
        
        for (p in start..end) {
            val x = (p - start) * kw
            drawLine(if (isPitchBlack(p)) Color(0xFF161B22) else Color(0xFF0D1117), Offset(x, 0f), Offset(x, size.height), 1f)
        }

        events.forEach { e ->
            if (e.pitch !in start..end) return@forEach
            val endMs = e.startMs + e.durationMs
            if (endMs < head || e.startMs > head + (size.height / scale)) return@forEach
            
            val x = (e.pitch - start) * kw
            val h = (e.durationMs * scale).coerceAtLeast(10f)
            val y = size.height - ((e.startMs - head) * scale) - h
            
            val isPressed = MidiInputManager.pressedKeys.contains(e.pitch)
            // Fix: Only highlight if the note is ACTUALLY passing the baseline
            val isHitting = head >= e.startMs && head <= (e.startMs + 50L)
            val isPassing = head > e.startMs && head < endMs

            val color = when {
                isPressed && isPassing -> ColorSuccess
                isHitting -> ColorTarget
                isPassing -> ColorGold.copy(alpha = 0.5f)
                else -> ColorGoldDim 
            }
            
            drawRoundRect(
                color = color, 
                topLeft = Offset(x + 3f, y.coerceIn(-h, size.height)), 
                size = Size(kw - 6f, h), 
                cornerRadius = CornerRadius(4.dp.toPx())
            )
            
            if (isHitting || (isPressed && isPassing)) {
                drawRect(
                    brush = Brush.verticalGradient(listOf(color.copy(0.4f), Color.Transparent)),
                    topLeft = Offset(x, y.coerceIn(-h, size.height) + h),
                    size = Size(kw, 40.dp.toPx())
                )
            }
        }
        drawLine(ColorBaseline, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 2f)
    }
}

@Composable
private fun WaitModeOverlay(notes: Set<Int>) {
    Box(Modifier.fillMaxWidth().padding(top = 24.dp), Alignment.TopCenter) {
        Surface(
            color = ColorGold, 
            shape = RoundedCornerShape(12.dp), 
            shadowElevation = 12.dp,
            border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(0.5f))
        ) {
            Text(
                text = "STRIKE: " + notes.sorted().joinToString("  ") { midiPitchName(it) }, 
                Modifier.padding(horizontal = 24.dp, vertical = 10.dp), 
                fontSize = 16.sp, 
                fontWeight = FontWeight.Black, 
                color = Color.Black
            )
        }
    }
}

@Composable
private fun PianoKeyboardRow(start: Int, end: Int, events: List<MidiNoteEvent>, head: Long) {
    val activePitches = remember(events, head) {
        events.filter { head >= it.startMs && head <= (it.startMs + it.durationMs) }.map { it.pitch }.toSet()
    }
    
    Row(Modifier.fillMaxWidth().height(80.dp).background(ColorBg)) {
        val pressed = MidiInputManager.pressedKeys.toSet()
        for (p in start..end) {
            val isBlack = isPitchBlack(p)
            val isPressed = pressed.contains(p)
            val isTarget = activePitches.contains(p)

            val baseColor = if (isBlack) ColorKeyBlack else ColorKeyWhite
            val highlightColor = when {
                isPressed && isTarget -> ColorSuccess
                isPressed -> ColorGold
                isTarget -> ColorGoldDim
                else -> baseColor
            }
            
            val gradient = Brush.verticalGradient(
                colors = listOf(highlightColor, highlightColor.copy(alpha = 0.8f)),
                startY = 0f,
                endY = 200f
            )

            Box(
                Modifier.weight(1f).fillMaxHeight().padding(0.5.dp)
                    .background(gradient, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                    .clickable { if (MidiInputManager.pressedKeys.contains(p)) MidiInputManager.simulateNoteOff(p) else MidiInputManager.simulateNoteOn(p) },
                Alignment.BottomCenter
            ) {
                val label = when (p) { 36->"C1";48->"C2";60->"C3";72->"C4";84->"C5";96->"C6"; else->"" }
                if (label.isNotEmpty()) Text(label, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = if (isPressed) Color.Black else ColorTextDim, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

@Composable
private fun MediaTimelineFooter(
    head: Long, dur: Long, isPlaying: Boolean, isLoop: Boolean, lStart: Long, lEnd: Long,
    onPlay: () -> Unit, onReset: () -> Unit, onSeek: (Long) -> Unit, onLoop: () -> Unit,
    onRange: (Long, Long) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(ColorSurface).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Playback Buttons Group
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton(onClick = onPlay, Modifier.size(44.dp).background(ColorGold, CircleShape)) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = onReset, modifier = Modifier.size(34.dp).background(ColorSurface, CircleShape).border(1.dp, ColorSlate, CircleShape)) {
                Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Center Column: Dual Sliders (Separated for precise touch)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            if (isLoop) {
                RangeSlider(
                    value = lStart.toFloat()..lEnd.toFloat(),
                    onValueChange = { onRange(it.start.toLong(), it.endInclusive.toLong()) },
                    valueRange = 0f..dur.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(thumbColor = ColorGold, activeTrackColor = ColorGoldDim, inactiveTrackColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().height(28.dp)
                )
            }
            
            Slider(
                value = head.toFloat().coerceIn(0f, dur.toFloat()),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..dur.toFloat().coerceAtLeast(1f),
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = ColorGold, inactiveTrackColor = ColorSlate),
                modifier = Modifier.fillMaxWidth().height(28.dp)
            )
            
            Text(
                text = "${formatTime(head)} / ${formatTime(dur)}",
                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, 
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Loop Toggle Button
        IconButton(
            onClick = onLoop,
            modifier = Modifier.size(40.dp).background(if (isLoop) ColorGold else ColorSurface, RoundedCornerShape(10.dp)).border(if (!isLoop) 1.dp else 0.dp, ColorSlate, RoundedCornerShape(10.dp))
        ) {
            Icon(Icons.Default.Repeat, null, tint = if (isLoop) Color.Black else ColorGold, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun EditorSettingsDialog(offset: Int, onChange: (Int)->Unit, onDismiss: ()->Unit) {
    Dialog(onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = ColorSurface, contentColor = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, ColorSlate)) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("Piano Editor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = ColorGold)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Transposition", color = ColorTextDim, fontWeight = FontWeight.Bold)
                        Text("${if (offset > 0) "+" else ""}$offset", color = ColorGold, fontWeight = FontWeight.Black, style = TextStyle(shadow = Shadow(Color.Black, blurRadius = 4f)))
                    }
                    Slider(value = offset.toFloat(), onValueChange = { onChange(it.toInt()) }, valueRange = -12f..12f, steps = 23, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = ColorGold))
                }
                Button(onClick = onDismiss, Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = ColorGold, contentColor = Color.Black), shape = RoundedCornerShape(12.dp)) {
                    Text("DONE", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun MidiErrorScreen(song: StoredMidi, error: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(ColorBg).padding(32.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Default.Warning, null, tint = ColorBaseline, modifier = Modifier.size(64.dp))
            Text("Midi Compatibility Error", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
            Text(song.songTitle, color = ColorTextDim, textAlign = TextAlign.Center)
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = ColorSurface)) { Text("Return to Library") }
        }
    }
}

private fun isPitchBlack(p: Int): Boolean { val n = p % 12 ; return n == 1 || n == 3 || n == 6 || n == 8 || n == 10 }
private fun formatTime(ms: Long): String { val s = (ms / 1000L).coerceAtLeast(0L) ; return "%02d:%02d".format(s / 60, s % 60) }
private fun midiPitchName(p: Int): String { val n = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B") ; return n[p % 12] + (p / 12 - 1) }
