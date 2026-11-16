package com.lumenchord.pianoweave.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumenchord.pianoweave.api.DeveloperServerState
import com.lumenchord.pianoweave.ui.components.settings.DeveloperLookChoice
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.viewmodel.DeveloperJobSnapshot
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** How often the open panel asks the server for its state again. */
private const val REFRESH_MS = 5_000L

/**
 * The developer panel, for an account the server's administrator made a developer's: the server's live queues and
 * workers, refreshed while open, and what the server reported about the latest job. Opened from the "∞ DEV" pill.
 */
@Composable
fun DeveloperPanelDialog(viewModel: PianoWeaveViewModel, context: Context) {
    val accent = LocalAppTheme.current.primaryColor
    val slate = MaterialTheme.colorScheme.tertiaryContainer
    val close = { viewModel.dismissDeveloperPanel() }
    // Set by a tap on Refresh, cleared when that load ends
    var manualRefresh by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel.isLoadingDeveloperServerState) {
        if (!viewModel.isLoadingDeveloperServerState) manualRefresh = false
    }

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshDeveloperServerState(context)
            delay(REFRESH_MS)
        }
    }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            DialogSurface(
                light = accent,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(0.98f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, slate)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    DialogHeader(title = "Developer", icon = Icons.Rounded.Code, onClose = close, titleColor = accent)

                    Text(
                        text = "${viewModel.googleUserEmail} · unlimited credits on this server. " +
                            "Jobs are never charged and not held to the per-user limit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    PanelSection(
                        title = "SERVER",
                        trailing = {
                            // The 5 s refresh is silent; only a tap shows the spinner, so nothing blinks while open.
                            // A fixed box keeps the header still when it swaps.
                            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                if (manualRefresh && viewModel.isLoadingDeveloperServerState) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = accent)
                                } else {
                                    IconButton(
                                        onClick = {
                                            manualRefresh = true
                                            viewModel.refreshDeveloperServerState(context)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = accent, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    ) {
                        FactRow("Address", viewModel.activeServerUrl, copy = { copy(context, "Server", viewModel.activeServerUrl) })
                        viewModel.developerServerError?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        val state = viewModel.developerServerState
                        if (state != null) {
                            ServerFacts(state)
                        } else if (viewModel.developerServerError == null) {
                            FactRow("State", "Loading…")
                        }
                    }

                    PanelSection(title = "LATEST JOB") {
                        val job = viewModel.developerLastJob
                        if (job == null) {
                            Text(
                                "Transcribe something: what the server reports about it shows here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            JobFacts(job, copyId = { copy(context, "Job ID", job.jobId) })
                        }
                    }

                    DeveloperLookChoice(
                        developerLook = viewModel.isDeveloperBackgroundEnabled,
                        onChange = { viewModel.setDeveloperBackgroundEnabled(context, it) },
                        accent = accent
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerFacts(state: DeveloperServerState) {
    val limit = state.maxActiveJobs?.let { " of $it" } ?: ""
    FactRow("Active jobs", "${state.activeJobs}$limit")
    state.backends.orEmpty().toSortedMap().forEach { (name, backend) ->
        val wait = if (backend.waiting > 0) ", oldest waits ${duration(backend.oldestWaitSeconds.toDouble())}" else ""
        FactRow(if (name == "remote") "RunPod" else name.replaceFirstChar { it.uppercase() },
            "${backend.running} running, ${backend.waiting} waiting$wait")
    }
    val workers = state.workers
    FactRow(
        "GPU workers",
        if (workers == null) "none alive"
        else buildString {
            append("${workers.count} (${workers.capacity} jobs at once")
            if (workers.arranging > 0) append(", ${workers.arranging} arranging")
            append(")")
            workers.averageJobSeconds?.let { append(", ~${duration(it.toDouble())} a job") }
        }
    )
    FactRow("Outsourcing", onOff(state.outsourcing))
    FactRow("Arrangements", onOff(state.arrangement))
    if (state.paused == true) FactRow("Submissions", "PAUSED by an administrator", warn = true)
    if (state.linksOff == true) FactRow("Video links", "OFF by an administrator", warn = true)
    state.maintenance?.takeIf { it.isValid }?.let { notice ->
        val inSeconds = notice.at - System.currentTimeMillis() / 1000
        FactRow(
            if (notice.isShutdown) "Shutdown" else "Restart",
            if (inSeconds > 0) "in ${duration(inSeconds.toDouble())}" else "under way",
            warn = true
        )
    }
}

@Composable
private fun JobFacts(job: DeveloperJobSnapshot, copyId: () -> Unit) {
    FactRow("Job", job.jobId, mono = true, copy = copyId)
    FactRow("Status", "${job.status} · ${(job.progress * 100).roundToInt()}%")
    job.mode?.let { FactRow("Mode", it) }
    val details = job.details
    val outsourced = details?.outsourced == true
    FactRow(
        "Ran on",
        when {
            job.backend == "remote" -> if (outsourced) "RunPod (moved there)" else "RunPod"
            job.backend != null -> "this server's GPU"
            else -> "not chosen yet"
        }
    )
    details?.requeueReason?.let { FactRow("Moved because", it) }
    job.errorCode?.let { FactRow("Error code", it, mono = true, warn = true) }
    if (details == null) return
    details.durationSeconds?.let { FactRow("Audio", duration(it.toDouble())) }
    details.costMinutes?.let { FactRow("Would cost", "$it credits (not charged)") }
    val queued = details.queuedAt ?: details.createdAt?.toDouble()
    val started = details.startedAt?.toDouble()
    val finished = details.finishedAt?.toDouble()
    if (queued != null && started != null) FactRow("Waited", duration(started - queued))
    if (started != null && finished != null) FactRow("Took", duration(finished - started))
}

@Composable
private fun PanelSection(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            trailing?.invoke()
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
        }
    }
}

@Composable
private fun FactRow(label: String, value: String, mono: Boolean = false, warn: Boolean = false, copy: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (copy != null) Modifier.clickable(onClick = copy) else Modifier),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(112.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = if (mono) FontFamily.Monospace else null),
            fontSize = if (mono) 11.sp else 12.sp,
            color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun onOff(value: Boolean?): String = when (value) {
    true -> "on"
    false -> "off"
    null -> "unknown"
}

/** "42 s", "3 min 5 s", "1 h 2 min". */
private fun duration(seconds: Double): String {
    val total = seconds.coerceAtLeast(0.0).roundToInt()
    val hours = total / 3600
    val minutes = total % 3600 / 60
    val rest = total % 60
    return when {
        hours > 0 -> "$hours h $minutes min"
        minutes > 0 -> "$minutes min $rest s"
        else -> "$rest s"
    }
}

private fun copy(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
}
