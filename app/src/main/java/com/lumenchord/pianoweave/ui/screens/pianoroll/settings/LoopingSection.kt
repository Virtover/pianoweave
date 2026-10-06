package com.lumenchord.pianoweave.ui.screens.pianoroll.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.ui.screens.pianoroll.formatTime
import com.lumenchord.pianoweave.ui.screens.pianoroll.parseTimeToMs
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel

@Composable
fun LoopingSection(
    viewModel: PianoWeaveViewModel,
    songDurationMs: Long,
    isLooping: Boolean,
    onLoopingChange: (Boolean) -> Unit,
    startText: String,
    onStartTextChange: (String) -> Unit,
    endText: String,
    onEndTextChange: (String) -> Unit
) {
    val appTheme = LocalAppTheme.current
    val colorAccent = appTheme.primaryColor
    val colorSlate = MaterialTheme.colorScheme.tertiaryContainer
    val colorTextDim = MaterialTheme.colorScheme.tertiary

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
                    onLoopingChange(it)
                    viewModel.isLoopingEnabled = it
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = colorAccent,
                    uncheckedThumbColor = colorTextDim,
                    uncheckedTrackColor = colorSlate
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
                            onStartTextChange(newText)
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
                            focusedBorderColor = colorAccent,
                            unfocusedBorderColor = colorSlate,
                            focusedLabelColor = colorAccent,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                            cursorColor = colorAccent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { newText ->
                            onEndTextChange(newText)
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
                            focusedBorderColor = colorAccent,
                            unfocusedBorderColor = colorSlate,
                            focusedLabelColor = colorAccent,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                            cursorColor = colorAccent,
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
                            onStartTextChange(currentFormatted)
                            viewModel.loopStartMs = viewModel.playheadMs
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        border = BorderStroke(1.dp, colorAccent.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colorAccent)
                    ) {
                        Text("Start = Current", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val currentFormatted = formatTime(viewModel.playheadMs)
                            onEndTextChange(currentFormatted)
                            viewModel.loopEndMs = viewModel.playheadMs
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        border = BorderStroke(1.dp, colorAccent.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colorAccent)
                    ) {
                        Text("End = Current", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
