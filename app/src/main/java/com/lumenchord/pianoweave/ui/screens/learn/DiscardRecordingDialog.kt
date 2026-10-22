package com.lumenchord.pianoweave.ui.screens.learn

import androidx.compose.foundation.BorderStroke
import com.lumenchord.pianoweave.ui.theme.PillShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lumenchord.pianoweave.ui.components.DangerButton
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme

/** Asks before a longer microphone take is thrown away, whether it is still recording or finished. */
@Composable
internal fun DiscardRecordingDialog(
    isRecording: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val appTheme = LocalAppTheme.current
    val colorSurface = MaterialTheme.colorScheme.surface
    val colorGold = appTheme.primaryColor
    val colorSlate = MaterialTheme.colorScheme.tertiaryContainer
    val colorTextDim = MaterialTheme.colorScheme.tertiary

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.98f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = colorSurface,
                contentColor = Color.White,
                border = BorderStroke(1.dp, colorSlate)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isRecording) "Discard Recording?" else "Delete Recording?",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = colorGold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = colorTextDim
                            )
                        }
                    }

                    HorizontalDivider(color = colorSlate.copy(alpha = 0.6f))

                    Text(
                        text = if (isRecording) {
                            "Recording will stop and everything recorded so far will be lost."
                        } else {
                            "This recording will be deleted and can't be recovered."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        lineHeight = 20.sp
                    )

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = PillShape,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorGold,
                                contentColor = if (appTheme.isLightAccent) Color.Black else Color.White
                            )
                        ) {
                            Text(
                                text = if (isRecording) "Keep Recording" else "Keep It",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        DangerButton(
                            onClick = onConfirm,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = PillShape
                        ) {
                            Text(
                                text = if (isRecording) "Discard" else "Delete",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}
