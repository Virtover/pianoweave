package com.lumenchord.pianoweave.ui.screens.pianoroll

import com.lumenchord.pianoweave.ui.components.DialogHeader
import com.lumenchord.pianoweave.ui.components.DialogSurface
import androidx.compose.foundation.BorderStroke
import com.lumenchord.pianoweave.ui.theme.PillShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lumenchord.pianoweave.ui.components.AppThemeDialog
import com.lumenchord.pianoweave.ui.components.settings.LinkRow
import com.lumenchord.pianoweave.ui.screens.pianoroll.settings.LoopingSection
import com.lumenchord.pianoweave.ui.screens.pianoroll.settings.MetronomeSection
import com.lumenchord.pianoweave.ui.screens.pianoroll.settings.SettingsGroup
import com.lumenchord.pianoweave.ui.screens.pianoroll.settings.TranspositionSection
import com.lumenchord.pianoweave.ui.screens.pianoroll.settings.SpeedSection
import com.lumenchord.pianoweave.ui.screens.pianoroll.settings.ThemeSection
import com.lumenchord.pianoweave.ui.screens.pianoroll.settings.WaitModeSection
import com.lumenchord.pianoweave.ui.screens.pianoroll.settings.VoiceCommandsSection
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel

@Composable
internal fun SettingsDialog(
    viewModel: PianoWeaveViewModel,
    songDurationMs: Long,
    hasBeats: Boolean,
    onOpenTutorial: () -> Unit,
    onDismiss: () -> Unit
) {
    val currentContext = LocalContext.current
    var showThemeDialog by remember { mutableStateOf(false) }
    val appTheme = LocalAppTheme.current
    val colorGold = appTheme.primaryColor
    val colorSlate = MaterialTheme.colorScheme.tertiaryContainer

    LaunchedEffect(Unit) {
        viewModel.isPlaying = false
    }

    var startText by remember { mutableStateOf(formatTime(viewModel.loopStartMs)) }
    var endText by remember { mutableStateOf(formatTime(viewModel.loopEndMs)) }
    var isLooping by remember { mutableStateOf(viewModel.isLoopingEnabled) }

    Dialog(onDismissRequest = onDismiss) {
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        DialogHeader(
                            title = "Settings",
                            icon = Icons.Default.Tune,
                            onClose = onDismiss,
                            titleColor = colorGold
                        )

                        // Groups ordered by how often they're changed while practicing; lines only between groups
                        val groupDivider = colorSlate.copy(alpha = 0.6f)

                        SettingsGroup("Tempo") {
                            SpeedSection(viewModel = viewModel, context = currentContext)
                            MetronomeSection(viewModel = viewModel, hasBeats = hasBeats)
                        }

                        HorizontalDivider(color = groupDivider)

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

                        HorizontalDivider(color = groupDivider)

                        SettingsGroup("Wait mode") {
                            WaitModeSection(viewModel = viewModel, context = currentContext)
                        }

                        HorizontalDivider(color = groupDivider)

                        SettingsGroup("Hands-free") {
                            VoiceCommandsSection(viewModel = viewModel)
                        }

                        HorizontalDivider(color = groupDivider)

                        SettingsGroup("Appearance") {
                            ThemeSection(onOpenThemeDialog = { showThemeDialog = true })
                        }

                        // Reopens the first-time tutorial, like "How it works" in the app's settings
                        LinkRow(
                            icon = Icons.Default.AutoStories,
                            title = "How it works",
                            subtitle = "The piano roll in four steps",
                            previewTheme = appTheme,
                            onClick = onOpenTutorial,
                            trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight
                        )

                        if (showThemeDialog) {
                            AppThemeDialog(
                                currentThemeId = viewModel.selectedThemeId,
                                onSelectTheme = { themeId ->
                                    viewModel.setSelectedTheme(currentContext, themeId)
                                },
                                onDismiss = { showThemeDialog = false }
                            )
                        }

                        Spacer(Modifier.height(4.dp))

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
                }
            }
        }
    }
}
