package com.lumenchord.pianoweave.ui.screens

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.lumenchord.pianoweave.api.ServerMaintenance
import com.lumenchord.pianoweave.midi.StoredMidi
import com.lumenchord.pianoweave.midi.hasEditableDetails
import com.lumenchord.pianoweave.ui.components.AppSnackbar
import com.lumenchord.pianoweave.ui.components.EditSongDetailsDialog
import com.lumenchord.pianoweave.ui.components.ServerMaintenanceBanner
import com.lumenchord.pianoweave.ui.screens.learn.*
import com.lumenchord.pianoweave.ui.screens.learn.cards.HowItWorksPath
import com.lumenchord.pianoweave.ui.screens.learn.cards.RecentSongsSection
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import com.lumenchord.pianoweave.ui.viewmodel.ServerStatus
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

@OptIn(FlowPreview::class, ExperimentalLayoutApi::class)
@Composable
fun LearnScreen(
    viewModel: PianoWeaveViewModel,
    context: Context,
    onSongSelect: (StoredMidi) -> Unit = {}
) {
    var showCancelConfirmDialog by remember { mutableStateOf(false) }
    var songBeingEdited by remember { mutableStateOf<StoredMidi?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    val scrollState = rememberScrollState()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var viewportHeightPx by remember { mutableFloatStateOf(0f) }
    var scrollContainerTopInRoot by remember { mutableFloatStateOf(0f) }
    var progressCardTopInRoot by remember { mutableFloatStateOf(0f) }
    var progressCardHeightPx by remember { mutableFloatStateOf(0f) }

    // Centers the progress card once when a transcription starts. Re-centering on every layout change
    // fought the user's scrolling and jumped whenever the card or the content above it changed size.
    LaunchedEffect(viewModel.isLoading) {
        if (!viewModel.isLoading) return@LaunchedEffect
        // Wait until the layout settles: the Transcribe button disappears and the keyboard closes.
        snapshotFlow { Triple(viewportHeightPx, progressCardTopInRoot, progressCardHeightPx) }
            .filter { (viewport, _, cardHeight) -> viewport > 0f && cardHeight > 0f }
            .debounce(150)
            .first()

        val cardCenterInRoot = progressCardTopInRoot + (progressCardHeightPx / 2f)
        val viewportCenterInRoot = scrollContainerTopInRoot + (viewportHeightPx / 2f)
        val deltaOnScreen = cardCenterInRoot - viewportCenterInRoot
        if (abs(deltaOnScreen) > 2f) {
            val targetScroll = (scrollState.value + deltaOnScreen)
                .toInt()
                .coerceIn(0, scrollState.maxValue)
            scrollState.animateScrollTo(targetScroll, animationSpec = tween(durationMillis = 300))
        }
    }

    // Typing a link: scroll once, as the keyboard starts to open, so the Transcribe button ends up just above
    // the keyboard. The link field matters more, so scrolling stops before the field's top leaves the screen.
    // Aiming at the keyboard's final height (not following it frame by frame) keeps this a single smooth scroll.
    val linkInputAnchors = remember { LinkInputAnchors() }
    val density = LocalDensity.current
    var rootHeightPx by remember { mutableFloatStateOf(0f) }
    val imeTargetPx = WindowInsets.imeAnimationTarget.getBottom(density)
    val imeNowPx = WindowInsets.ime.getBottom(density)
    fun overlapWith(imePx: Int): Float =
        if (rootHeightPx <= 0f) 0f
        else (scrollContainerTopInRoot + viewportHeightPx - (rootHeightPx - imePx)).coerceAtLeast(0f)
    // While closing, follows the keyboard down so the content doesn't jump.
    val keyboardOverlapPx = maxOf(overlapWith(imeTargetPx), overlapWith(imeNowPx))

    LaunchedEffect(linkInputAnchors.isLinkFieldFocused, imeTargetPx) {
        if (!linkInputAnchors.isLinkFieldFocused || imeTargetPx <= 0 || rootHeightPx <= 0f) return@LaunchedEffect
        val margin = with(density) { 12.dp.toPx() }
        val visibleTop = scrollContainerTopInRoot + margin
        val visibleBottom = minOf(scrollContainerTopInRoot + viewportHeightPx, rootHeightPx - imeTargetPx) - margin
        val needed = linkInputAnchors.transcribeButtonBottomInRoot - visibleBottom
        val allowed = linkInputAnchors.linkFieldTopInRoot - visibleTop
        val delta = minOf(needed, allowed)
        if (delta <= 0f) return@LaunchedEffect

        val target = scrollState.value + delta.roundToInt()
        // The spacer below grows in the next layout pass; until then the scroll range may be too short.
        withTimeoutOrNull(500) { snapshotFlow { scrollState.maxValue }.first { it >= target } }
        scrollState.animateScrollTo(
            target.coerceAtMost(scrollState.maxValue),
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
        )
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

    if (viewModel.showShopDialog) {
        ShopDialog(
            viewModel = viewModel,
            context = context,
            onDismiss = { viewModel.dismissShop() }
        )
    }

    songBeingEdited?.let { song ->
        EditSongDetailsDialog(
            song = song,
            onSave = { title, artist ->
                songBeingEdited = null
                viewModel.updateSongDetails(context, song, title, artist)
            },
            onDismiss = { songBeingEdited = null }
        )
    }

    if (viewModel.showSupportDialog && !viewModel.supportMeLink.isNullOrBlank()) {
        SupportDialog(
            supportUrl = viewModel.supportMeLink!!,
            context = context,
            onDismiss = { viewModel.dismissSupport() }
        )
    }

    if (viewModel.showNotEnoughCreditsDialog && viewModel.isGoogleSignedIn) {
        NotEnoughCreditsDialog(
            requiredCredits = viewModel.estimatedCostCredits,
            availableCredits = viewModel.userCredits,
            onBuyCredits = { viewModel.openShop(context) },
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
                // Before verticalScroll, so this measures the visible viewport rather than the scrolled content
                .onGloballyPositioned { coordinates ->
                    viewportHeightPx = coordinates.size.height.toFloat()
                    scrollContainerTopInRoot = coordinates.positionInRoot().y
                    rootHeightPx = coordinates.findRootCoordinates().size.height.toFloat()
                }
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = viewportHeight)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top right: the balance or Support ribbon, hanging from the top edge; scrolls away with the content
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 28.dp, bottom = if (isLandscape) 2.dp else 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Support follows the official server's billing, so it also shows while a custom server is selected.
                    if (viewModel.serverStatus == ServerStatus.ONLINE && viewModel.isBilledServer) {
                        CreditsBalancePill(
                            credits = viewModel.userCredits,
                            onClick = { viewModel.openShop(context) }
                        )
                    } else if (viewModel.canShowSupport) {
                        SupportPill(onClick = { viewModel.openSupport() })
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (isLandscape) 10.dp else 4.dp, bottom = 12.dp),
                    contentAlignment = Alignment.TopCenter
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
                                    onSongSelect = onSongSelect,
                                    onEditDetails = if (song.hasEditableDetails) {
                                        { songBeingEdited = song }
                                    } else null
                                )
                            }
                        }

                        // Planned server restart or shutdown, above the input it affects
                        ServerMaintenance.notice?.let { notice ->
                            ServerMaintenanceBanner(notice = notice)
                        }

                        // 2. INPUT CARD - Primary conversion entry
                        TranscriptionInputCard(
                            viewModel = viewModel,
                            context = context,
                            onOpenServerSettings = { viewModel.showServerSettingsDialog = true },
                            linkInputAnchors = linkInputAnchors
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
                                onErrorDismiss = { viewModel.clearTranscriptionError() },
                                onUploadInstead = if (viewModel.uploadOfferedFor == errorMsg) {
                                    { viewModel.uploadInsteadOfLink() }
                                } else null
                            )
                        }

                        // 5. HOW IT WORKS - for new users, right under the input it explains; stays until finished or hidden.
                        // Hidden while transcribing so the progress card has the screen to itself.
                        AnimatedVisibility(
                            visible = viewModel.showHowItWorksHint && !viewModel.isLoading,
                            enter = fadeIn(tween(400)),
                            exit = fadeOut(tween(300))
                        ) {
                            HowItWorksPath(
                                onClick = { viewModel.openWelcomeTutorial() },
                                onHide = {
                                    viewModel.setHowItWorksHintShown(context, false)
                                    snackbarScope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val result = snackbarHostState.showSnackbar(
                                            message = "You can find it in Settings › About",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.setHowItWorksHintShown(context, true)
                                        }
                                    }
                                },
                                modifier = Modifier.padding(top = if (isLandscape) 4.dp else 20.dp)
                            )
                        }

                        // 6. RECENT SONGS - quick way back into practicing
                        val recentSongs = remember(viewModel.songs, viewModel.readySong) {
                            viewModel.songs.filter { it.file != viewModel.readySong?.file }.take(3)
                        }
                        if (recentSongs.isNotEmpty()) {
                            RecentSongsSection(songs = recentSongs, onSongSelect = onSongSelect)
                        }
                    }
                }
            }

            // Room to scroll the Transcribe button above the keyboard, which covers the screen rather than shrinking it.
            Spacer(Modifier.height(with(density) { keyboardOverlapPx.toDp() }))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) { data ->
            AppSnackbar(data)
        }
    }
}
