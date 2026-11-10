package com.lumenchord.pianoweave.ui.screens

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.filled.Close
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.MusicNote
import android.Manifest
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.ui.theme.DisplayFontFamily
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lumenchord.pianoweave.audio.AcousticNoteDetector
import com.lumenchord.pianoweave.audio.MetronomeClick
import com.lumenchord.pianoweave.audio.PianoPlayer
import com.lumenchord.pianoweave.audio.VoiceCommand
import com.lumenchord.pianoweave.audio.VoiceCommandRecognizer
import com.lumenchord.pianoweave.audio.VoiceModelStore
import com.lumenchord.pianoweave.midi.Beat
import com.lumenchord.pianoweave.midi.MidiInputManager
import com.lumenchord.pianoweave.midi.MidiNoteEvent
import com.lumenchord.pianoweave.midi.StoredMidi
import com.lumenchord.pianoweave.midi.SustainPedalSpan
import com.lumenchord.pianoweave.ui.screens.pianoroll.*
import com.lumenchord.pianoweave.ui.components.MicPermissionDialog
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import com.lumenchord.pianoweave.ui.viewmodel.VoiceModelState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun ModernPianoPlayerContent(
    song: StoredMidi,
    rawEvents: List<MidiNoteEvent>,
    sustainPedal: List<SustainPedalSpan>,
    beats: List<Beat>,
    viewModel: PianoWeaveViewModel,
    onBack: () -> Unit
) {
    var showSettingsDialog by remember { mutableStateOf(false) }
    var settingsPage by rememberSaveable { mutableStateOf(SettingsPage.Main) }
    var scrollToVoiceCommandsReminder by remember { mutableStateOf(false) }
    var pulseVoiceCommandsReminder by remember { mutableStateOf(false) }
    var showVoiceCommandsStatus by remember { mutableStateOf(false) }
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
    // The beat a count-in is on (1, 2, ...), shown over the stage; 0 when not counting in
    var countInBeat by remember { mutableIntStateOf(0) }
    // Whether playback was already running when the playback loop restarts (speed or wait mode changed):
    // only a fresh press of play counts in
    val playRun = remember { PlayRun() }
    var currentWaitOnsetMs by remember { mutableLongStateOf(-1L) }
    var lastCompletedWaitOnsetMs by remember { mutableLongStateOf(-1L) }
    val waitGate = remember { WaitGate() }

    val chordHits = remember { mutableStateSetOf<Int>() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        PianoPlayer.initialize(context)
    }

    var showTutorial by remember { mutableStateOf(!viewModel.hasSeenPianoRollTutorial(context)) }

    // Keep the song paused while the tutorial is open
    LaunchedEffect(showTutorial) {
        if (showTutorial) viewModel.isPlaying = false
    }

    LaunchedEffect(viewModel.isVoiceCommandsEnabled, viewModel.voiceModelState, viewModel.isVoiceCommandsPillEnabled) {
        if (!viewModel.isVoiceCommandsEnabled || !viewModel.isVoiceCommandsPillEnabled || viewModel.voiceModelState != VoiceModelState.READY) {
            showVoiceCommandsStatus = false
            return@LaunchedEffect
        }
        showVoiceCommandsStatus = true
        delay(3000)
        showVoiceCommandsStatus = false
    }

    var showVoiceHelp by remember { mutableStateOf(false) }
    if (showVoiceHelp) {
        VoiceCommandsHelpDialog(viewModel = viewModel, onDismiss = { showVoiceHelp = false })
    }

    // Wait mode turned on with a MIDI keyboard never asked for the microphone, so unplugging the keyboard
    // (or the mic being revoked in settings) asks for it here instead of quietly turning wait mode off
    var showMicPermissionDialog by remember { mutableStateOf(false) }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && AcousticNoteDetector.start(context)) viewModel.isWaitModeEnabled = true
        else if (!granted) showMicPermissionDialog = true
    }
    if (showMicPermissionDialog) {
        MicPermissionDialog(
            reason = "Acoustic Wait Mode uses the microphone to listen to your piano and automatically pause the sheet music until you strike the correct notes.",
            onDismiss = { showMicPermissionDialog = false },
            icon = Icons.Default.Timer
        )
    }
    val startMicWaitMode = {
        if (viewModel.isWaitModeEnabled && !MidiInputManager.isMidiDeviceConnected() && !AcousticNoteDetector.start(context)) {
            // Off until the request is answered; leaving it on would retry and ask again on every resume
            viewModel.isWaitModeEnabled = false
            if (!AcousticNoteDetector.hasMicrophonePermission(context)) {
                viewModel.isPlaying = false
                PianoPlayer.stopAllNotes()
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Re-runs when a MIDI keyboard is plugged in or out, switching wait mode between keyboard and microphone
    DisposableEffect(lifecycleOwner, viewModel.isWaitModeEnabled, MidiInputManager.isConnected) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                startMicWaitMode()
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                AcousticNoteDetector.stop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        startMicWaitMode()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            AcousticNoteDetector.targetPitches = emptySet()
            AcousticNoteDetector.stop()
        }
    }

    /**
     * Wait mode's chord at [onsetMs]: notes starting within 30 ms of it, except those of the chord passed last
     * (at [completedOnsetMs], -1 if none). Both windows can hold the same note (one 15 ms after the last chord
     * and 25 ms before this one), and it was then wanted twice: a second press for one note.
     */
    fun waitChordNotes(onsetMs: Long, completedOnsetMs: Long) = noteEvents.filter {
        abs(it.startMs - onsetMs) <= 30L && (completedOnsetMs == -1L || it.startMs > completedOnsetMs + 30L)
    }

    val sustainedPitches = remember(noteEvents, viewModel.playheadMs, viewModel.isPlaying, viewModel.isWaitModeEnabled, currentWaitOnsetMs, lastCompletedWaitOnsetMs) {
        val waitTargetPitches = if (viewModel.isWaitModeEnabled && currentWaitOnsetMs != -1L) {
            waitChordNotes(currentWaitOnsetMs, lastCompletedWaitOnsetMs).map { it.pitch }.toSet()
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

    val notesToStrike = remember(noteEvents, nextRequiredOnset, lastCompletedWaitOnsetMs) {
        if (nextRequiredOnset == null) emptySet()
        else waitChordNotes(nextRequiredOnset, lastCompletedWaitOnsetMs).map { it.pitch }.toSet()
    }

    // The chord right after (close enough to be carried over, see WaitGate) may be played together with
    // this one, so the microphone listens for its keys as closely as for this chord's
    val notesToStrikeWithNext = remember(noteEvents, nextRequiredOnset, notesToStrike) {
        if (nextRequiredOnset == null) return@remember emptySet()
        val closeNext = noteEvents.filter { it.startMs > nextRequiredOnset + 30L }.minOfOrNull { it.startMs }
            ?.takeIf { it - nextRequiredOnset <= WaitGate.CARRY_OVER_GAP_MS }
            ?: return@remember notesToStrike
        notesToStrike + waitChordNotes(closeNext, nextRequiredOnset).map { it.pitch }
    }

    LaunchedEffect(notesToStrikeWithNext, viewModel.isWaitModeEnabled, viewModel.isPlaying, MidiInputManager.isConnected) {
        AcousticNoteDetector.targetPitches = if (viewModel.isWaitModeEnabled && viewModel.isPlaying) notesToStrikeWithNext else emptySet()
    }

    // While wait mode listens through the microphone, the song itself stays silent: the user plays
    // it on their piano, and the phone's speaker would otherwise be heard by the mic
    val isAcousticWaitMode = viewModel.isWaitModeEnabled && !MidiInputManager.isConnected
    val isSongSoundOn by rememberUpdatedState(!isAcousticWaitMode)

    LaunchedEffect(isAcousticWaitMode) {
        if (isAcousticWaitMode) PianoPlayer.stopAllNotes()
    }

    LaunchedEffect(viewModel.isPlaying, viewModel.speedMultiplier, viewModel.isWaitModeEnabled, noteEvents) {
        if (!viewModel.isPlaying) {
            playRun.isRunning = false
            return@LaunchedEffect
        }
        val isFreshStart = !playRun.isRunning
        playRun.isRunning = true

        // The metronome follows the song's beats; wait mode stops at every chord, so it has none
        val hasMetronome = beats.isNotEmpty() && !viewModel.isWaitModeEnabled
        // One bar counted in before playing from [fromMs]. With the metronome on it's heard, and starts from
        // the beat at (or just before) [fromMs], moving the playhead back to it, so the clicks lead straight
        // into a beat; with it off, it's only counted on screen and playback starts where it was
        // Without the metronome (or beats) it's a plain countdown instead: 3, 2, 1, a second each, on screen
        suspend fun countIn(fromMs: Long) {
            if (viewModel.isWaitModeEnabled || !viewModel.isCountInEnabled) return
            val isHeard = hasMetronome && viewModel.isMetronomeEnabled
            val startMs = if (isHeard) beatAtOrBefore(beats, fromMs)?.timeMs ?: fromMs else fromMs
            viewModel.playheadMs = startMs
            val plan = (if (isHeard) countInFor(beats, startMs) else null)
                ?: CountIn(clicks = COUNTDOWN_SECONDS, beatMs = 1000L)
            val beatNanos =
                if (!isHeard) plan.beatMs * 1_000_000L
                else (plan.beatMs * 1_000_000.0 / viewModel.speedMultiplier).toLong()
            val startNanos = System.nanoTime()
            // Waits in short steps so turning count-in off mid-count starts playback at once; false if it was
            suspend fun waitUntil(nanos: Long): Boolean {
                while (viewModel.isCountInEnabled) {
                    val left = (nanos - System.nanoTime()) / 1_000_000L
                    if (left <= 0) return true
                    delay(left.coerceAtMost(20L))
                }
                return false
            }
            try {
                for (click in 0 until plan.clicks) {
                    if (!waitUntil(startNanos + click * beatNanos)) return
                    // The beat of the bar this click stands for, so the count runs into the bar where it starts
                    val beatOfBar = plan.beatOfBar(click)
                    countInBeat = if (isHeard) beatOfBar + 1 else plan.clicks - click
                    if (isHeard) MetronomeClick.play(isBarStart = beatOfBar == 0)
                }
                waitUntil(startNanos + plan.clicks * beatNanos)
            } finally {
                countInBeat = 0
            }
        }

        // Play at the end starts the song over; otherwise it stopped again at once and seemed broken
        if (!(viewModel.isLoopingEnabled && viewModel.loopEndMs > viewModel.loopStartMs) && viewModel.playheadMs >= songDurationMs) {
            viewModel.playheadMs = 0L
        }
        if (isFreshStart) countIn(viewModel.playheadMs)

        // Pitches the song itself is sounding, so each gets its note off once no note of it is under the
        // playhead any more (ended, or jumped past by wait mode) and the sustain pedal is up
        val songSoundingPitches = mutableSetOf<Int>()
        fun songNoteOn(note: MidiNoteEvent) {
            PianoPlayer.noteOn(note.pitch, note.velocity)
            songSoundingPitches.add(note.pitch)
        }
        fun releaseEndedNotes() {
            val head = viewModel.playheadMs
            if (sustainPedal.any { head >= it.startMs && head < it.endMs }) return
            songSoundingPitches.retainAll { pitch ->
                val stillSounding = noteEvents.any { it.pitch == pitch && head >= it.startMs && head < it.startMs + it.durationMs }
                // A key the user holds (keyboard or on screen) shares the channel; cutting it would silence them
                if (!stillSounding && pitch !in MidiInputManager.pressedKeys) PianoPlayer.noteOff(pitch)
                stillSounding
            }
        }

        if (isSongSoundOn) {
            val resumed = noteEvents.filter { viewModel.playheadMs >= it.startMs && viewModel.playheadMs < (it.startMs + it.durationMs) }
            resumed.forEach(::songNoteOn)
        }

        lastTriggeredHeadMs = viewModel.playheadMs
        // Just before the playhead, so a beat right on it clicks too
        var lastClickHeadMs = viewModel.playheadMs - 1
        var lastTime = System.nanoTime()

        while (viewModel.isPlaying) {
            val now = System.nanoTime()
            val dt = (now - lastTime) / 1_000_000L
            lastTime = now

            val targetNext = viewModel.playheadMs + (dt * viewModel.speedMultiplier).toLong()

            if (viewModel.isWaitModeEnabled) {
                // The playhead went back (seek, drag, reset, loop wrap): chords at or after it must be
                // played again, and an interrupted wait restarts its timing
                if (lastCompletedWaitOnsetMs != -1L && viewModel.playheadMs <= lastCompletedWaitOnsetMs) {
                    lastCompletedWaitOnsetMs = -1L
                }
                if (currentWaitOnsetMs != -1L && viewModel.playheadMs < currentWaitOnsetMs) {
                    currentWaitOnsetMs = -1L
                }
                // Seeks elsewhere (drag, double tap, reset, voice) clear these two; the gate follows them
                if (currentWaitOnsetMs == -1L) waitGate.restartWait()
                if (lastCompletedWaitOnsetMs == -1L) waitGate.clearCarry()

                val upcoming = noteEvents.filter {
                    it.startMs >= viewModel.playheadMs &&
                            it.startMs <= targetNext &&
                            abs(it.startMs - lastCompletedWaitOnsetMs) > 30L
                }.minOfOrNull { it.startMs }

                if (upcoming != null) {
                    val chordNotes = waitChordNotes(upcoming, lastCompletedWaitOnsetMs)
                    val required = chordNotes.filter { it.pitch in startPitch..endPitch }.map { it.pitch }.toSet()
                    val gate = waitGate.check(
                        chordOnsetMs = upcoming,
                        required = required,
                        now = System.currentTimeMillis(),
                        lastPressAt = { MidiInputManager.lastPressTimestamps[it] ?: 0L },
                        consumedAt = { MidiInputManager.consumedPressTimestamps[it] ?: 0L }
                    )
                    currentWaitOnsetMs = upcoming

                    if (gate.passed || gate.hits != chordHits.toSet()) {
                        Log.d("WaitGate", "onset=$upcoming required=$required hits=${gate.hits} passed=${gate.passed}")
                    }
                    chordHits.clear()
                    chordHits.addAll(gate.hits)

                    if (!gate.passed && required.isNotEmpty()) {
                        gate.expired.forEach(MidiInputManager::simulateExternalNoteOff)
                        viewModel.playheadMs = upcoming
                        releaseEndedNotes()
                        delay(10)
                        continue
                    } else {
                        required.forEach { p ->
                            MidiInputManager.consumedPressTimestamps[p] = MidiInputManager.lastPressTimestamps[p] ?: 0L
                        }
                        // The jump below skips the trigger pass, so the rest of the song at this point
                        // (other hand, keys off screen) sounds here. The keys just played are left out:
                        // the keyboard, or its sound through the app, already plays them
                        if (isSongSoundOn) {
                            chordNotes.filter { it.pitch !in required }.forEach(::songNoteOn)
                        }
                        lastCompletedWaitOnsetMs = upcoming
                        currentWaitOnsetMs = -1L
                        chordHits.clear()
                        // Just past this chord's notes (within 30 ms); a note starting later, even 31 ms,
                        // is the next chord's, so it must not be jumped over (it was, at +35)
                        viewModel.playheadMs = upcoming + 31L
                        lastTriggeredHeadMs = upcoming + 31L
                    }
                }
            }

            val next = targetNext
            if (isSongSoundOn && abs(next - lastTriggeredHeadMs) < 500L) {
                val triggered = noteEvents.filter { it.startMs > lastTriggeredHeadMs && it.startMs <= next }
                triggered.forEach(::songNoteOn)
            }
            lastTriggeredHeadMs = next
            // Like the notes above: a jump (seek, drag) skips the beats in between instead of clicking them all
            if (hasMetronome && viewModel.isMetronomeEnabled && next - lastClickHeadMs in 0L..500L) {
                beatsBetween(beats, lastClickHeadMs, next).lastOrNull()?.let { MetronomeClick.play(it.isBarStart) }
            }
            lastClickHeadMs = next

            // A loop ending at or before its start (set that way in the loop settings) can't play: it
            // jumped back every frame, restarting its notes, so it counts as no loop
            val isLooping = viewModel.isLoopingEnabled && viewModel.loopEndMs > viewModel.loopStartMs
            if (isLooping && next >= viewModel.loopEndMs) {
                viewModel.playheadMs = viewModel.loopStartMs
                lastTriggeredHeadMs = viewModel.loopStartMs
                PianoPlayer.stopAllNotes()
                songSoundingPitches.clear()
                countIn(viewModel.loopStartMs)
                // The count-in may have moved it back to a beat
                val restartMs = viewModel.playheadMs
                lastTriggeredHeadMs = restartMs
                lastClickHeadMs = restartMs - 1
                lastTime = System.nanoTime()
                val loopNotes = noteEvents.filter { restartMs >= it.startMs && restartMs < (it.startMs + it.durationMs) }
                if (isSongSoundOn) loopNotes.forEach(::songNoteOn)
            } else if (!isLooping && next >= songDurationMs) {
                viewModel.playheadMs = songDurationMs
                viewModel.isPlaying = false
            } else {
                if (viewModel.playheadMs < next) {
                    viewModel.playheadMs = next
                }
            }
            releaseEndedNotes()
            delay(10)
        }
    }

    val isWaitingAtBaseline = viewModel.isPlaying && viewModel.isWaitModeEnabled && currentWaitOnsetMs != -1L
    val waitTargetPitches = if (isWaitingAtBaseline) notesToStrike else emptySet()
    var seekInfo by remember { mutableStateOf<SeekInfo?>(null) }
    var isUiHidden by remember { mutableStateOf(false) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // A voice model download that fails while practicing says so (the toolbar mic alone is easy to miss)
    var lastVoiceModelState by remember { mutableStateOf(viewModel.voiceModelState) }
    LaunchedEffect(viewModel.voiceModelState) {
        val failedNow = lastVoiceModelState == VoiceModelState.DOWNLOADING && viewModel.voiceModelState == VoiceModelState.FAILED
        lastVoiceModelState = viewModel.voiceModelState
        if (!failedNow || !viewModel.isVoiceCommandsEnabled) return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        val result = snackbarHostState.showSnackbar(
            message = voiceModelProblemText(VoiceModelState.FAILED, viewModel.voiceModelError),
            actionLabel = "Retry",
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.downloadVoiceModel(context)
    }

    BackHandler(enabled = isUiHidden) {
        isUiHidden = false
    }

    // The controller outlives a change of song's beats (it's remembered once), so it reads the latest
    val currentBeats by rememberUpdatedState(beats)
    // Voice commands listen while the screen is in the foreground (the recognizer frees its model on pause)
    val voiceController = remember {
        VoiceCommandController(
            viewModel,
            isFullscreen = { isUiHidden },
            setFullscreen = { isUiHidden = it },
            hasBeats = { currentBeats.isNotEmpty() }
        )
    }
    val isVoiceActive = viewModel.isVoiceCommandsEnabled && viewModel.voiceModelState == VoiceModelState.READY

    // Wait mode stops at every chord, so a steady click can't go with it (and the mic would hear it):
    // turning wait mode on, however it's done, turns the metronome off and says so
    LaunchedEffect(viewModel.isWaitModeEnabled) {
        if (viewModel.isWaitModeEnabled && viewModel.isMetronomeEnabled) {
            viewModel.setMetronomeEnabled(context, false)
            if (beats.isNotEmpty()) voiceController.showNote("Metronome off for wait mode", Icons.Default.MusicNote)
        }
    }
    DisposableEffect(lifecycleOwner, isVoiceActive) {
        if (!isVoiceActive) return@DisposableEffect onDispose { }
        val modelDir = VoiceModelStore.modelDir(context)
        // addObserver replays ON_RESUME when already resumed, which starts listening here
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) VoiceCommandRecognizer.start(context, modelDir)
            else if (event == Lifecycle.Event.ON_PAUSE) VoiceCommandRecognizer.stop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            VoiceCommandRecognizer.stop()
        }
    }

    // A jump by voice resets the wait and sound state like the timeline's own seeks
    fun afterVoiceJump() {
        PianoPlayer.stopAllNotes()
        lastTriggeredHeadMs = viewModel.playheadMs
        currentWaitOnsetMs = -1L
        lastCompletedWaitOnsetMs = -1L
        chordHits.clear()
    }

    LaunchedEffect(viewModel.voiceListeningMode) {
        VoiceCommandRecognizer.listeningMode = viewModel.voiceListeningMode
    }

    val currentSongDurationMs by rememberUpdatedState(songDurationMs)
    LaunchedEffect(Unit) {
        VoiceCommandRecognizer.commands.collect { commands ->
            // Dialogs keep the song paused; a command there would start it behind them
            if (showTutorial || showSettingsDialog) return@collect
            if (voiceController.apply(context, commands, currentSongDurationMs)) afterVoiceJump()
        }
    }
    LaunchedEffect(Unit) {
        VoiceCommandRecognizer.woke.collect {
            if (!showTutorial && !showSettingsDialog) voiceController.showWoke()
        }
    }
    // Debug builds show what a missed command sounded like, to tune recognition on real rooms
    if (isDebuggableBuild(context)) {
        LaunchedEffect(Unit) {
            VoiceCommandRecognizer.unmatched.collect { heard ->
                if (!showTutorial && !showSettingsDialog) voiceController.showUnmatched(heard)
            }
        }
    }

    // Taller with the bars hidden; the count-in also uses it to sit in the middle of notes and keys together
    val keyboardHeight = if (isUiHidden) 100.dp else 70.dp

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
                .onSizeChanged { containerSize = it }
                .pointerInput(songDurationMs, isUiHidden) {
                    val scale = 0.25f
                    val maxDist = 35.dp.toPx()
                    detectPianoRollGestures(
                        maxDistance = maxDist,
                        onTap = {
                            viewModel.isPlaying = !viewModel.isPlaying
                        },
                        onDoubleTap = { offset ->
                            // Double tap on falling notes section turns on / off UI bars visibility
                            isUiHidden = !isUiHidden

                            /*
                             * PREVIOUS MECHANIC (Moving back/forward 5s on double tap):
                             * Preserved below for reference/restoration:
                             *
                             * val isLeft = offset.x < size.width / 2f
                             * PianoPlayer.stopAllNotes()
                             * currentWaitOnsetMs = -1L
                             * lastCompletedWaitOnsetMs = -1L
                             * chordHits.clear()
                             * viewModel.playheadMs = calculateSeekPosition(
                             *     currentMs = viewModel.playheadMs,
                             *     deltaMs = if (isLeft) -5000L else 5000L,
                             *     songDurationMs = songDurationMs,
                             *     isLoopingEnabled = viewModel.isLoopingEnabled,
                             *     loopStartMs = viewModel.loopStartMs,
                             *     loopEndMs = viewModel.loopEndMs
                             * )
                             * seekInfo = SeekInfo(if (isLeft) SeekDirection.BACKWARD else SeekDirection.FORWARD)
                             */
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
                beats = beats,
                head = viewModel.playheadMs,
                start = startPitch,
                numWhiteKeys = totalWhiteKeys,
                waitTargetPitches = waitTargetPitches,
                satisfiedPitches = emptySet()
            )

            // The count, with a way to turn count-in off right where it's noticed; both only while counting
            var shownCountInBeat by remember { mutableIntStateOf(1) }
            LaunchedEffect(countInBeat) { if (countInBeat > 0) shownCountInBeat = countInBeat }
            // Qualified: inside the screen's Column, the bare name resolves to the Column-scoped overload
            androidx.compose.animation.AnimatedVisibility(
                visible = countInBeat > 0,
                // With the bars shown, nudged down toward the keyboard below the notes; in full screen, in the
                // middle of the notes
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = if (isUiHidden) 0.dp else keyboardHeight * 0.33f),
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(200))
            ) {
                val accent = LocalAppTheme.current.primaryColor
                // The number on its own, centred, with a small ✕ at its top-right that looks like the
                // toolbar's round buttons (dark tile, faint edge, accent icon), so it plainly turns the
                // count-in off. The box is padded the same on both sides, which keeps the number in the
                // middle and the ✕ inside the box (taps outside a parent's bounds don't reach its children)
                Box(modifier = Modifier.padding(horizontal = 32.dp)) {
                    Text(
                        text = (if (countInBeat > 0) countInBeat else shownCountInBeat).toString(),
                        color = accent.copy(alpha = 0.85f),
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 72.sp
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 34.dp, y = 2.dp)
                            // A full-size touch target around the small circle
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(onClickLabel = "Turn off count-in", role = Role.Button) {
                                // Stops here instead of playing on: the count was the lead-in, and after closing
                                // it the user starts again when ready (with no count now)
                                viewModel.isPlaying = false
                                viewModel.setCountInEnabled(context, false)
                                // Like hiding the note banner: a snackbar says where it's brought back, with Undo
                                coroutineScope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val result = snackbarHostState.showSnackbar(
                                        message = if (viewModel.isVoiceCommandsEnabled) "Count-in turned off. Bring it back from Settings or say “count”."
                                        else "Count-in turned off. Bring it back from Settings.",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.setCountInEnabled(context, true)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Just a faint ✕, with no button around it: there when looked for, without inviting a
                        // tap or pulling the eye from the count
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Turn off count-in",
                            tint = Color.White.copy(alpha = 0.22f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            if (!isUiHidden) {
                ModernToolbar(
                    viewModel = viewModel,
                    onBack = onBack,
                    onSetClick = {
                        viewModel.isPlaying = false
                        showSettingsDialog = true
                    },
                    metronome = SpeedChipMetronome(
                        isOn = beats.isNotEmpty() && viewModel.isMetronomeEnabled,
                        onToggle = {
                            when {
                                beats.isEmpty() ->
                                    voiceController.showNote("No beat information for this piece", Icons.Default.MusicNote)
                                viewModel.isWaitModeEnabled && !viewModel.isMetronomeEnabled ->
                                    voiceController.showNote("No metronome in wait mode · turn wait mode off first", Icons.Default.MusicNote)
                                else -> {
                                    val on = !viewModel.isMetronomeEnabled
                                    viewModel.setMetronomeEnabled(context, on)
                                    voiceController.showNote(
                                        if (on) "Metronome on · tap ♪ to turn off" else "Metronome off",
                                        Icons.Default.MusicNote
                                    )
                                }
                            }
                        },
                        pulse = {
                            // Bright on each beat, fading over 200 ms of real time; dim while paused
                            val beat = beatAtOrBefore(beats, viewModel.playheadMs)
                            if (beat == null || !viewModel.isPlaying) 0f
                            else (1f - (viewModel.playheadMs - beat.timeMs) / viewModel.speedMultiplier / 200f).coerceIn(0f, 1f)
                        }
                    )
                )
            }

            if (isWaitingAtBaseline && waitTargetPitches.isNotEmpty() && viewModel.isStrikeOverlayEnabled) {
                WaitModeOverlay(
                    notes = waitTargetPitches,
                    isUiHidden = isUiHidden,
                    onDismiss = {
                        viewModel.setStrikeOverlayEnabled(context, false)
                        coroutineScope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            val result = snackbarHostState.showSnackbar(
                                message = "Note banner hidden. Bring it back from the toolbar or Settings.",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.setStrikeOverlayEnabled(context, true)
                            }
                        }
                    }
                )
            }

            SeekIndicatorOverlay(seekInfo)

            androidx.compose.animation.AnimatedVisibility(
                visible = showVoiceCommandsStatus,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(200)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (isUiHidden) 12.dp else 58.dp)
            ) {
                VoiceFeedbackChip(
                    feedback = VoiceFeedback(VoiceCommand.UNDO, "Voice commands on", canUndo = false),
                    onUndo = { if (voiceController.undoLast(context)) afterVoiceJump() },
                    modifier = Modifier,
                    linkText = "What can I say?",
                    onOpenHelp = { showVoiceHelp = true },
                    onOpenSettings = {
                        settingsPage = SettingsPage.VoiceCommands
                        scrollToVoiceCommandsReminder = true
                        pulseVoiceCommandsReminder = false
                        showSettingsDialog = true
                    },
                    compactLinkOnly = true
                )
            }

            VoiceFeedbackChip(
                feedback = voiceController.feedback,
                onUndo = { if (voiceController.undoLast(context)) afterVoiceJump() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (isUiHidden) 12.dp else 58.dp)
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = ColorSurface,
                    contentColor = Color.White,
                    actionColor = LocalAppTheme.current.primaryColor
                )
            }

            DraggableVisibilityButton(
                isUiHidden = isUiHidden,
                onToggleVisibility = { isUiHidden = !isUiHidden },
                containerSize = containerSize,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp, top = if (isUiHidden) 12.dp else 58.dp)
            )
        }


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
            hasBeats = beats.isNotEmpty(),
            initialPage = settingsPage,
            scrollToVoiceCommandsReminder = scrollToVoiceCommandsReminder,
            pulseVoiceCommandsReminder = pulseVoiceCommandsReminder,
            onVoiceCommandsReminderScrolled = {
                scrollToVoiceCommandsReminder = false
                pulseVoiceCommandsReminder = true
            },
            onVoiceCommandsReminderPulseDone = { pulseVoiceCommandsReminder = false },
            onOpenTutorial = {
                showSettingsDialog = false
                settingsPage = SettingsPage.Main
                scrollToVoiceCommandsReminder = false
                pulseVoiceCommandsReminder = false
                showTutorial = true
            },
            onDismiss = {
                showSettingsDialog = false
                settingsPage = SettingsPage.Main
                scrollToVoiceCommandsReminder = false
                pulseVoiceCommandsReminder = false
            }
        )
    }

    if (showTutorial) {
        PianoRollTutorialDialog(
            onDismiss = {
                showTutorial = false
                viewModel.markPianoRollTutorialSeen(context)
            }
        )
    }
}
@Composable
private fun DraggableVisibilityButton(
    isUiHidden: Boolean,
    onToggleVisibility: () -> Unit,
    containerSize: IntSize,
    modifier: Modifier = Modifier
) {
    var buttonOffset by remember { mutableStateOf(Offset.Zero) }
    val viewConfiguration = LocalViewConfiguration.current
    val density = LocalDensity.current
    val touchSlop = viewConfiguration.touchSlop

    Box(
        modifier = modifier
            .offset { IntOffset(buttonOffset.x.roundToInt(), buttonOffset.y.roundToInt()) }
            .size(38.dp)
            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            .pointerInput(containerSize, isUiHidden) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var totalDrag = 0f
                    val pointerId = down.id

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) {
                            if (totalDrag < touchSlop) {
                                onToggleVisibility()
                            }
                            change.consume()
                            break
                        }
                        val dragDelta = change.positionChange()
                        if (dragDelta != Offset.Zero) {
                            totalDrag += dragDelta.getDistance()
                            if (totalDrag >= touchSlop) {
                                if (containerSize != IntSize.Zero) {
                                    val buttonSizePx = with(density) { 38.dp.toPx() }
                                    val marginEndPx = with(density) { 12.dp.toPx() }
                                    val marginTopPx = with(density) { (if (isUiHidden) 12.dp else 58.dp).toPx() }

                                    val minX = -(containerSize.width - buttonSizePx - marginEndPx).coerceAtLeast(0f)
                                    val maxX = marginEndPx
                                    val minY = -marginTopPx
                                    val maxY = (containerSize.height - buttonSizePx - marginTopPx).coerceAtLeast(0f)

                                    val newX = (buttonOffset.x + dragDelta.x).coerceIn(minX, maxX)
                                    val newY = (buttonOffset.y + dragDelta.y).coerceIn(minY, maxY)
                                    buttonOffset = Offset(newX, newY)
                                } else {
                                    buttonOffset += dragDelta
                                }
                            }
                        }
                        change.consume()
                    }
                }
            },
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

/** See `playRun` in [ModernPianoPlayerContent]. */
private class PlayRun {
    var isRunning = false
}

/** The count-in without the metronome: a countdown of this many seconds. */
private const val COUNTDOWN_SECONDS = 3
