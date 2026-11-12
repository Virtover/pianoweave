package com.lumenchord.pianoweave.ui.screens.learn.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumenchord.pianoweave.midi.StoredMidi
import com.lumenchord.pianoweave.ui.components.SectionHeader
import com.lumenchord.pianoweave.ui.components.SongThumbnail
import com.lumenchord.pianoweave.ui.components.songDetails
import com.lumenchord.pianoweave.ui.components.SongModeTag
import com.lumenchord.pianoweave.ui.components.madeAs
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import com.lumenchord.pianoweave.ui.components.rememberWindowRoom

/** The newest songs in the library, so the Transcribe screen is a way back into practicing too. */
@Composable
fun RecentSongsSection(
    songs: List<StoredMidi>,
    onSongSelect: (StoredMidi) -> Unit,
    /** Off beside the cards (tablet landscape), so the header lines up with the top of the Transcribe card. */
    showHeaderGap: Boolean = true
) {
    val room = rememberWindowRoom()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (showHeaderGap) 8.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(if (room.isLarge) 4.dp else 2.dp)
    ) {
        SectionHeader("Recently added")
        songs.forEach { song ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSongSelect(song) }
                    .padding(horizontal = if (room.isLarge) 8.dp else 4.dp, vertical = if (room.isLarge) 8.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SongThumbnail(thumbnailUrl = song.metadata.thumbnail, size = if (room.isLarge) 46.dp else 40.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.metadata.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val mode = song.madeAs
                    val details = songDetails(song.metadata)
                    if (mode != null || details != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            mode?.let {
                                SongModeTag(it, compact = !room.isWide)
                                Spacer(Modifier.width(6.dp))
                            }
                            details?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Practice ${song.metadata.title}",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
