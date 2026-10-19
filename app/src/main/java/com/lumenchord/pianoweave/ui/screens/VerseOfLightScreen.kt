package com.lumenchord.pianoweave.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.drawWithContent
import com.lumenchord.pianoweave.ui.theme.LetterFontFamily
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lumenchord.pianoweave.util.BibleVerse
import com.lumenchord.pianoweave.util.BibleVerses
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow

// The splash screen's lamplight palette (see splash_icon_vector.xml), independent of the app theme
private val LightBackground = Color(0xFF0F0B09)
private val LightGlow = Color(0xFFFFDEB2)
private val LightPale = Color(0xFFF6E2C5)
private val VerseColor = Color(0xFFFBEAD3)
private val SoftGold = Color(0xFFE6C08D)

private fun verseStyle(
    fontSize: TextUnit,
    letterSpacing: TextUnit = 0.em,
    fontFamily: FontFamily = LetterFontFamily,
    lineHeight: TextUnit = TextUnit.Unspecified
) = TextStyle(
    fontFamily = fontFamily,
    fontSize = fontSize,
    letterSpacing = letterSpacing,
    lineHeight = lineHeight,
    textAlign = TextAlign.Center
)

/**
 * Solid letters with light shining around them: a tight glow hugging each letter, over a wide, faint
 * halo drawn by an all-but-invisible copy of the text underneath.
 */
@Composable
private fun GlowText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    glow: Dp = 4.dp,
    halo: Dp = 16.dp,
    haloAlpha: Float = 0.7f,
    exposure: Float = 0f
) {
    // exposure 1 = brightest point of the reveal's glow swell, 0 = settled
    val density = LocalDensity.current
    val glowPx = with(density) { glow.toPx() } * (1f + 2f * exposure)
    val haloPx = with(density) { halo.toPx() } * (1f + exposure)
    val letterColor = lerp(color, Color.White, exposure)
    val haloStrength = (haloAlpha * (1f + exposure)).coerceAtMost(1f)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = style.copy(
                color = letterColor.copy(alpha = 0.01f),
                shadow = Shadow(LightGlow.copy(alpha = haloStrength), Offset.Zero, haloPx)
            ),
            overflow = TextOverflow.Visible
        )
        Text(
            text = text,
            style = style.copy(
                color = letterColor,
                shadow = Shadow(LightGlow.copy(alpha = (0.75f + 0.25f * exposure)), Offset.Zero, glowPx)
            ),
            overflow = TextOverflow.Visible
        )
    }
}

/**
 * Hidden screen (hold the Piano Weave icon): a cross of gold light with a random Bible verse
 * and the name of God that goes with it, in the style of the splash screen. Tapping the name or verse copies it.
 */
@Composable
fun VerseOfLightScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)

    val verse by produceState<BibleVerse?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching { BibleVerses.load(context).randomOrNull() }.getOrNull()
        }
    }

    // Revealed like the splash icon: after a beat of darkness a shine sweeps across, uncovering the cross,
    // then the name, then the verse, while the glow swells and relaxes (same timing as splash_icon.xml).
    val crossReveal = remember { Animatable(0f) }
    val nameReveal = remember { Animatable(0f) }
    val verseReveal = remember { Animatable(0f) }
    val swell = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(120)
        launch { crossReveal.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
        launch {
            swell.animateTo(1f, tween(400, delayMillis = 150, easing = FastOutSlowInEasing))
            swell.animateTo(0f, tween(450, easing = FastOutSlowInEasing))
        }
        delay(220)
        launch { nameReveal.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
        delay(180)
        verseReveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }

    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }
    val copy: (label: String, text: String) -> Unit = { label, text ->
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
        copied = true
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBackground)
            .pointerInput(Unit) { detectTapGestures { } } // Keep touches from reaching the app below
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            val minHeight = maxHeight
            val crossSize = (maxHeight * if (isLandscape) 0.4f else 0.22f).coerceIn(100.dp, 180.dp)
            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 40.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(40.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(0.42f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CrossOfLight(
                            size = crossSize,
                            swell = { swell.value },
                            modifier = Modifier.shineReveal({ crossReveal.value })
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(0.58f)
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = minHeight - 32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        verse?.let { Message(it, copy, swell.value, { nameReveal.value }, { verseReveal.value }) }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = minHeight)
                        .padding(horizontal = 32.dp, vertical = 48.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CrossOfLight(
                            size = crossSize,
                            swell = { swell.value },
                            modifier = Modifier.shineReveal({ crossReveal.value })
                        )
                    Spacer(Modifier.height(32.dp))
                    verse?.let { Message(it, copy, swell.value, { nameReveal.value }, { verseReveal.value }) }
                }
            }

            // Fixed at the bottom so it's seen whatever was tapped, even with a long, scrolled verse
            val copiedAlpha by animateFloatAsState(if (copied) 0.85f else 0f, tween(400), label = "copied")
            Text(
                text = "Copied",
                style = verseStyle(fontSize = 13.sp),
                color = SoftGold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
                    .graphicsLayer { alpha = copiedAlpha }
                    .background(LightBackground.copy(alpha = 0.85f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .graphicsLayer { alpha = crossReveal.value } // Pure darkness before the reveal
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = LightPale.copy(alpha = 0.35f))
            }
        }
    }
}

