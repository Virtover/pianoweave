package com.example.ytpiano.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
            parseError = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName ?: "Failed to parse MIDI"
        }
    }

    when {
        parseError != null -> {
            MidiErrorScreen(song = song, error = parseError!!, onBack = onBack)
        }
        noteEvents == null -> {
            MidiLoadingScreen()
        }
        else -> {
            ModernPianoPlayerContent(song = song, rawEvents = noteEvents!!, onBack = onBack)
        }
    }
}

@Composable
private fun MidiLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = Color(0xFF4FA8FF), strokeWidth = 3.dp)
            Text(
                text = "Parsing MIDI performance matrix...",
                color = Color(0xFF90949F),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ModernPianoPlayerContent(
    song: StoredMidi,
    rawEvents: List<MidiNoteEvent>,
    onBack: () -> Unit
) {
    // Media Playback Timers
    var isPlaying by remember { mutableStateOf(true) }
    var playheadMs by remember { mutableLongStateOf(0L) }
    var speedMultiplier by remember { mutableFloatStateOf(1.0f) }
    
    // Core Learning Settings
    var isWaitModeEnabled by remember { mutableStateOf(false) }
    var isLoopingEnabled by remember { mutableStateOf(false) }
    var transposeOffset by remember { mutableIntStateOf(0) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Dynamic Note Transposition Mapping
    val noteEvents = remember(rawEvents, transposeOffset) {
        rawEvents.map { it.copy(pitch = it.pitch + transposeOffset) }
    }

    val songDurationMs = remember(noteEvents) {
        noteEvents.maxOfOrNull { it.startMs + it.durationMs } ?: 10_000L
    }

    var loopStartMs by remember { mutableLongStateOf(0L) }
    var loopEndMs by remember { mutableLongStateOf(songDurationMs) }

    // Constrain pitches to matching 5-octave performance view (Pitches 36 to 96)
    val startPitch = 36
    val endPitch = 96
    val totalKeys = endPitch - startPitch + 1

    // Evaluate required keys for current playhead position
    val currentStepNotes = remember(noteEvents, playheadMs) {
        val tolerance = 80L
        noteEvents.filter { abs(it.startMs - playheadMs) <= tolerance }.map { it.pitch }.toSet()
    }

    val activeFallingNotes = remember(noteEvents, playheadMs) {
        noteEvents.filter { playheadMs >= it.startMs && playheadMs < (it.startMs + 150L) }.map { it.pitch }.toSet()
    }

    // High-performance clock ticking coroutine engine
    LaunchedEffect(isPlaying, speedMultiplier, isWaitModeEnabled, isLoopingEnabled, loopStartMs, loopEndMs) {
        var lastFrameTime = System.nanoTime()
        while (isPlaying) {
            val now = System.nanoTime()
            val elapsedMs = (now - lastFrameTime) / 1_000_000L
            lastFrameTime = now

            if (isWaitModeEnabled && currentStepNotes.isNotEmpty()) {
                val pressed = MidiInputManager.pressedKeys.toSet()
                val missing = currentStepNotes.filter { it in startPitch..endPitch && it !in pressed }
                if (missing.isNotEmpty()) {
                    delay(20)
                    continue
                }
            }

            var nextPosition = playheadMs + (elapsedMs * speedMultiplier).toLong()

            if (isLoopingEnabled && nextPosition >= loopEndMs) {
                nextPosition = loopStartMs
            } else if (!isLoopingEnabled && nextPosition >= songDurationMs) {
                nextPosition = songDurationMs
                isPlaying = false
            }

            playheadMs = nextPosition
            delay(12)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14)) // Premium Dark Cyber Aesthetic Background
    ) {
        // Upper Control Toolbar Row
        ModernToolbar(
            songTitle = song.songTitle,
            isWaitModeEnabled = isWaitModeEnabled,
            onWaitToggle = { isWaitModeEnabled = !isWaitModeEnabled },
            onSettingsClick = { showSettingsDialog = true },
            onBack = onBack
        )

        // Main Falling Notes Visualizer Panel Block
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            FallingNotesVisualizer(
                noteEvents = noteEvents,
                playheadMs = playheadMs,
                startPitch = startPitch,
                endPitch = endPitch,
                totalKeys = totalKeys,
                activeFallingNotes = activeFallingNotes
            )

            if (isWaitModeEnabled && currentStepNotes.isNotEmpty()) {
                WaitModeOverlay(currentStepNotes = currentStepNotes)
            }
        }

        // Virtual Studio Piano Keyboard Panel Layout Row
        PianoKeyboardRow(
            startPitch = startPitch,
            endPitch = endPitch,
            activeFallingNotes = activeFallingNotes
        )

        // Footer Timeline Control Center Panel
        MediaTimelineFooter(
            playheadMs = playheadMs,
            songDurationMs = songDurationMs,
            isPlaying = isPlaying,
            isLoopingEnabled = isLoopingEnabled,
            loopStartMs = loopStartMs,
            loopEndMs = loopEndMs,
            speedMultiplier = speedMultiplier,
            onPlayPauseToggle = { isPlaying = !isPlaying },
            onStopReset = {
                isPlaying = false
                playheadMs = if (isLoopingEnabled) loopStartMs else 0L
            },
            onPlayheadSeek = { playheadMs = it },
            onLoopToggle = { isLoopingEnabled = !isLoopingEnabled },
            onLoopRangeChange = { start, end ->
                loopStartMs = start
                loopEndMs = end
            },
            onSpeedChange = { speedMultiplier = it }
        )
    }

    if (showSettingsDialog) {
        EditorSettingsDialog(
            transposeOffset = transposeOffset,
            onTransposeChange = { transposeOffset = it },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
private fun ModernToolbar(
    songTitle: String,
    isWaitModeEnabled: Boolean,
    onWaitToggle: () -> Unit,
    onSettingsClick: () -> Unit,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF11151D))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = songTitle,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Mode Selector Toggle
            Button(
                onClick = onWaitToggle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isWaitModeEnabled) Color(0xFF4FA8FF) else Color(0xFF202632),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = if (isWaitModeEnabled) "Vertical • Wait" else "Vertical", fontSize = 12.sp)
            }

            // Editor Tools Popover Button
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFF202632), RoundedCornerShape(6.dp))
            ) {
                Icon(Icons.Default.Tune, contentDescription = "Editor Settings", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun FallingNotesVisualizer(
    noteEvents: List<MidiNoteEvent>,
    playheadMs: Long,
    startPitch: Int,
    endPitch: Int,
    totalKeys: Int,
    activeFallingNotes: Set<Int>
) {
    val speedScale = 0.22f // Custom optimized descent velocity scaling ratio

    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        if (canvasWidth <= 0f || canvasHeight <= 0f) return@Canvas

        val keyWidth = canvasWidth / totalKeys

        // 1. Draw sleek lane split dividers
        for (pitch in startPitch..endPitch) {
            val x = (pitch - startPitch) * keyWidth
            val isBlack = isPitchBlack(pitch)
            drawLine(
                color = if (isBlack) Color(0xFF141923) else Color(0xFF0F121A),
                start = Offset(x, 0f),
                end = Offset(x, canvasHeight),
                strokeWidth = 1f
            )
        }

        // 2. Draw capsule falling note bars
        noteEvents.forEach { event ->
            if (event.pitch !in startPitch..endPitch) return@forEach

            val noteEndMs = event.startMs + event.durationMs
            val visibleEndMs = playheadMs + (canvasHeight / speedScale).toLong()

            if (noteEndMs < playheadMs || event.startMs > visibleEndMs) return@forEach

            val x = (event.pitch - startPitch) * keyWidth
            val height = (event.durationMs * speedScale).coerceAtLeast(10f)
            val y = canvasHeight - ((event.startMs - playheadMs) * speedScale) - height
            val safeY = y.coerceIn(-height, canvasHeight)

            val isTarget = activeFallingNotes.contains(event.pitch)
            val isPressed = MidiInputManager.pressedKeys.contains(event.pitch)

            val barColor = when {
                isPressed && isTarget -> Color(0xFF4CAF50) // Satisfied match
                isTarget -> Color(0xFF4FA8FF)              // Target note hit highlight
                isPressed -> Color(0xFF2ECC71)             // Freely pressed key
                else -> Color(0xFF2980B9).copy(alpha = 0.85f) // Glowing cyan/blue capsules
            }

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x + 2f, safeY),
                size = Size(keyWidth - 4f, height),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }

        // 3. Draw neon red horizontal hit baseline line
        drawLine(
            color = Color(0xFFFF334B),
            start = Offset(0f, canvasHeight - 2f),
            end = Offset(canvasWidth, canvasHeight - 2f),
            strokeWidth = 3f
        )
    }
}

