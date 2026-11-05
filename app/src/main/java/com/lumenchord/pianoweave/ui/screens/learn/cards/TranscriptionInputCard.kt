package com.lumenchord.pianoweave.ui.screens.learn.cards

import com.lumenchord.pianoweave.ui.theme.PillShape
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import com.lumenchord.pianoweave.api.UploadFormats
import com.lumenchord.pianoweave.ui.viewmodel.TranscriptionSource
import androidx.compose.ui.text.buildAnnotatedString
import android.Manifest
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lumenchord.pianoweave.audio.AcousticNoteDetector
import com.lumenchord.pianoweave.ui.components.MicPermissionDialog
import androidx.compose.foundation.text.TextAutoSize
import com.lumenchord.pianoweave.ui.screens.learn.DiscardRecordingDialog
import com.lumenchord.pianoweave.ui.viewmodel.statusColor
import com.lumenchord.pianoweave.ui.viewmodel.ServerStatus
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.lumenchord.pianoweave.ui.components.appendLink
import com.lumenchord.pianoweave.ui.components.IconBadge
import com.lumenchord.pianoweave.ui.components.cardWash
import com.lumenchord.pianoweave.ui.components.quietFieldColors
import androidx.compose.material.icons.outlined.Info
import com.lumenchord.pianoweave.ui.screens.learn.LinkInputAnchors
import com.lumenchord.pianoweave.ui.screens.learn.TranscriptionTermsDialog
import com.lumenchord.pianoweave.util.AppLinks
import com.lumenchord.pianoweave.util.LegalConsent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import com.lumenchord.pianoweave.ui.theme.Moods

