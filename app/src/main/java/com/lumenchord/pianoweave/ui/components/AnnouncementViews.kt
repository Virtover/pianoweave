package com.lumenchord.pianoweave.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lumenchord.pianoweave.api.Announcement
import com.lumenchord.pianoweave.api.AnnouncementBlock
import com.lumenchord.pianoweave.api.AnnouncementParser
import com.lumenchord.pianoweave.api.AnnouncementSeverity
import com.lumenchord.pianoweave.api.AnnouncementSpan
import com.lumenchord.pianoweave.api.Announcements
import com.lumenchord.pianoweave.ui.components.settings.LinkRow
import com.lumenchord.pianoweave.ui.theme.AppTheme
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.theme.Mood
import com.lumenchord.pianoweave.ui.theme.Moods
import com.lumenchord.pianoweave.ui.theme.PillShape
import kotlinx.coroutines.delay

/*
 * Server announcements (see [Announcements]). Critical ones come over the whole app: one that can't be
 * dismissed fills the screen, and leaves it only through a warning, for a banner on the Learn screen; from
 * there it can be moved to the bottom of Settings. Everything else sits in that banner.
 */

/** Unix seconds, updated every [periodMs], so an announcement disappears when it ends. */
@Composable
private fun rememberNowSec(periodMs: Long = 30_000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis() / 1000
            delay(periodMs)
        }
    }
    return now
}

/**
 * Critical: [Moods.Lament], grief held rather than an alarm; warning: [Moods.Watch], the watchman's lamp;
 * info: the app's own accent.
 */
@Composable
private fun moodOf(severity: AnnouncementSeverity): Mood = when (severity) {
    AnnouncementSeverity.CRITICAL -> Moods.Lament
    AnnouncementSeverity.WARNING -> Moods.Watch
    AnnouncementSeverity.INFO -> LocalAppTheme.current.primaryColor.let {
        Mood(light = it, core = it, deep = MaterialTheme.colorScheme.surface)
    }
}

private fun iconOf(severity: AnnouncementSeverity): ImageVector =
    if (severity == AnnouncementSeverity.CRITICAL) Icons.Default.PriorityHigh else Icons.Default.Campaign

private val Announcement.displayTitle: String get() = title.ifEmpty { "Message" }

/** The body on one line, for a banner. */
private val Announcement.preview: String get() = body.replace(Regex("\\s+"), " ").trim()

/** Opens [url] in the browser, never inside the app, and only an `https` address. */
private fun openInBrowser(context: Context, url: String) {
    val safe = AnnouncementParser.safeHttpsUrl(url) ?: return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safe))
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w("Announcements", "No browser for a link")
    }
}

private fun spansText(spans: List<AnnouncementSpan>, linkColor: Color, context: Context): AnnotatedString =
    buildAnnotatedString {
        for (span in spans) {
            val style = SpanStyle(
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null
            )
            val link = span.link
            if (link == null) {
                withStyle(style) { append(span.text) }
            } else {
                val linkStyle = TextLinkStyles(
                    SpanStyle(color = linkColor, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)
                )
                withLink(LinkAnnotation.Url(link, linkStyle) { openInBrowser(context, link) }) {
                    withStyle(style) { append(span.text) }
                }
            }
        }
    }

