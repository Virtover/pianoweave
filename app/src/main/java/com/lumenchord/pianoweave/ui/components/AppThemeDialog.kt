package com.lumenchord.pianoweave.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lumenchord.pianoweave.ui.theme.AppThemeManager

@Composable
fun AppThemeGrid(
    selectedThemeId: String,
    onSelectTheme: (String) -> Unit
) {
    var selectedId by remember(selectedThemeId) { mutableStateOf(selectedThemeId) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 60.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(AppThemeManager.themes) { theme ->
            val isSelected = theme.id == selectedId
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        selectedId = theme.id
                        onSelectTheme(theme.id)
                    },
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background,
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) theme.primaryColor else MaterialTheme.colorScheme.tertiaryContainer
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

@Composable
fun AppThemeDialog(
    currentThemeId: String,
    onSelectTheme: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedId by remember { mutableStateOf(currentThemeId) }
    val previewTheme = AppThemeManager.getTheme(selectedId)

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
                    .heightIn(max = 520.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF161B22),
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
                                Icons.Default.Palette,
                                contentDescription = null,
                                tint = previewTheme.primaryColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Theme Settings",
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

                    AppThemeGrid(
                        selectedThemeId = selectedId,
                        onSelectTheme = { themeId ->
                            selectedId = themeId
                            onSelectTheme(themeId)
                        }
                    )

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
