package com.lumenchord.pianoweave.ui.screens

import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Path
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
import com.lumenchord.pianoweave.ui.theme.AppThemeManager
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

// The splash screen's palette (see splash_icon_vector.xml and its _light twin), independent of the accent theme:
// lamplight at night; by day sunlight in a warm mist, with deep bronze letters lit by a golden glow
private fun byDay(day: Long, night: Long) = Color(if (AppThemeManager.isLight) day else night)
private val LightBackground: Color get() = byDay(0xFFEEE3CF, 0xFF0F0B09)
// By day a warm white, not yellow: the light is the cross's, not a tint over the page
private val LightGlow: Color get() = byDay(0xFFFFF4DC, 0xFFFFDEB2)
private val LightPale: Color get() = byDay(0xFF5A4632, 0xFFF6E2C5)
// By day the words in deep gold, the name and reference in a brighter one
private val VerseColor: Color get() = byDay(0xFF6B430C, 0xFFFBEAD3)
private val SoftGold: Color get() = byDay(0xFFA87418, 0xFFE6C08D)
/** The verse's reference: by day a deeper gold than the name above, as in the design. */
private val RefGold: Color get() = byDay(0xFF8A6118, 0xFFE6C08D)
/** What the letters flash toward as the light reveals them. */
private val RevealFlash: Color get() = byDay(0xFFB8782E, 0xFFFFFFFF)

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
    val letterColor = lerp(color, RevealFlash, exposure)
    // By day the words settle without a glow, as in the design; the light only swells through them in the reveal
    val settled = if (AppThemeManager.isLight) exposure else 1f
    val haloStrength = (haloAlpha * (1f + exposure) * settled).coerceAtMost(1f)
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
                shadow = Shadow(LightGlow.copy(alpha = (0.75f + 0.25f * exposure) * settled), Offset.Zero, glowPx)
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
            // By day the sun's glow over the mist, as behind the app's own screens
            // By day the page is brightest around the cross and settles into soft warm shade at the edges: the cross is
            // the source of the light, with no disc or tint laid over the page
            .then(if (AppThemeManager.isLight) Modifier.drawBehind {
                drawRect(
                    Brush.radialGradient(
                        0f to Color(0xFFFBF6EC), 0.18f to Color(0xFFF6EEDF), 0.4f to Color(0xFFEEE3CF),
                        0.68f to Color(0xFFE4D5BB), 1f to Color(0xFFD9C7A8),
                        center = Offset(size.width / 2f, size.height * 0.27f),
                        radius = maxOf(size.width, size.height) * 0.9f
                    )
                )
                drawDayMotes()
            } else Modifier.background(LightBackground))
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
                Icon(Icons.Default.Close, contentDescription = "Close", tint = if (AppThemeManager.isLight) Color(0xFF9A8468) else LightPale.copy(alpha = 0.35f))
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
                color = RefGold,
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
// The cross: the splash icon's ribbon golds, the same by day and by night
private val RibbonGold: List<Color> = listOf(Color(0xFFD9A86E), Color(0xFFEBCB9C), Color(0xFFF6E2C5), Color(0xFFFFF8EF))
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
        if (AppThemeManager.isLight) {
            drawDayCross(glow, breath)
            return@Canvas
        }
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

/**
 * The cross by day, as the design draws it (a 120 x 170 cross, beams 18 wide, the bar 42 from the top): the upright
 * gold lit from the right, the bar lit from above, a fine white edge inside and three layers of warm light around.
 * Drawn as one shape, so the glow and the edge follow the whole outline, and where the beams meet a lit heart joins
 * the two golds without a seam: the cross is where the light comes from.
 */
