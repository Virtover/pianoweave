package com.lumenchord.pianoweave.ui.screens.learn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lumenchord.pianoweave.api.Clip
import com.lumenchord.pianoweave.api.TranscriptionMode
import com.lumenchord.pianoweave.api.describe
import com.lumenchord.pianoweave.ui.components.DialogHeader
import com.lumenchord.pianoweave.ui.components.DialogSurface
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.theme.PillShape
import com.lumenchord.pianoweave.ui.viewmodel.OverlapWarning

/**
 * Asked before a part of a link is made when the library already has an overlapping part (or the whole song) in
 * the same mode: making it again repeats work, and on a billed server pays for it twice. Several parts that don't
 * overlap need no asking.
 */
@Composable
internal fun PartOverlapDialog(warning: OverlapWarning, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val appTheme = LocalAppTheme.current
    val gold = appTheme.primaryColor
    val noun = if (warning.mode == TranscriptionMode.Arrangement) "arrangement" else "transcription"
    val what = warning.clip?.let { "This part (${it.describe()})" } ?: "The whole song"
    val count = warning.overlapping.size

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            DialogSurface(
                light = gold,
                modifier = Modifier.fillMaxWidth(0.98f).wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = Color.White,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DialogHeader(title = "Already in your library", icon = Icons.Default.ContentCut, onClose = onDismiss, titleColor = gold)
                    Text(
                        text = "$what overlaps ${if (count == 1) "a $noun" else "${noun}s"} of this video you already have:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        lineHeight = 20.sp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        warning.overlapping.forEach { part ->
                            Text(
                                text = "•  " + (part?.describe() ?: "The whole song"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = gold
                            )
                        }
                    }
                    Text(
                        text = "Make it anyway? Both stay in your library.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = PillShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            Text("Cancel", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                        }
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = PillShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = gold,
                                contentColor = if (appTheme.isLightAccent) Color.Black else Color.White
                            )
                        ) {
                            Text(
                                text = if (warning.mode == TranscriptionMode.Arrangement) "Arrange anyway" else "Transcribe anyway",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
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
