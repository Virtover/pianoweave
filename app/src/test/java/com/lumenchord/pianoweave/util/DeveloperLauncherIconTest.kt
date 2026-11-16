package com.lumenchord.pianoweave.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** Every state the two launcher aliases can be left in, also by a crash or power cut between two changes. */
class DeveloperLauncherIconTest {

    /** Applies [DeveloperLauncherIcon.changes] step by step; fails if a step would leave no launcher entry. */
    private fun settle(wantDeveloper: Boolean, defaultEnabled: Boolean, developerEnabled: Boolean): Pair<Boolean, Boolean> {
        var default = defaultEnabled
        var developer = developerEnabled
        val startsWithEntry = default || developer
        for ((isDeveloperAlias, enable) in DeveloperLauncherIcon.changes(wantDeveloper, default, developer)) {
            if (isDeveloperAlias) developer = enable else default = enable
            if (startsWithEntry) assert(default || developer) { "a step left no launcher entry" }
        }
        return default to developer
    }

    @Test
    fun everyStateEndsWithExactlyTheWantedIcon() {
        for (want in listOf(false, true)) for (default in listOf(false, true)) for (developer in listOf(false, true)) {
            val expected = if (want) false to true else true to false
            assertEquals("want=$want default=$default developer=$developer", expected, settle(want, default, developer))
        }
    }

    @Test
    fun theRightStateChangesNothing() {
        assertEquals(emptyList<Pair<Boolean, Boolean>>(), DeveloperLauncherIcon.changes(false, true, false))
        assertEquals(emptyList<Pair<Boolean, Boolean>>(), DeveloperLauncherIcon.changes(true, false, true))
    }

    @Test
    fun twoIconsLeftByACrashAreRepaired() {
        assertEquals(listOf(true to false), DeveloperLauncherIcon.changes(false, true, true))
        assertEquals(listOf(false to false), DeveloperLauncherIcon.changes(true, true, true))
    }
}
