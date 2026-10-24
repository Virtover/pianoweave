package com.lumenchord.pianoweave.ui.theme

import android.graphics.Color as AndroidColor
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

data class AppTheme(
    val id: String,
    val name: String,
    val primaryColor: Color,
    val lightColor: Color,
    val upcomingColor: Color,
    val waitTargetColor: Color,
    val waitTargetLightColor: Color,
    val targetColor: Color,
    val baselineColor: Color,
    val baselineGlowColor: Color,
    val successColor: Color,
    val successLightColor: Color,
    val isLightAccent: Boolean = false,
    val palette: NeutralPalette = NeutralPalette.Slate
)

/** The non-accent colors: backgrounds, cards, text and the falling-notes stage. */
data class NeutralPalette(
    val text: Color,
    val onText: Color,
    val container: Color,
    val secondaryContainer: Color,
    val background: Color,
    val surface: Color,
    val dialogSurface: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val muted: Color,
    val mutedContainer: Color,
    val errorContainer: Color,
    val stageTop: Color,
    val stageMiddle: Color,
    val stageBottom: Color,
    val stageLane: Color
) {
    companion object {
        /** Cool piano black, used by every accent theme. */
        val Slate = NeutralPalette(
            text = Color(0xFFECEFF1),
            onText = Color(0xFF101214),
            container = Color(0xFF2C2E33),
            secondaryContainer = Color(0xFF22272E),
            background = Color(0xFF0F1113),
            surface = Color(0xFF16181C),
            dialogSurface = Color(0xFF161B22),
            onSurface = Color(0xFFE3E4E8),
            onSurfaceVariant = Color(0xFF90949F),
            muted = Color(0xFF8B949E),
            mutedContainer = Color(0xFF30363D),
            errorContainer = Color(0xFF221B1C),
            stageTop = Color(0xFF090D14),
            stageMiddle = Color(0xFF0D1117),
            stageBottom = Color(0xFF070A0F),
            stageLane = Color(0xFF161B22)
        )

        /**
         * Slate with its slight tint turned to [hue] (0-360), so the backgrounds quietly take on
         * the theme's own color while keeping Slate's brightness. [strength] scales how much tint
         * there is (1 = as much as Slate's blue) and [depth] below 1 makes the darks deeper.
         */
        fun tinted(hue: Float, strength: Float = 1f, depth: Float = 1f): NeutralPalette {
            fun Color.retint(isDark: Boolean = true): Color {
                val hsv = FloatArray(3)
                AndroidColor.colorToHSV(toArgb(), hsv)
                val value = if (isDark) hsv[2] * depth else hsv[2]
                return Color(AndroidColor.HSVToColor(floatArrayOf(hue, (hsv[1] * strength).coerceIn(0f, 1f), value.coerceIn(0f, 1f))))
            }
            return with(Slate) {
                NeutralPalette(
                    text = text.retint(isDark = false),
                    onText = onText.retint(),
                    container = container.retint(),
                    secondaryContainer = secondaryContainer.retint(),
                    background = background.retint(),
                    surface = surface.retint(),
                    dialogSurface = dialogSurface.retint(),
                    onSurface = onSurface.retint(isDark = false),
                    onSurfaceVariant = onSurfaceVariant.retint(isDark = false),
                    muted = muted.retint(isDark = false),
                    mutedContainer = mutedContainer.retint(),
                    errorContainer = errorContainer,
                    stageTop = stageTop.retint(),
                    stageMiddle = stageMiddle.retint(),
                    stageBottom = stageBottom.retint(),
                    stageLane = stageLane.retint()
                )
            }
        }
    }
}

object AppThemeManager {

