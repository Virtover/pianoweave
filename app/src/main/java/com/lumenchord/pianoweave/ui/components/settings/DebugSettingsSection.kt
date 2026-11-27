package com.lumenchord.pianoweave.ui.components.settings

import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LightMode
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import com.lumenchord.pianoweave.ui.viewmodel.DebugServerResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.lumenchord.pianoweave.api.config.normalizeDevServerUrl
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumenchord.pianoweave.ui.components.TermsChangeDialog
import com.lumenchord.pianoweave.ui.theme.AppTheme
import com.lumenchord.pianoweave.util.LegalChanges
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Settings for testing, in debug builds only: the caller composes this section (its heading included) only when
 * the app is debuggable, so a release build has no trace of it.
 */
@Composable
fun DebugSettingsSection(
    context: Context,
    previewTheme: AppTheme,
    onSimulateUpdate: () -> Unit,
    isIgnoringAccountProgress: Boolean,
    onSetIgnoringAccountProgress: (Boolean) -> Unit,
    headerPillLabel: String,
    onCycleHeaderPill: () -> Unit,
    isLightThemeUnlocked: Boolean,
    onResetLightTheme: () -> Unit,
    onResetVerseOfLight: () -> Unit,
    debugServerEnabled: Boolean,
    debugServerUrl: String,
    debugServerName: String,
    debugServerPassword: String,
    onSetDebugServer: (enabled: Boolean, url: String, name: String, password: String) -> DebugServerResult
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "DEBUG",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Debug builds only: runs the in-app update flow against a fake Play (see AppUpdates.simulate)
            LinkRow(
                icon = Icons.Default.SystemUpdate,
                title = "Simulate app update",
                subtitle = "Fake Play update: download, then Restart prompt",
                previewTheme = previewTheme,
                onClick = onSimulateUpdate,
                trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight
            )

        // Debug builds only: shows the Terms / Privacy change notice with made-up dates, a different case each tap.
        // Nothing is stored, so the real notice still comes when a change is announced.
            var previewCase by remember { mutableIntStateOf(0) }
            var previewChanges by remember { mutableStateOf<LegalChanges?>(null) }
            LinkRow(
                icon = Icons.Default.Gavel,
                title = "Preview legal change notice",
                subtitle = "Next: " + LEGAL_PREVIEW_CASES[previewCase % LEGAL_PREVIEW_CASES.size],
                previewTheme = previewTheme,
                onClick = {
                    previewChanges = legalPreviewChanges(previewCase)
                    previewCase++
                },
                trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight
            )
            previewChanges?.let { changes ->
                TermsChangeDialog(changes = changes, onDismiss = { previewChanges = null }, linkToUpcomingPages = true)
            }

        // Debug builds only: tutorials, the light themes and the verse screen as a newcomer would meet them,
        // without the progress Android's backup or the Google account would bring back (see AccountProgress)
            LinkRow(
                icon = Icons.Default.School,
                title = "Ignore account progress",
                subtitle = if (isIgnoringAccountProgress) "On: as a newcomer, nothing synced. Tap to go back"
                else "Off: tutorials and unlocks from the account",
                previewTheme = previewTheme,
                onClick = { onSetIgnoringAccountProgress(!isIgnoringAccountProgress) },
                trailingIcon = if (isIgnoringAccountProgress) Icons.Default.ToggleOn else Icons.Default.ToggleOff
            )

        // Debug builds only: the "official server" (the one that gets the Google token and shows its announcements)
        // is a dev server instead of the real one; release builds never read this
            DebugServerCard(
                previewTheme = previewTheme,
                enabled = debugServerEnabled,
                url = debugServerUrl,
                name = debugServerName,
                password = debugServerPassword,
                onApply = onSetDebugServer
            )

        // Debug builds only: the Learn header's pill as the server decides, or forced to credits or to support
            LinkRow(
                icon = Icons.Default.Favorite,
                title = "Learn header pill",
                subtitle = "$headerPillLabel · tap to switch",
                previewTheme = previewTheme,
                onClick = onCycleHeaderPill,
                trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight
            )

        // Debug builds only: each hidden feature back to unfound, to find it again (on the account too)
            LinkRow(
                icon = Icons.Default.LightMode,
                title = "Reset light themes",
                subtitle = if (isLightThemeUnlocked) "Hide them again: hold Lamplight 5 s to find them" else "Hidden (not found yet)",
                previewTheme = previewTheme,
                onClick = onResetLightTheme,
                trailingIcon = Icons.Default.RestartAlt
            )
            LinkRow(
                icon = Icons.Default.AutoAwesome,
                title = "Reset Verse of Light",
                subtitle = "As never opened: hold the logo 7 s again",
                previewTheme = previewTheme,
                onClick = onResetVerseOfLight,
                trailingIcon = Icons.Default.RestartAlt
            )

    }
}

private val LEGAL_PREVIEW_CASES = listOf(
    "Terms only", "Privacy Policy only", "both, same date", "both, different dates",
    "both, already in effect", "Terms in effect, Policy upcoming"
)

/**
 * Made-up versions for each of [LEGAL_PREVIEW_CASES], in turn: announced ones 14 and 21 days ahead, and ones that
 * took effect 3 days ago (the user missed the notice period).
 */
private fun legalPreviewChanges(case: Int): LegalChanges {
    val soon = LocalDate.now(ZoneOffset.UTC).plusDays(14).toString()
    val later = LocalDate.now(ZoneOffset.UTC).plusDays(21).toString()
    val past = LocalDate.now(ZoneOffset.UTC).minusDays(3).toString()
    return when (case % LEGAL_PREVIEW_CASES.size) {
        0 -> LegalChanges(terms = soon, privacyPolicy = null)
        1 -> LegalChanges(terms = null, privacyPolicy = soon)
        2 -> LegalChanges(terms = soon, privacyPolicy = soon)
        3 -> LegalChanges(terms = later, privacyPolicy = soon)
        4 -> LegalChanges(terms = past, privacyPolicy = past)
        else -> LegalChanges(terms = past, privacyPolicy = soon)
    }
}

