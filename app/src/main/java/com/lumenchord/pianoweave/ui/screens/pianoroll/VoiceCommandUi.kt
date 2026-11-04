package com.lumenchord.pianoweave.ui.screens.pianoroll

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import com.lumenchord.pianoweave.ui.components.IconBadge
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Lock
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lumenchord.pianoweave.audio.AcousticNoteDetector
import com.lumenchord.pianoweave.audio.VoiceCommandRecognizer
import com.lumenchord.pianoweave.audio.VoiceModelError
import com.lumenchord.pianoweave.audio.VoiceModelStore
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.CloudDownload
import com.lumenchord.pianoweave.ui.components.DialogHeader
import com.lumenchord.pianoweave.ui.components.DialogSurface
import com.lumenchord.pianoweave.ui.components.MicPermissionDialog
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.theme.PillShape
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import com.lumenchord.pianoweave.ui.viewmodel.VoiceModelState
import kotlinx.coroutines.delay

/** Debug (developer) builds show voice tuning aids that release builds don't. */
internal fun isDebuggableBuild(context: Context): Boolean =
    (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

/**
 * Turning voice commands on or off from any control. Turning on asks for the microphone first, and
 * explains the feature and its one-time model download before fetching it. Renders its own dialogs.
 */
@Composable
internal fun rememberVoiceCommandsToggle(viewModel: PianoWeaveViewModel): (Boolean) -> Unit {
    val context = LocalContext.current
    var showIntro by remember { mutableStateOf(false) }
    var showMicDenied by remember { mutableStateOf(false) }

    fun enableWithMic() {
        if (viewModel.voiceModelState == VoiceModelState.READY) {
            viewModel.setVoiceCommandsEnabled(context, true)
        } else {
            // The song shouldn't play on under the dialog (PianoRollScreen silences its notes on pause)
            viewModel.isPlaying = false
            showIntro = true
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) enableWithMic() else showMicDenied = true
    }

    if (showIntro) {
        VoiceCommandsIntroDialog(
            needsDownload = viewModel.voiceModelState != VoiceModelState.READY,
            onConfirm = {
                showIntro = false
                viewModel.setVoiceCommandsEnabled(context, true)
            },
            onDismiss = { showIntro = false }
        )
    }
    if (showMicDenied) {
        MicPermissionDialog(
            reason = "Voice commands use the microphone to hear short words like “pause” or “loop”, so you can practice without touching the screen.",
            onDismiss = { showMicDenied = false }
        )
    }

    return { enabled ->
        when {
            !enabled -> viewModel.setVoiceCommandsEnabled(context, false)
            !AcousticNoteDetector.hasMicrophonePermission(context) -> {
                viewModel.isPlaying = false
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> enableWithMic()
        }
    }
}

/** What's wrong with the voice model, and what to do about it, for [state] FAILED or MISSING. */
internal fun voiceModelProblemText(state: VoiceModelState, error: VoiceModelError?): String = when {
    state == VoiceModelState.MISSING ->
        "The voice model isn't downloaded yet, so voice commands can't listen. It's a one-time ${VoiceModelStore.DOWNLOAD_SIZE_MB} MB download."
    error == VoiceModelError.NO_SPACE ->
        "Not enough free space on your phone. The voice model needs about ${VoiceModelStore.REQUIRED_FREE_MB} MB; free some up and try again."
    error == VoiceModelError.NO_CONNECTION ->
        "The voice model couldn't be downloaded. Check your internet connection and try again."
    else -> "The voice model download didn't come through. Please try again in a little while."
}

/**
 * Toolbar mic: off, downloading the model (progress ring), or listening. While listening it brightens
 * as soon as an utterance that could be a command starts, so you see it was heard before it's applied.
 * On but without a model (failed or interrupted download), it's crossed out with a dot, and a tap
 * explains why and offers the download (or turning voice commands off).
 */
@Composable
internal fun VoiceCommandButton(viewModel: PianoWeaveViewModel, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val enabled = viewModel.isVoiceCommandsEnabled
    val modelState = viewModel.voiceModelState
    val status by VoiceCommandRecognizer.status.collectAsState()
    val isHearing by VoiceCommandRecognizer.isHearing.collectAsState()
    val isListening = enabled && modelState == VoiceModelState.READY && status == VoiceCommandRecognizer.Status.LISTENING
    val needsModel = enabled && (modelState == VoiceModelState.FAILED || modelState == VoiceModelState.MISSING)
    var showProblem by remember { mutableStateOf(false) }

    val hearingAlpha by animateFloatAsState(if (isHearing) 1f else 0f, tween(120), label = "voiceHearing")

    if (showProblem && needsModel) {
        VoiceModelProblemDialog(
            state = modelState,
            error = viewModel.voiceModelError,
            onDownload = {
                showProblem = false
                viewModel.downloadVoiceModel(context)
            },
            onTurnOff = {
                showProblem = false
                onToggle(false)
            },
            onDismiss = { showProblem = false }
        )
    }

    RollButton(
        isOn = isListening,
        role = Role.Switch,
        onClickLabel = when {
            needsModel -> "Voice model needed"
            enabled -> "Turn off voice commands"
            else -> "Turn on voice commands"
        },
        onClick = {
            if (needsModel) {
                viewModel.isPlaying = false
                showProblem = true
            } else {
                onToggle(!enabled)
            }
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (needsModel) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 3.dp, y = (-3).dp)
                        .size(7.dp)
                        .background(LocalAppTheme.current.primaryColor, CircleShape)
                )
            }
            if (enabled && modelState == VoiceModelState.DOWNLOADING) {
                CircularProgressIndicator(
                    progress = { viewModel.voiceModelProgress },
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                    color = LocalAppTheme.current.primaryColor,
                    trackColor = Color.White.copy(alpha = 0.12f)
                )
            }
            Icon(
                imageVector = if (enabled && !needsModel) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = when {
                    !enabled -> "Voice commands off"
                    modelState == VoiceModelState.DOWNLOADING -> "Downloading voice model"
                    modelState == VoiceModelState.FAILED -> "Voice model download failed"
                    modelState == VoiceModelState.MISSING -> "Voice model not downloaded"
                    else -> "Voice commands on"
                },
                modifier = Modifier
                    .size(if (enabled && modelState == VoiceModelState.DOWNLOADING) 15.dp else 19.dp)
                    .graphicsLayer {
                        // No idle animation: the toolbar shouldn't redraw every frame just to look alive
                        alpha = if (isListening) 0.7f + 0.3f * hearingAlpha else 1f
                        val grow = 1f + 0.15f * hearingAlpha
                        scaleX = grow
                        scaleY = grow
                    }
            )
        }
    }
}

