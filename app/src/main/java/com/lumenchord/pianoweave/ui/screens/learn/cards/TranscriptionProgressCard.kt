package com.lumenchord.pianoweave.ui.screens.learn.cards

import com.lumenchord.pianoweave.ui.components.rememberWindowRoom

import com.lumenchord.pianoweave.api.TranscriptionMode
import com.lumenchord.pianoweave.ui.theme.PillShape
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.ui.components.DangerButton
import com.lumenchord.pianoweave.ui.components.DangerCard
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.lumenchord.pianoweave.ui.components.IconBadge
import com.lumenchord.pianoweave.ui.components.cardWash
import androidx.compose.material.icons.outlined.Info
import androidx.compose.animation.core.animateFloatAsState
import com.lumenchord.pianoweave.ui.theme.Moods

@Composable
fun TranscriptionProgressCard(
    viewModel: PianoWeaveViewModel,
    progress: Float,
    status: String,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.secondary
    val isShort = rememberWindowRoom().isShort
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
                .padding(if (isShort) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (isShort) 8.dp else 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Default.GraphicEq, tint = accent, size = if (isShort) 26.dp else 30.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (viewModel.transcriptionMode == TranscriptionMode.Arrangement) "Arranging" else "Transcribing",
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
                            text = "If you close the app during transcription and do not reconnect within $retentionText, the result will be deleted from server and spent credits will be lost.",
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
    onErrorDismiss: () -> Unit,
    /** Shown as "Upload a file instead" when the server could not read the link. */
    onUploadInstead: (() -> Unit)? = null
) {
    val isShort = rememberWindowRoom().isShort
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
                .padding(if (isShort) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // A tear, kept in His bottle
                IconBadge(Icons.Default.WaterDrop, tint = lament, size = if (isShort) 26.dp else 30.dp)
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
            if (onUploadInstead != null) {
                TextButton(
                    onClick = onUploadInstead,
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, tint = lament, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Upload a file instead", fontWeight = FontWeight.Bold, color = lament)
                }
            }
        }
    }
}
