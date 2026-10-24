package com.lumenchord.pianoweave

import androidx.compose.ui.graphics.Color
import com.lumenchord.pianoweave.ui.components.glowScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlowScaleTest {

    private fun scale(argb: Long) = glowScale(Color(argb))

    @Test
    fun calmAccentsKeepMostOfTheGlow() {
        assertEquals(0.86f, scale(0xFFB76E79), 0.03f) // muted rose
        assertEquals(0.84f, scale(0xFF90A4AE), 0.03f) // slate
        assertEquals(0.71f, scale(0xFF3A9A6E), 0.03f) // leaf green
    }

    @Test
    fun vividRedsAndVioletsKeepTheLeast() {
        assertEquals(0.4f, scale(0xFFE53935), 0.01f) // vivid scarlet
        assertEquals(0.4f, scale(0xFFFF3D00), 0.01f) // vivid flame
        assertEquals(0.4f, scale(0xFF8E24AA), 0.01f) // vivid purple
    }

    @Test
    fun brightAccentsAreDimmedByBrightness() {
        assertEquals(0.4f, scale(0xFFECEFF1), 0.01f) // near-white silver: no color, all haze
        assertEquals(0.42f, scale(0xFFFFD93D), 0.03f) // bright yellow
    }

    @Test
    fun noThemeKeepsTheFullGlowAndNoneLosesIt() {
        val gold = scale(0xFFD4AF37) // old gold
        assertTrue("Classic Gold is $gold", gold in 0.5f..0.7f)
        listOf(0xFF009688, 0xFF00ACC1, 0xFF5C6BC0).forEach { assertTrue(scale(it) in 0.4f..0.9f) }
    }
}
