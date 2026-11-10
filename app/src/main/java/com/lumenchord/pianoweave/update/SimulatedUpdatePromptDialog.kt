package com.lumenchord.pianoweave.update

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lumenchord.pianoweave.ui.components.DialogHeader
import com.lumenchord.pianoweave.ui.components.DialogSurface
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.theme.PillShape

/**
 * Debug only: stands in for Google Play's "Update available" sheet during [AppUpdates.simulate], which the
 * fake Play doesn't show. Real users see Play's own sheet, never this. Closing it counts as "No thanks",
 * as dismissing Play's sheet does.
 */
@Composable
fun SimulatedUpdatePromptDialog(onAnswer: (accept: Boolean) -> Unit) {
    val appTheme = LocalAppTheme.current
    val colorGold = appTheme.primaryColor
    val colorSlate = MaterialTheme.colorScheme.tertiaryContainer

    Dialog(onDismissRequest = { onAnswer(false) }) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            DialogSurface(
                light = colorGold,
                modifier = Modifier
                    .fillMaxWidth(0.98f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, colorSlate)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DialogHeader(
                        title = "Update available",
                        icon = Icons.Default.SystemUpdate,
                        onClose = { onAnswer(false) },
                        titleColor = colorGold
                    )

                    Text(
                        text = "A new version of Piano Weave is ready to download. It downloads in the " +
                            "background while you keep playing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    )
                    Text(
                        text = "Debug simulation: on a real install, Google Play shows its own sheet here.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onAnswer(false) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 46.dp),
                            shape = PillShape,
                            border = BorderStroke(1.dp, colorSlate),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "No thanks",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        }

                        Button(
                            onClick = { onAnswer(true) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 46.dp),
                            shape = PillShape,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorGold,
                                contentColor = if (appTheme.isLightAccent) MaterialTheme.colorScheme.surface
                                else MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = "Update",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
