package com.lumenchord.pianoweave.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveNavigation(
    isLearnSelected: Boolean,
    onLearnSelect: () -> Unit,
    isStorageSelected: Boolean,
    onStorageSelect: () -> Unit,
    onOpenSettingsDialog: () -> Unit,
    logoHoldMillis: Long,
    onLogoLongHold: () -> Unit,
    content: @Composable () -> Unit
) {
    val room = rememberWindowRoom()
    // A rail from tablet width up too, where a bottom bar would stretch two items across the whole screen
    val useRail = room.isLandscape || room.isWide
    // Tablets get a roomier rail: larger mark and labels, items under the header and Settings at the foot
    val isLarge = room.isLarge
    val accent = LocalAppTheme.current.primaryColor
    val sky = Modifier.lampGlow(background = MaterialTheme.colorScheme.background, accent = accent)
    val barColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)

    if (useRail) {
        Row(modifier = Modifier.fillMaxSize().then(sky)) {
            NavigationRail(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = if (isLarge) 104.dp else 88.dp, max = if (isLarge) 120.dp else 110.dp)
                    .drawBehind { edgeLight(accent, vertical = true) },
                containerColor = barColor,
                windowInsets = NavigationRailDefaults.windowInsets,
                header = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = if (isLarge) 28.dp else 16.dp, bottom = 8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(if (isLarge) 60.dp else 52.dp)
                                .clip(CircleShape)
                                .clickableWithLongHold(logoHoldMillis, onHold = onLogoLongHold, onClick = onOpenSettingsDialog),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(id = R.drawable.app_icon),
                                    contentDescription = "Piano Weave Settings",
                                    modifier = Modifier
                                        .size(if (isLarge) 44.dp else 38.dp)
                                        .clip(CircleShape)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(if (isLarge) 10.dp else 8.dp))

                        PianoWeaveWordmark(fontSize = if (isLarge) 15.sp else 13.sp, stacked = true)
                    }
                }
            ) {
                val labelSize = if (isLarge) 13.sp else 11.sp
                if (isLarge) Spacer(Modifier.height(36.dp)) else Spacer(modifier = Modifier.weight(1f))

                NavigationRailItem(
                    selected = isLearnSelected,
                    onClick = onLearnSelect,
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    label = {
                        Text(
                            text = "Transcribe",
                            fontSize = labelSize,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.secondary,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.secondary
                    )
                )

                Spacer(modifier = Modifier.height(if (isLarge) 20.dp else 16.dp))

                NavigationRailItem(
                    selected = isStorageSelected,
                    onClick = onStorageSelect,
                    icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                    label = {
                        Text(
                            text = "Library",
                            fontSize = labelSize,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.secondary,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.secondary
                    )
                )

                Spacer(modifier = Modifier.weight(1f))

                // The logo opens Settings too, but on a tablet's tall rail a plain gear makes it easy to find
                if (isLarge) {
                    IconButton(onClick = onOpenSettingsDialog, modifier = Modifier.padding(bottom = 20.dp)) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (isLarge) Modifier.padding(start = 20.dp, end = 32.dp, top = 24.dp, bottom = 20.dp)
                        else Modifier.padding(start = 12.dp, end = 24.dp, top = 16.dp, bottom = 16.dp)
                    )
            ) {
                content()
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .clickableWithLongHold(logoHoldMillis, onHold = onLogoLongHold, onClick = onOpenSettingsDialog),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Image(
                                        painter = painterResource(id = R.drawable.app_icon),
                                        contentDescription = "Piano Weave Settings",
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                    )
                                }
                            }

                            PianoWeaveWordmark(fontSize = 18.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    modifier = Modifier.drawBehind { edgeLight(accent, vertical = false, atBottom = true) }
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = barColor,
                    modifier = Modifier.drawBehind { edgeLight(accent, vertical = false, atBottom = false) }
                ) {
                    NavigationBarItem(
                        selected = isLearnSelected,
                        onClick = onLearnSelect,
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                        label = { Text("Transcribe", maxLines = 1, softWrap = false) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.secondary,
                            indicatorColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                    NavigationBarItem(
                        selected = isStorageSelected,
                        onClick = onStorageSelect,
                        icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                        label = { Text("Library", maxLines = 1, softWrap = false) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.secondary,
                            indicatorColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                }
            },
            modifier = sky,
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0.dp)
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                content()
            }
        }
    }
}

/**
 * A hairline of the accent's light along a bar's edge, brightest in the middle and fading at the ends,
 * where the bar meets the content.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.edgeLight(
    accent: Color,
    vertical: Boolean,
    atBottom: Boolean = false
) {
    val fade = listOf(Color.Transparent, accent.copy(alpha = 0.22f), Color.Transparent)
    val stroke = 1.dp.toPx()
    if (vertical) {
        drawLine(
            brush = Brush.verticalGradient(fade),
            start = Offset(size.width - stroke / 2, 0f),
            end = Offset(size.width - stroke / 2, size.height),
            strokeWidth = stroke
        )
    } else {
        val y = if (atBottom) size.height - stroke / 2 else stroke / 2
        drawLine(
            brush = Brush.horizontalGradient(fade),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = stroke
        )
    }
}
