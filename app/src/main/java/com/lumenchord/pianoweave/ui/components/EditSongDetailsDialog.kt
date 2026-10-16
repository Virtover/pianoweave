package com.lumenchord.pianoweave.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lumenchord.pianoweave.midi.StoredMidi
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme

private const val MAX_TITLE_LENGTH = 120
private const val MAX_ARTIST_LENGTH = 80
private val PLACEHOLDER_ARTISTS = setOf("Unknown", "Device Storage")

/** Title and artist of a song that has no source link to take them from. */
@Composable
fun EditSongDetailsDialog(
    song: StoredMidi,
    onSave: (title: String, artist: String) -> Unit,
    onDismiss: () -> Unit
) {
    val appTheme = LocalAppTheme.current
    val colorSurface = MaterialTheme.colorScheme.surface
    val colorPrimary = appTheme.primaryColor
    val colorSlate = MaterialTheme.colorScheme.tertiaryContainer
    val colorTextDim = MaterialTheme.colorScheme.tertiary

    // "Device Storage" is what imports show in place of an artist, not a name to edit.
    val initialArtist = song.metadata.author.takeUnless { it in PLACEHOLDER_ARTISTS }.orEmpty()
    var title by remember(song.file) { mutableStateOf(song.metadata.title) }
    var artist by remember(song.file) { mutableStateOf(initialArtist) }
    val canSave = title.isNotBlank() &&
        (title.trim() != song.metadata.title || artist.trim() != initialArtist)
    val save = { if (canSave) onSave(title, artist) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colorPrimary,
        focusedLabelColor = colorPrimary,
        cursorColor = colorPrimary
    )

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Keeps the fields above the keyboard on smaller phones.
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.98f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = colorSurface,
                contentColor = MaterialTheme.colorScheme.onSurface,
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
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = colorPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Edit Song Details",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = colorPrimary
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

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(MAX_TITLE_LENGTH) },
                        label = { Text("Title") },
                        singleLine = true,
                        isError = title.isBlank(),
                        supportingText = if (title.isBlank()) {
                            { Text("A title is required") }
                        } else null,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = artist,
                        onValueChange = { artist = it.take(MAX_ARTIST_LENGTH) },
                        label = { Text("Artist (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 46.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, colorSlate),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Text(
                                text = "Cancel",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }

                        Button(
                            onClick = save,
                            enabled = canSave,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 46.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorPrimary,
                                contentColor = if (appTheme.isLightAccent) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = "Save",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
