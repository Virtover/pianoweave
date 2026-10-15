package com.lumenchord.pianoweave.ui.screens.learn.cards

import androidx.compose.animation.core.Animatable
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
import com.lumenchord.pianoweave.ui.viewmodel.statusColor
import kotlinx.coroutines.launch

/**
 * Shown when a transcription finishes: the song's cover art over a blurred backdrop of itself, and a
 * gold glow that flares up once as the card appears, then settles.
 */
@Composable
fun TranscriptionSuccessCard(
    song: StoredMidi,
    onSongSelect: (StoredMidi) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val accent = MaterialTheme.colorScheme.secondary
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
                elevation = 28.dp * glow.value,
                shape = shape,
                ambientColor = accent,
                spotColor = accent
            )
            .clip(shape)
            .background(surface)
            .border(BorderStroke(1.5.dp, accent.copy(alpha = 0.35f + 0.65f * glow.value)), shape)
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
                        .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(14.dp))
            } else {
                Icon(
                    imageVector = Icons.Default.Stars,
                    contentDescription = null,
                    modifier = Modifier.size(if (isLandscape) 28.dp else 44.dp),
                    tint = accent
                )
                Spacer(Modifier.height(if (isLandscape) 6.dp else 10.dp))
            }
            Text(
                text = "Transcription ready",
                style = (if (isLandscape) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall)
                    .copy(fontWeight = FontWeight.Bold),
                color = accent
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
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(if (isLandscape) 20.dp else 24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start practicing", fontWeight = FontWeight.ExtraBold, fontSize = if (isLandscape) 14.sp else 16.sp)
            }
        }
    }
}

@Composable
fun TranscriptionInputCard(
    viewModel: PianoWeaveViewModel,
    context: Context,
    onOpenServerSettings: () -> Unit
) {
    val isSuccess = !viewModel.isLoading && viewModel.readySong != null
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.selectMediaFile(context, it) }
    }
    // Servers that cannot take uploads (e.g. after switching servers) only offer links.
    val canUseFiles = viewModel.supportsFileUpload || viewModel.isLoading
    val isFileMode = canUseFiles && viewModel.transcriptionSource == TranscriptionSource.File
    val selectedFile = viewModel.selectedMediaFile.takeIf { isFileMode }
    val pickFile = { filePicker.launch(UploadFormats.mimeTypes) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSuccess) MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI Transcription",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    color = MaterialTheme.colorScheme.secondary
                )

                val statusColor = viewModel.serverStatus.statusColor
                val statusLabel = if (viewModel.isUsingDefaultServer) "Default" else "Custom"

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

                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Server Settings",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (canUseFiles) {
                SourceSelector(
                    selected = if (isFileMode) TranscriptionSource.File else TranscriptionSource.Link,
                    enabled = !viewModel.isLoading,
                    onSelect = { viewModel.selectTranscriptionSource(it) }
                )
            }

            Text(
                text = if (isFileMode) "Pick a recording you have the right to use"
                else "Paste a video link you have the right to use",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isFileMode) {
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
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Paste a link", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                enabled = !viewModel.isLoading,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.secondary,
                    cursorColor = MaterialTheme.colorScheme.secondary
                ),
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

            if (!viewModel.isLoading) {
                val isBilled = viewModel.isBilledServer
                val cost = viewModel.estimatedCostCredits
                val buttonLabel = if (isLandscape) "Transcribe to MIDI" else "Transcribe"

                Button(
                    onClick = {
                        if (viewModel.isBilledServer && !viewModel.isGoogleSignedIn) {
                            viewModel.signInWithGoogle(context)
                        } else {
                            viewModel.startTranscription(context)
                        }
                    },
                    enabled = (if (isFileMode) selectedFile != null else viewModel.videoUrl.isNotBlank()) &&
                        !viewModel.isCalculatingCost,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
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
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MonetizationOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "$cost",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Link or file: what the card transcribes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceSelector(
    selected: TranscriptionSource,
    enabled: Boolean,
    onSelect: (TranscriptionSource) -> Unit
) {
    val options = listOf(
        Triple(TranscriptionSource.Link, "Link", Icons.Default.Link),
        Triple(TranscriptionSource.File, "File", Icons.Default.AudioFile)
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (source, label, icon) ->
            SegmentedButton(
                selected = source == selected,
                onClick = { onSelect(source) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                    activeContentColor = MaterialTheme.colorScheme.secondary,
                    activeBorderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                    inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) }
            )
        }
    }
}

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
        Text(
            text = UploadFormats.DISPLAY_NAMES,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
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
    onClear: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = if (enabled) 0.6f else 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClickLabel = "Choose another file", onClick = onChange)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.AudioFile, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
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
                val changeHint = "Change"
                Text(
                    text = buildAnnotatedString {
                        if (details.isNotEmpty()) append("$details · ")
                        if (enabled) {
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)) {
                                append(changeHint)
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
                    Icon(Icons.Default.Clear, contentDescription = "Remove file")
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}

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
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Status", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (progress > 0f) {
                            Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 2)
                    if (progress < 1f) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            if (viewModel.isBilledServer) {
                val retentionText = viewModel.formatRetentionTime()
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Api,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
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
                    shape = RoundedCornerShape(12.dp),
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
                    shape = RoundedCornerShape(12.dp)
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

@Composable
fun TranscriptionErrorCard(
    errorMessage: String,
    onErrorDismiss: () -> Unit
) {
    DangerCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "Transcription Failed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                        )
                    }
                }

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
        }
    }
}
