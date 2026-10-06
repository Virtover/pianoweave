package com.lumenchord.pianoweave.ui.screens.pianoroll.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun VisualizerSettingsSection(
    viewModel: PianoWeaveViewModel,
    context: Context,
    onOpenThemeDialog: () -> Unit
) {
    val appTheme = LocalAppTheme.current
    val colorAccent = appTheme.primaryColor
    val colorSlate = MaterialTheme.colorScheme.tertiaryContainer
    val colorTextDim = MaterialTheme.colorScheme.tertiary

    var isStrikeOverlay by remember { mutableStateOf(viewModel.isStrikeOverlayEnabled) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Strike Overlay Section
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
                    viewModel.setStrikeOverlayEnabled(context, it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = colorAccent,
                    uncheckedThumbColor = colorTextDim,
                    uncheckedTrackColor = colorSlate
                )
            )
        }

        HorizontalDivider(color = colorSlate.copy(alpha = 0.6f))

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
                    color = colorSlate.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, colorAccent.copy(alpha = 0.4f))
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
                        color = colorAccent,
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
                        viewModel.setTopBarSpeed(context, newSpeed)
                    } else {
                        viewModel.setTopBarSpeed(context, 1.0f)
                    }
                    viewModel.speedMultiplier = newSpeed
                },
                valueRange = 0f..(speedValues.size - 1).toFloat(),
                steps = speedValues.size - 2,
                colors = SliderDefaults.colors(
                    thumbColor = colorAccent,
                    activeTrackColor = colorAccent,
                    inactiveTrackColor = colorSlate
                )
            )
        }

        HorizontalDivider(color = colorSlate.copy(alpha = 0.6f))

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
                    onClick = onOpenThemeDialog,
                    border = BorderStroke(1.dp, colorAccent),
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
                                .background(colorAccent, CircleShape)
                                .border(1.dp, Color.White, CircleShape)
                        )
                        Text("Change", color = colorAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
