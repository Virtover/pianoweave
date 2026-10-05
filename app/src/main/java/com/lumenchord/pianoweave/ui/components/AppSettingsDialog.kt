package com.lumenchord.pianoweave.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lumenchord.pianoweave.ui.theme.AppThemeManager
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel

@Composable
fun AppSettingsDialog(
    viewModel: PianoWeaveViewModel,
    context: Context,
    onDismiss: () -> Unit
) {
    var selectedThemeId by remember { mutableStateOf(viewModel.selectedThemeId) }
    val previewTheme = AppThemeManager.getTheme(selectedThemeId)

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.98f)
                    .heightIn(max = 580.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = Color.White,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                tint = previewTheme.primaryColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = previewTheme.primaryColor
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // --- Google Account Section ---
                        Text(
                            text = "GOOGLE ACCOUNT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp)
                            ) {
                                val showSignOut = viewModel.isGoogleSignedIn && !viewModel.isBilledServer && !viewModel.requireGoogleAccount
                                val emailLength = viewModel.googleUserEmail.length
                                val isTightSpace = maxWidth < 220.dp || (showSignOut && maxWidth < 300.dp) || (emailLength > 20 && maxWidth < 260.dp)

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isTightSpace) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccountCircle,
                                                contentDescription = null,
                                                tint = previewTheme.primaryColor,
                                                modifier = Modifier.size(26.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = if (viewModel.isGoogleSignedIn) viewModel.googleUserEmail else "Not signed in",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                    fontSize = 13.sp,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (viewModel.isGoogleSignedIn) "Signed in" else "Select account",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (viewModel.isGoogleSignedIn) {
                                                val isTranscribing = viewModel.isLoading
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    OutlinedButton(
                                                        onClick = { viewModel.signInWithGoogle(context) },
                                                        enabled = !viewModel.isGoogleAuthLoading && !isTranscribing,
                                                        shape = RoundedCornerShape(6.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                        modifier = Modifier.height(30.dp),
                                                        border = BorderStroke(
                                                            1.dp,
                                                            if (isTranscribing) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.tertiaryContainer
                                                        )
                                                    ) {
                                                        Text(
                                                            text = "Switch",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = if (isTranscribing) Color.Gray else Color.White
                                                        )
                                                    }

                                                    if (showSignOut) {
                                                        OutlinedButton(
                                                            onClick = { viewModel.signOutGoogle(context) },
                                                            enabled = !isTranscribing,
                                                            shape = RoundedCornerShape(6.dp),
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(30.dp),
                                                            border = BorderStroke(
                                                                1.dp,
                                                                if (isTranscribing) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f) else Color(0xFF8C3235)
                                                            )
                                                        ) {
                                                            Text(
                                                                text = "Sign Out",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                color = if (isTranscribing) Color.Gray else Color(0xFFE57373)
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                Button(
                                                    onClick = { viewModel.signInWithGoogle(context) },
                                                    enabled = !viewModel.isGoogleAuthLoading,
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(30.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = previewTheme.primaryColor,
                                                        contentColor = if (previewTheme.isLightAccent) Color.Black else Color.White
                                                    )
                                                ) {
                                                    if (viewModel.isGoogleAuthLoading) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(12.dp),
                                                            strokeWidth = 2.dp,
                                                            color = Color.White
                                                        )
                                                    } else {
                                                        Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        // Single row layout when horizontal space is plentiful
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AccountCircle,
                                                    contentDescription = null,
                                                    tint = previewTheme.primaryColor,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = if (viewModel.isGoogleSignedIn) viewModel.googleUserEmail else "Not signed in",
                                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                        fontSize = 13.sp,
                                                        color = Color.White,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = if (viewModel.isGoogleSignedIn) "Signed in" else "Select account",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.tertiary
                                                    )
                                                }
                                            }

                                            Spacer(Modifier.width(6.dp))

                                            if (viewModel.isGoogleSignedIn) {
                                                val isTranscribing = viewModel.isLoading
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    OutlinedButton(
                                                        onClick = { viewModel.signInWithGoogle(context) },
                                                        enabled = !viewModel.isGoogleAuthLoading && !isTranscribing,
                                                        shape = RoundedCornerShape(6.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                        modifier = Modifier.height(30.dp),
                                                        border = BorderStroke(
                                                            1.dp,
                                                            if (isTranscribing) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.tertiaryContainer
                                                        )
                                                    ) {
                                                        Text(
                                                            text = "Switch",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = if (isTranscribing) Color.Gray else Color.White
                                                        )
                                                    }

                                                    if (showSignOut) {
                                                        OutlinedButton(
                                                            onClick = { viewModel.signOutGoogle(context) },
                                                            enabled = !isTranscribing,
                                                            shape = RoundedCornerShape(6.dp),
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(30.dp),
                                                            border = BorderStroke(
                                                                1.dp,
                                                                if (isTranscribing) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f) else Color(0xFF8C3235)
                                                            )
                                                        ) {
                                                            Text(
                                                                text = "Sign Out",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                color = if (isTranscribing) Color.Gray else Color(0xFFE57373)
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                Button(
                                                    onClick = { viewModel.signInWithGoogle(context) },
                                                    enabled = !viewModel.isGoogleAuthLoading,
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(30.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = previewTheme.primaryColor,
                                                        contentColor = if (previewTheme.isLightAccent) Color.Black else Color.White
                                                    )
                                                ) {
                                                    if (viewModel.isGoogleAuthLoading) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(12.dp),
                                                            strokeWidth = 2.dp,
                                                            color = Color.White
                                                        )
                                                    } else {
                                                        Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (viewModel.isLoading) {
                                        Text(
                                            text = "Account switching disabled during transcription",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    } else if (viewModel.googleAuthError != null) {
                                        Text(
                                            text = viewModel.googleAuthError!!,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f))

                        // --- App Theme Section ---
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Palette,
                                contentDescription = null,
                                tint = previewTheme.primaryColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "APP THEME",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }

                        AppThemeGrid(
                            selectedThemeId = selectedThemeId,
                            onSelectTheme = { themeId ->
                                selectedThemeId = themeId
                                viewModel.setSelectedTheme(context, themeId)
                            }
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = previewTheme.primaryColor,
                            contentColor = if (previewTheme.isLightAccent) Color.Black else Color.White
                        )
                    ) {
                        Text("DONE", fontWeight = FontWeight.Black, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
