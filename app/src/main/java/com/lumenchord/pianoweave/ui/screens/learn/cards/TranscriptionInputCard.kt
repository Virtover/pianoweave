package com.lumenchord.pianoweave.ui.screens.learn.cards

import androidx.compose.animation.Crossfade
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.compose.SubcomposeAsyncImage
import com.lumenchord.pianoweave.api.LinkPreview
import com.lumenchord.pianoweave.api.formatDuration
import com.lumenchord.pianoweave.ui.components.smallThumbnailUrl
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import com.lumenchord.pianoweave.ui.theme.onKeyButton
import com.lumenchord.pianoweave.ui.theme.Ink
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.theme.PillShape
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import android.content.Context
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
import com.lumenchord.pianoweave.ui.components.rememberWindowRoom
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
import com.lumenchord.pianoweave.ui.screens.learn.TranscriptionModeDialog
import com.lumenchord.pianoweave.ui.screens.learn.TranscriptionTermsDialog
import com.lumenchord.pianoweave.api.TranscriptionMode
import com.lumenchord.pianoweave.util.AppLinks
import com.lumenchord.pianoweave.util.LegalConsent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import com.lumenchord.pianoweave.ui.theme.Moods
import com.lumenchord.pianoweave.api.Clip
import com.lumenchord.pianoweave.api.describe
import com.lumenchord.pianoweave.ui.screens.learn.PartOverlapDialog
import com.lumenchord.pianoweave.ui.screens.learn.TrimDialog
import com.lumenchord.pianoweave.ui.viewmodel.activeClip
import com.lumenchord.pianoweave.ui.viewmodel.canTrim
import com.lumenchord.pianoweave.ui.viewmodel.trimSourceSeconds

