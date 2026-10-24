package com.lumenchord.pianoweave.ui.screens.pianoroll

import android.Manifest
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lumenchord.pianoweave.audio.AcousticNoteDetector
import com.lumenchord.pianoweave.audio.PianoPlayer
import com.lumenchord.pianoweave.midi.MidiInputManager
import com.lumenchord.pianoweave.ui.components.AppDropdownMenu
import com.lumenchord.pianoweave.ui.components.AppMenuItem
import com.lumenchord.pianoweave.ui.components.MicPermissionDialog
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.viewmodel.PLAYBACK_SPEEDS
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import kotlinx.coroutines.delay

internal enum class SeekDirection { BACKWARD, FORWARD }

internal data class SeekInfo(
    val direction: SeekDirection,
    val id: Long = System.currentTimeMillis()
)

@Composable
internal fun SeekIndicatorOverlay(seekInfo: SeekInfo?) {
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
internal fun ModernToolbar(
    viewModel: PianoWeaveViewModel,
    onBack: () -> Unit,
    onSetClick: () -> Unit
) {
    val appTheme = LocalAppTheme.current
    val ColorGold = appTheme.primaryColor

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val current = LocalContext.current

    var showMicPermissionDialog by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (AcousticNoteDetector.start(current)) {
                viewModel.isWaitModeEnabled = true
            }
        } else {
            viewModel.isPlaying = false
            PianoPlayer.stopAllNotes()
            showMicPermissionDialog = true
        }
    }

    if (showMicPermissionDialog) {
        MicPermissionDialog(
            reason = "Acoustic Wait Mode uses the microphone to listen to your piano and automatically pause the sheet music until you strike the correct notes.",
            onDismiss = { showMicPermissionDialog = false },
            icon = Icons.Default.Timer
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Fades into the falling notes instead of ending in a hard edge
            .background(Brush.verticalGradient(listOf(ColorSurface.copy(alpha = 0.85f), ColorSurface.copy(alpha = 0.45f), Color.Transparent)))
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 10.dp)
            .pointerInput(Unit) { detectTapGestures { } }, // Consume gestures so UI toolbar doesn't pass taps through
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        if (!isPortrait) {
            Text(
                text = viewModel.activePracticeSong?.metadata?.title ?: "",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                modifier = Modifier.weight(1f),
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isPortrait) Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isPortrait) 6.dp else 12.dp)
        ) {
            SpeedChip(
                speed = viewModel.speedMultiplier,
                onSelect = { viewModel.selectPlaybackSpeed(current, it) }
            )
            RollButton(
                isOn = viewModel.isWaitModeEnabled,
                role = Role.Switch,
                onClickLabel = if (viewModel.isWaitModeEnabled) "Turn off wait mode" else "Turn on wait mode",
                horizontalPadding = if (isPortrait) 0.dp else 14.dp,
                onClick = {
                        if (viewModel.isWaitModeEnabled) {
                            AcousticNoteDetector.stop()
                            viewModel.isWaitModeEnabled = false
                        } else {
                            if (MidiInputManager.isMidiDeviceConnected()) {
                                viewModel.isWaitModeEnabled = true
                            } else if (AcousticNoteDetector.hasMicrophonePermission(current)) {
                                if (AcousticNoteDetector.start(current)) {
                                    viewModel.isWaitModeEnabled = true
                                }
                            } else {
                                viewModel.isPlaying = false
                                PianoPlayer.stopAllNotes()
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = if (isPortrait) "Wait mode" else null,
                    modifier = Modifier.size(19.dp)
                )
                if (!isPortrait) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Wait mode",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            if (viewModel.isWaitModeEnabled) {
                // Quick on/off for the STRIKE banner, next to the mode it belongs to
                val isBannerOn = viewModel.isStrikeOverlayEnabled
                RollButton(
                    isOn = isBannerOn,
                    role = Role.Switch,
                    onClick = { viewModel.setStrikeOverlayEnabled(current, !isBannerOn) }
                ) {
                    Icon(
                        imageVector = if (isBannerOn) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                        contentDescription = if (isBannerOn) "Hide note banner" else "Show note banner",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            RollButton(
                onClick = {
                    viewModel.isPlaying = false
                    PianoPlayer.stopAllNotes()
                    onSetClick()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Practice settings",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun speedLabel(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"

/** Current speed; tapping opens the list of speeds. Highlighted when not playing at normal speed. */
@Composable
private fun SpeedChip(speed: Float, onSelect: (Float) -> Unit) {
    val colorGold = LocalAppTheme.current.primaryColor
    val isNormal = speed == 1.0f
    var expanded by remember { mutableStateOf(false) }

    Box {
        RollButton(
            isOn = !isNormal,
            onClick = { expanded = true },
            onClickLabel = "Change playback speed"
        ) {
            Text(
                text = speedLabel(speed),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isNormal) Color.White else LocalContentColor.current,
                // Clear of the pill's rounded end
                modifier = Modifier.padding(start = 14.dp)
            )
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = "Playback speed",
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(20.dp)
            )
        }
        // On the roll's own dark surface, like the toolbar buttons that open it
        AppDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            heading = "Playback speed",
            containerColor = ColorSurface
        ) {
            PLAYBACK_SPEEDS.forEach { option ->
                AppMenuItem(
                    text = speedLabel(option),
                    hint = if (option == 1.0f) "Normal" else null,
                    selected = option == speed,
                    tint = colorGold,
                    role = Role.RadioButton,
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

/**
 * The notes-to-play banner shown while wait mode is paused on a note. [onDismiss] hides it for good
 * (the ✕ or a swipe up); it can be brought back from the toolbar or Settings.
 */
@Composable
internal fun WaitModeOverlay(notes: Set<Int>, isUiHidden: Boolean = false, onDismiss: () -> Unit) {
    val appTheme = LocalAppTheme.current
    val ColorWaitTarget = appTheme.waitTargetColor
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val topPadding = if (isPortrait || !isUiHidden) 56.dp else 12.dp
    val dismissDistancePx = with(LocalDensity.current) { 48.dp.toPx() }

    // Follows the finger upwards while swiping; snaps back if released early
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val animatedOffsetY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = tween(durationMillis = 120),
        label = "strikeBannerDrag"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier
                .graphicsLayer {
                    translationY = animatedOffsetY
                    alpha = 1f - (-animatedOffsetY / dismissDistancePx).coerceIn(0f, 0.7f)
                }
                // Only the pill itself catches touches, so the notes around it stay usable
                .pointerInput(Unit) { detectTapGestures { } }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (-dragOffsetY >= dismissDistancePx) onDismiss()
                            dragOffsetY = 0f
                        },
                        onDragCancel = { dragOffsetY = 0f },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            dragOffsetY = (dragOffsetY + dy).coerceAtMost(0f)
                        }
                    )
                },
            color = ColorSurface.copy(alpha = 0.85f),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Play",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(end = 10.dp)
                )
                // Notes change with every chord, so they fade into each other instead of snapping
                AnimatedContent(
                    targetState = notes.sorted(),
                    transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(100)) },
                    // A wide chord scrolls sideways rather than pushing the ✕ off a narrow screen
                    modifier = Modifier.weight(1f, fill = false),
                    label = "strikeNotes"
                ) { sorted ->
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        sorted.forEach { NoteChip(it, ColorWaitTarget, appTheme.waitTargetLightColor) }
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Hide note banner",
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * One note to strike, in the wait-target color of the notes waiting on the roll: the letter large
 * (with a real sharp sign) and the octave small beside it, so the letter is what the eye catches.
 */
@Composable
private fun NoteChip(pitch: Int, color: Color, lightColor: Color) {
    val name = midiPitchName(pitch)
    val pitchClass = name.takeWhile { !it.isDigit() && it != '-' }
    // The ♯ glyph sits low beside a capital letter, so it's drawn smaller and raised
    val letter = buildAnnotatedString {
        append(pitchClass.take(1))
        if (pitchClass.endsWith("#")) {
            withStyle(SpanStyle(fontSize = 17.sp, baselineShift = BaselineShift(0.28f))) { append("♯") }
        }
    }
    val octave = name.drop(name.takeWhile { !it.isDigit() && it != '-' }.length)
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = letter,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = lightColor
        )
        Text(
            text = octave,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = lightColor.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 1.dp, bottom = 4.dp)
        )
    }
}
