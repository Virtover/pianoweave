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
    val palette: NeutralPalette = NeutralPalette.Slate,
    /** A gentle, living touch on the falling-notes stage, taken from the theme's own image. */
    val stageOrnament: StageOrnament = StageOrnament.None
)

/** What moves on a theme's falling-notes stage, behind the notes. Each is faint, slow and never in the way. */
enum class StageOrnament {
    None,
    /** A night sky: still stars that twinkle, fading toward the horizon (Night Watch). */
    Stars
}

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
         * [textStrength] tints the text greys separately: a strongly colored night or dusk keeps
         * its text near neutral, since tinting greys that much also dims them.
         */
        fun tinted(hue: Float, strength: Float = 1f, depth: Float = 1f, textStrength: Float = strength): NeutralPalette {
            fun Color.retint(isDark: Boolean = true): Color {
                val hsv = FloatArray(3)
                AndroidColor.colorToHSV(toArgb(), hsv)
                val value = if (isDark) hsv[2] * depth else hsv[2]
                val saturation = hsv[1] * if (isDark) strength else textStrength
                return Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f))))
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
        primary = Color(0xFFFAD093),
        customWaitTarget = Color(0xFF5CCFFF),
        customSuccess = Color(0xFF4ADE80),
        customBaseline = Color(0xFFFF8A5C),
        palette = NeutralPalette.tinted(hue = 28f, strength = 1.35f, depth = 0.88f)
    )

    /** Reverence before His glory (Psalm 29:2). */
    private val classicGoldTheme = createTheme(
        id = "gold",
        name = "Classic Gold",
        primary = Color(0xFFDBB955),
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
        createTheme("sunburst", "Morning Joy", Color(0xFFF1E065)), // Joy that comes in the morning (Ps 30:5)
        createTheme("orange", "Dayspring", Color(0xFFFDB080)), // Hope at first light (Lk 1:78)
        // Holy awe, fire that does not consume (Ex 3:2). Warm through and through: an ember-gold keyboard
        // line rather than the derived crimson, and backgrounds that glow faintly like a fireside.
        createTheme(
            "solar", "Burning Bush", Color(0xFFF87840),
            customBaseline = Color(0xFFFFB258),
            palette = NeutralPalette.tinted(hue = 22f, strength = 1.3f, depth = 0.92f)
        ),
        // Joy at the reaping: "Those who sow with tears will reap with songs of joy; those who go out weeping,
        // carrying seed to sow, will return with songs of joy, carrying sheaves with them" (Ps 126:5-6). Sun-ripe
        // wheat in a golden field: the stage glows like the field at sunset, the keyboard line is the sheaves.
        // Its sibling, Sowing in Tears, is the first half of the same psalm.
        createTheme(
            "goldenrod", "Harvest", Color(0xFFFCA942),
            customBaseline = Color(0xFFFBD452),
            palette = NeutralPalette.tinted(hue = 40f, strength = 1.5f),
            stage = listOf(Color(0xFF170F07), Color(0xFF22170A), Color(0xFF3A270C), Color(0xFF2A2014))
        ),
        createTheme("crimson", "Scarlet Cord", Color(0xFFF47C84)), // Redemption held by faith (Josh 2:18)
        createTheme("rose", "Rose of Sharon", Color(0xFFF4A1CF)), // Tender love (Song 2:1)
        createTheme("cherry", "Almond Blossom", Color(0xFFF8CAE0)), // Renewal, the first to wake (Num 17:8)
        createTheme("lavender", "Lavender", Color(0xFFCDB7E2)), // Rest for the soul (Mt 11:29)
        createTheme("electric_purple", "Iris", Color(0xFFBE9FF1)), // Wonder (Ps 8:3)
        createTheme("royal_violet", "Royal Robe", Color(0xFFCD81D6)), // Dignity, a royal priesthood (1 Pet 2:9)
        // Keeping watch: "I meditate on you through the watches of the night" (Ps 63:6), waiting "more
        // than watchmen wait for the morning" (Ps 130:6). One of the few strongly colored themes: a true
        // night-navy all around, moonlight as the accent, and the watchman's lamp as the keyboard line,
        // in the amber of Moods.Watch.
        createTheme(
            "deep_velvet", "Night Watch", Color(0xFFB2D0FC),
            customBaseline = Moods.Watch.core,
            palette = NeutralPalette.tinted(hue = 222f, strength = 3.4f, depth = 0.95f, textStrength = 1.3f),
            // A night sky: darkest overhead, a faint deep-blue horizon above the keys where the lamp sits
            stage = listOf(Color(0xFF01030A), Color(0xFF040A1A), Color(0xFF0B1631), Color(0xFF101A33)),
            stageOrnament = StageOrnament.Stars
        ),
        // Warm evening communion with God: "may my prayer be set before you like incense, the lifting up
        // of my hands like the evening sacrifice" (Ps 141:2). One of the few strongly colored themes: the
        // whole room in a deep, warm dusk plum, the afterglow of sunset as the accent, and His light as a
        // soft gold keyboard line.
        createTheme(
            "indigo", "Evening Prayer", Color(0xFFE79F9C),
            customBaseline = Color(0xFFF5D38E),
            palette = NeutralPalette.tinted(hue = 328f, strength = 3f, depth = 1f, textStrength = 1.3f)
        ),
        createTheme("cyan", "Living Water", Color(0xFF6DE1F6)), // Refreshment, thirst met (Jn 4:10)
        // Gentle grace: "I will be like the dew to Israel; he will blossom like a lily" (Hos 14:5). Dew on a
        // garden at sunrise, not frost: a soft fresh sage, a sunrise-peach keyboard line, a warm green morning.
        createTheme(
            "neon_cyan", "Morning Dew", Color(0xFFBDDBBA),
            customBaseline = Color(0xFFFFC29A),
            palette = NeutralPalette.tinted(hue = 95f, strength = 1.2f),
            stage = listOf(Color(0xFF0C110E), Color(0xFF121812), Color(0xFF2A1E16), Color(0xFF1C221B))
        ),
        // Calm, being led: "He leads me beside still waters, He restores my soul" (Ps 23:2-3). Warm, not cold:
        // the water stays blue, but evening sunlight lies on it as a soft golden keyboard line, and the room
        // is gently warm.
        createTheme(
            "aqua", "Still Waters", Color(0xFF82C5DC),
            customBaseline = Color(0xFFF2CB83),
            palette = NeutralPalette.tinted(hue = 32f, strength = 1f)
        ),
        createTheme("mint", "Spring Rain", Color(0xFF79DFB7)), // Blessing that falls in season (Joel 2:23)
        createTheme("emerald", "Green Pastures", Color(0xFF7ECB79)), // Provision and rest (Ps 23:2)
        // New beginnings: "I am doing a new thing; now it springs up" (Is 43:19). Growth out of the earth: the
        // lime notes are young shoots, the keyboard line is rich soil with their light breaking through at
        // its center, and the stage darkens toward the soil below.
        createTheme(
            "lime", "New Growth", Color(0xFFB5E171),
            customBaseline = Color(0xFFA77C56),
            stage = listOf(Color(0xFF07090A), Color(0xFF0A100B), Color(0xFF19140D), Color(0xFF1C1A14))
        ),
        // Faithfulness, flourishing: "like a green olive tree in the house of God" (Ps 52:8). The stage is the
        // tree: the shade of silvery olive leaves above, the warm dark of roots and earth below, and the
        // keyboard line its trunk, in grey-brown bark.
        createTheme(
            "olive", "Olive Tree", Color(0xFFACB470),
            customBaseline = Color(0xFFB19270),
            stage = listOf(Color(0xFF0E120C), Color(0xFF10120D), Color(0xFF17120C), Color(0xFF1A1D16))
        ),
        createTheme("champagne", "Fine Linen", Color(0xFFF8E8CE), tint = 0.6f), // Purity (Rev 19:8)
        // Security in a living God: "The Lord is my rock, my fortress and my deliverer" (Ps 18:2). Not dead
        // stone: sun-warmed rock, and the keyboard line is the water that flowed from it (Ex 17:6), for
        // "that rock was Christ" (1 Cor 10:4).
        createTheme(
            "titanium", "Rock of Refuge", Color(0xFFC8B49C),
            customBaseline = Color(0xFF4DDAE9),
            palette = NeutralPalette.tinted(hue = 34f, strength = 0.9f)
        ),

        // Deep themes, for evenings and quieter hearts: darker backgrounds and lower, still readable
        // accents. Like the lament psalms, each holds sorrow or night together with trust.
        createTheme( // Longing in sorrow, "deep calls to deep" (Ps 42:7)
            "deep_waters", "Deep Waters", Color(0xFF5F9FD2),
            palette = NeutralPalette.tinted(hue = 215f, strength = 1.2f, depth = DEEP_THEME_DEPTH)
        ),
        createTheme( // Communion by candlelight (Lk 22:12)
            "upper_room", "Upper Room", Color(0xFFE79A51),
            customBaseline = Color(0xFFFEBD56),
            palette = NeutralPalette.tinted(hue = 32f, strength = 1.3f, depth = DEEP_THEME_DEPTH)
        ),
        // Through the valley (Ps 23:4): sorrow, fear, anger and wrath, each held where Jesus meets it. Strongly
        // colored dark rooms, like Night Watch; text stays near neutral so it reads easily.
        // Sorrow carried in hope, the first half of Harvest's psalm: "those who go out weeping, carrying seed
        // to sow" (Ps 126:5-6); and Jesus: "unless a kernel of wheat falls to the ground and dies, it remains
        // only a single seed. But if it dies, it produces many seeds" (Jn 12:24). An overcast room, notes
        // falling like rain-grey tears, and a keyboard line of earth holding the seed's hidden gold.
        createTheme(
            "sowing_in_tears", "Sowing in Tears", Color(0xFFA7B6C6),
            customBaseline = Color(0xFFC7A667),
            palette = NeutralPalette.tinted(hue = 215f, strength = 1.6f, depth = 0.85f, textStrength = 1.3f),
            stage = listOf(Color(0xFF0E1218), Color(0xFF12161C), Color(0xFF1C1610), Color(0xFF1A1E25))
        ),
        // Sorrow, grief: "a man of sorrows, and acquainted with grief" (Is 53:3); "Jesus wept" (Jn 11:35).
        // A deep night of grief in grey violet, and a gentle rose keyboard line: His compassion, weeping with us.
        createTheme(
            "man_of_sorrows", "Man of Sorrows", Color(0xFFA8A5D2),
            customBaseline = Color(0xFFEDA7B9),
            palette = NeutralPalette.tinted(hue = 250f, strength = 2.2f, depth = 0.8f, textStrength = 1.3f),
            stage = listOf(Color(0xFF08070F), Color(0xFF0E0C18), Color(0xFF171426), Color(0xFF17152A))
        ),
        // Fear, and the One who stills it: "Peace, be still" (Mk 4:39); "Take courage! It is I. Don't be afraid"
        // (Mt 14:27). A storm-dark sea, notes the pale of foam, and a gold keyboard line: He is in the boat.
        createTheme(
            "storm_on_the_lake", "Storm on the Lake", Color(0xFFA4E1E4),
            customBaseline = Color(0xFFEFC469),
            palette = NeutralPalette.tinted(hue = 195f, strength = 2.4f, depth = 0.8f, textStrength = 1.3f),
            stage = listOf(Color(0xFF080E12), Color(0xFF0B181D), Color(0xFF0F2A30), Color(0xFF12242A))
        ),
        // Anger that is holy: "Zeal for your house will consume me" (Jn 2:17, Ps 69:9), as He clears the temple;
        // He "looked around at them in anger, deeply distressed at their stubborn hearts" (Mk 3:5). A smoldering
        // ember room, copper-flame notes, and a keyboard line of temple gold: anger for what is holy.
        createTheme(
            "zeal", "Zeal for Your House", Color(0xFFE57669),
            customBaseline = Color(0xFFF4CC64),
            palette = NeutralPalette.tinted(hue = 10f, strength = 2.6f, depth = 0.85f, textStrength = 1.3f),
            stage = listOf(Color(0xFF120605), Color(0xFF1E0A07), Color(0xFF3A1408), Color(0xFF2A120E))
        ),
        // Wrath, borne by Him: "My Father, if it is possible, may this cup be taken from me. Yet not as I will"
        // (Mt 26:39); the cup of His wrath (Is 51:17); "we shall be saved from God's wrath through him" (Rom 5:9).
        // A near-black wine room, deep garnet notes, and a keyboard line of pale dawn: after the cup, "It is
        // finished", and the morning.
        createTheme(
            "the_cup", "The Cup", Color(0xFFDC7D96),
            customBaseline = Color(0xFFF1E4BF),
            palette = NeutralPalette.tinted(hue = 345f, strength = 2.8f, depth = 0.72f, textStrength = 1.3f),
            stage = listOf(Color(0xFF080305), Color(0xFF120509), Color(0xFF200710), Color(0xFF1E0A12))
        ),
    )

    /**
     * The themes as the picker shows them: by the feeling each expresses, so neighbors read as kin and
     * the choice is first of a feeling, then of a color.
     */
    val themeGroups: List<Pair<String, List<AppTheme>>> = listOf(
        "Joy & Glory" to listOf("lamplight", "gold", "sunburst", "goldenrod"),
        // Right after Harvest, so its sibling Sowing in Tears sits beside it; and, as in Psalm 23, the valley
        // comes before the green pastures and still waters
        "Through the Valley" to listOf("sowing_in_tears", "man_of_sorrows", "storm_on_the_lake", "zeal", "the_cup"),
        "Hope & Love" to listOf("orange", "crimson", "rose", "cherry"),
        "Awe & Wonder" to listOf("solar", "royal_violet", "electric_purple"),
        "Peace & Rest" to listOf("lavender", "aqua", "cyan", "neon_cyan", "mint", "emerald"),
        "Faithfulness" to listOf("lime", "olive", "titanium", "champagne"),
        "Evening & Night" to listOf("indigo", "upper_room", "deep_velvet", "deep_waters"),
    ).map { (feeling, ids) -> feeling to ids.map { id -> themes.first { it.id == id } } }

    /** How much darker the deep themes' backgrounds are than the others'. */
    private const val DEEP_THEME_DEPTH = 0.8f

    /**
     * Themes that were merged into another of the same feeling, so their colors could stay distinct. A
     * saved choice of one moves to the theme that took its place instead of falling back to the default.
     */
    private val mergedThemeIds = mapOf(
        "amber" to "gold",
        "coral" to "solar",
        "copper" to "goldenrod",
        "ruby" to "crimson",
        "chartreuse" to "royal_violet",
        "magenta" to "orange",
        "rose_gold" to "cherry",
        "midnight" to "deep_velvet",
        "sapphire" to "deep_waters",
        "electric_blue" to "aqua",
        "cyber_turquoise" to "aqua",
        "teal" to "emerald",
        "toxic_green" to "emerald",
        "silver" to "champagne",
        "gethsemane" to "olive",
        "beauty_for_ashes" to "upper_room",
        "shadow_of_wings" to "deep_velvet"
    )

    /** The theme id to use for a saved [id], following merged themes to their replacement. */
    fun resolveId(id: String?): String? = mergedThemeIds[id] ?: id

    fun getTheme(id: String?): AppTheme {
        val resolved = resolveId(id)
        return themes.find { it.id == resolved } ?: defaultTheme
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
        palette: NeutralPalette? = null,
        /** The falling-notes stage from top to bottom, then its lane lines; the palette's own otherwise. */
        stage: List<Color>? = null,
        stageOrnament: StageOrnament = StageOrnament.None
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
            palette = (palette ?: if (sat < 0.15f || tint <= 0f) NeutralPalette.Slate else NeutralPalette.tinted(hue, strength = tint))
                .let { base ->
                    if (stage == null) base
                    else base.copy(stageTop = stage[0], stageMiddle = stage[1], stageBottom = stage[2], stageLane = stage[3])
                },
            stageOrnament = stageOrnament
        )
    }
}

val LocalAppTheme = compositionLocalOf { AppThemeManager.defaultTheme }