@Composable
fun TranscriptionInputCard(
    viewModel: PianoWeaveViewModel,
    context: Context,
    onOpenServerSettings: () -> Unit,
    linkInputAnchors: LinkInputAnchors = remember { LinkInputAnchors() }
) {
    val isSuccess = !viewModel.isLoading && viewModel.readySong != null
    val room = rememberWindowRoom()
    val isShort = room.isShort
    val isWide = room.isWide
    // Tablets: a little more air and a larger button, so the card doesn't look like a phone's on a big screen
    val isLarge = room.isLarge
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

    // Transcribe: the choice of what to make (when the server arranges), the Terms once, then the job.
    // The mode waiting for the Terms is kept, so agreeing starts what was chosen.
    var hasAcceptedTerms by remember { mutableStateOf(LegalConsent.hasAcceptedTerms(context)) }
    val needsSignIn = viewModel.isBilledServer && !viewModel.isGoogleSignedIn
    val transcribe = { mode: TranscriptionMode ->
        if (needsSignIn) {
            viewModel.signInWithGoogle(context)
        } else {
            viewModel.startTranscription(context, mode)
        }
    }
    val startOrAskTerms = { mode: TranscriptionMode ->
        if (hasAcceptedTerms) transcribe(mode) else viewModel.termsPendingMode = mode
    }
    val onTranscribeClick = {
        // Signing in comes first: a choice made before it would be lost to the account picker
        if (viewModel.offersModeChoice && !needsSignIn) viewModel.modeChoice = viewModel.lastChosenMode(context)
        else startOrAskTerms(TranscriptionMode.Transcription)
    }
    viewModel.modeChoice?.let { choice ->
        TranscriptionModeDialog(
            selected = choice,
            onSelect = { viewModel.modeChoice = it },
            second = viewModel.secondMode ?: TranscriptionMode.Ensemble,
            cost = viewModel.estimatedCostCredits.takeIf { viewModel.isBilledServer },
            onConfirm = { mode ->
                viewModel.modeChoice = null
                startOrAskTerms(mode)
            },
            onDismiss = { viewModel.modeChoice = null }
        )
    }
    viewModel.termsPendingMode?.let { mode ->
        TranscriptionTermsDialog(
            explainPianoOnly = !viewModel.offersModeChoice,
            onAgree = {
                viewModel.termsPendingMode = null
                LegalConsent.acceptTerms(context)
                hasAcceptedTerms = true
                transcribe(mode)
            },
            onDismiss = { viewModel.termsPendingMode = null }
        )
    }

    if (viewModel.showTrimDialog) {
        TrimDialog(
            totalSeconds = viewModel.trimSourceSeconds,
            initial = viewModel.activeClip,
            isBilled = viewModel.isBilledServer,
            error = viewModel.trimLengthError,
            onRetry = { viewModel.openTrim(context) },
            onApply = { viewModel.applyTrim(it) },
            onDismiss = { viewModel.showTrimDialog = false }
        )
    }
    viewModel.overlapWarning?.let { warning ->
        PartOverlapDialog(
            warning = warning,
            onConfirm = {
                viewModel.overlapWarning = null
                viewModel.startTranscription(context, warning.mode, overlapConfirmed = true)
            },
            onDismiss = { viewModel.overlapWarning = null }
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
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // The selector shares the title's row when it fits there in full at the current font scale: what the row
        // leaves beside the badge, the title and the status chip (measured, so any width and font scale works) is
        // compared with what the selector needs. A phone in landscape keeps the shared row however narrow, as it
        // has no height to spare (the selector then drops its icons rather than cut a label).
        val textMeasurer = rememberTextMeasurer()
        val localDensity = LocalDensity.current
        val titleStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp)
        val titleWidth = remember(localDensity, titleStyle) {
            with(localDensity) { textMeasurer.measure("AI Transcription", titleStyle, maxLines = 1).size.width.toDp() }
        }
        val selectorMinWidth = rememberSourceSelectorMinWidth()
        val cardPadding = if (isShort) 12.dp else if (isLarge) 20.dp else 16.dp
        val badge = if (isShort) 26.dp else 30.dp
        // Badge, its gap, the title, the status chip (50dp: dot, icon and padding), the selector's own side padding
        val beside = badge + 10.dp + titleWidth + 50.dp + 32.dp + 4.dp
        val inlineSelector = isWide && (isShort || maxWidth - cardPadding * 2 - beside >= selectorMinWidth)
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
                    // A phone in landscape has about 330dp of height: tighter, so the whole card fits without scrolling
                    .padding(cardPadding),
                verticalArrangement = Arrangement.spacedBy(if (isShort) 8.dp else if (isLarge) 14.dp else 12.dp)
            ) {
                val sourceSelector = @Composable { modifier: Modifier ->
                    SourceSelector(
                        selected = source,
                        enabled = !viewModel.isLoading && !viewModel.isRecording,
                        isCompact = !inlineSelector,
                        onSelect = { viewModel.selectTranscriptionSource(it) },
                        modifier = modifier
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(Icons.Default.AutoAwesome, tint = accent, size = if (isShort) 26.dp else 30.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "AI Transcription",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (inlineSelector) Modifier else Modifier.weight(1f),
                        color = accent
                    )

                    // A wide card: the source choice shares the title's row instead of taking its own.
                    if (inlineSelector) {
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

                if (canUseFiles && !inlineSelector) {
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
                    // Without arrangements piano covers are what the server does best; with them, any video will do
                    placeholder = {
                        val hint = when {
                            viewModel.offersModeChoice -> if (isWide) "Paste a video link" else "Video link"
                            else -> if (isWide) "Paste a piano cover link" else "Piano cover link"
                        }
                        Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
                    },
                    singleLine = true,
                    enabled = !viewModel.isLoading,
                    shape = PillShape,
                    colors = quietFieldColors(),
                    // The link's thumbnail, once the server has read it, in the link icon's place and size
                    leadingIcon = {
                        LinkLeadingIcon(
                            thumbnail = viewModel.linkPreview?.thumbnail,
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                        )
                    },
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
                                // A smaller icon in a soft well of the accent: clearly a button, without growing the field
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.ContentPaste, "Paste link", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(19.dp))
                                }
                            }
                        }
                    }
                )

                val needsPassword = viewModel.serverStatus == ServerStatus.PASSWORD_REQUIRED
                if (needsPassword && !viewModel.isLoading) {
                    ServerPasswordNotice(isOfficialServer = viewModel.isUsingDefaultServer, onClick = onOpenServerSettings)
                }

                // Until there is a link, a file or a recording, a new user sees only how to give one: the button comes with it
                val awaitingLink = if (source == TranscriptionSource.Link) viewModel.videoUrl.isBlank() else selectedFile == null
                if (!viewModel.isLoading && !viewModel.isRecording && awaitingLink) {
                    // How to fill the field: centred under it, the places a link can come from in the accent
                    val siteStyle = SpanStyle(fontWeight = FontWeight.Medium)
                    Text(
                        text = buildAnnotatedString {
                            when (source) {
                                // With arrangements any song will do; without them a piano performance
                                TranscriptionSource.Link -> {
                                    append(if (viewModel.offersModeChoice) "Paste a song video link, e.g. from " else "Paste a piano video link, e.g. from ")
                                    withStyle(siteStyle) { append("YouTube") }
                                    append(", ")
                                    withStyle(siteStyle) { append("TikTok") }
                                    append(" or ")
                                    withStyle(siteStyle) { append("Instagram") }
                                    append(".")
                                }
                                TranscriptionSource.File -> {
                                    append("Choose an ")
                                    withStyle(siteStyle) { append("audio") }
                                    append(" or ")
                                    withStyle(siteStyle) { append("video") }
                                    append(if (viewModel.offersModeChoice) " file of a song or of piano playing." else " file of piano playing.")
                                }
                                TranscriptionSource.Record -> append(
                                    if (viewModel.offersModeChoice) "Record piano playing, or a song, near your phone."
                                    else "Play the piano near your phone and record it."
                                )
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .onGloballyPositioned { linkInputAnchors.transcribeButtonBottomInRoot = it.boundsInRoot().bottom }
                    )
                }
                if (!viewModel.isLoading && !viewModel.isRecording && !awaitingLink) {
                    val isBilled = viewModel.isBilledServer
                    val cost = viewModel.estimatedCostCredits
                    val buttonLabel = if (isWide) "Transcribe to MIDI" else "Transcribe"
                    val canTranscribe = (if (source == TranscriptionSource.Link) viewModel.videoUrl.isNotBlank() else selectedFile != null) &&
                        !viewModel.isCalculatingCost && !needsPassword
                    // Fades from waiting to ready as a link is pasted, rather than switching abruptly
                    val readiness by animateFloatAsState(
                        targetValue = if (canTranscribe) 1f else 0f,
                        animationSpec = tween(durationMillis = 260),
                        label = "transcribeReadiness"
                    )
                    val container = lerp(accent.copy(alpha = 0.08f), accent, readiness)
                    // Lit like the other key buttons: gold leaf by day, the accent lit from above by night
                    val leafTheme = LocalAppTheme.current
                    val leaf = leafTheme.fill
                    val content = if (leaf != null) lerp(accent.copy(alpha = 0.45f), leafTheme.onKeyButton, readiness)
                    else lerp(accent.copy(alpha = 0.45f), MaterialTheme.colorScheme.onSecondary, readiness)
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val pressScale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "transcribePress")
                    // Trim is the button's own end, behind a hairline: no row or button of its own
                    val showTrim = viewModel.canTrim && !needsSignIn
                    val clip = viewModel.activeClip
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // The card's 8dp short-window gap felt cramped under the field; the button gets a little more air
                            .padding(top = if (isShort) 6.dp else 0.dp)
                            .height(if (isShort) 44.dp else if (isLarge) 54.dp else 50.dp)
                            .onGloballyPositioned { linkInputAnchors.transcribeButtonBottomInRoot = it.boundsInRoot().bottom }
                            .graphicsLayer {
                                scaleX = pressScale
                                scaleY = pressScale
                            }
                    ) {
                        Button(
                            onClick = onTranscribeClick,
                            enabled = canTranscribe,
                            interactionSource = interaction,
                            contentPadding = if (showTrim) PaddingValues(start = 20.dp, end = TRIM_SEGMENT_WIDTH + 8.dp)
                            else ButtonDefaults.ContentPadding,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(PillShape)
                                .then(
                                    if (leaf != null) Modifier.background(Brush.verticalGradient(leaf.map { lerp(it.copy(alpha = 0.12f), it, readiness) }))
                                    else Modifier.background(container)
                                )
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
                                    // Under the label, what will be sent: the video's title as the server read it (with its
                                    // channel where there is room) and its length, or the part chosen with Trim
                                    val preview = if (source == TranscriptionSource.Link) viewModel.linkPreview else null
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Text(buttonLabel, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                                        sentLine(preview, clip, withChannel = isWide)?.let { (name, length) ->
                                            val described = sentLineDescription(preview, clip)
                                            Row(
                                                modifier = Modifier.semantics { contentDescription = described },
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                name?.let {
                                                    Text(
                                                        text = it,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        lineHeight = 12.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        color = content.copy(alpha = 0.8f),
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                }
                                                length?.let {
                                                    Text(
                                                        text = if (name != null) " · $it" else it,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        lineHeight = 12.sp,
                                                        maxLines = 1,
                                                        color = content.copy(alpha = 0.8f)
                                                    )
                                                }
                                            }
                                        }
                                    }
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
                        if (showTrim) {
                            TrimSegment(
                                isTrimmed = clip != null,
                                tint = content,
                                enabled = !needsPassword,
                                onClick = { viewModel.openTrim(context) },
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
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
private val TRIM_SEGMENT_WIDTH = 48.dp

/**
 * The end of the Transcribe button that opens Trim: a scissors behind a hairline, lit a little once a part is
 * chosen (the part itself is said under the button's label).
 */
@Composable
private fun TrimSegment(isTrimmed: Boolean, tint: Color, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(TRIM_SEGMENT_WIDTH)
            .fillMaxHeight()
            .clip(RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50))
            .background(if (isTrimmed) tint.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(
                enabled = enabled,
                role = androidx.compose.ui.semantics.Role.Button,
                onClickLabel = if (isTrimmed) "Change the part to transcribe" else "Trim: transcribe only a part",
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(1.dp)
                .fillMaxHeight(0.5f)
                .background(tint.copy(alpha = 0.3f))
        )
        Icon(
            Icons.Default.ContentCut,
            contentDescription = if (isTrimmed) "Change the part to transcribe" else "Trim: transcribe only a part",
            tint = tint,
            modifier = Modifier.size(18.dp).offset(x = (-2).dp)
        )
    }
}

/** "Only convert music you have the right to use", with the Terms of Service that explain it one tap away. */
@Composable
private fun RightsFootnote() {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val linkColor = MaterialTheme.colorScheme.secondary
    val isWide = rememberWindowRoom().isWide
    // A phone in portrait has room for one short line only; the full sentence wrapped and left "Terms" on a line of its own
    val notice = if (isWide) "Only convert music you have the right to use · " else "Convert music you've the right to use · "
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

/**
 * The line under Transcribe: the title (and channel, [withChannel]) that may be cut short, and the length that never
 * is: the part chosen with Trim, else the video's. Null when there is neither a title nor a part, so a server that
 * sends no title leaves the button as it always was.
 */
internal fun sentLine(preview: LinkPreview?, clip: Clip?, withChannel: Boolean): Pair<String?, String?>? {
    val title = preview?.title
    if (title == null && clip == null) return null
    val name = title?.let { t -> preview.channel?.takeIf { withChannel }?.let { "$t · $it" } ?: t }
    val length = clip?.describe() ?: preview?.durationSeconds?.let(::formatDuration)
    return name to length
}

/** What a screen reader says for [sentLine], in words rather than the dots of the line. */
internal fun sentLineDescription(preview: LinkPreview?, clip: Clip?): String =
    listOfNotNull(preview?.contentDescription?.takeIf { it.isNotEmpty() }, clip?.let { "part ${it.describe()}" })
        .joinToString(", ")

/**
 * The link field's leading icon: the link symbol, or the pasted video's thumbnail at the same size, rounded, faded
 * in once it has loaded. While it loads, and if it fails, the symbol stays, so the field never changes size.
 */
@Composable
private fun LinkLeadingIcon(thumbnail: String?, tint: Color) {
    val icon: @Composable () -> Unit = { Icon(Icons.Default.Link, null, tint = tint, modifier = Modifier.size(20.dp)) }
    Crossfade(targetState = thumbnail, label = "linkThumbnail") { url ->
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (url == null) icon()
            else SubcomposeAsyncImage(
                model = smallThumbnailUrl(url),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = { icon() },
                error = { icon() },
                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
            )
        }
    }
}
