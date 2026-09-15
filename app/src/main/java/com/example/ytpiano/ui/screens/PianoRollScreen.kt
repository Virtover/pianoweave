package com.example.ytpiano.ui.screens

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun PianoRollScreen(
    song: StoredMidi,
    onBack: () -> Unit
) {
    var noteEvents by remember(song) {
        mutableStateOf<List<MidiNoteEvent>?>(null)
    }

    var parseError by remember(song) {
        mutableStateOf<String?>(null)
    }

    /*
     * Parse the MIDI away from the main thread.
     *
     * If the MIDI is malformed or the parser does not support
     * something in the file, show an error instead of crashing
     * the Activity.
     */
    LaunchedEffect(song) {
        noteEvents = null
        parseError = null

        try {
            val parsed = withContext(Dispatchers.IO) {
                SimpleMidiReader.parse(song.file)
            }

            noteEvents = parsed
        } catch (e: Exception) {
            parseError =
                e.message?.takeIf { it.isNotBlank() }
                    ?: e::class.simpleName
                            ?: "Unknown MIDI parsing error"
        }
    }

    /*
     * Do not construct the visualizer until the MIDI has been
     * successfully parsed.
     */
    if (parseError != null) {
        MidiErrorScreen(
            song = song,
            error = parseError!!,
            onBack = onBack
        )
        return
    }

    val events = noteEvents

    if (events == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F1113)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator()

                Text(
                    text = "Loading MIDI...",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        return
    }

    PianoRollContent(
        song = song,
        noteEvents = events,
        onBack = onBack
    )
}

