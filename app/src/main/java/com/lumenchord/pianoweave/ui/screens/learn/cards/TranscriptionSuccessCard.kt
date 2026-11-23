package com.lumenchord.pianoweave.ui.screens.learn.cards

import com.lumenchord.pianoweave.ui.components.AccentButton
import com.lumenchord.pianoweave.ui.components.rememberWindowRoom

import com.lumenchord.pianoweave.api.isArrangementKey
import androidx.compose.animation.core.Animatable
import com.lumenchord.pianoweave.ui.theme.PillShape
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import com.lumenchord.pianoweave.ui.theme.AppThemeManager
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.lumenchord.pianoweave.ui.components.ArtBackdrop
import com.lumenchord.pianoweave.ui.components.smallThumbnailUrl
import com.lumenchord.pianoweave.ui.components.songDetails
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.midi.StoredMidi
import kotlinx.coroutines.launch
import com.lumenchord.pianoweave.ui.theme.Moods

/**
 * Shown when a transcription finishes: the song's cover art over a blurred backdrop of itself, ringed
 * in [Moods.Glory]'s gold (Revelation 21:23), whose glow flares up once as the card appears, then settles.
 * By day the glow is warm light all round instead of an elevation shadow, which on parchment reads as a grey
 * smudge, and the art behind is fainter, so the card stays a lit page.
 */
@Composable
fun TranscriptionSuccessCard(
    song: StoredMidi,
    onSongSelect: (StoredMidi) -> Unit,
    /** Offered for uploads and recordings, which arrive named after the file. */
    onEditDetails: (() -> Unit)? = null
) {
    val isShort = rememberWindowRoom().isShort
    val accent = MaterialTheme.colorScheme.secondary
    val glory = Moods.Glory
    val surface = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(if (isShort) 14.dp else 20.dp)
    val metadata = song.metadata
    val isDay = AppThemeManager.isLight

    val glow = remember(song.file) { Animatable(0f) }
    val appear = remember(song.file) { Animatable(0f) }
    LaunchedEffect(song.file) {
        launch { appear.animateTo(1f, tween(durationMillis = 450, easing = FastOutSlowInEasing)) }
        glow.animateTo(1f, tween(durationMillis = 500, easing = FastOutSlowInEasing))
        glow.animateTo(0.3f, tween(durationMillis = 1400, easing = LinearOutSlowInEasing))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value
                // Fades each draw call instead of an offscreen layer, which would clip the glow to a rectangle
                compositingStrategy = CompositingStrategy.ModulateAlpha
                val scale = 0.94f + 0.06f * appear.value
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (isDay) Modifier.drawBehind {
                    // Layered rings of warm light round the card, brighter than the page (a ring of the page's own
                    // brightness reads as a smudge), strongest as it appears
                    for ((spread, alpha) in listOf(16.dp to 0.10f, 10.dp to 0.16f, 5.dp to 0.24f, 2.dp to 0.32f)) {
                        val px = spread.toPx() * (0.5f + 0.5f * glow.value)
                        val outline = shape.createOutline(Size(size.width + px * 2, size.height + px * 2), layoutDirection, this)
                        translate(-px, -px) { drawOutline(outline, DayHalo.copy(alpha = alpha * (0.35f + 0.65f * glow.value))) }
                    }
                } else Modifier.shadow(
                    elevation = 24.dp * glow.value,
                    shape = shape,
                    ambientColor = glory.core,
                    spotColor = glory.core
                )
            )
            .clip(shape)
            .background(surface)
            // By day the deep gold of the text would be a heavy dark line round a lit card, so it stays faint
            .border(BorderStroke(1.dp, glory.core.copy(alpha = if (isDay) 0.22f + 0.3f * glow.value else 0.3f + 0.6f * glow.value)), shape)
    ) {
        ArtBackdrop(thumbnailUrl = metadata.thumbnail, alpha = 0.55f, fadeTo = surface)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isShort) 12.dp else 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (metadata.thumbnail.isNotBlank() && !isShort) {
                AsyncImage(
                    model = smallThumbnailUrl(metadata.thumbnail),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, glory.core.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(14.dp))
            } else {
                Icon(
                    imageVector = Icons.Default.Stars,
                    contentDescription = null,
                    modifier = Modifier.size(if (isShort) 28.dp else 44.dp),
                    tint = glory.core
                )
                Spacer(Modifier.height(if (isShort) 6.dp else 10.dp))
            }
            Text(
                text = if (song.videoUrl.isArrangementKey) "Arrangement ready" else "Transcription ready",
                style = (if (isShort) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall)
                    .copy(fontWeight = FontWeight.Bold),
                color = glory.core
            )
            Text(
                text = metadata.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            songDetails(metadata)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(if (isShort) 10.dp else 16.dp))
            AccentButton(
                onClick = { onSongSelect(song) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isShort) 42.dp else 52.dp),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(if (isShort) 20.dp else 24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start practicing", fontWeight = FontWeight.ExtraBold, fontSize = if (isShort) 14.sp else 16.sp)
            }
            if (onEditDetails != null) {
                TextButton(
                    onClick = onEditDetails,
                    modifier = Modifier.padding(top = if (isShort) 2.dp else 6.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = accent)
                    Spacer(Modifier.width(6.dp))
                    Text("Edit details", color = accent, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** The success card's halo by day: warm light a little brighter than the parchment, gold in it but not glare. */
private val DayHalo = androidx.compose.ui.graphics.Color(0xFFFFEDC2)
