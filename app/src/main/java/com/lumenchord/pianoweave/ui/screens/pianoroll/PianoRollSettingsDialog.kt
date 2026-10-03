package com.lumenchord.pianoweave.ui.screens.pianoroll

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun SettingsDialog(
    viewModel: PianoWeaveViewModel,
    songDurationMs: Long,
    onDismiss: () -> Unit
) {
    val currentContext = LocalContext.current
    var showThemeDialog by remember { mutableStateOf(false) }
    val appTheme = LocalAppTheme.current
    val ColorGold = appTheme.primaryColor
    val ColorSlate = MaterialTheme.colorScheme.tertiaryContainer
    val ColorTextDim = MaterialTheme.colorScheme.tertiary

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
                border = BorderStroke(1.dp, ColorSlate)
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
                                color = ColorGold
                            )
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = ColorTextDim)
                            }
                        }

                        HorizontalDivider(color = ColorSlate.copy(alpha = 0.6f))

                        // Transposition Section
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Transposition", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Surface(
                                    color = ColorSlate.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, ColorGold.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "${if (viewModel.transposeOffset > 0) "+" else ""}${viewModel.transposeOffset} semi",
                                        color = ColorGold,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Slider(
                                value = viewModel.transposeOffset.toFloat(),
                                onValueChange = { viewModel.transposeOffset = it.toInt() },
                                valueRange = -12f..12f,
                                steps = 23,
                                colors = SliderDefaults.colors(
                                    thumbColor = ColorGold,
                                    activeTrackColor = ColorGold,
                                    inactiveTrackColor = ColorSlate
                                )
                            )
                        }

                        HorizontalDivider(color = ColorSlate.copy(alpha = 0.6f))

                        // Loop Section
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text("Loop Playback", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Repeat section during practice", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 16.sp)
                                }
                                Switch(
                                    checked = isLooping,
                                    onCheckedChange = {
                                        isLooping = it
                                        viewModel.isLoopingEnabled = it
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = ColorGold,
                                        uncheckedThumbColor = ColorTextDim,
                                        uncheckedTrackColor = ColorSlate
                                    )
                                )
                            }

                            AnimatedVisibility(
                                visible = isLooping,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = startText,
                                            onValueChange = { newText ->
                                                startText = newText
                                                val parsed = parseTimeToMs(newText)
                                                if (parsed != null) {
                                                    viewModel.loopStartMs = parsed.coerceIn(0L, songDurationMs)
                                                }
                                            },
                                            label = { Text("Start (mm:ss)") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = ColorGold,
                                                unfocusedBorderColor = ColorSlate,
                                                focusedLabelColor = ColorGold,
                                                unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                                                cursorColor = ColorGold,
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            )
                                        )
                                        OutlinedTextField(
                                            value = endText,
                                            onValueChange = { newText ->
                                                endText = newText
                                                val parsed = parseTimeToMs(newText)
                                                if (parsed != null) {
                                                    viewModel.loopEndMs = parsed.coerceIn(0L, songDurationMs)
                                                }
                                            },
                                            label = { Text("End (mm:ss)") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = ColorGold,
                                                unfocusedBorderColor = ColorSlate,
                                                focusedLabelColor = ColorGold,
                                                unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                                                cursorColor = ColorGold,
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            )
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val currentFormatted = formatTime(viewModel.playheadMs)
                                                startText = currentFormatted
                                                viewModel.loopStartMs = viewModel.playheadMs
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                            border = BorderStroke(1.dp, ColorGold.copy(alpha = 0.6f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorGold)
                                        ) {
                                            Text("Start = Current", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val currentFormatted = formatTime(viewModel.playheadMs)
                                                endText = currentFormatted
                                                viewModel.loopEndMs = viewModel.playheadMs
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                            border = BorderStroke(1.dp, ColorGold.copy(alpha = 0.6f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorGold)
                                        ) {
                                            Text("End = Current", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = ColorSlate.copy(alpha = 0.6f))

                        // Strike Overlay Section
                        var isStrikeOverlay by remember { mutableStateOf(viewModel.isStrikeOverlayEnabled) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text("Strike Overlay", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Show STRIKE instruction banner in wait mode", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 16.sp)
                            }
                            Switch(
                                checked = isStrikeOverlay,
                                onCheckedChange = {
                                    isStrikeOverlay = it
                                    viewModel.setStrikeOverlayEnabled(currentContext, it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = ColorGold,
                                    uncheckedThumbColor = ColorTextDim,
                                    uncheckedTrackColor = ColorSlate
                                )
                            )
                        }

                        HorizontalDivider(color = ColorSlate.copy(alpha = 0.6f))

                        // Speed Section
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text("Speed", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Set playback speed (0.25x - 2.0x)", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 16.sp)
                                }
                                Surface(
                                    color = ColorSlate.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, ColorGold.copy(alpha = 0.4f))
                                ) {
                                    val speedText = when (viewModel.speedMultiplier) {
                                        0.25f -> "0.25x"
                                        0.5f -> "0.5x"
                                        1.0f -> "1.0x"
                                        1.25f -> "1.25x"
                                        1.5f -> "1.5x"
                                        2.0f -> "2.0x"
                                        else -> String.format(Locale.US, "%.2fx", viewModel.speedMultiplier)
                                    }
                                    Text(
                                        text = speedText,
                                        color = ColorGold,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            val speedValues = listOf(0.25f, 0.5f, 1.0f, 1.25f, 1.5f, 2.0f)
                            val currentIndex = speedValues.indices.minByOrNull { abs(speedValues[it] - viewModel.speedMultiplier) } ?: 2
                            Slider(
                                value = currentIndex.toFloat(),
                                onValueChange = { newValue ->
                                    val index = newValue.roundToInt().coerceIn(0, speedValues.size - 1)
                                    val newSpeed = speedValues[index]
                                    if (newSpeed >= 1.0f) {
                                        viewModel.setTopBarSpeed(currentContext, newSpeed)
                                    } else {
                                        viewModel.setTopBarSpeed(currentContext, 1.0f)
                                    }
                                    viewModel.speedMultiplier = newSpeed
                                },
                                valueRange = 0f..(speedValues.size - 1).toFloat(),
                                steps = speedValues.size - 2,
                                colors = SliderDefaults.colors(
                                    thumbColor = ColorGold,
                                    activeTrackColor = ColorGold,
                                    inactiveTrackColor = ColorSlate
                                )
                            )
                        }

                        HorizontalDivider(color = ColorSlate.copy(alpha = 0.6f))

                        // App Color Theme Option
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text("Theme", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Current: ${appTheme.name}", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = { showThemeDialog = true },
                                    border = BorderStroke(1.dp, ColorGold),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .background(ColorGold, CircleShape)
                                                .border(1.dp, Color.White, CircleShape)
                                        )
                                        Text("Change", color = ColorGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                                containerColor = ColorGold,
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
