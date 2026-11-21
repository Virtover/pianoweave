package com.lumenchord.pianoweave.ui.components

import com.lumenchord.pianoweave.ui.theme.onKeyButton
import com.lumenchord.pianoweave.ui.theme.keyButtonContainer
import com.lumenchord.pianoweave.ui.theme.goldLeaf
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lumenchord.pianoweave.ui.theme.AppTheme
import com.lumenchord.pianoweave.ui.theme.AppThemeManager
import com.lumenchord.pianoweave.ui.theme.Ink
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppThemeGrid(
    selectedThemeId: String,
    onSelectTheme: (String) -> Unit,
    modifier: Modifier = Modifier,
    light: LightThemeControl? = null
) {
    var selectedId by remember(selectedThemeId) { mutableStateOf(AppThemeManager.resolveId(selectedThemeId) ?: selectedThemeId) }
    // Shown once, right as the light is found
    var justUnlocked by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (light != null && light.isUnlocked) {
            DayNightToggle(isLight = AppThemeManager.isLight, onSetLight = light.onSetLight)
            if (justUnlocked) {
                Text(
                    text = "\u201CLet there be light,\u201D and there was light.",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = LocalAppTheme.current.primaryColor,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
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
                    color = Ink.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 2.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    themes.forEach { theme ->
                        val select = {
                            selectedId = theme.id
                            onSelectTheme(theme.id)
                        }
                        ThemeTile(
                            theme = theme,
                            isSelected = theme.id == selectedId,
                            onClick = select,
                            // Hidden: holding the lamp long enough brings the daylight (once found, it's just a tap)
                            onHold = if (light != null && !light.isUnlocked && theme.id == LIGHT_KEY_THEME_ID) {
                                {
                                    select()
                                    if (light.onUnlock()) justUnlocked = true
                                }
                            } else null
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
    onClick: () -> Unit,
    onHold: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier
            .width(60.dp)
            .clip(shape)
            .then(
                if (onHold != null) Modifier.clickableWithLongHold(holdMillis = LIGHT_UNLOCK_HOLD_MILLIS, onHold = onHold, onClick = onClick)
                else Modifier.clickable(onClickLabel = "Use ${theme.name}", onClick = onClick)
            ),
        shape = shape,
        color = theme.palette.surface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) theme.primaryColor else Ink.copy(alpha = 0.1f)
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
                    // A day theme shows its gold leaf, as its buttons will look; a night theme its accent
                    .then(if (theme.fill != null) Modifier.goldLeaf(theme, CircleShape) else Modifier.background(theme.primaryColor, CircleShape)),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = theme.onKeyButton,
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

/** The light themes in the picker: whether they were found, finding them, and choosing Dark or Light. */
class LightThemeControl(
    val isUnlocked: Boolean,
    /** Called when Lamplight is held long enough; returns whether this unlocked it just now. */
    val onUnlock: () -> Boolean,
    val onSetLight: (Boolean) -> Unit
)

/** Hold the Lamplight tile this long to find the light themes: "Your word is a lamp... and a light" (Psalm 119:105). */
private const val LIGHT_UNLOCK_HOLD_MILLIS = 5_000L
private const val LIGHT_KEY_THEME_ID = "lamplight"

/** Night or day: the whole app in the dark themes or in the light ones. */
@Composable
private fun DayNightToggle(isLight: Boolean, onSetLight: (Boolean) -> Unit) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Ink.copy(alpha = 0.12f), PillShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        listOf(false to "Dark", true to "Light").forEach { (light, label) ->
            val selected = light == isLight
            val content = if (selected) theme.onKeyButton else Ink.copy(alpha = 0.7f)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(PillShape)
                    .then(if (selected && theme.fill != null) Modifier.goldLeaf(theme) else Modifier.background(if (selected) theme.primaryColor else Color.Transparent))
                    .clickable(onClickLabel = "Use the ${label.lowercase()} themes") { onSetLight(light) }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (light) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(label, color = content, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun AppThemeDialog(
    currentThemeId: String,
    onSelectTheme: (String) -> Unit,
    onDismiss: () -> Unit,
    light: LightThemeControl? = null
) {
    var selectedId by remember(currentThemeId) { mutableStateOf(currentThemeId) }
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
                contentColor = Ink,
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
                                modifier = Modifier.fillMaxWidth(),
                                light = light
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Fixed DONE button pinned at the bottom
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .goldLeaf(previewTheme),
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = previewTheme.keyButtonContainer,
                            contentColor = previewTheme.onKeyButton
                        )
                    ) {
                        Text(
                            "Done",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = previewTheme.onKeyButton
                        )
                    }
                }
            }
        }
    }
}
