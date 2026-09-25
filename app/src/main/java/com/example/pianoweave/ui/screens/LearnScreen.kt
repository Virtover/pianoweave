package com.example.pianoweave.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.pianoweave.midi.StoredMidi
import com.example.pianoweave.ui.viewmodel.PianoWeaveViewModel
import com.example.pianoweave.ui.viewmodel.ServerStatus

@Composable
fun LearnScreen(
    viewModel: PianoWeaveViewModel,
    context: Context,
    onSongSelect: (StoredMidi) -> Unit = {}
) {
    var showServerSettingsDialog by remember { mutableStateOf(false) }

    if (showServerSettingsDialog) {
        ServerSettingsDialog(
            viewModel = viewModel,
            context = context,
            onDismiss = { showServerSettingsDialog = false }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. SUCCESS HERO - High visibility practicing prompt
            AnimatedVisibility(
                visible = !viewModel.isLoading && viewModel.readySong != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                viewModel.readySong?.let { song ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Stars, 
                                null, 
                                Modifier.size(48.dp), 
                                tint = MaterialTheme.colorScheme.onSecondary
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Transcription Ready!",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onSecondary
                            )
                            Text(
                                text = song.metadata.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = { onSongSelect(song) },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onSecondary,
                                    contentColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("START PRACTICING", fontWeight = FontWeight.Black, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }

            // 2. INPUT CARD - Primary conversion entry
            val isSuccess = !viewModel.isLoading && viewModel.readySong != null
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (isSuccess) MaterialTheme.colorScheme.surface.copy(alpha = 0.5f) 
                                     else MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AI Transcription",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.secondary
                        )

                        // Single-line compact server status & settings pill
                        val (statusColor, statusLabel) = when (viewModel.serverStatus) {
                            ServerStatus.ONLINE -> Color(0xFF4CAF50) to (if (viewModel.isUsingDefaultServer) "Default" else "Custom")
                            ServerStatus.OFFLINE -> Color(0xFFEF5350) to (if (viewModel.isUsingDefaultServer) "Default" else "Custom")
                            ServerStatus.CHECKING -> Color(0xFFFFA726) to "Checking..."
                            ServerStatus.UNKNOWN -> Color(0xFF90949F) to (if (viewModel.isUsingDefaultServer) "Default" else "Custom")
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.clickable { showServerSettingsDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(color = statusColor, shape = CircleShape)
                                )

                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Server Settings",
                                    tint = Color(0xFFD4AF37), // Gold accent matching piano roll UI
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "Enter a video URL you have the right to use",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = viewModel.videoUrl,
                        onValueChange = { viewModel.updateUrl(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://www.youtube.com/watch?v=...", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        enabled = !viewModel.isLoading,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                            cursorColor = MaterialTheme.colorScheme.secondary
                        ),
                        leadingIcon = { Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.secondary) },
                        trailingIcon = {
                            if (viewModel.videoUrl.isNotEmpty() && !viewModel.isLoading) {
                                IconButton(onClick = { viewModel.updateUrl("") }) {
                                    Icon(Icons.Default.Clear, "Clear")
                                }
                            }
                        }
                    )

                    Button(
                        onClick = { viewModel.startTranscription(context) },
                        enabled = viewModel.videoUrl.isNotBlank() && !viewModel.isLoading,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        if (viewModel.isLoading) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSecondary)
                        } else {
                            Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (viewModel.isLoading) "Processing..." else "Transcribe to MIDI",
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // 3. PROGRESS CARD - Active loading feedback
            AnimatedVisibility(visible = viewModel.isLoading || viewModel.status.contains("Error")) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        val isError = viewModel.status.contains("Error")
                        Icon(
                            imageVector = if (isError) Icons.Default.Error else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Status", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (viewModel.progress > 0f && !isError) {
                                    Text("${(viewModel.progress * 100).toInt()}%", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                            Text(viewModel.status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 2)
                            if (viewModel.isLoading && viewModel.progress < 1f) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { viewModel.progress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.secondary
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
fun ServerSettingsDialog(
    viewModel: PianoWeaveViewModel,
    context: Context,
    onDismiss: () -> Unit
) {
    var selectedUseCustom by remember { mutableStateOf(viewModel.isCustomServer) }
    var customUrlText by remember { mutableStateOf(viewModel.customServerUrl) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var dialogTestStatus by remember { mutableStateOf(ServerStatus.CHECKING) }

    val ColorSurface = Color(0xFF161B22)
    val ColorGold = Color(0xFFD4AF37)
    val ColorSlate = Color(0xFF30363D)
    val ColorTextDim = Color(0xFF8B949E)

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
                                    Icons.Default.Close,
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
                                    selectedUseCustom = false
                                    validateUrl(false, customUrlText)
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
                                            selectedUseCustom = false
                                            validateUrl(false, customUrlText)
                                        },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = ColorGold,
                                            unselectedColor = ColorTextDim
                                        )
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "PianoWeave Cloud",
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
                                selectedUseCustom = true
                                validateUrl(true, customUrlText)
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
                                            selectedUseCustom = true
                                            validateUrl(true, customUrlText)
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
                                                    customUrlText = ""
                                                    validateUrl(true, "")
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

                        Spacer(Modifier.height(4.dp))

                        Button(
                            onClick = {
                                if (validateUrl(selectedUseCustom, customUrlText)) {
                                    viewModel.updateServerSettings(context, selectedUseCustom, customUrlText)
                                    onDismiss()
                                }
                            },
                            enabled = !selectedUseCustom || (customUrlText.isNotBlank() && urlError == null),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ColorGold,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("SAVE", fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
