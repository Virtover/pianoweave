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
import com.lumenchord.pianoweave.audio.VoiceModelStore
import com.lumenchord.pianoweave.ui.components.DialogHeader
import com.lumenchord.pianoweave.ui.components.DialogSurface
import com.lumenchord.pianoweave.ui.components.MicPermissionDialog
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.theme.PillShape
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import com.lumenchord.pianoweave.ui.viewmodel.VoiceModelState
import kotlinx.coroutines.delay

/** The spoken words, as shown to the user. */
internal const val VOICE_COMMANDS_HELP =
    "Say it on its own: play, pause, wait, loop, full screen, loop start, loop end, again, back, forward, slower, faster, normal. " +
        "Several in a row work too (“full screen, play”). Say “undo” to take one back."

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

/**
 * Toolbar mic: off, downloading the model (progress ring), or listening. While listening it brightens
 * as soon as an utterance that could be a command starts, so you see it was heard before it's applied.
 */
@Composable
internal fun VoiceCommandButton(viewModel: PianoWeaveViewModel, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val enabled = viewModel.isVoiceCommandsEnabled
    val modelState = viewModel.voiceModelState
    val status by VoiceCommandRecognizer.status.collectAsState()
    val isHearing by VoiceCommandRecognizer.isHearing.collectAsState()
    val isListening = enabled && modelState == VoiceModelState.READY && status == VoiceCommandRecognizer.Status.LISTENING

    val hearingAlpha by animateFloatAsState(if (isHearing) 1f else 0f, tween(120), label = "voiceHearing")

    RollButton(
        isOn = isListening,
        role = Role.Switch,
        onClickLabel = when {
            enabled && modelState == VoiceModelState.FAILED -> "Retry voice model download"
            enabled -> "Turn off voice commands"
            else -> "Turn on voice commands"
        },
        onClick = {
            if (enabled && modelState == VoiceModelState.FAILED) viewModel.downloadVoiceModel(context)
            else onToggle(!enabled)
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
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
                imageVector = if (enabled && modelState != VoiceModelState.FAILED) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = when {
                    !enabled -> "Voice commands off"
                    modelState == VoiceModelState.DOWNLOADING -> "Downloading voice model"
                    modelState == VoiceModelState.FAILED -> "Voice model download failed"
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

@Composable
private fun VoiceCommandsIntroDialog(needsDownload: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val appTheme = LocalAppTheme.current
    val colorGold = appTheme.primaryColor
    val colorSurface = MaterialTheme.colorScheme.surface
    val colorSlate = MaterialTheme.colorScheme.tertiaryContainer

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            DialogSurface(
                light = colorGold,
                modifier = Modifier
                    .fillMaxWidth(0.98f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = colorSurface,
                border = BorderStroke(1.dp, colorSlate)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DialogHeader(
                        title = "Voice Commands",
                        icon = Icons.Default.Mic,
                        onClose = onDismiss,
                        titleColor = colorGold
                    )

                    Text(
                        text = "Keep your hands on the keys. $VOICE_COMMANDS_HELP\n\n" +
                                "In a noisy room you can have it listen only after “Selah” (Settings → Voice Commands).\n\n" +
                                "Speech is recognized on your device, nothing is sent anywhere." +
                                if (needsDownload) " It needs a one-time ${VoiceModelStore.DOWNLOAD_SIZE_MB} MB download." else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 46.dp),
                            shape = PillShape,
                            border = BorderStroke(1.dp, colorSlate),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Not now",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        }

                        Button(
                            onClick = onConfirm,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 46.dp),
                            shape = PillShape,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorGold,
                                contentColor = if (appTheme.isLightAccent) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = if (needsDownload) "Download & turn on" else "Turn on",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}
