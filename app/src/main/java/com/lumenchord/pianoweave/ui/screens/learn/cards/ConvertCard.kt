package com.lumenchord.pianoweave.ui.screens.learn.cards

import androidx.compose.animation.core.Animatable
import com.lumenchord.pianoweave.ui.theme.PillShape
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.lumenchord.pianoweave.ui.components.ArtBackdrop
import com.lumenchord.pianoweave.ui.components.smallThumbnailUrl
import com.lumenchord.pianoweave.ui.components.songDetails
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.midi.StoredMidi
import com.lumenchord.pianoweave.ui.components.DangerButton
import com.lumenchord.pianoweave.ui.components.DangerCard
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import com.lumenchord.pianoweave.api.UploadFormats
import com.lumenchord.pianoweave.ui.viewmodel.SelectedMediaFile
import com.lumenchord.pianoweave.ui.viewmodel.TranscriptionSource
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import android.Manifest
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lumenchord.pianoweave.audio.AcousticNoteDetector
import com.lumenchord.pianoweave.ui.components.MicPermissionDialog
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.unit.Dp
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.theme.Moods

/**
 * Shown when a transcription finishes: the song's cover art over a blurred backdrop of itself, ringed
 * in [Moods.Glory]'s gold (Revelation 21:23), whose glow flares up once as the card appears, then settles.
 */