/** An announcement's formatted text, laid out with plain text views (never as HTML or Markdown). */
@Composable
fun AnnouncementText(blocks: List<AnnouncementBlock>, linkColor: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bodyColor = MaterialTheme.colorScheme.onSurface
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is AnnouncementBlock.Paragraph -> Text(
                    text = spansText(block.spans, linkColor, context),
                    style = MaterialTheme.typography.bodyMedium,
                    color = bodyColor,
                    lineHeight = 22.sp
                )
                is AnnouncementBlock.Heading -> Text(
                    text = spansText(block.spans, linkColor, context),
                    style = if (block.level == 1) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = bodyColor,
                    // A heading belongs to what follows it
                    modifier = if (index > 0) Modifier.padding(top = 4.dp) else Modifier
                )
                is AnnouncementBlock.ItemList -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    block.items.forEachIndexed { i, item ->
                        Row {
                            Text(
                                text = if (block.ordered) "${block.start + i}." else "•",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = linkColor,
                                modifier = Modifier.width(24.dp)
                            )
                            Text(
                                text = spansText(item, linkColor, context),
                                style = MaterialTheme.typography.bodyMedium,
                                color = bodyColor,
                                lineHeight = 22.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Learn screen banner ---

/**
 * The Learn screen's announcements, shown as one banner (the most severe) in the style of
 * [ServerMaintenanceBanner]. Tapping opens them all; the cross dismisses one, or for a hidden critical one,
 * asks to move it to Settings.
 */
@Composable
fun AnnouncementBanners(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now = rememberNowSec()
    val items = Announcements.forBanner(now)
    var showAll by remember { mutableStateOf(false) }
    var toMove by remember { mutableStateOf<Announcement?>(null) }
    // Closes the list once its last message is dismissed or ends, so it doesn't reopen with the next one
    LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) showAll = false }

    val close: (Announcement) -> Unit = {
        if (it.dismissible) Announcements.dismiss(context, it) else toMove = it
    }
    if (items.isNotEmpty()) {
        AnnouncementBanner(
            announcement = items.first(),
            moreCount = items.size - 1,
            onOpen = { showAll = true },
            onClose = close,
            modifier = modifier
        )
    }
    if (showAll && items.isNotEmpty()) {
        if (items.size == 1) {
            val only = items.first()
            AnnouncementDialog(
                announcement = only,
                onClose = { showAll = false },
                onDismiss = { Announcements.dismiss(context, it) },
                onMoveToSettings = if (only.isBlocking) {
                    {
                        showAll = false
                        toMove = only
                    }
                } else null
            )
        } else {
            AnnouncementListDialog(
                announcements = items,
                onClose = { showAll = false },
                onRemove = close
            )
        }
    }
    toMove?.let { announcement ->
        MoveToSettingsDialog(
            onConfirm = {
                Announcements.setArchived(context, announcement, true)
                toMove = null
            },
            onCancel = { toMove = null }
        )
    }
}

@Composable
private fun AnnouncementBanner(
    announcement: Announcement,
    moreCount: Int,
    onOpen: () -> Unit,
    onClose: (Announcement) -> Unit,
    modifier: Modifier = Modifier
) {
    val mood = moodOf(announcement.severity)
    val detail = announcement.preview
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, mood.core.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .cardWash(mood.core)
                .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconBadge(icon = iconOf(announcement.severity), tint = mood.core)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = announcement.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = mood.light,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (detail.isNotEmpty() && detail != announcement.title) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (moreCount > 0) {
                    Text(
                        text = if (moreCount == 1) "1 more message" else "$moreCount more messages",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = mood.core,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            IconButton(onClick = { onClose(announcement) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = if (announcement.dismissible) "Dismiss" else "Move to Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// --- Dialogs ---

/** The dialogs' frame, as in [TermsChangeDialog]: a panel lit by [light], centred on the screen. */
@Composable
private fun AnnouncementDialogFrame(
    light: Color,
    onDismissRequest: () -> Unit,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            DialogSurface(
                light = light,
                modifier = Modifier
                    .fillMaxWidth(0.98f)
                    .widthIn(max = 560.dp)
                    .wrapContentHeight(),
                color = MaterialTheme.colorScheme.surface,
                border = border ?: BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content
                )
            }
        }
    }
}

/** Two pill buttons side by side, as in the app's other dialogs: [secondary] outlined, [primary] filled. */
@Composable
private fun DialogButtons(
    primary: String,
    onPrimary: () -> Unit,
    primaryColor: Color,
    primaryContentColor: Color,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
    secondaryIcon: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (secondary != null) {
            OutlinedButton(
                onClick = onSecondary,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = 46.dp),
                shape = PillShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            ) {
                if (secondaryIcon != null) {
                    Icon(secondaryIcon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = secondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Button(
            onClick = onPrimary,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .heightIn(min = 46.dp),
            shape = PillShape,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = primaryContentColor)
        ) {
            Text(
                text = primary,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** The app's accent and the text color that reads on it. */
@Composable
private fun accentColors(): Pair<Color, Color> {
    val theme = LocalAppTheme.current
    return theme.primaryColor to
        if (theme.isLightAccent) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimary
}

/**
 * One announcement in full. Its link is the outlined button; the filled one dismisses it, or just closes the
 * dialog for one that can't be dismissed. [onRestore] adds a way back to the Learn screen (from Settings),
 * [onMoveToSettings] the way there (for a hidden critical one on the Learn screen).
 */
@Composable
private fun AnnouncementDialog(
    announcement: Announcement,
    onClose: () -> Unit,
    onDismiss: (Announcement) -> Unit,
    onRestore: (() -> Unit)? = null,
    onMoveToSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val mood = moodOf(announcement.severity)
    val (accent, onAccent) = accentColors()
    val link = announcement.link

    AnnouncementDialogFrame(light = mood.core, onDismissRequest = onClose) {
        DialogHeader(
            title = announcement.displayTitle,
            icon = iconOf(announcement.severity),
            onClose = onClose,
            titleColor = mood.core
        )
        AnnouncementText(
            blocks = announcement.blocks,
            linkColor = mood.core,
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        )
        val placeAction = onRestore?.let { "Show on the Learn screen again" to it }
            ?: onMoveToSettings?.let { "Move to Settings" to it }
        if (placeAction != null) {
            Text(
                text = placeAction.first,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = placeAction.second)
                    .padding(vertical = 4.dp)
            )
        }
        DialogButtons(
            secondary = link?.let { it.label ?: "Open link" },
            secondaryIcon = Icons.AutoMirrored.Filled.OpenInNew,
            onSecondary = { link?.let { openInBrowser(context, it.url) } },
            primary = if (announcement.dismissible) "Dismiss" else "Close",
            onPrimary = {
                if (announcement.dismissible) onDismiss(announcement)
                onClose()
            },
            primaryColor = accent,
            primaryContentColor = onAccent
        )
    }
}

/** Several announcements, each in its own card with its own actions. */
@Composable
private fun AnnouncementListDialog(
    announcements: List<Announcement>,
    onClose: () -> Unit,
    onRemove: (Announcement) -> Unit
) {
    val (accent, onAccent) = accentColors()
    val first = announcements.first()
    AnnouncementDialogFrame(light = moodOf(first.severity).core, onDismissRequest = onClose) {
        DialogHeader(
            title = "Messages",
            icon = Icons.Default.Campaign,
            onClose = onClose,
            titleColor = accent
        )
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for (announcement in announcements) {
                AnnouncementCard(announcement, onRemove = { onRemove(announcement) })
            }
        }
        DialogButtons(primary = "Done", onPrimary = onClose, primaryColor = accent, primaryContentColor = onAccent)
    }
}

@Composable
private fun AnnouncementCard(announcement: Announcement, onRemove: () -> Unit) {
    val context = LocalContext.current
    val mood = moodOf(announcement.severity)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, mood.core.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .cardWash(mood.core)
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBadge(icon = iconOf(announcement.severity), tint = mood.core, size = 26.dp)
                Text(
                    text = announcement.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = mood.light
                )
            }
            AnnouncementText(announcement.blocks, linkColor = mood.core)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                announcement.link?.let { link ->
                    // Shrinks before the other action does: a long label must not squeeze it into a column
                    TextButton(
                        onClick = { openInBrowser(context, link.url) },
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = mood.core, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = link.label ?: "Open link",
                            color = mood.core,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                TextButton(onClick = onRemove) {
                    Text(
                        text = if (announcement.dismissible) "Dismiss" else "Move to Settings",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

/** Moving a hidden critical message off the Learn screen: a light check, as it stays readable in Settings. */
@Composable
private fun MoveToSettingsDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    val (accent, onAccent) = accentColors()
    AnnouncementDialogFrame(light = accent, onDismissRequest = onCancel) {
        DialogHeader(title = "Hide from Learn?", icon = Icons.Default.VisibilityOff, onClose = onCancel, titleColor = accent)
        Text(
            text = "This is still an important message from the server. It moves to the bottom of Settings, " +
                "where you can read it any time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 22.sp
        )
        DialogButtons(
            secondary = "Move to Settings",
            onSecondary = onConfirm,
            primary = "Keep it here",
            onPrimary = onCancel,
            primaryColor = accent,
            primaryContentColor = onAccent
        )
    }
}

// --- Settings ---

/** The bottom of Settings: critical messages moved off the Learn screen. Nothing when there are none. */
@Composable
fun AnnouncementSettingsSection(previewTheme: AppTheme) {
    val context = LocalContext.current
    val now = rememberNowSec()
    val items = Announcements.inSettings(now)
    var open by remember { mutableStateOf<Announcement?>(null) }
    if (items.isNotEmpty()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                text = "IMPORTANT MESSAGES",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            for (announcement in items) {
                LinkRow(
                    icon = iconOf(announcement.severity),
                    title = announcement.displayTitle,
                    subtitle = announcement.preview.ifEmpty { "From the server" },
                    previewTheme = previewTheme.copy(primaryColor = Moods.Lament.core),
                    onClick = { open = announcement },
                    trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight
                )
            }
        }
    }
    open?.let { announcement ->
        AnnouncementDialog(
            announcement = announcement,
            onClose = { open = null },
            onDismiss = {},
            onRestore = {
                Announcements.setArchived(context, announcement, false)
                open = null
            }
        )
    }
}

// --- Critical, over the whole app ---

/**
 * Critical announcements over everything, the piano roll included: one that can't be dismissed as a full
 * screen, one that can as a pop-up once per run (it stays in the banner until dismissed).
 */
@Composable
fun CriticalAnnouncementHost() {
    val context = LocalContext.current
    val now = rememberNowSec()
    val visible = Announcements.visible(now)
    val hidden = Announcements.hiddenCritical
    val blocking = visible.firstOrNull { it.isBlocking && it.key !in hidden }
    if (blocking != null) {
        CriticalAnnouncementScreen(blocking, onHideAnyway = { Announcements.hideCritical(context, blocking) })
        return
    }
    val poppedUp = Announcements.poppedUp
    val popup = visible.firstOrNull { it.severity == AnnouncementSeverity.CRITICAL && it.dismissible && it.key !in poppedUp }
    if (popup != null) {
        AnnouncementDialog(
            announcement = popup,
            onClose = { Announcements.markPoppedUp(popup) },
            onDismiss = { Announcements.dismiss(context, it) }
        )
    }
}

/**
 * A critical announcement that can't be dismissed: fills the screen (full screen like the app, bars hidden),
 * and Back doesn't close it. The message sits in a lit card under the wordmark; the way past it is a quiet
 * button below, guarded by [HideCriticalAnnouncementDialog].
 */
@Composable
private fun CriticalAnnouncementScreen(announcement: Announcement, onHideAnyway: () -> Unit) {
    val context = LocalContext.current
    val mood = moodOf(announcement.severity)
    val background = MaterialTheme.colorScheme.background
    var confirmHide by remember(announcement.key) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // The app runs without system bars; a dialog window brings them back unless told otherwise
        val view = LocalView.current
        LaunchedEffect(view) {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(mood.core.copy(alpha = 0.14f), Color.Transparent),
                            center = Offset(size.width / 2f, size.height * 0.32f),
                            radius = size.maxDimension * 0.55f
                        )
                    )
                }
        ) {
            val screenHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = screenHeight)
                    .padding(horizontal = 20.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                PianoWeaveWordmark(fontSize = 15.sp)
                Spacer(Modifier.height(24.dp))
                DialogSurface(
                    light = mood.core,
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, mood.core.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            IconBadge(icon = iconOf(announcement.severity), tint = mood.core, size = 52.dp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "IMPORTANT MESSAGE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = announcement.displayTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = mood.core,
                                textAlign = TextAlign.Center
                            )
                        }
                        AnnouncementText(announcement.blocks, linkColor = mood.core)
                        announcement.link?.let { link ->
                            Button(
                                onClick = { openInBrowser(context, link.url) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                shape = PillShape,
                                colors = ButtonDefaults.buttonColors(containerColor = mood.core, contentColor = mood.deep)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = link.label ?: "Open link",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                TextButton(onClick = { confirmHide = true }) {
                    Text(
                        text = "Hide this message",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        if (confirmHide) {
            HideCriticalAnnouncementDialog(
                onConfirm = {
                    confirmHide = false
                    onHideAnyway()
                },
                onCancel = { confirmHide = false }
            )
        }
    }
}

/** Seconds the Hide button stays disabled, so the warning gets read. */
private const val HIDE_COUNTDOWN_SEC = 5

/**
 * The guarded way past a critical announcement: says what may be lost, asks for an explicit agreement, and
 * holds the button back a few seconds. Hiding moves the message to the Learn screen; it isn't gone.
 */
@Composable
private fun HideCriticalAnnouncementDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    val (accent, onAccent) = accentColors()
    val error = MaterialTheme.colorScheme.error
    var understood by remember { mutableStateOf(false) }
    var remaining by remember { mutableIntStateOf(HIDE_COUNTDOWN_SEC) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
    }

    AnnouncementDialogFrame(light = error, onDismissRequest = onCancel, border = dangerBorder(alpha = 0.5f)) {
        DialogHeader(title = "Hide this message?", icon = Icons.Default.Warning, onClose = onCancel, titleColor = error)
        DangerCard(shape = RoundedCornerShape(14.dp)) {
            Text(
                text = "The server marked it as critical. It may be the only way to learn how to keep Piano " +
                    "Weave working, for example where to get updates if the app leaves Google Play.",
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 21.sp,
                modifier = Modifier.padding(14.dp)
            )
        }
        Text(
            text = "It will move to a banner on the Learn screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { understood = !understood }
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = understood,
                onCheckedChange = { understood = it },
                colors = CheckboxDefaults.colors(checkedColor = error, checkmarkColor = MaterialTheme.colorScheme.surface)
            )
            Text(
                text = "I understand I may miss something important",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DangerButton(
                onClick = onConfirm,
                enabled = understood && remaining == 0,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = 46.dp)
            ) {
                Text(
                    text = if (remaining > 0) "Hide ($remaining)" else "Hide",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
            // The safe choice is the prominent one
            Button(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = 46.dp),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent)
            ) {
                Text("Keep it", fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
        }
    }
}
