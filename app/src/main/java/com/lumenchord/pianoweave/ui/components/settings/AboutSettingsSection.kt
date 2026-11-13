package com.lumenchord.pianoweave.ui.components.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.ui.components.TermsChangeDialog
import com.lumenchord.pianoweave.ui.theme.AppTheme
import com.lumenchord.pianoweave.util.LegalChanges
import com.lumenchord.pianoweave.util.AppLinks
import com.lumenchord.pianoweave.util.isDebuggable
import com.lumenchord.pianoweave.util.openUrl
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun AboutSettingsSection(
    context: Context,
    previewTheme: AppTheme,
    discordLink: String?,
    isSignedIn: Boolean,
    onOpenWalkthrough: () -> Unit,
    onSimulateUpdate: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "ABOUT",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // In-app, so it leads with an arrow rather than the open-in-browser mark
        LinkRow(
            icon = Icons.Default.AutoStories,
            title = "How it works",
            subtitle = "From a link to playing along, in three steps",
            previewTheme = previewTheme,
            onClick = onOpenWalkthrough,
            trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight
        )

        if (!discordLink.isNullOrBlank()) {
            LinkRow(
                icon = Icons.Default.Forum,
                title = "Join our Discord",
                subtitle = "Community, help and feedback",
                previewTheme = previewTheme,
                onClick = { context.openUrl(discordLink) }
            )
        }

        LinkRow(
            icon = Icons.Default.Feedback,
            title = "Suggestions & Bug Reports",
            subtitle = "Share an idea or tell us what went wrong",
            previewTheme = previewTheme,
            onClick = { context.openUrl(AppLinks.SUGGESTIONS_AND_BUG_REPORTS) }
        )

        LinkRow(
            icon = Icons.Default.PrivacyTip,
            title = "Privacy Policy",
            subtitle = "What data is used and how",
            previewTheme = previewTheme,
            onClick = { context.openUrl(AppLinks.PRIVACY_POLICY) }
        )

        LinkRow(
            icon = Icons.Default.Gavel,
            title = "Terms of Service",
            subtitle = "Rules for using the app and credits",
            previewTheme = previewTheme,
            onClick = { context.openUrl(AppLinks.TERMS_OF_SERVICE) }
        )

        // Debug builds only: runs the in-app update flow against a fake Play (see AppUpdates.simulate)
        if (context.isDebuggable) {
            LinkRow(
                icon = Icons.Default.SystemUpdate,
                title = "Simulate app update (debug)",
                subtitle = "Fake Play update: download, then Restart prompt",
                previewTheme = previewTheme,
                onClick = onSimulateUpdate,
                trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight
            )
        }

        // Debug builds only: shows the Terms / Privacy change notice with made-up dates, a different case each tap.
        // Nothing is stored, so the real notice still comes when a change is announced.
        if (context.isDebuggable) {
            var previewCase by remember { mutableIntStateOf(0) }
            var previewChanges by remember { mutableStateOf<LegalChanges?>(null) }
            LinkRow(
                icon = Icons.Default.Gavel,
                title = "Preview legal change notice (debug)",
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
        }

        if (isSignedIn) {
            // Kept deliberately low-key; the actual deletion happens on the web page.
            TextButton(
                onClick = { context.openUrl(AppLinks.ACCOUNT_DELETION) },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                modifier = Modifier
                    .align(Alignment.End)
                    .height(28.dp)
            ) {
                Text(
                    text = "Delete account & data",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private val LEGAL_PREVIEW_CASES = listOf("Terms only", "Privacy Policy only", "both, same date", "both, different dates")

/** Made-up announced versions (14 and 21 days ahead) for each of [LEGAL_PREVIEW_CASES], in turn. */
private fun legalPreviewChanges(case: Int): LegalChanges {
    val soon = LocalDate.now(ZoneOffset.UTC).plusDays(14).toString()
    val later = LocalDate.now(ZoneOffset.UTC).plusDays(21).toString()
    return when (case % LEGAL_PREVIEW_CASES.size) {
        0 -> LegalChanges(terms = soon, privacyPolicy = null)
        1 -> LegalChanges(terms = null, privacyPolicy = soon)
        2 -> LegalChanges(terms = soon, privacyPolicy = soon)
        else -> LegalChanges(terms = later, privacyPolicy = soon)
    }
}

/** A tappable row with an icon, a title and a one-line subtitle, leading somewhere else (in or out of the app). */
@Composable
internal fun LinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    previewTheme: AppTheme,
    onClick: () -> Unit,
    trailingIcon: ImageVector = Icons.AutoMirrored.Filled.OpenInNew
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = previewTheme.primaryColor,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
