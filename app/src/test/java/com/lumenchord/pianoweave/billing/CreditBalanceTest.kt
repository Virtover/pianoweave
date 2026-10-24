package com.lumenchord.pianoweave.billing

import com.lumenchord.pianoweave.ui.screens.learn.notEnoughCreditsText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditBalanceTest {

    @Test
    fun owedMinutesComeFromANegativeBalance() {
        assertEquals(4, owedMinutes(-4))
        assertEquals(0, owedMinutes(0))
        assertEquals(0, owedMinutes(12))
    }

    @Test
    fun negativeBalanceAffordsNothing() {
        assertFalse(canAfford(-3, 2))
        assertFalse(canAfford(-3, null))
        assertFalse(canAfford(-1, 0))
    }

    @Test
    fun unknownCostNeedsTheServersMinimum() {
        // The server refuses an upload with less than a minute before reading it
        assertFalse(canAfford(0, null))
        assertTrue(canAfford(1, null))
    }

    @Test
    fun knownCostMustBeCovered() {
        assertTrue(canAfford(5, 5))
        assertFalse(canAfford(4, 5))
        assertFalse(canAfford(0, 0))
    }

    @Test
    fun labelSaysWhatIsOwed() {
        assertEquals("12 Credits", creditsLabel(12))
        assertEquals("1 Credit", creditsLabel(1))
        assertEquals("0 Credits", creditsLabel(0))
        assertEquals("Owes 3 Credits", creditsLabel(-3))
        assertEquals("Owes 1 Credit", creditsLabel(-1))
    }

    @Test
    fun notEnoughCreditsExplainsAnOwedBalance() {
        val text = notEnoughCreditsText(required = 3, available = -2)
        assertTrue(text, "requires 3 credits" in text)
        assertTrue(text, "2 credits below zero" in text)
        assertTrue(text, "buy more credits" in text)
    }

    @Test
    fun notEnoughCreditsWithUnknownCost() {
        val text = notEnoughCreditsText(required = null, available = 0)
        assertTrue(text, "at least 1 credit" in text)
        assertTrue(text, "only have 0 credits" in text)
    }
}
