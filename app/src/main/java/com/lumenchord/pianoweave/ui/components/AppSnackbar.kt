package com.lumenchord.pianoweave.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme

/**
 * A snackbar made like the screen's cards rather than Material's dark slab: the card surface (opaque, as
 * it floats over content), their hairline outline and their wash of the theme's gold from the top, with
 * the action in gold. Kept to a readable width in landscape. Use as a [androidx.compose.material3.SnackbarHost]'s content.
 */
@Composable
fun AppSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    val gold = LocalAppTheme.current.primaryColor
    Surface(
        modifier = modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .cardWash(gold)
                .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = data.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp)
            )
            data.visuals.actionLabel?.let { label ->
                TextButton(
                    onClick = { data.performAction() },
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text(label, fontWeight = FontWeight.Bold, color = gold)
                }
            } ?: Spacer(Modifier.padding(end = 10.dp))
        }
    }
}
