package com.example.pianoweave.ui.screens.pianoroll

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pianoweave.audio.AcousticNoteDetector
import com.example.pianoweave.ui.theme.LocalAppTheme
import com.example.pianoweave.ui.viewmodel.PianoWeaveViewModel
import kotlinx.coroutines.delay

internal enum class SeekDirection { BACKWARD, FORWARD }

internal data class SeekInfo(
    val direction: SeekDirection,
    val id: Long = System.currentTimeMillis()
)

@Composable
internal fun SeekIndicatorOverlay(seekInfo: SeekInfo?) {
    val appTheme = LocalAppTheme.current
    val ColorGold = appTheme.primaryColor

    var visibleInfo by remember { mutableStateOf<SeekInfo?>(null) }
    var alpha by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(seekInfo) {
        if (seekInfo != null) {
            visibleInfo = seekInfo
            alpha = 1f
            delay(500)
            alpha = 0f
            delay(200)
            visibleInfo = null
        }
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = alpha,
        animationSpec = tween(durationMillis = 200),
        label = "seekAlpha"
    )

    visibleInfo?.let { info ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = animatedAlpha },
            contentAlignment = if (info.direction == SeekDirection.BACKWARD) Alignment.CenterStart else Alignment.CenterEnd
        ) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 48.dp)
                    .size(90.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.7f),
                border = BorderStroke(1.5.dp, ColorGold.copy(alpha = 0.8f))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (info.direction == SeekDirection.BACKWARD) Icons.Default.FastRewind else Icons.Default.FastForward,
                        contentDescription = null,
                        tint = ColorGold,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (info.direction == SeekDirection.BACKWARD) "-5s" else "+5s",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
internal fun ModernToolbar(
    head: Long,
    dur: Long,
    viewModel: PianoWeaveViewModel,
    onBack: () -> Unit,
    onSetClick: () -> Unit
) {
    val appTheme = LocalAppTheme.current
    val ColorGold = appTheme.primaryColor

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val current = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorSurface.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .pointerInput(Unit) { detectTapGestures { } }, // Consume gestures so UI toolbar doesn't pass taps through
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
        }
        if (!isPortrait) {
            Text(
                text = viewModel.activePracticeSong?.metadata?.title ?: "",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                modifier = Modifier.weight(1f),
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "${formatTime(head)} / ${formatTime(dur)}",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        if (isPortrait) Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isPortrait) 6.dp else 12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(if (isPortrait) 4.dp else 8.dp)) {
                listOf(0.25f, 0.5f, 1.0f).forEach { s ->
                    val isSelected = viewModel.speedMultiplier == s
                    Box(
                        modifier = Modifier
                            .size(if (isPortrait) 32.dp else 36.dp)
                            .background(if (isSelected) ColorGold else ColorSlate.copy(alpha = 0.8f), CircleShape)
                            .clickable { viewModel.speedMultiplier = s },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${s}x",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isSelected) Color.Black else Color.White
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .height(38.dp)
                    .then(if (isPortrait) Modifier.width(38.dp) else Modifier.wrapContentWidth())
                    .background(if (viewModel.isWaitModeEnabled) ColorGold else ColorSurface.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                    .border(1.dp, if (viewModel.isWaitModeEnabled) ColorGold else ColorSlate.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .clickable {
                        if (viewModel.isWaitModeEnabled) AcousticNoteDetector.stop()
                        else AcousticNoteDetector.start(current)
                        viewModel.isWaitModeEnabled = !viewModel.isWaitModeEnabled
                    }
                    .padding(horizontal = if (isPortrait) 0.dp else 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = if (viewModel.isWaitModeEnabled) Color.Black else ColorGold,
                        modifier = Modifier.size(18.dp)
                    )
                    if (!isPortrait) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Wait mode",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (viewModel.isWaitModeEnabled) Color.Black else ColorGold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(ColorSurface.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                    .border(1.dp, ColorSlate.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .clickable { onSetClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = ColorGold,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
internal fun WaitModeOverlay(notes: Set<Int>) {
    val appTheme = LocalAppTheme.current
    val ColorWaitTarget = appTheme.waitTargetColor
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 56.dp)
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            color = ColorWaitTarget.copy(alpha = 0.8f),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 10.dp,
            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.8f))
        ) {
            Text(
                text = "STRIKE: " + notes.sorted().joinToString("   ") { midiPitchName(it) },
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp),
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black
            )
        }
    }
}
