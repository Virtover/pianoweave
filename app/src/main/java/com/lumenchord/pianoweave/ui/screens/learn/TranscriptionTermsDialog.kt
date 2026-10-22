package com.lumenchord.pianoweave.ui.screens.learn

import androidx.compose.foundation.BorderStroke
import com.lumenchord.pianoweave.ui.theme.PillShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lumenchord.pianoweave.ui.components.appendLink
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.util.AppLinks

/** Shown before the first transcription, and again whenever the Terms change. */
@Composable
internal fun TranscriptionTermsDialog(
    onAgree: () -> Unit,
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
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Gavel,
                                contentDescription = null,
                                tint = colorGold,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Before You Transcribe",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = colorGold
                            )
                        }
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
                        text = buildAnnotatedString {
                            append("A transcription is a copy of the music, turned into an AI-made MIDI version. ")
                            append("Only transcribe music you have the right to copy and modify. ")
                            appendLink("Terms of Service", AppLinks.TERMS_OF_SERVICE, colorGold)
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
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = PillShape,
                            border = BorderStroke(1.dp, colorSlate),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text(
                                text = "Cancel",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Button(
                            onClick = onAgree,
                            modifier = Modifier
                                .weight(1.4f)
                                .height(46.dp),
                            shape = PillShape,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorGold,
                                contentColor = if (appTheme.isLightAccent) Color.Black else Color.White
                            )
                        ) {
                            Text(
                                text = "Agree & Continue",
                                fontWeight = FontWeight.Black,
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