@Composable
fun TranscriptionInputCard(
    viewModel: PianoWeaveViewModel,
    context: Context,
    onOpenServerSettings: () -> Unit,
    linkInputAnchors: LinkInputAnchors = remember { LinkInputAnchors() }
) {
    val isSuccess = !viewModel.isLoading && viewModel.readySong != null
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.selectMediaFile(context, it) }
    }
    var showMicPermissionDialog by remember { mutableStateOf(false) }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startRecording(context) else showMicPermissionDialog = true
    }
    // Servers that cannot take uploads (e.g. after switching servers) only offer links.
    val canUseFiles = viewModel.supportsFileUpload || viewModel.isLoading
    val source = if (canUseFiles) viewModel.transcriptionSource else TranscriptionSource.Link
    val selectedFile = when (source) {
        TranscriptionSource.Link -> null
        TranscriptionSource.File -> viewModel.selectedMediaFile
        TranscriptionSource.Record -> viewModel.recordedMediaFile
    }
    val pickFile = { filePicker.launch(UploadFormats.mimeTypes) }
    val startRecording = {
        if (AcousticNoteDetector.hasMicrophonePermission(context)) viewModel.startRecording(context)
        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Losing a longer take by a stray tap is costly, so throwing one away asks first.
    var pendingDiscard by remember { mutableStateOf<(() -> Unit)?>(null) }
    val confirmDiscard = { action: () -> Unit ->
        val takeSeconds = if (viewModel.isRecording) viewModel.recordingElapsedMs / 1000f
        else viewModel.recordedMediaFile?.durationSeconds ?: 0f
        if (takeSeconds > DISCARD_CONFIRM_SECONDS) pendingDiscard = action else action()
    }
    val record = { confirmDiscard(startRecording) }
    val discard = { confirmDiscard { viewModel.discardRecording() } }

    pendingDiscard?.let { action ->
        DiscardRecordingDialog(
            isRecording = viewModel.isRecording,
            onConfirm = {
                pendingDiscard = null
                action()
            },
            onDismiss = { pendingDiscard = null }
        )
    }

    // Android silences the microphone once the app is in the background, so the take ends there.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && viewModel.isRecording) viewModel.stopRecording()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showTermsDialog by remember { mutableStateOf(false) }
    var hasAcceptedTerms by remember { mutableStateOf(LegalConsent.hasAcceptedTerms(context)) }
    val transcribe = {
        if (viewModel.isBilledServer && !viewModel.isGoogleSignedIn) {
            viewModel.signInWithGoogle(context)
        } else {
            viewModel.startTranscription(context)
        }
    }
    if (showTermsDialog) {
        TranscriptionTermsDialog(
            onAgree = {
                showTermsDialog = false
                LegalConsent.acceptTerms(context)
                hasAcceptedTerms = true
                transcribe()
            },
            onDismiss = { showTermsDialog = false }
        )
    }

    if (showMicPermissionDialog) {
        MicPermissionDialog(
            reason = "Record uses the microphone to capture music playing near you, so it can be transcribed to MIDI.",
            onDismiss = { showMicPermissionDialog = false }
        )
    }

    val accent = MaterialTheme.colorScheme.secondary
    val cardShape = RoundedCornerShape(20.dp)
    // While a transcription runs, the progress card below carries the light, so this one dims.
    val washStrength by animateFloatAsState(
        targetValue = if (viewModel.isLoading) 0f else 1f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "convertCardWash"
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(
            containerColor = if (isSuccess) MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        )
    ) {
        Column(
            modifier = Modifier
                .cardWash(accent, washStrength)
                // Landscape has about 330dp of height: tighter, so the whole card fits without scrolling
                .padding(if (isLandscape) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 12.dp)
        ) {
            val sourceSelector = @Composable { modifier: Modifier ->
                SourceSelector(
                    selected = source,
                    enabled = !viewModel.isLoading && !viewModel.isRecording,
                    isCompact = !isLandscape,
                    onSelect = { viewModel.selectTranscriptionSource(it) },
                    modifier = modifier
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(Icons.Default.AutoAwesome, tint = accent, size = if (isLandscape) 26.dp else 30.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "AI Transcription",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (isLandscape) Modifier else Modifier.weight(1f),
                    color = accent
                )

                // Landscape is short but wide: the source choice shares the title's row instead of taking its own.
                if (isLandscape) {
                    if (canUseFiles) {
                        sourceSelector(Modifier.weight(1f).padding(horizontal = 16.dp))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }

                val statusColor = viewModel.serverStatus.statusColor

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.clickable {
                        if (!viewModel.isLoading) {
                            onOpenServerSettings()
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(color = statusColor, shape = CircleShape)
                        )

                        // A custom server shows the server icon of Server Settings in place of the gear: a word
                        // ("Custom") widened the chip enough to cut off the title in portrait.
                        val isCustom = !viewModel.isUsingDefaultServer
                        Icon(
                            imageVector = if (isCustom) Icons.Default.Dns else Icons.Default.Settings,
                            contentDescription = if (isCustom) "Custom server settings" else "Server Settings",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (canUseFiles && !isLandscape) {
                sourceSelector(Modifier)
            }

            if (source == TranscriptionSource.Record) {
                when {
                    viewModel.isRecording -> RecordingArea(
                        elapsedMs = viewModel.recordingElapsedMs,
                        level = viewModel.recordingLevel,
                        maxMinutes = viewModel.maxVideoLengthMinutes,
                        onStop = { viewModel.stopRecording() },
                        onDiscard = discard
                    )
                    selectedFile != null -> SelectedFileRow(
                        file = selectedFile,
                        enabled = !viewModel.isLoading,
                        onChange = record,
                        onClear = discard,
                        icon = Icons.Default.Mic,
                        changeLabel = "Record again",
                        clearLabel = "Delete recording"
                    )
                    !viewModel.isLoading -> StartRecordingArea(
                        maxMinutes = viewModel.maxVideoLengthMinutes,
                        onClick = startRecording
                    )
                }
            } else if (source == TranscriptionSource.File) {
                when {
                    selectedFile != null -> SelectedFileRow(
                        file = selectedFile,
                        enabled = !viewModel.isLoading,
                        onChange = pickFile,
                        onClear = { viewModel.clearSelectedMediaFile() }
                    )
                    !viewModel.isLoading -> ChooseFileArea(
                        maxSizeMb = viewModel.maxUploadSizeMb,
                        maxMinutes = viewModel.maxVideoLengthMinutes,
                        onClick = pickFile
                    )
                }
            } else OutlinedTextField(
                value = viewModel.videoUrl,
                onValueChange = { viewModel.updateUrl(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { linkInputAnchors.isLinkFieldFocused = it.isFocused }
                    .onGloballyPositioned { linkInputAnchors.linkFieldTopInRoot = it.positionInRoot().y },
                placeholder = { Text(if (isLandscape) "Paste a piano cover link" else "Piano cover link", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                enabled = !viewModel.isLoading,
                shape = RoundedCornerShape(14.dp),
                colors = quietFieldColors(),
                leadingIcon = { Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.secondary) },
                trailingIcon = {
                    if (viewModel.isLoading) return@OutlinedTextField
                    if (viewModel.videoUrl.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateUrl("") }) {
                            Icon(Icons.Default.Clear, "Clear")
                        }
                    } else {
                        IconButton(onClick = {
                            scope.launch {
                                val pasted = clipboard.getClipEntry()?.clipData
                                    ?.takeIf { it.itemCount > 0 }
                                    ?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                                if (!pasted.isNullOrEmpty()) viewModel.updateUrl(pasted)
                            }
                        }) {
                            Icon(Icons.Default.ContentPaste, "Paste link", tint = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            )

            val needsPassword = viewModel.serverStatus == ServerStatus.PASSWORD_REQUIRED
            if (needsPassword && !viewModel.isLoading) {
                ServerPasswordNotice(isOfficialServer = viewModel.isUsingDefaultServer, onClick = onOpenServerSettings)
            }

            if (!viewModel.isLoading && !viewModel.isRecording) {
                val isBilled = viewModel.isBilledServer
                val cost = viewModel.estimatedCostCredits
                val buttonLabel = if (isLandscape) "Transcribe to MIDI" else "Transcribe"
                val canTranscribe = (if (source == TranscriptionSource.Link) viewModel.videoUrl.isNotBlank() else selectedFile != null) &&
                    !viewModel.isCalculatingCost && !needsPassword
                // Fades from waiting to ready as a link is pasted, rather than switching abruptly
                val readiness by animateFloatAsState(
                    targetValue = if (canTranscribe) 1f else 0f,
                    animationSpec = tween(durationMillis = 260),
                    label = "transcribeReadiness"
                )
                val container = lerp(accent.copy(alpha = 0.08f), accent, readiness)
                val content = lerp(accent.copy(alpha = 0.45f), MaterialTheme.colorScheme.onSecondary, readiness)
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val pressScale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "transcribePress")

                Button(
                    onClick = {
                        if (hasAcceptedTerms) transcribe() else showTermsDialog = true
                    },
                    enabled = canTranscribe,
                    interactionSource = interaction,
                    modifier = Modifier
                        .fillMaxWidth()
                        // The card's 8dp landscape gap felt cramped under the field; the button gets a little more air
                        .padding(top = if (isLandscape) 6.dp else 0.dp)
                        .height(if (isLandscape) 44.dp else 50.dp)
                        .onGloballyPositioned { linkInputAnchors.transcribeButtonBottomInRoot = it.boundsInRoot().bottom }
                        .graphicsLayer {
                            scaleX = pressScale
                            scaleY = pressScale
                        }
                        .clip(PillShape)
                        .background(container)
                        // A faint sheen on top once ready, so it reads as lit rather than flat
                        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f * readiness), Color.Transparent))),
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = content,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = content
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        if (isBilled && viewModel.isCalculatingCost) {
                            Text("Calculating cost...", fontWeight = FontWeight.ExtraBold)
                        } else {
                            Text(buttonLabel, fontWeight = FontWeight.ExtraBold)
                            if (isBilled && cost != null) {
                                Spacer(Modifier.width(10.dp))
                                Surface(
                                    shape = PillShape,
                                    color = content.copy(alpha = 0.14f),
                                    contentColor = content
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MonetizationOn,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "$cost",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // A reminder of what the terms dialog said, so it only appears once the user has agreed to them.
            // A quiet footnote under the button, rather than a row of its own between the source and the input.
            if (hasAcceptedTerms) {
                RightsFootnote()
            }
        }
    }
}
/**
 * The server is private and the app has no password for it, or the wrong one. In [Moods.Watch]: it asks
 * for attention, nothing is broken. A custom server's password is the user's to enter; the official one
 * comes with the app, so then the way out is a newer app or another server, both in Server Settings.
 */
@Composable
private fun ServerPasswordNotice(isOfficialServer: Boolean, onClick: () -> Unit) {
    val watch = Moods.Watch.core
    // The whole notice is the button, so it needs no room for one beside the text on a narrow screen
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = watch.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, watch.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(Icons.Default.Lock, tint = watch, size = 30.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isOfficialServer) "Official server is locked" else "Server password needed",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                // Wraps rather than cutting off on narrow phones or with large system fonts
                Text(
                    text = if (isOfficialServer) "Update the app or pick a server" else "Tap to enter it",
                    style = MaterialTheme.typography.labelSmall,
                    color = watch
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = if (isOfficialServer) "Open server settings" else "Enter server password",
                tint = watch,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
/** "Only convert music you have the right to use", with the Terms of Service that explain it one tap away. */
@Composable
private fun RightsFootnote() {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val linkColor = MaterialTheme.colorScheme.secondary
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Portrait has room for one short line only; the full sentence wrapped and left "Terms" on a line of its own
    val notice = if (isLandscape) "Only convert music you have the right to use · " else "Convert music you've the right to use · "
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = muted,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = buildAnnotatedString {
                append(notice)
                appendLink("Terms", AppLinks.TERMS_OF_SERVICE, linkColor)
            },
            style = MaterialTheme.typography.labelSmall,
            color = muted,
            maxLines = 1,
            softWrap = false,
            // Steps down a little on the narrowest phones or with large system fonts instead of wrapping
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = MaterialTheme.typography.labelSmall.fontSize),
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}
