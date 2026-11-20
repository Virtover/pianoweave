package com.lumenchord.pianoweave.util

import com.lumenchord.pianoweave.util.LauncherIcon.Alias
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every state the launcher aliases can be left in, also by a crash or power cut between two changes. */
class LauncherIconTest {

    /** Applies [LauncherIcon.changes] step by step; fails if a step would leave no launcher entry. */
    private fun settle(want: Alias, enabled: List<Boolean>): List<Boolean> {
        val state = enabled.toMutableList()
        val startsWithEntry = state.any { it }
        for ((alias, enable) in LauncherIcon.changes(want, state.toList())) {
            state[alias.ordinal] = enable
            if (startsWithEntry) assert(state.any { it }) { "a step left no launcher entry" }
        }
        return state
    }

    private val allStates: List<List<Boolean>> =
        (0 until (1 shl Alias.entries.size)).map { bits -> Alias.entries.map { bits and (1 shl it.ordinal) != 0 } }

    @Test
    fun everyStateEndsWithExactlyTheWantedIcon() {
        for (want in Alias.entries) for (state in allStates) {
            val expected = Alias.entries.map { it == want }
            assertEquals("want=$want state=$state", expected, settle(want, state))
        }
    }

    @Test
    fun theRightStateChangesNothing() {
        for (want in Alias.entries) {
            assertEquals(emptyList<Pair<Alias, Boolean>>(), LauncherIcon.changes(want, Alias.entries.map { it == want }))
        }
    }

    @Test
    fun twoIconsLeftByACrashAreRepaired() {
        assertEquals(listOf(Alias.Developer to false), LauncherIcon.changes(Alias.Default, listOf(true, true, false)))
        assertEquals(listOf(Alias.Default to false), LauncherIcon.changes(Alias.Light, listOf(true, false, true)))
    }

    @Test
    fun theDeveloperIconWinsOverTheLightOne() {
        assertEquals(Alias.Developer, LauncherIcon.wanted(developer = true, light = true))
        assertEquals(Alias.Light, LauncherIcon.wanted(developer = false, light = true))
        assertEquals(Alias.Default, LauncherIcon.wanted(developer = false, light = false))
    }
}
