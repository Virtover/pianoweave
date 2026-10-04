package com.lumenchord.pianoweave.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
                color = Color(0xFF161B22),
                contentColor = Color.White,
                border = BorderStroke(1.dp, Color(0xFF30363D))
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
                                tint = Color(0xFF8B949E)
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = Color(0xFF30363D).copy(alpha = 0.6f)
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
                            color = Color(0xFF8B949E)
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0D1117),
                            border = BorderStroke(1.dp, Color(0xFF30363D))
                        ) {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp)
                            ) {
                                val showSignOut = viewModel.isGoogleSignedIn && !viewModel.isBilledServer && !viewModel.requireGoogleAccount
                                val emailLength = viewModel.googleUserEmail.length
                                // Detect if horizontal spacing is tight for email + buttons
                                val isTightSpace = maxWidth < 220.dp || (showSignOut && maxWidth < 300.dp) || (emailLength > 20 && maxWidth < 260.dp)

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isTightSpace) {
                                        // Stacked layout: Full-width email header row, action buttons below aligned right
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
                                                    color = Color(0xFF8B949E)
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
                                                            if (isTranscribing) Color(0xFF30363D).copy(alpha = 0.4f) else Color(0xFF30363D)
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
                                                                if (isTranscribing) Color(0xFF30363D).copy(alpha = 0.4f) else Color(0xFF8C3235)
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
                                                        color = Color(0xFF8B949E)
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
                                                            if (isTranscribing) Color(0xFF30363D).copy(alpha = 0.4f) else Color(0xFF30363D)
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
                                                                if (isTranscribing) Color(0xFF30363D).copy(alpha = 0.4f) else Color(0xFF8C3235)
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
                                            color = Color(0xFF8B949E)
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

                        HorizontalDivider(color = Color(0xFF30363D).copy(alpha = 0.6f))

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
                                color = Color(0xFF8B949E)
                            )
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 60.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(AppThemeManager.themes) { theme ->
                                val isSelected = theme.id == selectedThemeId
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            selectedThemeId = theme.id
                                            viewModel.setSelectedTheme(context, theme.id)
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) Color(0xFF22272E) else Color(0xFF0D1117),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) theme.primaryColor else Color(0xFF30363D)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(theme.primaryColor, CircleShape)
                                                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                                            contentAlignment = Alignment.BottomEnd
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .background(theme.waitTargetColor, CircleShape)
                                                    .border(1.dp, Color.Black, CircleShape)
                                            )
                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color.Black.copy(alpha = 0.25f), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = if (theme.isLightAccent) Color.Black else Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
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