    /**
     * "Your word is a lamp to my feet and a light to my path" (Psalm 119:105). Deep and warm, with
     * honey-gold light and lively companion colors: sky blue for notes to play, fresh green for
     * notes played right, a coral hit line.
     */
    private val lamplightTheme = createTheme(
        id = "lamplight",
        name = "Lamplight",
        primary = Color(0xFFF8CC8C),
        customWaitTarget = Color(0xFF5CCFFF),
        customSuccess = Color(0xFF4ADE80),
        customBaseline = Color(0xFFFF8A5C),
        palette = NeutralPalette.tinted(hue = 28f, strength = 1.35f, depth = 0.88f)
    )

    /** Reverence before His glory (Psalm 29:2). */
    private val classicGoldTheme = createTheme(
        id = "gold",
        name = "Classic Gold",
        primary = Color(0xFFD3B555),
        customWaitTarget = Color(0xFF00D2FF),
        customSuccess = Color(0xFF2EA043),
        customBaseline = Color(0xFFFD9B4A),
        tint = 0.8f
    )

    val defaultTheme = classicGoldTheme

    /**
     * Names come from the Bible's imagery of creation, worship and joy. Ids stay as they were, so
     * a saved choice keeps working.
     */
    val themes: List<AppTheme> = listOf(
        lamplightTheme,
        classicGoldTheme,
        createTheme("amber", "Honey", Color(0xFFF3AA36)), // Delight in His word (Ps 19:10)
        createTheme("sunburst", "Morning Joy", Color(0xFFF0DD66)), // Joy that comes in the morning (Ps 30:5)
        createTheme("orange", "Dayspring", Color(0xFFF7AE81)), // Hope at first light (Lk 1:78)
        createTheme("coral", "Hearth", Color(0xFFED8F61)), // Warmth of fellowship (Acts 2:46)
        // Holy awe, fire that does not consume (Ex 3:2). Warm through and through: an ember-gold keyboard
        // line rather than the derived crimson, and backgrounds that glow faintly like a fireside.
        createTheme(
            "solar", "Burning Bush", Color(0xFFF87C49),
            customBaseline = Color(0xFFFFB258),
            palette = NeutralPalette.tinted(hue = 22f, strength = 1.3f, depth = 0.92f)
        ),
        createTheme("copper", "Potter's Clay", Color(0xFFC79170)), // Humility in His hands (Is 64:8)
        createTheme("goldenrod", "Harvest", Color(0xFFC19B54)), // Gratitude, reaping with joy (Ps 126:6)
        createTheme("crimson", "Scarlet Cord", Color(0xFFF27C7E)), // Redemption held by faith (Josh 2:18)
        createTheme("ruby", "Pomegranate", Color(0xFFE680A1)), // Abundant love (Song 4:3)
        createTheme("rose", "Rose of Sharon", Color(0xFFF1A1CD)), // Tender love (Song 2:1)
        createTheme("magenta", "Desert Bloom", Color(0xFFF89993)), // Restoration, the desert in flower (Is 35:1)
        createTheme("cherry", "Almond Blossom", Color(0xFFF5C9DD)), // Renewal, the first to wake (Num 17:8)
        createTheme("rose_gold", "Rose Gold", Color(0xFFCA999F)), // Gentleness (Phil 4:5)
        createTheme("lavender", "Lavender", Color(0xFFD4BCEB)), // Rest for the soul (Mt 11:29)
        createTheme("electric_purple", "Iris", Color(0xFFBD9FF2)), // Wonder (Ps 8:3)
        createTheme("royal_violet", "Royal Robe", Color(0xFFCD85D8)), // Dignity, a royal priesthood (1 Pet 2:9)
        createTheme("deep_velvet", "Night Watch", Color(0xFFA194E7)), // Longing, meditating through the night (Ps 63:6)
        // Communion with God: "may my prayer be set before you like incense" (Ps 141:2). Both sides of the
        // meeting: the prayer rising in a warm dusk lilac, His light answering in a soft gold keyboard
        // line, held in the deep blue of the evening sky.
        createTheme(
            "indigo", "Evening Prayer", Color(0xFFD3A6CE),
            customBaseline = Color(0xFFF5D38E),
            palette = NeutralPalette.tinted(hue = 232f, strength = 1.25f, depth = 0.9f)
        ),
        createTheme("midnight", "Starry Host", Color(0xFFA0BDFF)), // Awe at the stars He names (Ps 147:4)
        createTheme("sapphire", "Sapphire Heaven", Color(0xFF5DA6EF)), // Majesty, holiness (Ex 24:10)
        createTheme("electric_blue", "Open Sky", Color(0xFF75C9F7)), // Praise, the heavens declare (Ps 19:1)
        createTheme("cyan", "Living Water", Color(0xFF72E0F6)), // Refreshment, thirst met (Jn 4:10)
        createTheme("neon_cyan", "Morning Dew", Color(0xFFABECEF)), // Gentle grace (Hos 14:5)
        createTheme("aqua", "Still Waters", Color(0xFF81BFD4)), // Calm, being led (Ps 23:2)
        createTheme("cyber_turquoise", "Galilee", Color(0xFF56C2BA)), // The call to follow (Mt 4:18)
        createTheme("teal", "Riverside", Color(0xFF85C291)), // Steadfastness, rooted by streams (Ps 1:3)
        createTheme("mint", "Spring Rain", Color(0xFF77E3BC)), // Blessing that falls in season (Joel 2:23)
        createTheme("emerald", "Green Pastures", Color(0xFF7FCE7E)), // Provision and rest (Ps 23:2)
        createTheme("toxic_green", "Evergreen", Color(0xFF68B08D)), // Perseverance, leaf that does not wither (Ps 1:3)
        createTheme("lime", "New Growth", Color(0xFFB9E270)), // New beginnings (Is 43:19)
        createTheme("chartreuse", "Vineyard", Color(0xFFD488B8)), // Abiding and fruitfulness (Jn 15:5)
        createTheme("olive", "Olive Tree", Color(0xFFACB16D)), // Faithfulness, flourishing (Ps 52:8)
        createTheme("champagne", "Fine Linen", Color(0xFFF4E6CE), tint = 0.6f), // Purity (Rev 19:8)
        createTheme("silver", "Refined Silver", Color(0xFFCCD2D7)), // The pure word, refined (Ps 12:6)
        createTheme("titanium", "Rock of Refuge", Color(0xFF9DAEB9)), // Security, my refuge (Ps 18:2)

        // Deep themes, for evenings and quieter hearts: darker backgrounds and lower, still readable
        // accents. Like the lament psalms, each holds sorrow or night together with trust.
        createTheme( // Longing in sorrow, "deep calls to deep" (Ps 42:7)
            "deep_waters", "Deep Waters", Color(0xFF5F9FD2),
            palette = NeutralPalette.tinted(hue = 215f, strength = 1.2f, depth = DEEP_THEME_DEPTH)
        ),
        createTheme( // Surrender in anguish, "not my will, but yours" (Lk 22:42)
            "gethsemane", "Gethsemane", Color(0xFF8CA66C),
            palette = NeutralPalette.tinted(hue = 95f, strength = 1f, depth = DEEP_THEME_DEPTH)
        ),
        createTheme( // Mourning turned to hope: a crown of beauty instead of ashes (Is 61:3)
            "beauty_for_ashes", "Beauty for Ashes", Color(0xFFD9897C),
            // Gold rising from the ashes on the keyboard line; backgrounds close to charcoal
            customBaseline = Color(0xFFFEBD56),
            palette = NeutralPalette.tinted(hue = 20f, strength = 0.6f, depth = DEEP_THEME_DEPTH)
        ),
        createTheme( // Communion by candlelight (Lk 22:12)
            "upper_room", "Upper Room", Color(0xFFDB9249),
            customBaseline = Color(0xFFFEBD56),
            palette = NeutralPalette.tinted(hue = 32f, strength = 1.3f, depth = DEEP_THEME_DEPTH)
        ),
        createTheme( // Refuge in the dark, under the shadow of His wings (Ps 91:4)
            "shadow_of_wings", "Shadow of Wings", Color(0xFFB48BCE),
            palette = NeutralPalette.tinted(hue = 285f, strength = 1.1f, depth = DEEP_THEME_DEPTH)
        ),
    )

