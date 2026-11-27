package com.lumenchord.pianoweave.ui.components.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.api.config.AppConfig
import com.lumenchord.pianoweave.api.config.normalizeDevServerUrl
import com.lumenchord.pianoweave.ui.components.AccentButton
import com.lumenchord.pianoweave.ui.components.IconBadge
import com.lumenchord.pianoweave.ui.components.SectionHeader
import com.lumenchord.pianoweave.ui.components.SettingLabel
import com.lumenchord.pianoweave.ui.components.quietFieldColors
import com.lumenchord.pianoweave.ui.theme.AppTheme
import com.lumenchord.pianoweave.ui.viewmodel.DebugServerResult
import com.lumenchord.pianoweave.ui.theme.PillShape
import com.lumenchord.pianoweave.ui.theme.goldLeaf
import com.lumenchord.pianoweave.ui.theme.keyButtonContainer
import com.lumenchord.pianoweave.ui.theme.onKeyButton

/**
 * Debug builds only: which server is "the official one" (the one that gets the Google token, shows its
 * announcements and name): the real one, or a dev server with its own address, display name and optional password.
 * One card with a Real / Dev choice and a single Save, which stays off until something changed and the address is
 * valid; the dev fields fold away while Real is chosen. The saved values come in as [enabled], [url], [name] and
 * [password]; [onApply] saves and reconnects.
 */
@Composable
internal fun DebugServerCard(
    previewTheme: AppTheme,
    enabled: Boolean,
    url: String,
    name: String,
    password: String,
    onApply: (enabled: Boolean, url: String, name: String, password: String) -> DebugServerResult
) {
    val context = LocalContext.current
    var useDev by remember(enabled) { mutableStateOf(enabled) }
    var urlText by remember(url) { mutableStateOf(url) }
    var nameText by remember(name) { mutableStateOf(name) }
    var passwordText by remember(password) { mutableStateOf(password) }
    var showPassword by remember { mutableStateOf(false) }

    val normalized = normalizeDevServerUrl(urlText)
    val urlError = when {
        !useDev -> null
        urlText.isBlank() -> "Enter the dev server's address"
        normalized == null -> "Not a valid address"
        else -> null
    }
    val changed = useDev != enabled ||
        (useDev && (urlText.trim() != url.trim() || nameText.trim() != name.trim() || passwordText != password))
    val canSave = changed && urlError == null

    // What the app treats as the official server now (the saved choice), as a short line
    val target = remember(enabled, url, name) {
        AppConfig.getConfigOrNull()?.let { "${it.name} · ${it.baseUrl.removePrefix("https://").removeSuffix("/")}" } ?: "Not loaded"
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader("Official server")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Default.Dns, tint = previewTheme.primaryColor, size = 32.dp)
                    SettingLabel(
                        title = if (enabled) "Using the dev server" else "Using the real server",
                        subtitle = target,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    )
                }

                ServerChoice(useDev = useDev, accent = previewTheme.primaryColor, onChange = { useDev = it })

                AnimatedVisibility(visible = useDev) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = urlText,
                            onValueChange = { urlText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Address") },
                            placeholder = { Text("https://dev.example.com/") },
                            singleLine = true,
                            isError = urlError != null,
                            supportingText = { Text(urlError ?: "https:// is added if missing; type http:// for a local server") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                            shape = RoundedCornerShape(12.dp),
                            colors = quietFieldColors()
                        )
                        OutlinedTextField(
                            value = nameText,
                            onValueChange = { nameText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Display name") },
                            placeholder = { Text("Official server name (dev)") },
                            singleLine = true,
                            supportingText = { Text("Shown where the official server's name is; optional") },
                            shape = RoundedCornerShape(12.dp),
                            colors = quietFieldColors()
                        )
                        OutlinedTextField(
                            value = passwordText,
                            onValueChange = { passwordText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Password (optional)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showPassword) "Hide password" else "Show password",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            supportingText = { Text("Sent to the dev server alone; the real server's password never is") },
                            shape = RoundedCornerShape(12.dp),
                            colors = quietFieldColors()
                        )
                    }
                }

                AccentButton(
                    onClick = {
                        val message = when (onApply(useDev, urlText, nameText, passwordText)) {
                            DebugServerResult.Saved ->
                                if (useDev) "Saved. Using the dev server ${normalized.orEmpty().removeSuffix("/")}"
                                else "Saved. Using the real official server"
                            DebugServerResult.Busy -> "Not saved: a transcription is running. Finish or cancel it first."
                            DebugServerResult.NotDebugBuild -> "Not saved: only debug builds have this option."
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    },
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .goldLeaf(previewTheme),
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = previewTheme.keyButtonContainer,
                        contentColor = if (previewTheme.fill != null) previewTheme.onKeyButton
                        else if (previewTheme.isLightAccent) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(if (changed) "Save and reconnect" else "Saved", fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
            }
        }
    }
}

/** Real / Dev as two pills side by side; the chosen one is filled with the accent's wash. */
@Composable
private fun ServerChoice(useDev: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf(false to "Real", true to "Dev").forEach { (dev, label) ->
            val selected = useDev == dev
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(PillShape)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onChange(dev) }),
                shape = PillShape,
                color = if (selected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                border = BorderStroke(
                    1.dp,
                    if (selected) accent.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
                )
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = if (selected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}