@Composable
private fun PianoRollContent(
    song: StoredMidi,
    noteEvents: List<MidiNoteEvent>,
    onBack: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(true) }
    var playheadMs by remember { mutableLongStateOf(0L) }

    var speedMultiplier by remember {
        mutableFloatStateOf(1.0f)
    }

    var isWaitModeEnabled by remember {
        mutableStateOf(false)
    }

    var isLoopingEnabled by remember {
        mutableStateOf(false)
    }

    var loopStartMs by remember {
        mutableLongStateOf(0L)
    }

    var loopEndMs by remember {
        mutableLongStateOf(
            noteEvents.maxOfOrNull {
                it.startMs + it.durationMs
            } ?: 10_000L
        )
    }

    val startPitch = 36
    val endPitch = 96
    val totalKeys = endPitch - startPitch + 1

    val songDurationMs =
        noteEvents.maxOfOrNull {
            it.startMs + it.durationMs
        } ?: 0L

    /*
     * Group notes that start essentially simultaneously.
     *
     * These form one "step" for Wait Mode.
     */
    val currentStep = remember(
        noteEvents,
        playheadMs
    ) {
        val tolerance = 80L

        noteEvents
            .filter { event ->
                kotlin.math.abs(
                    event.startMs - playheadMs
                ) <= tolerance
            }
            .map { it.pitch }
            .toSet()
    }

    /*
     * A note is visually "current" only when its start
     * reaches the playhead.
     *
     * Future notes remain gray.
     */
    val currentNotes = remember(
        noteEvents,
        playheadMs
    ) {
        noteEvents
            .filter { event ->
                playheadMs >= event.startMs &&
                        playheadMs <
                        event.startMs + 150L
            }
            .map { it.pitch }
            .toSet()
    }

    /*
     * Playback loop.
     *
     * We deliberately don't use playheadMs as a LaunchedEffect
     * key. The coroutine continuously updates it.
     */
    LaunchedEffect(
        isPlaying,
        speedMultiplier,
        isWaitModeEnabled,
        isLoopingEnabled,
        loopStartMs,
        loopEndMs
    ) {
        var lastFrameTime = 0L

        while (isPlaying) {
            val now = System.nanoTime()

            if (lastFrameTime == 0L) {
                lastFrameTime = now
            }

            val elapsedMs =
                (now - lastFrameTime) / 1_000_000L

            lastFrameTime = now

            /*
             * Wait Mode:
             *
             * If there is a current step and not all required
             * keys are pressed, don't advance the timeline.
             */
            if (isWaitModeEnabled && currentStep.isNotEmpty()) {
                val pressed =
                    MidiInputManager.pressedKeys.toSet()

                val missing = currentStep.filter { pitch ->
                    pitch in startPitch..endPitch &&
                            pitch !in pressed
                }

                if (missing.isNotEmpty()) {
                    delay(20)
                    continue
                }
            }

            val movement =
                (
                        elapsedMs *
                                speedMultiplier
                        ).toLong()

            var nextPosition =
                playheadMs + movement

            /*
             * A/B loop.
             */
            if (
                isLoopingEnabled &&
                loopEndMs > loopStartMs &&
                (nextPosition !in loopStartMs..<loopEndMs)
            ) {
                nextPosition = loopStartMs
            }

            /*
             * End of song.
             */
            if (
                !isLoopingEnabled &&
                songDurationMs > 0 &&
                nextPosition >
                songDurationMs + 1000
            ) {
                nextPosition = songDurationMs
                isPlaying = false
            }

            playheadMs = nextPosition

            delay(10)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1113))
    ) {

        /*
         * =====================================================
         * TOP CONTROL BAR
         * =====================================================
         */

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surface
                )
                .padding(
                    horizontal = 12.dp,
                    vertical = 6.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            IconButton(
                onClick = onBack
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song.songTitle,
                    maxLines = 1,
                    style =
                        MaterialTheme.typography.titleMedium
                            .copy(
                                fontWeight =
                                    FontWeight.Bold
                            )
                )

                Text(
                    text = formatTime(playheadMs) +
                            " / " +
                            formatTime(songDurationMs),
                    style =
                        MaterialTheme.typography
                            .bodySmall,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )
            }

            /*
             * Speed
             */
            listOf(
                0.5f,
                0.75f,
                1.0f
            ).forEach { speed ->

                FilterChip(
                    selected =
                        speedMultiplier == speed,
                    onClick = {
                        speedMultiplier = speed
                    },
                    label = {
                        Text(
                            "${speed}x",
                            fontSize = 11.sp
                        )
                    }
                )
            }

            /*
             * Wait Mode
             */
            FilterChip(
                selected = isWaitModeEnabled,
                onClick = {
                    isWaitModeEnabled =
                        !isWaitModeEnabled
                },
                label = {
                    Text(
                        "Wait",
                        fontSize = 11.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        if (isWaitModeEnabled) {
                            Icons.Default.Check
                        } else {
                            Icons.Default.PlayArrow
                        },
                        contentDescription = null,
                        modifier =
                            Modifier.size(16.dp)
                    )
                }
            )

            /*
             * Play / pause
             */
            IconButton(
                onClick = {
                    isPlaying = !isPlaying
                }
            ) {
                Icon(
                    imageVector =
                        if (isPlaying) {
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
                        },
                    contentDescription =
                        if (isPlaying) {
                            "Pause"
                        } else {
                            "Play"
                        }
                )
            }
        }

        /*
         * =====================================================
         * PIANO ROLL
         * =====================================================
         *
         * This is now entirely below the top UI.
         */

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {

            val speedScale = 0.15f

            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                if (
                    canvasWidth <= 0f ||
                    canvasHeight <= 0f
                ) {
                    return@Canvas
                }

                val keyWidth =
                    canvasWidth / totalKeys

                /*
                 * Key lanes.
                 */
                for (pitch in startPitch..endPitch) {
                    val x =
                        (pitch - startPitch) *
                                keyWidth

                    drawLine(
                        color =
                            if (isPitchBlack(pitch)) {
                                Color(0xFF191B1F)
                            } else {
                                Color(0xFF131518)
                            },
                        start = Offset(x, 0f),
                        end = Offset(
                            x,
                            canvasHeight
                        ),
                        strokeWidth = 1f
                    )
                }

                /*
                 * Falling notes.
                 */
                noteEvents.forEach { event ->

                    if (
                        event.pitch !in
                        startPitch..endPitch
                    ) {
                        return@forEach
                    }

                    val noteEndMs =
                        event.startMs +
                                event.durationMs

                    val visibleEndMs =
                        playheadMs +
                                (
                                        canvasHeight /
                                                speedScale
                                        ).toLong()

                    if (
                        noteEndMs < playheadMs ||
                        event.startMs >
                        visibleEndMs
                    ) {
                        return@forEach
                    }

                    val x =
                        (event.pitch - startPitch) *
                                keyWidth

                    /*
                     * IMPORTANT:
                     *
                     * The visual duration is ONLY the actual
                     * MIDI note duration.
                     *
                     * Sustain pedal does not extend this.
                     */
                    val height =
                        (
                                event.durationMs *
                                        speedScale
                                ).coerceAtLeast(6f)

                    val y =
                        canvasHeight -
                                (
                                        (event.startMs -
                                                playheadMs) *
                                                speedScale
                                        ) -
                                height

                    val safeY =
                        y.coerceIn(
                            -height,
                            canvasHeight
                        )

                    /*
                     * Only the current note is highlighted.
                     *
                     * Future notes are NOT targets.
                     */
                    val isCurrent =
                        currentNotes.contains(
                            event.pitch
                        )

                    val isPressed =
                        MidiInputManager
                            .pressedKeys
                            .contains(
                                event.pitch
                            )

                    val color = when {
                        isCurrent && isPressed ->
                            Color(0xFF4CAF50)

                        isCurrent ->
                            Color(0xFFD4AF37)

                        isPressed ->
                            Color(0xFF81C784)

                        else ->
                            Color(0xFF43474E)
                    }

                    drawRect(
                        color = color,
                        topLeft = Offset(
                            x + 2f,
                            safeY
                        ),
                        size = Size(
                            (keyWidth - 4f)
                                .coerceAtLeast(1f),
                            height
                        )
                    )
                }

                /*
                 * Playhead.
                 *
                 * Notes fall toward this line.
                 */
                drawLine(
                    color = Color(0xFFD4AF37),
                    start = Offset(
                        0f,
                        canvasHeight - 2f
                    ),
                    end = Offset(
                        canvasWidth,
                        canvasHeight - 2f
                    ),
                    strokeWidth = 3f
                )
            }

            /*
             * Wait mode indicator.
             */
            if (
                isWaitModeEnabled &&
                currentStep.isNotEmpty()
            ) {
                Surface(
                    modifier =
                        Modifier
                            .align(
                                Alignment.TopCenter
                            )
                            .padding(
                                top = 12.dp
                            ),
                    color =
                        MaterialTheme.colorScheme
                            .secondary,
                    shape =
                        RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text =
                            "PLAY: " +
                                    currentStep
                                        .sorted()
                                        .joinToString(
                                            "  "
                                        ) {
                                            midiPitchName(it)
                                        },
                        modifier =
                            Modifier.padding(
                                horizontal = 12.dp,
                                vertical = 6.dp
                            ),
                        fontSize = 12.sp,
                        fontWeight =
                            FontWeight.Bold,
                        color =
                            MaterialTheme.colorScheme
                                .onSecondary
                    )
                }
            }
        }

        /*
         * =====================================================
         * VIRTUAL KEYBOARD
         * =====================================================
         */

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .background(
                    Color(0xFF111215)
                )
        ) {

            val pressed =
                MidiInputManager
                    .pressedKeys
                    .toSet()

            for (pitch in startPitch..endPitch) {

                val isBlack =
                    isPitchBlack(pitch)

                val isPressed =
                    pressed.contains(pitch)

                val isTarget =
                    currentNotes.contains(pitch)

                val color = when {
                    isPressed && isTarget ->
                        Color(0xFF4CAF50)

                    isPressed ->
                        Color(0xFFD4AF37)

                    isTarget ->
                        Color(0xFF5A4C20)

                    isBlack ->
                        Color(0xFF1E2229)

                    else ->
                        Color(0xFFE3E4E8)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(
                            horizontal = 0.5.dp
                        )
                        .background(color)
                        .clickable {

                            if (
                                MidiInputManager
                                    .pressedKeys
                                    .contains(pitch)
                            ) {
                                MidiInputManager
                                    .simulateNoteOff(
                                        pitch
                                    )
                            } else {
                                MidiInputManager
                                    .simulateNoteOn(
                                        pitch
                                    )
                            }
                        }
                )
            }
        }

        /*
         * =====================================================
         * TIMELINE / A-B CONTROLS
         * =====================================================
         */

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surface
                )
                .padding(
                    horizontal = 12.dp,
                    vertical = 6.dp
                )
        ) {

            Slider(
                value =
                    if (songDurationMs > 0) {
                        playheadMs
                            .coerceIn(
                                0L,
                                songDurationMs
                            )
                            .toFloat() /
                                songDurationMs
                    } else {
                        0f
                    },
                onValueChange = { value ->

                    if (songDurationMs > 0) {
                        playheadMs =
                            (
                                    value *
                                            songDurationMs
                                    ).toLong()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {

                Text(
                    text = formatTime(playheadMs),
                    style =
                        MaterialTheme.typography
                            .bodySmall
                )

                Spacer(
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text =
                        "A ${formatTime(loopStartMs)}",
                    style =
                        MaterialTheme.typography
                            .bodySmall
                )

                Text(
                    text =
                        "B ${formatTime(loopEndMs)}",
                    style =
                        MaterialTheme.typography
                            .bodySmall
                )

                TextButton(
                    onClick = {
                        loopStartMs =
                            playheadMs
                                .coerceIn(
                                    0L,
                                    songDurationMs
                                )

                        if (
                            loopStartMs >=
                            loopEndMs
                        ) {
                            loopEndMs =
                                songDurationMs
                        }
                    }
                ) {
                    Text("Set A")
                }

                TextButton(
                    onClick = {
                        loopEndMs =
                            playheadMs
                                .coerceIn(
                                    0L,
                                    songDurationMs
                                )

                        if (
                            loopEndMs <=
                            loopStartMs
                        ) {
                            loopStartMs = 0L
                        }
                    }
                ) {
                    Text("Set B")
                }

                IconButton(
                    onClick = {
                        isLoopingEnabled =
                            !isLoopingEnabled
                    }
                ) {
                    Icon(
                        imageVector =
                            Icons.Default.Repeat,
                        contentDescription =
                            "Toggle A/B loop",
                        tint =
                            if (isLoopingEnabled) {
                                MaterialTheme.colorScheme
                                    .secondary
                            } else {
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant
                            }
                    )
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1113))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Text(
            text = "Could not load MIDI",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = song.songTitle,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Button(
            onClick = onBack
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = null
            )

            Spacer(
                modifier = Modifier.width(8.dp)
            )

            Text("Back")
        }
    }
}

private fun isPitchBlack(pitch: Int): Boolean {
    val noteInOctave = pitch % 12

    return noteInOctave == 1 ||
            noteInOctave == 3 ||
            noteInOctave == 6 ||
            noteInOctave == 8 ||
            noteInOctave == 10
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)

    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return "%d:%02d".format(
        minutes,
        seconds
    )
}

private fun midiPitchName(pitch: Int): String {
    val names = arrayOf(
        "C",
        "C#",
        "D",
        "D#",
        "E",
        "F",
        "F#",
        "G",
        "G#",
        "A",
        "A#",
        "B"
    )

    val octave = pitch / 12 - 1

    return names[pitch % 12] + octave
}