private fun DrawScope.drawDayCross(glow: Float, breath: Float) {
    val h = size.height
    val k = h / 170f
    val cx = size.width / 2f
    val beam = 18f * k
    val r = beam / 2f
    val arm = 60f * k
    val barTop = 42f * k
    val upright = Path().apply { addRoundRect(RoundRect(cx - r, 0f, cx + r, h, CornerRadius(r))) }
    val bar = Path().apply { addRoundRect(RoundRect(cx - arm, barTop, cx + arm, barTop + beam, CornerRadius(r))) }
    val cross = Path().apply { op(upright, bar, PathOperation.Union) }

    // The design's three glows (blur, spread, colour, strength), widest first, each as soft strokes of the outline
    val glows = listOf(
        Triple(90f to 30f, Color(0xFFFFECC4), 0.4f),
        Triple(36f to 10f, Color(0xFFFFE8B4), 0.7f),
        Triple(10f to 3f, Color(0xFFFFF8E1), 0.95f)
    )
    val steps = 10
    for ((blurSpread, color, strength) in glows) {
        val (blur, spread) = blurSpread
        for (i in steps - 1 downTo 0) {
            val t = (i + 0.5f) / steps
            val width = 2f * (spread + blur * t) * k
            val alpha = (strength * (1f - t) * 2.2f / steps * glow).coerceIn(0f, 1f)
            drawPath(cross, color.copy(alpha = alpha), style = Stroke(width = width, join = StrokeJoin.Round))
        }
    }

    // The gold: the bar lit from above, the upright from the right
    drawPath(bar, Brush.verticalGradient(listOf(Color(0xFFF7DB96), Color(0xFFD9A23F)), startY = barTop, endY = barTop + beam))
    drawPath(
        upright,
        Brush.horizontalGradient(0f to Color(0xFFC99134), 0.55f to Color(0xFFF2CF7E), 1f to Color(0xFFFFF0C8), startX = cx - r, endX = cx + r)
    )
    clipPath(cross) {
        // Where the beams meet: their light's heart, joining the two golds
        val heart = Offset(cx, barTop + r)
        drawCircle(
            Brush.radialGradient(
                0f to Color(0xFFFFF8E4).copy(alpha = 0.95f * breath),
                0.45f to Color(0xFFFCE6AE).copy(alpha = 0.75f * breath),
                1f to Color(0xFFF6D88E).copy(alpha = 0f),
                center = heart,
                radius = beam * 1.25f
            ),
            radius = beam * 1.25f,
            center = heart
        )
        // A fine white edge just inside the whole outline, no line across the meeting
        drawPath(cross, Color.White.copy(alpha = 0.7f), style = Stroke(width = 2.dp.toPx()))
    }
}

/** Motes of light drifting in the warm haze by day, thickest near the cross, as dust catches sunlight (the design's). */
private val DayMotes = listOf(
    floatArrayOf(96f, 150f, 6f, 0.8f), floatArrayOf(282f, 128f, 5f, 0.7f), floatArrayOf(140f, 96f, 4f, 0.6f),
    floatArrayOf(250f, 210f, 7f, 0.85f), floatArrayOf(78f, 250f, 4f, 0.55f), floatArrayOf(318f, 270f, 5f, 0.6f),
    floatArrayOf(170f, 300f, 3f, 0.7f), floatArrayOf(226f, 92f, 3f, 0.65f), floatArrayOf(120f, 330f, 5f, 0.5f),
    floatArrayOf(300f, 180f, 3f, 0.75f)
)

private fun DrawScope.drawDayMotes() {
    // The design's board is 390 x 844
    val sx = size.width / 390f
    val sy = size.height / 844f
    for ((x, y, s, o) in DayMotes.map { listOf(it[0], it[1], it[2], it[3]) }) {
        val radius = s * 1.5f / 2f * minOf(sx, sy) * 1.4f
        val center = Offset((x + s * 0.75f) * sx, (y + s * 0.75f) * sy)
        drawCircle(
            Brush.radialGradient(
                0f to Color.White.copy(alpha = o * 0.8f),
                0.45f to Color.White.copy(alpha = o * 0.8f * 0.55f),
                0.7f to Color.Transparent,
                center = center,
                radius = radius
            ),
            radius = radius,
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
