package com.lumenchord.pianoweave.ui.screens.learn.cards

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.api.LinkPreview
import com.lumenchord.pianoweave.ui.components.SongThumbnail

/** The shapes a preview takes: the quiet stand-in while the server reads the link, or the details. */
private sealed interface PreviewState {
    data object Loading : PreviewState
    data class Shown(val preview: LinkPreview) : PreviewState
}

/**
 * Under the link field: the pasted video's thumbnail, title (two lines at most) and "channel · length", from the
 * server's cost check. While it is read ([preview] null), faint bars of the same size stand in, with no spinner.
 * The caller shows none for no link, a link the server couldn't read, or a server that sends nothing to show.
 */
@Composable
internal fun LinkPreviewRow(preview: LinkPreview?, modifier: Modifier = Modifier) {
    val state = when {
        preview != null -> PreviewState.Shown(preview)
        else -> PreviewState.Loading
    }
    AnimatedContent(
        targetState = state,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = { it::class },
        label = "linkPreview",
        modifier = modifier.fillMaxWidth()
    ) { shown ->
        when (shown) {
            PreviewState.Loading -> PreviewLayout(thumbnail = { Placeholder(Modifier.fillMaxSize()) }) {
                Placeholder(Modifier.fillMaxWidth(0.8f).height(12.dp))
                Placeholder(Modifier.fillMaxWidth(0.45f).height(10.dp))
            }
            is PreviewState.Shown -> {
                val p = shown.preview
                PreviewLayout(
                    modifier = Modifier.clearAndSetSemantics { contentDescription = p.contentDescription },
                    thumbnail = { SongThumbnail(thumbnailUrl = p.thumbnail.orEmpty(), size = THUMBNAIL_HEIGHT) }
                ) {
                    p.title?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 18.sp
                        )
                    }
                    p.detailLine?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private val THUMBNAIL_HEIGHT = 44.dp

@Composable
private fun PreviewLayout(
    modifier: Modifier = Modifier,
    thumbnail: @Composable () -> Unit,
    text: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(THUMBNAIL_HEIGHT)) { thumbnail() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), content = text)
    }
}

@Composable
private fun Placeholder(modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f), RoundedCornerShape(6.dp)))
}
