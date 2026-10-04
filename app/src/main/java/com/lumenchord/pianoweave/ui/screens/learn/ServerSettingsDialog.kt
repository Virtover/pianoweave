package com.lumenchord.pianoweave.ui.screens.learn

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
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
import com.lumenchord.pianoweave.api.config.AppConfig
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import com.lumenchord.pianoweave.ui.viewmodel.ServerStatus

@Composable
internal fun ServerSettingsDialog(
    viewModel: PianoWeaveViewModel,
    context: Context,
    onDismiss: () -> Unit
) {
    var selectedUseCustom by remember { mutableStateOf(viewModel.isCustomServer) }
    var customUrlText by remember { mutableStateOf(viewModel.customServerUrl) }
    var customClientIdText by remember { mutableStateOf(viewModel.customGoogleClientId) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var dialogTestStatus by remember { mutableStateOf(ServerStatus.CHECKING) }

    val ColorSurface = MaterialTheme.colorScheme.surface
    val ColorGold = MaterialTheme.colorScheme.secondary
    val ColorSlate = MaterialTheme.colorScheme.tertiaryContainer
    val ColorTextDim = MaterialTheme.colorScheme.tertiary

    LaunchedEffect(viewModel.isLoading) {
        if (viewModel.isLoading) {
            onDismiss()
        }
    }

    val candidateUrl = if (selectedUseCustom && customUrlText.isNotBlank()) {
        var u = customUrlText.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
        if (!u.endsWith("/")) u = "$u/"
        u
    } else {
        viewModel.defaultServerUrl
    }

    LaunchedEffect(selectedUseCustom, customUrlText) {
        if (candidateUrl.isNotBlank()) {
            dialogTestStatus = ServerStatus.CHECKING
            dialogTestStatus = viewModel.testServerConnection(candidateUrl)
        }
    }

    fun validateUrl(useCustom: Boolean, url: String): Boolean {
        if (!useCustom) {
            urlError = null
            return true
        }
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            urlError = "Custom server URL cannot be empty"
            return false
        }
        val lowercase = trimmed.lowercase()
        if (lowercase.contains("://") && !lowercase.startsWith("http://") && !lowercase.startsWith("https://")) {
            urlError = "Must use http:// or https://"
            return false
        }
        urlError = null
        return true
    }

    fun updateSettings(useCustom: Boolean, url: String, clientId: String) {
        selectedUseCustom = useCustom
        customUrlText = url
        customClientIdText = clientId
        validateUrl(useCustom, url)
        viewModel.updateServerSettings(context, useCustom, url, clientId)
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .heightIn(max = 620.dp),
                shape = RoundedCornerShape(20.dp),
                color = ColorSurface,
                contentColor = Color.White,
                border = BorderStroke(1.dp, ColorSlate)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Server Settings",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = ColorGold
                            )
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = ColorTextDim
                                )
                            }
                        }

                        HorizontalDivider(color = ColorSlate.copy(alpha = 0.6f))

                        Text(
                            text = "Select the backend server used for transcribing piano audio/video links into MIDI.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            lineHeight = 18.sp
                        )

                        // --- Default Server Card ---
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Surface(
                                onClick = {
                                    updateSettings(false, customUrlText, customClientIdText)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = if (!selectedUseCustom) ColorSlate.copy(alpha = 0.4f) else ColorSurface,
                                border = BorderStroke(
                                    1.dp,
                                    if (!selectedUseCustom) ColorGold else ColorSlate
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = !selectedUseCustom,
                                        onClick = {
                                            updateSettings(false, customUrlText, customClientIdText)
                                        },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = ColorGold,
                                            unselectedColor = ColorTextDim
                                        )
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = AppConfig.getConfig().name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color.White
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = viewModel.defaultServerUrl.ifBlank { "Configured in assets/config/config.txt" },
                                            fontSize = 12.sp,
                                            color = ColorTextDim,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // --- Custom Server Card ---
                        Surface(
                            onClick = {
                                updateSettings(true, customUrlText, customClientIdText)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedUseCustom) ColorSlate.copy(alpha = 0.4f) else ColorSurface,
                            border = BorderStroke(
                                1.dp,
                                if (selectedUseCustom) ColorGold else ColorSlate
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedUseCustom,
                                        onClick = {
                                            updateSettings(true, customUrlText, customClientIdText)
                                        },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = ColorGold,
                                            unselectedColor = ColorTextDim
                                        )
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Custom Server",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "Connect to custom server instance",
                                            fontSize = 12.sp,
                                            color = ColorTextDim,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (selectedUseCustom) {
                                    OutlinedTextField(
                                        value = customUrlText,
                                        onValueChange = {
                                            customUrlText = it
                                            validateUrl(true, it)
                                            viewModel.updateServerSettings(context, true, it, customClientIdText)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Server URL") },
                                        placeholder = { Text("http://192.168.1.100:8000/") },
                                        singleLine = true,
                                        isError = urlError != null,
                                        supportingText = {
                                            if (urlError != null) {
                                                Text(urlError!!, color = MaterialTheme.colorScheme.error)
                                            } else {
                                                Text("E.g. http://10.0.2.2:8000/")
                                            }
                                        },
                                        trailingIcon = {
                                            if (customUrlText.isNotEmpty()) {
                                                IconButton(onClick = {
                                                    updateSettings(true, "", customClientIdText)
                                                }) {
                                                    Icon(Icons.Default.Clear, "Clear", tint = ColorTextDim)
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = ColorGold,
                                            unfocusedBorderColor = ColorSlate,
                                            focusedLabelColor = ColorGold,
                                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                                            cursorColor = ColorGold,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        )
                                    )

                                    OutlinedTextField(
                                        value = customClientIdText,
                                        onValueChange = {
                                            customClientIdText = it
                                            viewModel.updateServerSettings(context, true, customUrlText, it)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Google OAuth Client ID (Optional)") },
                                        placeholder = { Text("xxxx.apps.googleusercontent.com") },
                                        singleLine = true,
                                        supportingText = {
                                            Text("Required if Google Play billing and OAuth auth are enabled")
                                        },
                                        trailingIcon = {
                                            if (customClientIdText.isNotEmpty()) {
                                                IconButton(onClick = {
                                                    updateSettings(true, customUrlText, "")
                                                }) {
                                                    Icon(Icons.Default.Clear, "Clear", tint = ColorTextDim)
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = ColorGold,
                                            unfocusedBorderColor = ColorSlate,
                                            focusedLabelColor = ColorGold,
                                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                                            cursorColor = ColorGold,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        )
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = ColorSlate.copy(alpha = 0.6f))

                        // Active summary & Health indicator
                        val (dotColor, statusText) = when (dialogTestStatus) {
                            ServerStatus.ONLINE -> Color(0xFF4CAF50) to "Online"
                            ServerStatus.OFFLINE -> Color(0xFFEF5350) to "Offline"
                            ServerStatus.CHECKING -> Color(0xFFFFA726) to "Checking..."
                            ServerStatus.UNKNOWN -> Color(0xFF90949F) to "Unknown"
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ColorSlate.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, ColorSlate),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Target Endpoint:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ColorTextDim
                                    )
                                    Text(
                                        text = candidateUrl,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = ColorGold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = dotColor.copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(color = dotColor, shape = CircleShape)
                                        )
                                        Text(
                                            text = statusText,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = dotColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
