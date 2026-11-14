package com.lumenchord.pianoweave.ui.screens.pianoroll

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import com.lumenchord.pianoweave.ui.components.DialogHeader
import com.lumenchord.pianoweave.ui.components.DialogSurface
import androidx.compose.foundation.BorderStroke
import com.lumenchord.pianoweave.ui.theme.PillShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lumenchord.pianoweave.midi.MidiInputManager
import com.lumenchord.pianoweave.ui.components.AppThemeDialog
import com.lumenchord.pianoweave.ui.components.settings.LinkRow
import com.lumenchord.pianoweave.ui.screens.pianoroll.settings.LoopingSection
import com.lumenchord.pianoweave.ui.screens.pianoroll.settings.MetronomeSection
import com.lumenchord.pianoweave.ui.screens.pianoroll.settings.SettingsGroup
import com.lumenchord.pianoweave.ui.screens.pianoroll.settings.SpeedSection
import com.lumenchord.pianoweave.ui.screens.pianoroll.settings.TranspositionSection
import com.lumenchord.pianoweave.ui.screens.pianoroll.settings.VoiceCommandsSection
import com.lumenchord.pianoweave.ui.screens.pianoroll.settings.WaitModeSection
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay

/** The settings dialog's pages: the main one, and the less-used groups, each opened from a row on it. */
internal enum class SettingsPage { Main, WaitMode, VoiceCommands }

/**
 * Practice settings. The main page keeps what's changed while practicing (tempo, loop, transposition);
 * wait mode and voice commands each open as a page of their own from a one-line row that shows their
 * state, and Theme opens the theme picker, so the dialog stays short.
 */
