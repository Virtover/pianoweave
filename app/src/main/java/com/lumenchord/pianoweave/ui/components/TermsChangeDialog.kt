package com.lumenchord.pianoweave.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.theme.PillShape
import com.lumenchord.pianoweave.util.AppLinks
import com.lumenchord.pianoweave.util.LegalChanges
import com.lumenchord.pianoweave.util.openUrl
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Advance notice of new Terms and/or a new Privacy Policy ([changes], effective dates in ISO), in one dialog when
 * both change. The Terms promise this notice in the App at least 14 days ahead, so it is shown once per version;
 * any way of closing it counts as seen. The text stays general because a change can be announced without an app
 * release; each document's page lists what changes.
 */
@Composable
fun TermsChangeDialog(
    changes: LegalChanges,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appTheme = LocalAppTheme.current
    val colorGold = appTheme.primaryColor
    val colorSurface = MaterialTheme.colorScheme.surface
    val colorSlate = MaterialTheme.colorScheme.tertiaryContainer
    // The Terms page has both languages; open the user's one first. The Privacy Policy is in English only.
    val termsUrl = changes.terms?.let { AppLinks.termsVersion(it, polish = Locale.getDefault().language == "pl") }
    val privacyUrl = changes.privacyPolicy?.let { AppLinks.privacyVersion(it) }
    val both = termsUrl != null && privacyUrl != null
    val title = when {
        both -> "Terms and Privacy Update"
        termsUrl != null -> "Terms of Service Update"
        else -> "Privacy Policy Update"
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            DialogSurface(
                light = colorGold,
                modifier = Modifier
                    .fillMaxWidth(0.98f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = colorSurface,
                border = BorderStroke(1.dp, colorSlate)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DialogHeader(
                        title = title,
                        icon = Icons.Default.Gavel,
                        onClose = onDismiss,
                        titleColor = colorGold
                    )

                    Text(
                        text = changeNoticeText(changes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    )

                    Spacer(Modifier.height(4.dp))

                    // With both documents the two read buttons share the row and "Got It" goes below them
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val cell = Modifier.weight(1f).fillMaxHeight()
                        termsUrl?.let { url ->
                            ReadButton("Read New Terms", colorSlate, cell) { context.openUrl(url) }
                        }
                        privacyUrl?.let { url ->
                            ReadButton(if (both) "Read New Policy" else "Read New Privacy Policy", colorSlate, cell) {
                                context.openUrl(url)
                            }
                        }
                        if (!both) GotItButton(colorGold, appTheme.isLightAccent, cell, onDismiss)
                    }
                    if (both) GotItButton(colorGold, appTheme.isLightAccent, Modifier.fillMaxWidth(), onDismiss)
                }
            }
        }
    }
}

private fun formatDate(version: String): String = runCatching {
    LocalDate.parse(version).format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH))
}.getOrDefault(version)

/** The notice for whichever documents change; the deadline to leave is the earliest change. */
private fun changeNoticeText(changes: LegalChanges): String {
    val terms = changes.terms
    val privacy = changes.privacyPolicy
    val earliest = formatDate(listOfNotNull(terms, privacy).min())
    val leave = "If you don't agree, you can stop using the App and delete your account before $earliest."
    return when {
        terms != null && privacy != null && terms == privacy ->
            "New Terms of Service and a new Privacy Policy take effect on ${formatDate(terms)}. What changes is " +
                "listed at the top of each.\n\nUntil then, the current ones apply. $leave"
        terms != null && privacy != null ->
            "New Terms of Service take effect on ${formatDate(terms)}, and a new Privacy Policy on " +
                "${formatDate(privacy)}. What changes is listed at the top of each.\n\nUntil then, the current " +
                "ones apply. $leave"
        terms != null ->
            "New Terms of Service take effect on ${formatDate(terms)}. What changes is listed at the top of the " +
                "new Terms.\n\nUntil then, the current Terms apply. $leave"
        else ->
            "A new Privacy Policy takes effect on ${formatDate(privacy!!)}. What changes is listed at the top of " +
                "the new Policy.\n\nUntil then, the current Privacy Policy applies. $leave"
    }
}

@Composable
private fun ReadButton(label: String, borderColor: Color, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 46.dp),
        shape = PillShape,
        border = BorderStroke(1.dp, borderColor),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GotItButton(color: Color, isLightAccent: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 46.dp),
        shape = PillShape,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = if (isLightAccent) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(
            text = "Got It",
            fontWeight = FontWeight.Black,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
