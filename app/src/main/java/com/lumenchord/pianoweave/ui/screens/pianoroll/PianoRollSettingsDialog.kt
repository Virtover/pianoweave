package com.lumenchord.pianoweave.ui.screens.pianoroll

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.lumenchord.pianoweave.ui.screens.pianoroll.settings.LoopingSection
import com.lumenchord.pianoweave.ui.screens.pianoroll.settings.TranspositionSection
import com.lumenchord.pianoweave.ui.screens.pianoroll.settings.VisualizerSettingsSection
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel

@Composable
internal fun SettingsDialog(
    viewModel: PianoWeaveViewModel,
    songDurationMs: Long,
    onDismiss: () -> Unit
) {
    val currentContext = LocalContext.current
    var showThemeDialog by remember { mutableStateOf(false) }
    val appTheme = LocalAppTheme.current
    val colorGold = appTheme.primaryColor
    val colorSlate = MaterialTheme.colorScheme.tertiaryContainer
    val colorTextDim = MaterialTheme.colorScheme.tertiary

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
            Surface(
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
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = colorGold
                            )
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = colorTextDim)
                            }
                        }

                        HorizontalDivider(color = colorSlate.copy(alpha = 0.6f))

                        // Transposition Section
                        TranspositionSection(viewModel = viewModel)

                        HorizontalDivider(color = colorSlate.copy(alpha = 0.6f))

                        // Loop Section
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

                        HorizontalDivider(color = colorSlate.copy(alpha = 0.6f))

                        // Visualizer & Audio Settings
                        VisualizerSettingsSection(
                            viewModel = viewModel,
                            context = currentContext,
                            onOpenThemeDialog = { showThemeDialog = true }
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

                        Button(
                            onClick = {
                                val parsedStart = parseTimeToMs(startText)
                                val parsedEnd = parseTimeToMs(endText)

                                if (parsedStart != null) {
                                    viewModel.loopStartMs = parsedStart.coerceIn(0L, songDurationMs)
                                }
                                if (parsedEnd != null) {
                                    viewModel.loopEndMs = parsedEnd.coerceIn(0L, songDurationMs)
                                }
                                viewModel.isLoopingEnabled = isLooping
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorGold,
                                contentColor = if (appTheme.isLightAccent) Color.Black else Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("DONE", fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
