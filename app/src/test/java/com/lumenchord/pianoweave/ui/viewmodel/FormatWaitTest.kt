package com.lumenchord.pianoweave.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatWaitTest {

    @Test
    fun waitsReadNaturally() {
        assertEquals("a minute", formatWait(20))
        assertEquals("a minute", formatWait(60))
        assertEquals("25 minutes", formatWait(25 * 60 - 10))
        assertEquals("about an hour", formatWait(3600))
        assertEquals("about 3 hours", formatWait(3 * 3600 + 600))
        assertEquals("about 24 hours", formatWait(86400))
    }
}