    /** How much darker the deep themes' backgrounds are than the others'. */
    private const val DEEP_THEME_DEPTH = 0.8f

    fun getTheme(id: String?): AppTheme {
        return themes.find { it.id == id } ?: defaultTheme
    }

    private fun createTheme(
        id: String,
        name: String,
        primary: Color,
        isLightAccent: Boolean = false,
        customWaitTarget: Color? = null,
        customSuccess: Color? = null,
        customBaseline: Color? = null,
        /** How strongly the backgrounds take on the theme's color; 0 keeps plain Slate. */
        tint: Float = 0.9f,
        palette: NeutralPalette? = null
    ): AppTheme {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(primary.toArgb(), hsv)
        val hue = hsv[0]
        val sat = hsv[1]
        val valVal = hsv[2]

        val lightColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, maxOf(0f, sat * 0.4f), minOf(1f, valVal * 1.25f))))
        val upcomingColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, minOf(1f, sat * 1.05f), valVal * 0.85f)))

        val waitTargetColor = customWaitTarget ?: run {
            val targetHue = if (hue in 160f..220f) {
                45f
            } else if (hue in 40f..80f) {
                195f
            } else {
                (hue + 150f) % 360f
            }
            Color(AndroidColor.HSVToColor(floatArrayOf(targetHue, 0.85f, 1f)))
        }

        val waitTargetHsv = FloatArray(3)
        AndroidColor.colorToHSV(waitTargetColor.toArgb(), waitTargetHsv)
        val waitTargetLightColor = Color(AndroidColor.HSVToColor(floatArrayOf(waitTargetHsv[0], 0.3f, 1f)))

        val targetColor = Color(AndroidColor.HSVToColor(floatArrayOf((hue + 15f) % 360f, minOf(1f, sat * 1.1f), minOf(1f, valVal * 1.1f))))
        val baselineColor = customBaseline ?: Color(AndroidColor.HSVToColor(floatArrayOf((hue - 20f + 360f) % 360f, minOf(1f, sat * 1.2f), 1f)))
        val baselineGlowColor = Color(AndroidColor.HSVToColor(floatArrayOf((hue - 30f + 360f) % 360f, sat, valVal * 0.9f)))

        val successColor = customSuccess ?: run {
            if (hue in 80f..160f) {
                Color(0xFF00B0FF)
            } else {
                Color(0xFF2EA043)
            }
        }
        val successLightColor = if (hue in 80f..160f) Color(0xFF80D8FF) else Color(0xFF69C67E)

        // Dark text on the accent wherever it reads better than white: the WCAG contrast of the two
        // is equal at a relative luminance of about 0.18, so soft accents get dark text.
        val resolvedIsLightAccent = isLightAccent || primary.luminance() > 0.179f

        return AppTheme(
            id = id,
            name = name,
            primaryColor = primary,
            lightColor = lightColor,
            upcomingColor = upcomingColor,
            waitTargetColor = waitTargetColor,
            waitTargetLightColor = waitTargetLightColor,
            targetColor = targetColor,
            baselineColor = baselineColor,
            baselineGlowColor = baselineGlowColor,
            successColor = successColor,
            successLightColor = successLightColor,
            isLightAccent = resolvedIsLightAccent,
            // Near-gray accents have no real hue to borrow, so they keep Slate
            palette = palette ?: if (sat < 0.15f || tint <= 0f) NeutralPalette.Slate else NeutralPalette.tinted(hue, strength = tint)
        )
    }
}

val LocalAppTheme = compositionLocalOf { AppThemeManager.defaultTheme }
