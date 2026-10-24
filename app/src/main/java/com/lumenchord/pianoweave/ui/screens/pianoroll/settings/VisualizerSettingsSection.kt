package com.lumenchord.pianoweave.ui.screens.pianoroll.settings

import android.content.Context
import com.lumenchord.pianoweave.ui.theme.PillShape
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
import com.lumenchord.pianoweave.ui.viewmodel.PLAYBACK_SPEEDS
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import java.util.Locale
import kotlin.math.abs

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
                // Read straight from the view model: the banner can also be hidden from the toolbar or by swiping it away
                checked = viewModel.isStrikeOverlayEnabled,
                onCheckedChange = { viewModel.setStrikeOverlayEnabled(context, it) },
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
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val speedValues = PLAYBACK_SPEEDS
            val normalIndex = speedValues.indexOf(1.0f)
            val currentIndex = speedValues.indices.minByOrNull { abs(speedValues[it] - viewModel.speedMultiplier) } ?: normalIndex
            val speedText = formatSpeed(viewModel.speedMultiplier)
            SettingHeader(
                title = "Speed",
                subtitle = "Slow down to learn, speed up to challenge",
                value = if (currentIndex == normalIndex) "Normal" else speedText,
                isDefault = currentIndex == normalIndex,
                accent = colorAccent,
                onReset = { viewModel.selectPlaybackSpeed(context, 1.0f) }
            )
            StepSlider(
                stepCount = speedValues.size,
                index = currentIndex,
                onIndexChange = { viewModel.selectPlaybackSpeed(context, speedValues[it]) },
                originIndex = normalIndex,
                labels = speedValues.indices.associateWith { formatSpeed(speedValues[it]) },
                accent = colorAccent,
                valueDescription = "Playback speed, $speedText"
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
                    shape = PillShape,
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

/** "0.25x", "0.5x", "1x", "1.25x": no trailing zeros, so the labels under the speed slider stay short. */
private fun formatSpeed(speed: Float): String =
    String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.') + "x"