/** The Hebrew name of God above the verse, then the verse; tapping either copies it. */
@Composable
private fun Message(
    verse: BibleVerse,
    copy: (label: String, text: String) -> Unit,
    swell: Float,
    nameReveal: () -> Float,
    verseReveal: () -> Float
) {
    // The glow swells with the cross's, a little gentler on text
    val exposure = 0.5f * swell
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (verse.godNameHebrew.isNotBlank()) {
            GodName(
                verse.godNameHebrew,
                exposure,
                onCopy = { copy(verse.godName, verse.godNameHebrew) },
                modifier = Modifier.shineReveal(nameReveal, rightToLeft = true) // The way Hebrew reads
            )
            Spacer(Modifier.height(6.dp))
        }
        VerseText(
            verse,
            onCopy = { copy(verse.ref, "${verse.text} — ${verse.ref}") },
            exposure = exposure,
            modifier = Modifier.shineReveal(verseReveal)
        )
    }
}

@Composable
private fun GodName(hebrew: String, exposure: Float, onCopy: () -> Unit, modifier: Modifier = Modifier) {
    GlowText(
        text = hebrew,
        style = verseStyle(fontSize = 26.sp, fontFamily = FontFamily.Serif),
        color = SoftGold,
        glow = 3.dp,
        halo = 12.dp,
        haloAlpha = 0.45f,
        exposure = exposure,
        modifier = modifier
            .clickable(interactionSource = null, indication = null, onClick = onCopy)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun VerseText(
    verse: BibleVerse,
    onCopy: () -> Unit,
    exposure: Float,
    modifier: Modifier = Modifier
) {
    Column(
            modifier = modifier
                .drawBehind {
                    // Faint aura of light behind the whole verse
                    val radius = size.width * 0.6f
                    scale(scaleX = 1f, scaleY = (size.height / size.width * 1.4f).coerceAtMost(1f)) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(LightGlow.copy(alpha = 0.085f), LightGlow.copy(alpha = 0.03f), Color.Transparent),
                                center = center,
                                radius = radius
                            ),
                            radius = radius
                        )
                    }
                }
                .clickable(interactionSource = null, indication = null, onClick = onCopy)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Warm and soft, like a note written to you, with gentle light around the words
            GlowText(
                text = verse.text,
                style = verseStyle(fontSize = 21.sp, lineHeight = 1.6.em),
                color = VerseColor,
                glow = 3.dp,
                halo = 14.dp,
                haloAlpha = 0.45f,
                exposure = exposure
            )
            Spacer(Modifier.height(16.dp))
            GlowText(
                text = verse.ref,
                style = verseStyle(fontSize = 15.sp, letterSpacing = 0.04.em),
                color = SoftGold,
                glow = 2.dp,
                halo = 8.dp,
                haloAlpha = 0.35f,
                exposure = exposure
            )
        }
}

/**
 * Uncovers the content the way the splash icon is lit: a band of white light sweeps from left to right,
 * and only what it has passed is visible. [overflow] leaves room for glow drawn outside the bounds;
 * [rightToLeft] sweeps the other way.
 */
private fun Modifier.shineReveal(
    progress: () -> Float,
    overflow: Dp = 48.dp,
    rightToLeft: Boolean = false
): Modifier = drawWithContent {
    val p = progress()
    if (p >= 1f) {
        drawContent()
        return@drawWithContent
    }
    if (p <= 0f) return@drawWithContent

    val margin = overflow.toPx()
    val band = maxOf(size.width * 0.35f, 96.dp.toPx())
    val area = Rect(-margin, -margin, size.width + margin, size.height + margin)
    // Leading edge of the light, travelling from before the left margin to past the right one
    val edge = -margin + (size.width + 2 * margin + band) * p

    drawIntoCanvas { it.saveLayer(area, Paint()) }
    drawContent()
    // Right to left: mirror the light, not the content
    if (rightToLeft) drawIntoCanvas { it.save(); it.translate(size.width, 0f); it.scale(-1f, 1f) }
    // Keep only what the light has already passed, with a soft edge
    drawRect(
        brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = edge - band * 0.5f, endX = edge),
        topLeft = area.topLeft,
        size = area.size,
        blendMode = BlendMode.DstIn
    )
    // The shine itself, lighting up what it passes over (like the splash icon's "shine" group)
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = 0.95f), Color.Transparent),
            startX = edge - band,
            endX = edge
        ),
        topLeft = area.topLeft,
        size = area.size,
        blendMode = BlendMode.SrcAtop
    )
    if (rightToLeft) drawIntoCanvas { it.restore() }
    drawIntoCanvas { it.restore() }
}

