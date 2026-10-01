package com.lumenchord.pianoweave.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lumenchord.pianoweave.midi.StoredMidi
import com.lumenchord.pianoweave.ui.screens.learn.*
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel

@Composable
fun LearnScreen(
    viewModel: PianoWeaveViewModel,
    context: Context,
    onSongSelect: (StoredMidi) -> Unit = {}
) {
    var showServerSettingsDialog by remember { mutableStateOf(false) }
    var showCancelConfirmDialog by remember { mutableStateOf(false) }

    fun handleCancelClick() {
        if (viewModel.progress > 0.05f) {
            showCancelConfirmDialog = true
        } else {
            viewModel.cancelTranscription(context)
        }
    }

    if (showCancelConfirmDialog) {
        CancelTranscriptionDialog(
            progressPercent = (viewModel.progress * 100).toInt(),
            isBilledServer = viewModel.isBilledServer,
            onConfirmCancel = {
                showCancelConfirmDialog = false
                viewModel.cancelTranscription(context)
            },
            onDismiss = { showCancelConfirmDialog = false }
        )
    }

    if (showServerSettingsDialog) {
        ServerSettingsDialog(
            viewModel = viewModel,
            context = context,
            onDismiss = { showServerSettingsDialog = false }
        )
    }

    if (viewModel.showShopDialog) {
        ShopDialog(
            viewModel = viewModel,
            context = context,
            onDismiss = { viewModel.dismissShop() }
        )
    }

    if (viewModel.showSupportDialog && !viewModel.supportMeLink.isNullOrBlank()) {
        SupportDialog(
            supportUrl = viewModel.supportMeLink!!,
            context = context,
            onDismiss = { viewModel.dismissSupport() }
        )
    }

    if (viewModel.showNotEnoughCreditsDialog) {
        NotEnoughCreditsDialog(
            requiredCredits = viewModel.estimatedCostCredits ?: 0,
            availableCredits = viewModel.userCredits,
            onBuyCredits = { viewModel.openShop() },
            onDismiss = { viewModel.dismissNotEnoughCredits() }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Floating Top-Right Action Button
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
                .zIndex(10f)
        ) {
            if (viewModel.isBilledServer) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable { viewModel.openShop() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MonetizationOn,
                            contentDescription = "Credits",
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "${viewModel.userCredits} Credits",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Credits",
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            } else if (viewModel.isUsingDefaultServer && !viewModel.supportMeLink.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.clickable { viewModel.openSupport() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Support",
                            tint = Color(0xFFE57373).copy(alpha = 0.75f),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Give",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }

        // Center Content Area
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. SUCCESS HERO - High visibility practicing prompt
                AnimatedVisibility(
                    visible = !viewModel.isLoading && viewModel.readySong != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    viewModel.readySong?.let { song ->
                        TranscriptionSuccessCard(
                            song = song,
                            onSongSelect = onSongSelect
                        )
                    }
                }

                // 2. INPUT CARD - Primary conversion entry
                TranscriptionInputCard(
                    viewModel = viewModel,
                    context = context,
                    onOpenServerSettings = { showServerSettingsDialog = true },
                    onCancelClick = { handleCancelClick() }
                )

                // 3. PROGRESS CARD - Active loading feedback
                AnimatedVisibility(visible = viewModel.isLoading) {
                    TranscriptionProgressCard(
                        progress = viewModel.progress,
                        status = viewModel.status
                    )
                }

                // 4. ERROR CARD - Persistent error feedback (dismissible)
                AnimatedVisibility(
                    visible = !viewModel.isLoading && (viewModel.transcriptionError != null || viewModel.status.startsWith("Error")),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val errorMsg = viewModel.transcriptionError ?: viewModel.status.removePrefix("Error: ")
                    TranscriptionErrorCard(
                        errorMessage = errorMsg,
                        onErrorDismiss = { viewModel.clearTranscriptionError() }
                    )
                }
            }
        }
    }
}