@Composable
private fun WaitModeOverlay(currentStepNotes: Set<Int>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            color = Color(0xFF4FA8FF),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = "PLAY: " + currentStepNotes.sorted().joinToString("  ") { midiPitchName(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun PianoKeyboardRow(
    startPitch: Int,
    endPitch: Int,
    activeFallingNotes: Set<Int>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(Color(0xFF090B0E))
    ) {
        val userPressedKeys = MidiInputManager.pressedKeys.toSet()

        for (pitch in startPitch..endPitch) {
            val isBlack = isPitchBlack(pitch)
            val isPressed = userPressedKeys.contains(pitch)
            val isTarget = activeFallingNotes.contains(pitch)

            val keyColor = when {
                isPressed && isTarget -> Color(0xFF4CAF50)
                isPressed -> Color(0xFFD4AF37)
                isTarget -> Color(0xFF1E2D3E) // Dark blue-highlight target key track
                isBlack -> Color(0xFF151A22)
                else -> Color(0xFF2C3545)
            }

            // High UX Octave label matching screenshot
            val label = when (pitch) {
                36 -> "C1"
                48 -> "C2"
                60 -> "C3"
                72 -> "C4"
                84 -> "C5"
                96 -> "C6"
                else -> ""
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 0.5.dp)
                    .background(
                        color = keyColor,
                        shape = RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp)
                    )
                    .clickable {
                        if (MidiInputManager.pressedKeys.contains(pitch)) {
                            MidiInputManager.simulateNoteOff(pitch)
                        } else {
                            MidiInputManager.simulateNoteOn(pitch)
                        }
                    },
                contentAlignment = Alignment.BottomCenter
            ) {
                if (label.isNotEmpty()) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPressed) Color.Black else Color(0xFF90949F),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaTimelineFooter(
    playheadMs: Long,
    songDurationMs: Long,
    isPlaying: Boolean,
    isLoopingEnabled: Boolean,
    loopStartMs: Long,
    loopEndMs: Long,
    speedMultiplier: Float,
    onPlayPauseToggle: () -> Unit,
    onStopReset: () -> Unit,
    onPlayheadSeek: (Long) -> Unit,
    onLoopToggle: () -> Unit,
    onLoopRangeChange: (Long, Long) -> Unit,
    onSpeedChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F121A))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Main Timeline Scrub Slider
        Slider(
            value = if (songDurationMs > 0) playheadMs.coerceIn(0L, songDurationMs).toFloat() / songDurationMs else 0f,
            onValueChange = { onPlayheadSeek((it * songDurationMs).toLong()) },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFF4FA8FF),
                inactiveTrackColor = Color(0xFF202632)
            )
        )

        // Loop points secondary double range slider, activated reactively by the Loop Button toggle click
        if (isLoopingEnabled) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            ) {
                Text("Loop bounds:", fontSize = 10.sp, color = Color(0xFF90949F), modifier = Modifier.width(72.dp))
                RangeSlider(
                    value = loopStartMs.toFloat()..loopEndMs.toFloat(),
                    onValueChange = { range ->
                        onLoopRangeChange(range.start.toLong(), range.endInclusive.toLong())
                    },
                    valueRange = 0f..songDurationMs.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color(0xFFD4AF37),
                        inactiveTrackColor = Color(0xFF202632)
                    )
                )
            }
        }

        // Bottom Dashboard Playback Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Side Controls: Play, Reset, Timestamps
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onPlayPauseToggle,
                    modifier = Modifier.size(36.dp).background(Color(0xFF4FA8FF), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onStopReset,
                    modifier = Modifier.size(36.dp).background(Color(0xFF202632), CircleShape)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Reset Song", tint = Color.White, modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = "${formatTime(playheadMs)} / ${formatTime(songDurationMs)}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                
                if (isLoopingEnabled) {
                    Text(
                        text = "[A:${formatTime(loopStartMs)} - B:${formatTime(loopEndMs)}]",
                        color = Color(0xFFD4AF37),
                        fontSize = 11.sp
                    )
                }
            }

            // Right Side Controls: Speed, Loop Button Trigger
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Granular Speed controls
                listOf(0.5f, 1.0f, 1.5f).forEach { speed ->
                    FilterChip(
                        selected = speedMultiplier == speed,
                        onClick = { onSpeedChange(speed) },
                        label = { Text("${speed}x", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF4FA8FF),
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xFF202632),
                            labelColor = Color.White
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }

                // High UX Loop Button Trigger (activates start/end range sliders above layout row)
                IconButton(
                    onClick = onLoopToggle,
                    modifier = Modifier
                        .size(32.dp)
                        .background(if (isLoopingEnabled) Color(0xFFD4AF37) else Color(0xFF202632), RoundedCornerShape(4.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Toggle A/B Loop Sliders",
                        tint = if (isLoopingEnabled) Color.Black else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorSettingsDialog(
    transposeOffset: Int,
    onTransposeChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF161A24),
            contentColor = Color.White,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "Editor Tools",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Transpose Pitch", fontSize = 14.sp)
                        Text(
                            text = if (transposeOffset >= 0) "+$transposeOffset semitones" else "$transposeOffset semitones",
                            color = Color(0xFF4FA8FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Slider(
                        value = transposeOffset.toFloat(),
                        onValueChange = { onTransposeChange(it.toInt()) },
                        valueRange = -12f..12f,
                        steps = 23,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color(0xFF4FA8FF)
                        )
                    )
                }

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
                    TextButton(onClick = onDismiss) {
                        Text("Done", color = Color(0xFF4FA8FF), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MidiErrorScreen(
    song: StoredMidi,
    error: String,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0B0E14)).padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
            Text(text = "Could not initialize MIDI playback matrix", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
            Text(text = song.songTitle, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF90949F), textAlign = TextAlign.Center)
            Text(text = error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202632))) {
                Text("Return to Library")
            }
        }
    }
}

private fun isPitchBlack(pitch: Int): Boolean {
    val noteInOctave = pitch % 12
    return noteInOctave == 1 || noteInOctave == 3 || noteInOctave == 6 || noteInOctave == 8 || noteInOctave == 10
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val tenths = ((ms % 1000L) / 100L).coerceAtLeast(0L)
    return "%02d:%02d.%d".format(minutes, seconds, tenths)
}

private fun midiPitchName(pitch: Int): String {
    val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val octave = pitch / 12 - 1
    return names[pitch % 12] + octave
}