// The splash icon's ribbon gradients (see splash_icon_vector.xml)
private val RibbonGold = listOf(Color(0xFFD9A86E), Color(0xFFEBCB9C), Color(0xFFF6E2C5), Color(0xFFFFF8EF))
private val RibbonEdge = Color(0xFFFFFBF4)

private const val GLOW_LAYERS = 22
private const val GLOW_GROWTH = 1.13f

/**
 * A Latin cross made like the splash icon's ribbons: polished gold beams with a fine white edge, in a soft
 * warm glow over a faint radial light. [swell] (0..1) brightens the glow during the reveal.
 */
@Composable
private fun CrossOfLight(size: Dp, swell: () -> Float, modifier: Modifier = Modifier) {
    val breath by rememberInfiniteTransition(label = "crossBreath").animateFloat(
        initialValue = 0.9f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "crossGlow"
    )

    Canvas(modifier = modifier.size(width = size * 0.72f, height = size)) {
        val glow = breath * (1f + 1.2f * swell())
        val h = this.size.height
        val cx = this.size.width / 2f
        val beam = h * 0.05f
        val top = Offset(cx, h * 0.04f + beam / 2f)
        val bottom = Offset(cx, h * 0.96f - beam / 2f)
        val barY = h * 0.33f
        val left = Offset(cx - h * 0.27f, barY)
        val right = Offset(cx + h * 0.27f, barY)
        val center = Offset(cx, barY)

        // Faint radial light behind, like the splash icon's background glow
        val lightRadius = h * 0.75f
        drawCircle(
            brush = Brush.radialGradient(
                0f to LightGlow.copy(alpha = (0.10f * glow).coerceAtMost(1f)),
                0.35f to LightGlow.copy(alpha = (0.07f * glow).coerceAtMost(1f)),
                0.65f to LightGlow.copy(alpha = (0.027f * glow).coerceAtMost(1f)),
                0.85f to LightGlow.copy(alpha = (0.008f * glow).coerceAtMost(1f)),
                1f to Color.Transparent,
                center = center,
                radius = lightRadius
            ),
            radius = lightRadius,
            center = center
        )

        // Glow: many faint strokes of growing width stack into a smooth falloff (works on every API level,
        // unlike blur). Same idea as the splash icon's glow layers, just finer-grained.
        for (i in 0 until GLOW_LAYERS) {
            val width = beam * 1.1f * GLOW_GROWTH.pow(i)
            // Wider layers are fainter, so the glow fades out instead of ending at an edge
            val fade = (1f - i.toFloat() / GLOW_LAYERS).pow(1.6f)
            val glowColor = LightGlow.copy(alpha = (0.05f * fade * glow).coerceAtMost(1f))
            drawBeams(top, bottom, left, right, width) { s, e, w ->
                drawLine(glowColor, s, e, w, StrokeCap.Round)
            }
        }

        // Gold, lit from the top right like the ribbons
        val gold = Brush.linearGradient(RibbonGold, start = Offset(left.x, bottom.y), end = Offset(right.x, top.y))
        val edgeWidth = 1.2.dp.toPx()
        val edgeColor = RibbonEdge.copy(alpha = 0.75f)

        // Crossbar first, so the upright reads as in front of it
        drawLine(gold, left, right, beam, StrokeCap.Round)
        val barEdgeY = barY - beam / 2f + edgeWidth
        drawLine(edgeColor, Offset(left.x, barEdgeY), Offset(cx - beam / 2f, barEdgeY), edgeWidth, StrokeCap.Round)
        drawLine(edgeColor, Offset(cx + beam / 2f, barEdgeY), Offset(right.x, barEdgeY), edgeWidth, StrokeCap.Round)

        drawLine(gold, top, bottom, beam, StrokeCap.Round)
        val uprightEdgeX = cx - beam / 2f + edgeWidth
        drawLine(edgeColor, Offset(uprightEdgeX, top.y), Offset(uprightEdgeX, bottom.y), edgeWidth, StrokeCap.Round)

        // Soft glint where the beams meet
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.7f * breath), Color.Transparent),
                center = center,
                radius = beam * 1.6f
            ),
            radius = beam * 1.6f,
            center = center
        )
    }
}

private inline fun DrawScope.drawBeams(
    top: Offset, bottom: Offset, left: Offset, right: Offset, width: Float,
    draw: DrawScope.(Offset, Offset, Float) -> Unit
) {
    draw(top, bottom, width)
    draw(left, right, width)
}