/** A short note of what a voice command did; tap it (or say "undo") to take it back. */
@Composable
internal fun VoiceFeedbackChip(
    feedback: VoiceFeedback?,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAppTheme.current.primaryColor
    var shown by remember { mutableStateOf<VoiceFeedback?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(feedback) {
        if (feedback == null) return@LaunchedEffect
        shown = feedback
        visible = true
        delay(2400)
        visible = false
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(150)) + slideInVertically(tween(200)) { -it / 2 },
        exit = fadeOut(tween(250))
    ) {
        val info = shown ?: return@AnimatedVisibility
        Surface(
            shape = PillShape,
            color = Color.Black.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.7f)),
            modifier = if (info.canUndo) Modifier.clickable(onClickLabel = "Undo", onClick = {
                visible = false
                onUndo()
            }) else Modifier
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Text(info.text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (info.canUndo) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

/** Spoken examples on the intro, as chips. */
private val INTRO_EXAMPLES = listOf("pause", "play", "loop", "slower", "full screen, play")

/**
 * What voice commands are, before they're turned on: a line on the idea, a few examples to say, and
 * four short tips (how to say them, undo, Selah, privacy). One column; on a short (landscape) screen
 * the content scrolls while the buttons stay pinned below it.
 */
@Composable
private fun VoiceCommandsIntroDialog(needsDownload: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val appTheme = LocalAppTheme.current
    val accent = appTheme.primaryColor
    val colorSlate = MaterialTheme.colorScheme.tertiaryContainer
    val compact = LocalConfiguration.current.screenHeightDp < 480
    val tips = listOf(
        Icons.Default.Mic to "Say it on its own, with a short pause around it. A few in a row work too.",
        Icons.AutoMirrored.Filled.Undo to "Misheard? Say “undo” to take it back.",
        Icons.Default.Tune to "Noisy room or a lesson? Choose “Selah” first in settings.",
        Icons.Default.Lock to "Private: recognized on your phone, nothing is sent." +
            if (needsDownload) " Needs a one-time ${VoiceModelStore.DOWNLOAD_SIZE_MB} MB download." else ""
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Full-window box that centers the card (the window is full width), and dismisses on a tap beside it
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null, onClick = onDismiss)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            DialogSurface(
                light = accent,
                modifier = Modifier
                    // Narrower on a landscape phone, where a wide, short box read like a banner. The cap goes
                    // before fillMaxWidth: after it, the full width was already fixed and the cap did nothing.
                    .widthIn(max = if (compact) 360.dp else 420.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = ColorSurface,
                border = BorderStroke(1.dp, colorSlate)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = if (compact) 18.dp else 20.dp, vertical = if (compact) 14.dp else 20.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)
                ) {
                    // Only the content scrolls; the buttons always stay in view
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp)
                    ) {
                        IntroHeader(accent, compact)
                        IntroTips(tips, accent, compact)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Not now", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Button(
                            onClick = onConfirm,
                            shape = PillShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = if (appTheme.isLightAccent) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(if (needsDownload) "Download & turn on" else "Turn on", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntroHeader(accent: Color, compact: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Default.RecordVoiceOver, tint = accent, size = if (compact) 32.dp else 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Voice Commands",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = accent
            )
        }
        Text(
            text = "Keep your hands on the keys and just say what to do. Try:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 20.sp
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            INTRO_EXAMPLES.forEach { example ->
                Text(
                    text = "“$example”",
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .border(1.dp, accent.copy(alpha = 0.5f), PillShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun IntroTips(tips: List<Pair<ImageVector, String>>, accent: Color, compact: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)
    ) {
        tips.forEach { (icon, text) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

/** Voice commands are on but have no model: why (from [voiceModelProblemText]), then download or turn off. */
@Composable
private fun VoiceModelProblemDialog(
    state: VoiceModelState,
    error: VoiceModelError?,
    onDownload: () -> Unit,
    onTurnOff: () -> Unit,
    onDismiss: () -> Unit
) {
    val appTheme = LocalAppTheme.current
    val accent = appTheme.primaryColor
    val colorSlate = MaterialTheme.colorScheme.tertiaryContainer

    Dialog(onDismissRequest = onDismiss) {
        DialogSurface(
            light = accent,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = ColorSurface,
            border = BorderStroke(1.dp, colorSlate)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DialogHeader(
                    title = if (state == VoiceModelState.FAILED) "Download Failed" else "Voice Model Needed",
                    icon = Icons.Default.CloudDownload,
                    onClose = onDismiss,
                    titleColor = accent
                )
                Text(
                    text = voiceModelProblemText(state, error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onTurnOff) {
                        Text("Turn off", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Button(
                        onClick = onDownload,
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = if (appTheme.isLightAccent) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            if (state == VoiceModelState.FAILED) "Try again" else "Download",
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}