@Composable
internal fun SettingsDialog(
    viewModel: PianoWeaveViewModel,
    songDurationMs: Long,
    hasBeats: Boolean,
    isTempoUnstable: Boolean = false,
    initialPage: SettingsPage = SettingsPage.Main,
    scrollToVoiceCommandsReminder: Boolean = false,
    pulseVoiceCommandsReminder: Boolean = false,
    onVoiceCommandsReminderScrolled: () -> Unit = {},
    onVoiceCommandsReminderPulseDone: () -> Unit = {},
    onOpenTutorial: () -> Unit,
    onDismiss: () -> Unit
) {
    val currentContext = LocalContext.current
    var showThemeDialog by remember { mutableStateOf(false) }
    val appTheme = LocalAppTheme.current
    val colorGold = appTheme.primaryColor
    val colorSlate = MaterialTheme.colorScheme.tertiaryContainer
    val scrollState = rememberScrollState()
    var page by rememberSaveable { mutableStateOf(initialPage) }

    LaunchedEffect(initialPage) {
        page = initialPage
    }

    LaunchedEffect(page, scrollToVoiceCommandsReminder) {
        if (page == SettingsPage.VoiceCommands && scrollToVoiceCommandsReminder) {
            withFrameNanos { }
            delay(140)
            if (scrollState.maxValue > 0) {
                snapshotFlow { scrollState.maxValue }
                    .filter { it > 0 }
                    .first()
                scrollState.animateScrollTo(scrollState.maxValue)
            }
            onVoiceCommandsReminderScrolled()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.isPlaying = false
    }

    var startText by remember { mutableStateOf(formatTime(viewModel.loopStartMs)) }
    var endText by remember { mutableStateOf(formatTime(viewModel.loopEndMs)) }
    var isLooping by remember { mutableStateOf(viewModel.isLoopingEnabled) }

    Dialog(onDismissRequest = onDismiss) {
        // System back on a page returns to the main one instead of closing the dialog
        BackHandler(enabled = page != SettingsPage.Main) { page = SettingsPage.Main }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            DialogSurface(
                light = colorGold,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(max = 650.dp),
                shape = RoundedCornerShape(20.dp),
                color = ColorSurface,
                contentColor = Color.White,
                border = BorderStroke(1.dp, colorSlate)
            ) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        // A page slides in from the right; going back slides the other way
                        val forward = targetState != SettingsPage.Main
                        (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut())
                    },
                    label = "settingsPage"
                ) { shown ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        when (shown) {
                            SettingsPage.Main -> {
                                DialogHeader(
                                    title = "Settings",
                                    icon = Icons.Default.Tune,
                                    onClose = onDismiss,
                                    titleColor = colorGold
                                )

                                SettingsGroup("Tempo") {
                                    SpeedSection(viewModel = viewModel, context = currentContext)
                                    MetronomeSection(viewModel = viewModel, hasBeats = hasBeats, isTempoUnstable = isTempoUnstable)
                                }

                                HorizontalDivider(color = colorSlate.copy(alpha = 0.6f))

                                SettingsGroup("Practice") {
                                    LoopingSection(
                                        viewModel = viewModel,
                                        songDurationMs = songDurationMs,
                                        isLooping = isLooping,
                                        onLoopingChange = { isLooping = it },
                                        startText = startText,
                                        onStartTextChange = { startText = it },
                                        endText = endText,
                                        onEndTextChange = { endText = it }
                                    )
                                    TranspositionSection(viewModel = viewModel)
                                }

                                HorizontalDivider(color = colorSlate.copy(alpha = 0.6f))

                                SettingsGroup("More") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        LinkRow(
                                            icon = Icons.Default.Timer,
                                            title = "Wait mode",
                                            subtitle = waitModeSummary(viewModel),
                                            previewTheme = appTheme,
                                            onClick = { page = SettingsPage.WaitMode },
                                            trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight
                                        )
                                        LinkRow(
                                            icon = Icons.Default.RecordVoiceOver,
                                            title = "Voice commands",
                                            subtitle = if (viewModel.isVoiceCommandsEnabled) "On" else "Off",
                                            previewTheme = appTheme,
                                            onClick = { page = SettingsPage.VoiceCommands },
                                            trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight
                                        )
                                        LinkRow(
                                            icon = Icons.Default.Palette,
                                            title = "Theme",
                                            subtitle = appTheme.name,
                                            previewTheme = appTheme,
                                            onClick = { showThemeDialog = true },
                                            trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight
                                        )
                                        // Reopens the first-time tutorial, like "How it works" in the app's settings
                                        LinkRow(
                                            icon = Icons.Default.AutoStories,
                                            title = "How it works",
                                            subtitle = "The piano roll in four steps",
                                            previewTheme = appTheme,
                                            onClick = onOpenTutorial,
                                            trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight
                                        )
                                    }
                                }

                                // Every setting applies as it changes (the loop times as they're typed), so this only closes
                                Button(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colorGold,
                                        contentColor = if (appTheme.isLightAccent) Color.Black else Color.White
                                    ),
                                    shape = PillShape
                                ) {
                                    Text("Done", fontWeight = FontWeight.Black, fontSize = 14.sp)
                                }
                            }

                            SettingsPage.WaitMode -> {
                                PageHeader("Wait mode", onBack = { page = SettingsPage.Main }, onClose = onDismiss)
                                WaitModeSection(viewModel = viewModel, context = currentContext)
                            }

                            SettingsPage.VoiceCommands -> {
                                PageHeader("Voice commands", onBack = { page = SettingsPage.Main }, onClose = onDismiss)
                                VoiceCommandsSection(
                                    viewModel = viewModel,
                                    highlightPillToggle = pulseVoiceCommandsReminder,
                                    onHighlightPillToggleDone = onVoiceCommandsReminderPulseDone
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showThemeDialog) {
            AppThemeDialog(
                currentThemeId = viewModel.selectedThemeId,
                onSelectTheme = { themeId ->
                    viewModel.setSelectedTheme(currentContext, themeId)
                },
                onDismiss = { showThemeDialog = false }
            )
        }
    }
}

/** What the wait mode row says about its settings, so the page needn't be opened to check them. */
private fun waitModeSummary(viewModel: PianoWeaveViewModel): String {
    val banner = if (viewModel.isStrikeOverlayEnabled) "Strike overlay on" else "Strike overlay off"
    if (!MidiInputManager.isConnected) return banner
    return banner + if (viewModel.isKeyboardSoundEnabled) " · keyboard sound on" else " · keyboard sound off"
}

/** A settings page's header: back to the main page, the page's name, and close. */
@Composable
private fun PageHeader(title: String, onBack: () -> Unit, onClose: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to settings", tint = LocalAppTheme.current.primaryColor)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = LocalAppTheme.current.primaryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
