package com.example.ytpiano.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ytpiano.midi.MidiInputManager
import com.example.ytpiano.midi.MidiNoteEvent
import com.example.ytpiano.midi.SimpleMidiReader
import com.example.ytpiano.midi.StoredMidi
import kotlinx.coroutines.delay

@Composable
fun PianoRollScreen(
    song: StoredMidi,
    onBack: () -> Unit
) {
    // Parse MIDI contents when screen is initialized
    val noteEvents = remember(song) { SimpleMidiReader.parse(song.file) }

    // Playback control states
    var isPlaying by remember { mutableStateOf(true) }
    var playheadMs by remember { mutableStateOf(0L) }
    var speedMultiplier by remember { mutableStateOf(1.0f) }
    var isWaitModeEnabled by remember { mutableStateOf(false) }
    var isLoopingEnabled by remember { mutableStateOf(false) }

    // Constants for 5-octave viewer frame (Pitches 36 to 96)
    val startPitch = 36
    val endPitch = 96
    val totalKeys = endPitch - startPitch + 1

    // A/B loop bounds preset helper
    val loopStartMs = 0L
    val loopEndMs = remember(noteEvents) { 
        if (noteEvents.isNotEmpty()) noteEvents.last().startMs + noteEvents.last().durationMs else 10000L 
    }

    // Determine currently required notes at active playhead timestamp
    val requiredNotes = remember(noteEvents, playheadMs) {
        noteEvents.filter { event ->
            playheadMs >= event.startMs && playheadMs <= (event.startMs + 150)
        }.map { it.pitch }.toSet()
    }

    // Interactive clock ticking driver loop
    LaunchedEffect(isPlaying, speedMultiplier, isWaitModeEnabled, isLoopingEnabled, playheadMs) {
        if (!isPlaying) return@LaunchedEffect

        // Evaluate Wait Mode: Check if required notes are fully matched by user physical input
        if (isWaitModeEnabled && requiredNotes.isNotEmpty()) {
            val userPressed = MidiInputManager.pressedKeys.toSet()
            val missing = requiredNotes.filter { it in startPitch..endPitch && !userPressed.contains(it) }
            if (missing.isNotEmpty()) {
                // Halt timeline progression until user hits correct notes
                delay(50)
                return@LaunchedEffect
            }
        }

        val stepMs = 30L
        delay(stepMs)
        var nextPlayhead = playheadMs + (stepMs * speedMultiplier).toLong()

        // Handle A/B bounds resetting
        if (isLoopingEnabled && nextPlayhead >= loopEndMs) {
            nextPlayhead = loopStartMs
        } else if (noteEvents.isNotEmpty() && nextPlayhead > (noteEvents.last().startMs + noteEvents.last().durationMs + 2000)) {
            nextPlayhead = 0L // Loop full song if bounds end reached
        }

        playheadMs = nextPlayhead
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1113))
    ) {
        // Control Toolbar Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.youtubeUrl,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${noteEvents.size} total notes loaded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Speed multiplier selector chip buttons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(0.5f, 0.75f, 1.0f).forEach { speed ->
                    FilterChip(
                        selected = speedMultiplier == speed,
                        onClick = { speedMultiplier = speed },
                        label = { Text("${speed}x", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondary,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                        )
                    )
                }
            }

            // Loop Toggle Button
            IconButton(
                onClick = { isLoopingEnabled = !isLoopingEnabled },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isLoopingEnabled) MaterialTheme.colorScheme.secondary else Color.Transparent
                )
            ) {
                Icon(
                    Icons.Default.Repeat, 
                    contentDescription = "Loop Song",
                    tint = if (isLoopingEnabled) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Wait Mode Toggle Button
            Button(
                onClick = { isWaitModeEnabled = !isWaitModeEnabled },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isWaitModeEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (isWaitModeEnabled) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = if (isWaitModeEnabled) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Wait Mode", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            // Master Play / Pause Button
            IconButton(
                onClick = { isPlaying = !isPlaying },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Live Falling Notes Canvas visualizer workspace
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val speedScale = 0.15f // Pixels per millisecond ratio

            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val keyWidth = canvasWidth / totalKeys

                // Draw background lane partitions for coordinates alignment
                for (p in startPitch..endPitch) {
                    val x = (p - startPitch) * keyWidth
                    val isBlack = isPitchBlack(p)
                    drawLine(
                        color = if (isBlack) Color(0xFF191B1F) else Color(0xFF131518),
                        start = Offset(x, 0f),
                        end = Offset(x, canvasHeight),
                        strokeWidth = 1f
                    )
                }

                // Render falling note rect chunks onto timeline
                noteEvents.forEach { event ->
                    if (event.pitch in startPitch..endPitch) {
                        val noteEndMs = event.startMs + event.durationMs
                        
                        // Check if block falls within active screen window view bounds
                        if (noteEndMs >= playheadMs && event.startMs <= playheadMs + (canvasHeight / speedScale)) {
                            val x = (event.pitch - startPitch) * keyWidth
                            val y = canvasHeight - ((event.startMs - playheadMs) * speedScale) - (event.durationMs * speedScale)
                            val height = event.durationMs * speedScale

                            val isKeyRequired = requiredNotes.contains(event.pitch)
                            val isKeyMatched = MidiInputManager.pressedKeys.contains(event.pitch)

                            val color = when {
                                isKeyMatched && isKeyRequired -> Color(0xFF4CAF50) // User successfully striking required key
                                isKeyRequired -> Color(0xFFD4AF37)                 // Golden required target note
                                isKeyMatched -> Color(0xFF81C784)                  // Key pressed separately
                                else -> Color(0xFF43474E)                          // Standard generic falling item
                            }

                            drawRect(
                                color = color,
                                topLeft = Offset(x + 2f, y),
                                size = Size(keyWidth - 4f, height.coerceAtLeast(6f))
                            )
                        }
                    }
                }
            }

            // Prompt layer over canvas when Wait Mode locks timeline progression
            if (isWaitModeEnabled && requiredNotes.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "WAITING FOR KEYSTROKE MATCHES...",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondary,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // Virtual Interactive Musical Keyboard Row (at bottom of screen)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .background(Color(0xFF111215))
        ) {
            val userPressedKeys = MidiInputManager.pressedKeys.toSet()

            for (pitch in startPitch..endPitch) {
                val isBlack = isPitchBlack(pitch)
                val isPressed = userPressedKeys.contains(pitch)
                val isTarget = requiredNotes.contains(pitch)

                val keyColor = when {
                    isPressed && isTarget -> Color(0xFF4CAF50)
                    isPressed -> Color(0xFFD4AF37)
                    isTarget -> Color(0xFF2E2A1A)
                    isBlack -> Color(0xFF1E2229)
                    else -> Color(0xFFE3E4E8)
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
                            // Support simulation touch triggers directly on keys
                            if (MidiInputManager.pressedKeys.contains(pitch)) {
                                MidiInputManager.simulateNoteOff(pitch)
                            } else {
                                MidiInputManager.simulateNoteOn(pitch)
                            }
                        }
                )
            }
        }
    }
}

private fun isPitchBlack(pitch: Int): Boolean {
    val noteInOctave = pitch % 12
    return noteInOctave == 1 || noteInOctave == 3 || noteInOctave == 6 || noteInOctave == 8 || noteInOctave == 10
}
