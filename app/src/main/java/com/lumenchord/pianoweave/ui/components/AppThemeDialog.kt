package com.lumenchord.pianoweave.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import com.lumenchord.pianoweave.ui.theme.PillShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.lumenchord.pianoweave.ui.theme.AppTheme
import com.lumenchord.pianoweave.ui.theme.AppThemeManager

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppThemeGrid(
    selectedThemeId: String,
    onSelectTheme: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedId by remember(selectedThemeId) { mutableStateOf(AppThemeManager.resolveId(selectedThemeId) ?: selectedThemeId) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppThemeManager.themeGroups.forEach { (feeling, themes) ->
            Column(
                modifier = Modifier.animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = feeling.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 2.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    themes.forEach { theme ->
                        ThemeTile(
                            theme = theme,
                            isSelected = theme.id == selectedId,
                            onClick = {
                                selectedId = theme.id
                                onSelectTheme(theme.id)
                            }
                        )
                    }
                }
                themes.firstOrNull { it.id == selectedId }?.let { ThemeName(it) }
            }
        }
    }
}

/** The selected theme's name, under its own row, so a pick is named where the eye already is. */
@Composable
private fun ThemeName(theme: AppTheme) {
    Text(
        text = theme.name,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = theme.primaryColor,
        modifier = Modifier.padding(start = 2.dp)
    )
}

/**
 * One theme as a small piece of itself: its own backdrop, its accent, and beneath it the keyboard line in
 * its baseline color, so themes with close accents still differ by the room they make.
 */
@Composable
private fun ThemeTile(
    theme: AppTheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier
            .width(60.dp)
            .clip(shape)
            .clickable(onClickLabel = "Use ${theme.name}", onClick = onClick),
        shape = shape,
        color = theme.palette.surface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) theme.primaryColor else Color.White.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(top = 9.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(theme.primaryColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = if (theme.isLightAccent) Color.Black else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(width = 34.dp, height = 3.dp)
                    .background(theme.baselineColor, PillShape)
            )
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
                .padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            DialogSurface(
                light = previewTheme.primaryColor,
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.88f),
                shape = RoundedCornerShape(20.dp),
                color = previewTheme.palette.dialogSurface,
                contentColor = Color.White,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header & Grid section takes remaining height
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        DialogHeader(
                            title = "Theme Settings",
                            icon = Icons.Default.Palette,
                            onClose = onDismiss,
                            titleColor = previewTheme.primaryColor
                        )
                        Spacer(Modifier.height(14.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AppThemeGrid(
                                selectedThemeId = selectedId,
                                onSelectTheme = { themeId ->
                                    selectedId = themeId
                                    onSelectTheme(themeId)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Fixed DONE button pinned at the bottom
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = previewTheme.primaryColor,
                            contentColor = if (previewTheme.isLightAccent) Color.Black else Color.White
                        )
                    ) {
                        Text(
                            "Done",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = if (previewTheme.isLightAccent) Color.Black else Color.White
                        )
                    }
                }
            }
        }
    }
}
