package com.lumenchord.pianoweave.ui.components

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lumenchord.pianoweave.api.MaintenanceTexts
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.theme.Moods
import com.lumenchord.pianoweave.ui.theme.PillShape
import com.lumenchord.pianoweave.ui.viewmodel.ServerConnectionProblem
import com.lumenchord.pianoweave.ui.viewmodel.formatClockTime

private enum class ConnectionAction { Retry, InternetSettings, ServerSettings, Close }

private data class ConnectionDialogText(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val primary: ConnectionAction,
    val secondary: ConnectionAction
)

/**
 * Shown when the app opens and the server can't be used, worded for [problem]: offline, server down,
 * maintenance, or a missing password. Closes by itself once a later check reaches the server.
 */
@Composable
fun ServerConnectionDialog(
    problem: ServerConnectionProblem,
    isCustomServer: Boolean,
    isChecking: Boolean,
    onRetry: () -> Unit,
    onOpenServerSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appTheme = LocalAppTheme.current
    val colorSlate = MaterialTheme.colorScheme.tertiaryContainer
    val text = dialogText(problem, isCustomServer, context)
    // Something to wait out is the watchman's lamp; something broken is lament
    val mood = when (problem) {
        is ServerConnectionProblem.Maintenance, ServerConnectionProblem.PasswordRequired -> Moods.Watch.core
        else -> Moods.Lament.core
    }

    fun run(action: ConnectionAction) {
        when (action) {
            ConnectionAction.Retry -> onRetry()
            ConnectionAction.InternetSettings -> openInternetSettings(context)
            ConnectionAction.ServerSettings -> {
                onDismiss()
                onOpenServerSettings()
            }
            ConnectionAction.Close -> onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            DialogSurface(
                light = mood,
                modifier = Modifier
                    .fillMaxWidth(0.98f)
                    .widthIn(max = 460.dp)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, colorSlate)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DialogHeader(
                        title = text.title,
                        icon = text.icon,
                        onClose = onDismiss,
                        titleColor = mood
                    )

                    Text(
                        text = text.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    )

                    Text(
                        text = "Songs in your Library still play without it." +
                            if (problem != ServerConnectionProblem.PasswordRequired) " The app keeps trying and this closes once it's connected." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { run(text.secondary) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 46.dp),
                            shape = PillShape,
                            border = BorderStroke(1.dp, colorSlate),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = actionLabel(text.secondary),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        }

                        val primaryBusy = isChecking && text.primary == ConnectionAction.Retry
                        Button(
                            onClick = { run(text.primary) },
                            enabled = !primaryBusy,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 46.dp),
                            shape = PillShape,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = appTheme.primaryColor,
                                contentColor = if (appTheme.isLightAccent) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            if (primaryBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                Text(
                                    text = actionLabel(text.primary),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun dialogText(problem: ServerConnectionProblem, isCustomServer: Boolean, context: Context): ConnectionDialogText =
    when (problem) {
        ServerConnectionProblem.NoInternet -> ConnectionDialogText(
            icon = Icons.Default.WifiOff,
            title = "You're offline",
            body = "Piano Weave needs the internet to turn videos into notes. Turn on Wi-Fi or mobile data.",
            primary = ConnectionAction.InternetSettings,
            secondary = ConnectionAction.Retry
        )
        is ServerConnectionProblem.Unreachable -> ConnectionDialogText(
            icon = Icons.Default.CloudOff,
            title = if (isCustomServer) "Can't reach your server" else "Can't reach the server",
            body = problem.detail ?: if (isCustomServer) {
                "Your server isn't answering. Make sure it's running and its address in Server Settings is right."
            } else {
                "The server isn't answering right now. It may be down for a short while. Please try again in a moment."
            },
            primary = ConnectionAction.Retry,
            secondary = if (isCustomServer) ConnectionAction.ServerSettings else ConnectionAction.Close
        )
        is ServerConnectionProblem.Maintenance -> {
            val nowSec = System.currentTimeMillis() / 1000
            val formatTime: (Long) -> String = { formatClockTime(context, it) }
            ConnectionDialogText(
                icon = if (problem.notice.isShutdown) Icons.Default.PowerSettingsNew else Icons.Default.RestartAlt,
                title = MaintenanceTexts.title(problem.notice, nowSec, formatTime),
                body = MaintenanceTexts.detail(problem.notice, nowSec, formatTime),
                primary = ConnectionAction.Retry,
                secondary = ConnectionAction.Close
            )
        }
        ServerConnectionProblem.PasswordRequired -> ConnectionDialogText(
            icon = Icons.Default.Lock,
            title = if (isCustomServer) "Server password needed" else "Official server is locked",
            body = if (isCustomServer) {
                "This server is protected by a password. Enter it in Server Settings."
            } else {
                "The official server can't be used from this version of the app. Update Piano Weave, or pick another server in Server Settings."
            },
            primary = ConnectionAction.ServerSettings,
            secondary = ConnectionAction.Close
        )
    }

private fun actionLabel(action: ConnectionAction): String = when (action) {
    ConnectionAction.Retry -> "Try again"
    ConnectionAction.InternetSettings -> "Internet settings"
    ConnectionAction.ServerSettings -> "Server settings"
    ConnectionAction.Close -> "Close"
}

/** The system's Wi-Fi / mobile data panel, or the wireless settings page before Android 10. */
private fun openInternetSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
    } else {
        Intent(Settings.ACTION_WIRELESS_SETTINGS)
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
