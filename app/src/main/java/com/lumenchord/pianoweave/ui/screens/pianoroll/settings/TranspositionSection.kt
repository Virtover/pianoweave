package com.lumenchord.pianoweave.ui.screens.pianoroll.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel

@Composable
fun TranspositionSection(viewModel: PianoWeaveViewModel) {
    val appTheme = LocalAppTheme.current
    val colorAccent = appTheme.primaryColor
    val colorSlate = MaterialTheme.colorScheme.tertiaryContainer

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Transposition", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Surface(
                color = colorSlate.copy(alpha = 0.5f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, colorAccent.copy(alpha = 0.4f))
            ) {
                Text(
                    text = "${if (viewModel.transposeOffset > 0) "+" else ""}${viewModel.transposeOffset} semi",
                    color = colorAccent,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Slider(
            value = viewModel.transposeOffset.toFloat(),
            onValueChange = { viewModel.transposeOffset = it.toInt() },
            valueRange = -12f..12f,
            steps = 23,
            colors = SliderDefaults.colors(
                thumbColor = colorAccent,
                activeTrackColor = colorAccent,
                inactiveTrackColor = colorSlate
            )
        )
    }
}
