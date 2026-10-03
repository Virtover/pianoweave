package com.lumenchord.pianoweave.ui.screens

import android.content.Context
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumenchord.pianoweave.midi.StoredMidi
import com.lumenchord.pianoweave.ui.screens.learn.*
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import kotlin.math.abs

@Composable
fun LearnScreen(
    viewModel: PianoWeaveViewModel,
    context: Context,
    onSongSelect: (StoredMidi) -> Unit = {}
) {
    var showServerSettingsDialog by remember { mutableStateOf(false) }
    var showCancelConfirmDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    var viewportHeightPx by remember { mutableFloatStateOf(0f) }
    var scrollContainerTopInRoot by remember { mutableFloatStateOf(0f) }
    var progressCardTopInRoot by remember { mutableFloatStateOf(0f) }
    var progressCardHeightPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(
        viewModel.isLoading,
        viewportHeightPx,
        scrollContainerTopInRoot,
        progressCardTopInRoot,
        progressCardHeightPx,
        scrollState.maxValue
    ) {
        if (viewModel.isLoading && viewportHeightPx > 0f && progressCardHeightPx > 0f) {
            val cardCenterInRoot = progressCardTopInRoot + (progressCardHeightPx / 2f)
            val viewportCenterInRoot = scrollContainerTopInRoot + (viewportHeightPx / 2f)
            val deltaOnScreen = cardCenterInRoot - viewportCenterInRoot

            if (abs(deltaOnScreen) > 2f) {
                val targetScroll = (scrollState.value + deltaOnScreen)
                    .toInt()
                    .coerceIn(0, scrollState.maxValue)
                scrollState.animateScrollTo(targetScroll, animationSpec = tween(durationMillis = 50))
            }
        }
    }

    LaunchedEffect(viewModel.isLoading, viewModel.readySong) {
        if (!viewModel.isLoading && viewModel.readySong != null) {
            scrollState.animateScrollTo(0, animationSpec = tween(durationMillis = 150))
        }
    }

    fun handleCancelClick() {
        if (viewModel.progress >= 0.03f) {
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

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val viewportHeight = maxHeight

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .onGloballyPositioned { coordinates ->
                    viewportHeightPx = coordinates.size.height.toFloat()
                    scrollContainerTopInRoot = coordinates.positionInRoot().y
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = viewportHeight)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Right Action Button (Far top right edge, moves out on scroll)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
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
                                    text = "Support",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }

                // Cards Container (Centered in viewport when un-scrolled, expands freely when scrolled)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(0.9f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 1. SUCCESS HERO - High visibility practicing prompt
                        if (!viewModel.isLoading && viewModel.readySong != null) {
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
                            onOpenServerSettings = { showServerSettingsDialog = true }
                        )

                        // 3. PROGRESS CARD - Active loading feedback
                        if (viewModel.isLoading) {
                            TranscriptionProgressCard(
                                viewModel = viewModel,
                                progress = viewModel.progress,
                                status = viewModel.status,
                                onCancelClick = { handleCancelClick() },
                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                    progressCardTopInRoot = coordinates.positionInRoot().y
                                    progressCardHeightPx = coordinates.size.height.toFloat()
                                }
                            )
                        }

                        // 4. ERROR CARD - Persistent error feedback (dismissible)
                        if (!viewModel.isLoading && (viewModel.transcriptionError != null || viewModel.status.startsWith("Error"))) {
                            val errorMsg = viewModel.transcriptionError ?: viewModel.status.removePrefix("Error: ")
                            TranscriptionErrorCard(
                                errorMessage = errorMsg,
                                onErrorDismiss = { viewModel.clearTranscriptionError() }
                            )
                        }
                    }
                }

                // Spacer at bottom to balance top action bar
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