@Composable
fun TranscriptionSuccessCard(
    song: StoredMidi,
    onSongSelect: (StoredMidi) -> Unit,
    /** Offered for uploads and recordings, which arrive named after the file. */
    onEditDetails: (() -> Unit)? = null
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val accent = MaterialTheme.colorScheme.secondary
    val glory = Moods.Glory
    val surface = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(if (isLandscape) 14.dp else 20.dp)
    val metadata = song.metadata

    val glow = remember(song.file) { Animatable(0f) }
    val appear = remember(song.file) { Animatable(0f) }
    LaunchedEffect(song.file) {
        launch { appear.animateTo(1f, tween(durationMillis = 450, easing = FastOutSlowInEasing)) }
        glow.animateTo(1f, tween(durationMillis = 500, easing = FastOutSlowInEasing))
        glow.animateTo(0.3f, tween(durationMillis = 1400, easing = LinearOutSlowInEasing))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value
                // Fades each draw call instead of an offscreen layer, which would clip the glow to a rectangle
                compositingStrategy = CompositingStrategy.ModulateAlpha
                val scale = 0.94f + 0.06f * appear.value
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 24.dp * glow.value,
                shape = shape,
                ambientColor = glory.core,
                spotColor = glory.core
            )
            .clip(shape)
            .background(surface)
            .border(BorderStroke(1.dp, glory.core.copy(alpha = 0.3f + 0.6f * glow.value)), shape)
    ) {
        ArtBackdrop(thumbnailUrl = metadata.thumbnail, alpha = 0.55f, fadeTo = surface)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isLandscape) 12.dp else 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (metadata.thumbnail.isNotBlank() && !isLandscape) {
                AsyncImage(
                    model = smallThumbnailUrl(metadata.thumbnail),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, glory.core.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(14.dp))
            } else {
                Icon(
                    imageVector = Icons.Default.Stars,
                    contentDescription = null,
                    modifier = Modifier.size(if (isLandscape) 28.dp else 44.dp),
                    tint = glory.core
                )
                Spacer(Modifier.height(if (isLandscape) 6.dp else 10.dp))
            }
            Text(
                text = "Transcription ready",
                style = (if (isLandscape) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall)
                    .copy(fontWeight = FontWeight.Bold),
                color = glory.core
            )
            Text(
                text = metadata.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            songDetails(metadata)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(if (isLandscape) 10.dp else 16.dp))
            Button(
                onClick = { onSongSelect(song) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isLandscape) 42.dp else 52.dp),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(if (isLandscape) 20.dp else 24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start practicing", fontWeight = FontWeight.ExtraBold, fontSize = if (isLandscape) 14.sp else 16.sp)
            }
            if (onEditDetails != null) {
                TextButton(
                    onClick = onEditDetails,
                    modifier = Modifier.padding(top = if (isLandscape) 2.dp else 6.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = accent)
                    Spacer(Modifier.width(6.dp))
                    Text("Edit details", color = accent, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

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

/**
 * Link, file or microphone: what the card transcribes. Hand-built rather than a Material SegmentedButton,
 * which always reserves room for a checkmark and never shrinks its label, so "Record" ran into the
 * rounded end of a three-way row in portrait. Portrait says "Mic" instead, so the three segments share the width equally.
 */
@Composable
private fun SourceSelector(
    selected: TranscriptionSource,
    enabled: Boolean,
    isCompact: Boolean,
    onSelect: (TranscriptionSource) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        Triple(TranscriptionSource.Link, "Link", Icons.Default.Link),
        Triple(TranscriptionSource.File, "File", Icons.Default.AudioFile),
        Triple(TranscriptionSource.Record, "Mic", Icons.Default.Mic)
    )
    val accent = MaterialTheme.colorScheme.secondary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val pill = RoundedCornerShape(percent = 50)
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // One size for every label, the largest at which all of them fit, so the segments read as a set
        // rather than each shrinking on its own.
        val segmentWidth = (maxWidth - SegmentDivider * (options.size - 1)) / options.size
        val labelFontSize = remember(segmentWidth, labelStyle, density) {
            val labelWidthsPx = options.indices.map { index ->
                val padding = segmentPadding(isFirst = index == 0, isLast = index == options.lastIndex)
                with(density) { (segmentWidth - padding - SegmentIconWithGap).toPx() }
            }
            var size = LabelMaxFontSize
            while (size > LabelMinFontSize && options.indices.any { index ->
                    textMeasurer.measure(options[index].second, labelStyle.copy(fontSize = size.sp), maxLines = 1)
                        .size.width > labelWidthsPx[index]
                }) {
                size -= 0.5f
            }
            size.sp
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isCompact) 42.dp else 36.dp)
                .clip(pill)
                .border(1.dp, outline, pill)
                .selectableGroup()
        ) {
            options.forEachIndexed { index, (source, label, icon) ->
                val isFirst = index == 0
                val isLast = index == options.lastIndex
                val isSelected = source == selected
                val shape = RoundedCornerShape(
                    topStartPercent = if (isFirst) 50 else 0,
                    bottomStartPercent = if (isFirst) 50 else 0,
                    topEndPercent = if (isLast) 50 else 0,
                    bottomEndPercent = if (isLast) 50 else 0
                )
                val contentColor = (if (isSelected) accent else MaterialTheme.colorScheme.onSurface)
                    .copy(alpha = if (enabled) 1f else 0.38f)

                if (!isFirst) VerticalDivider(thickness = SegmentDivider, color = outline)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(
                            if (isSelected) Modifier
                                .background(accent.copy(alpha = 0.18f), shape)
                                .border(1.dp, accent.copy(alpha = 0.6f), shape)
                            else Modifier
                        )
                        .selectable(
                            selected = isSelected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(source) }
                        )
                        // The rounded ends eat into the outer segments, so their content keeps clear of the curve.
                        .padding(start = if (isFirst) 12.dp else 6.dp, end = if (isLast) 12.dp else 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = label,
                        color = contentColor,
                        style = labelStyle.copy(fontSize = labelFontSize),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }
}

private val SegmentDivider = 1.dp
/** Icon (18dp) plus the gap before the label. */
private val SegmentIconWithGap = 24.dp
private const val LabelMaxFontSize = 14f
private const val LabelMinFontSize = 10f

/** Matches the segment padding: the outer ends keep clear of the pill's curve. */
private fun segmentPadding(isFirst: Boolean, isLast: Boolean): Dp =
    (if (isFirst) 12.dp else 6.dp) + (if (isLast) 12.dp else 6.dp)

/** The empty file slot: one large target that also says which files work and how big they may be. */
@Composable
private fun ChooseFileArea(
    maxSizeMb: Float?,
    maxMinutes: Float?,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.secondary
    val limits = listOfNotNull(
        maxSizeMb?.let { "up to ${"%.0f".format(it)} MB" },
        maxMinutes?.let { "${"%.0f".format(it)} minutes" }
    ).joinToString(" · ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .drawBehind {
                drawRoundRect(
                    color = accent.copy(alpha = 0.55f),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = DrawStroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
                    )
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(Icons.Default.UploadFile, contentDescription = null, tint = accent, modifier = Modifier.size(30.dp))
        Text(
            text = "Choose a recording",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = accent
        )
        // No format list: the picker only offers files the server accepts.
        if (limits.isNotEmpty()) {
            Text(
                text = limits.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

/** The picked file in place of the link field: name, length and size. Tapping it picks another; × removes it. */
@Composable
private fun SelectedFileRow(
    file: SelectedMediaFile,
    enabled: Boolean,
    onChange: () -> Unit,
    onClear: () -> Unit,
    icon: ImageVector = Icons.Default.AudioFile,
    changeLabel: String = "Change",
    clearLabel: String = "Remove file"
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = if (enabled) 0.6f else 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClickLabel = changeLabel, onClick = onChange)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val details = listOfNotNull(
                    file.durationSeconds?.let { formatFileDuration(it) },
                    file.sizeBytes?.let { "%.1f MB".format(it / (1024.0 * 1024.0)) }
                ).joinToString(" · ")
                Text(
                    text = buildAnnotatedString {
                        if (details.isNotEmpty()) append("$details · ")
                        if (enabled) {
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)) {
                                append(changeLabel)
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (enabled) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, contentDescription = clearLabel)
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}

/** The empty recording slot: tapping it starts recording from the microphone. */
@Composable
private fun StartRecordingArea(
    maxMinutes: Float?,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.secondary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .drawBehind {
                drawRoundRect(
                    color = accent.copy(alpha = 0.55f),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = DrawStroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
                    )
                )
            }
            .clickable(onClickLabel = "Start recording", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(RecordRed, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tap to record",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = accent
        )
        maxMinutes?.let {
            Text(
                text = "Up to ${"%.0f".format(it)} minutes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

/** While recording: a pulsing dot, the elapsed time, a level meter, and stop / discard. */
@Composable
private fun RecordingArea(
    elapsedMs: Long,
    level: Float,
    maxMinutes: Float?,
    onStop: () -> Unit,
    onDiscard: () -> Unit
) {
    val pulse by rememberInfiniteTransition(label = "recording").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 700), RepeatMode.Reverse),
        label = "recordingDot"
    )
    val elapsed = formatFileDuration(elapsedMs / 1000f)
    val limit = maxMinutes?.let { " / ${formatFileDuration(it * 60f)}" }.orEmpty()

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = RecordRed.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, RecordRed.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .graphicsLayer { alpha = pulse }
                        .background(RecordRed, CircleShape)
                )
                Text(
                    text = "Recording",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = elapsed + limit,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ProgressLine(fraction = level, color = RecordRed)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDiscard,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = PillShape,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Discard",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    onClick = onStop,
                    modifier = Modifier
                        .weight(1.4f)
                        .height(44.dp),
                    shape = PillShape,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RecordRed, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Stop",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Recording is courage, not danger: [Moods.Furnace], the fire that did not harm the three in it
 * (Daniel 3:27).
 */
private val RecordRed = Moods.Furnace.core

/** A rounded bar filled to [fraction] with [color]. */
@Composable
private fun ProgressLine(fraction: Float, color: Color, height: Dp = 6.dp) {
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(percent = 50)),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}

/** Takes longer than this ask before they are thrown away. */
private const val DISCARD_CONFIRM_SECONDS = 10f

private fun formatFileDuration(seconds: Float): String {
    val total = seconds.toInt()
    return if (total >= 3600) "%d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
    else "%d:%02d".format(total / 60, total % 60)
}

@Composable
fun TranscriptionProgressCard(
    viewModel: PianoWeaveViewModel,
    progress: Float,
    status: String,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.secondary
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val onSurface = MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .cardWash(accent)
                .padding(if (isLandscape) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Default.GraphicEq, tint = accent, size = if (isLandscape) 26.dp else 30.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Transcribing",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
                    color = accent,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if (progress > 0f) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        color = accent,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Column {
                Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 2)
                if (progress < 1f) {
                    Spacer(Modifier.height(8.dp))
                    TranscriptionProgressBar(progress = progress, color = accent)
                }
            }

            if (viewModel.isBilledServer) {
                val retentionText = viewModel.formatRetentionTime()
                // The same quiet well as the link field, so the note sits in the card rather than on top of it
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = onSurface.copy(alpha = 0.04f),
                    border = BorderStroke(1.dp, onSurface.copy(alpha = 0.14f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp).padding(top = 2.dp)
                        )
                        Text(
                            text = "If you close the app during transcription and do not reconnect within $retentionText, it will be deleted from server and spent credits will be lost.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = PillShape,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                        disabledContentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSecondary)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Processing...",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                DangerButton(
                    onClick = onCancelClick,
                    enabled = !viewModel.isCancelling,
                    modifier = Modifier.height(48.dp),
                    shape = PillShape
                ) {
                    if (viewModel.isCancelling) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Cancelling...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    } else {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Cancel",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

/**
 * The transcription's progress: a solid accent fill on a faint accent track. The server reports only
 * every few seconds, so the fill glides to each new value instead of jumping.
 */
@Composable
private fun TranscriptionProgressBar(progress: Float, color: Color) {
    val modifier = Modifier
        .fillMaxWidth()
        .height(8.dp)
        .clip(PillShape)
    val track = color.copy(alpha = 0.15f)
    val shown by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "transcriptionProgress"
    )
    LinearProgressIndicator(
        progress = { shown },
        modifier = modifier,
        color = color,
        trackColor = track,
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}

@Composable
fun TranscriptionErrorCard(
    errorMessage: String,
    onErrorDismiss: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Sorrow that is held, not alarm: Moods.Lament (Psalm 56:8)
    val lament = Moods.Lament.core
    DangerCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        borderAlpha = 0.45f
    ) {
        Column(
            modifier = Modifier
                .cardWash(lament)
                .padding(if (isLandscape) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // A tear, kept in His bottle
                IconBadge(Icons.Default.WaterDrop, tint = lament, size = if (isLandscape) 26.dp else 30.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Transcription failed",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onErrorDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Error",
                        tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
            )
        }
    }
}
