package com.lumenchord.pianoweave.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/** YouTube's full-size thumbnails are 1280px wide; the 320px one is plenty for list rows and backdrops. */
private val YOUTUBE_THUMBNAIL = Regex("""^https://i\.ytimg\.com/vi(?:_webp)?/([^/]+)/[^/]+$""")

fun smallThumbnailUrl(url: String): String =
    YOUTUBE_THUMBNAIL.find(url)?.let { "https://i.ytimg.com/vi/${it.groupValues[1]}/mqdefault.jpg" } ?: url

/** Blur needs Android 12; below that a sharp, busy image behind text would hurt readability, so none is drawn. */
private val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * The song's cover art, heavily blurred and dimmed, filling the parent [Box] behind its content.
 * [fadeTo] darkens it towards the bottom so text and buttons placed there stay readable.
 */
@Composable
fun BoxScope.ArtBackdrop(
    thumbnailUrl: String,
    alpha: Float,
    fadeTo: Color? = null
) {
    if (thumbnailUrl.isBlank() || !canBlur) return
    AsyncImage(
        model = smallThumbnailUrl(thumbnailUrl),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .matchParentSize()
            .blur(28.dp)
            .alpha(alpha)
    )
    if (fadeTo != null) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, fadeTo)))
        )
    }
}
