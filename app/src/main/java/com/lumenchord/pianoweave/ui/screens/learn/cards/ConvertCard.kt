package com.lumenchord.pianoweave.ui.screens.learn.cards

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.lumenchord.pianoweave.ui.viewmodel.statusColor

@Composable
fun TranscriptionSuccessCard(
    song: StoredMidi,
    onSongSelect: (StoredMidi) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondary
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Stars,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSecondary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Transcription Ready!",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSecondary
            )
            Text(
                text = song.metadata.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onSongSelect(song) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onSecondary,
                    contentColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("START PRACTICING", fontWeight = FontWeight.Black, fontSize = 16.sp)
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
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
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

            Text(
                text = "Enter a video URL you have the right to use",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = viewModel.videoUrl,
                onValueChange = { viewModel.updateUrl(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://www.youtube.com/watch?v=...", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                enabled = !viewModel.isLoading,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.secondary,
                    cursorColor = MaterialTheme.colorScheme.secondary
                ),
                leadingIcon = { Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.secondary) },
                trailingIcon = {
                    if (viewModel.videoUrl.isNotEmpty() && !viewModel.isLoading) {
                        IconButton(onClick = { viewModel.updateUrl("") }) {
                            Icon(Icons.Default.Clear, "Clear")
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
                    enabled = viewModel.videoUrl.isNotBlank() && !viewModel.isCalculatingCost,
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
