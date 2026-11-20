package com.lumenchord.pianoweave.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Progress kept with the Google account: moving older values in, and merging the account's copy both ways. */
class AccountProgressTest {

    private fun FakePrefs.flag(key: String, value: Boolean, at: Long) = apply {
        edit().putBoolean(key, value).putLong(key + AppPrefs.PROGRESS_CHANGED_AT_SUFFIX, at).apply()
    }

    private fun FakePrefs.count(key: String, value: Int, at: Long) = apply {
        edit().putInt(key, value).putLong(key + AppPrefs.PROGRESS_CHANGED_AT_SUFFIX, at).apply()
    }

    @Test
    fun aNewDeviceTakesEverythingFromTheAccount() {
        val account = FakePrefs()
            .flag(AppPrefs.HAS_SEEN_PIANO_ROLL_TUTORIAL, true, at = 10)
            .flag(AppPrefs.IS_LIGHT_THEME_UNLOCKED, true, at = 20)
            .count(AppPrefs.VERSE_OF_LIGHT_OPEN_COUNT, 2, at = 30)
        val device = FakePrefs()
        assertTrue(AccountProgress.mergeJson(device, AccountProgress.toJson(account)))
        assertTrue(device.getBoolean(AppPrefs.HAS_SEEN_PIANO_ROLL_TUTORIAL, false))
        assertTrue(device.getBoolean(AppPrefs.IS_LIGHT_THEME_UNLOCKED, false))
        assertEquals(2, device.getInt(AppPrefs.VERSE_OF_LIGHT_OPEN_COUNT, 0))
        // Merging again changes nothing
        assertFalse(AccountProgress.mergeJson(device, AccountProgress.toJson(account)))
    }

    @Test
    fun theLaterChangeWins() {
        // The walkthrough's path brought back here (Undo) after it was finished on another device
        val device = FakePrefs().flag(AppPrefs.HAS_SEEN_WELCOME_TUTORIAL, false, at = 200)
        val account = FakePrefs().flag(AppPrefs.HAS_SEEN_WELCOME_TUTORIAL, true, at = 100)
        assertFalse(AccountProgress.mergeJson(device, AccountProgress.toJson(account)))
        assertFalse(device.getBoolean(AppPrefs.HAS_SEEN_WELCOME_TUTORIAL, true))

        val later = FakePrefs().flag(AppPrefs.HAS_SEEN_WELCOME_TUTORIAL, true, at = 300)
        assertTrue(AccountProgress.mergeJson(device, AccountProgress.toJson(later)))
        assertTrue(device.getBoolean(AppPrefs.HAS_SEEN_WELCOME_TUTORIAL, false))
    }

    @Test
    fun countsOnlyGrow() {
        val device = FakePrefs().count(AppPrefs.VERSE_OF_LIGHT_OPEN_COUNT, 2, at = 500)
        val account = FakePrefs().count(AppPrefs.VERSE_OF_LIGHT_OPEN_COUNT, 1, at = 900)
        assertFalse(AccountProgress.mergeJson(device, AccountProgress.toJson(account)))
        assertEquals(2, device.getInt(AppPrefs.VERSE_OF_LIGHT_OPEN_COUNT, 0))
    }

    @Test
    fun unknownOrBrokenFilesAreIgnored() {
        val device = FakePrefs().flag(AppPrefs.IS_LIGHT_THEME_UNLOCKED, true, at = 1)
        for (json in listOf("", "not json", "[]", """{"values": 3}""",
            """{"version": 9, "values": {"future_key": {"value": 1, "at": 5}, "is_light_theme_unlocked": {"value": "yes"}}}""")) {
            assertFalse(json, AccountProgress.mergeJson(device, json))
        }
        assertTrue(device.getBoolean(AppPrefs.IS_LIGHT_THEME_UNLOCKED, false))
    }

    @Test
    fun olderVersionsProgressMovesIntoItsOwnFile() {
        val main = FakePrefs()
        main.edit()
            .putBoolean(AppPrefs.HAS_SEEN_PIANO_ROLL_TUTORIAL, true)
            .putBoolean(AppPrefs.HAS_OPENED_VERSE_OF_LIGHT, true)
            .putString("selected_theme_id", "gold")
            .apply()
        val progress = FakePrefs()
        AccountProgress.migrate(main, progress)
        assertTrue(progress.getBoolean(AppPrefs.HAS_SEEN_PIANO_ROLL_TUTORIAL, false))
        assertEquals(1, progress.getInt(AppPrefs.VERSE_OF_LIGHT_OPEN_COUNT, 0))
        assertEquals(setOf("selected_theme_id"), main.all.keys)
        // Moved with the oldest date, so anything the account already has wins
        val account = FakePrefs().flag(AppPrefs.HAS_SEEN_PIANO_ROLL_TUTORIAL, false, at = 1)
        assertTrue(AccountProgress.mergeJson(progress, AccountProgress.toJson(account)))
    }

    @Test
    fun aNewUsersFirstLaunchWritesNoMainPreferences() {
        val main = FakePrefs()
        AccountProgress.migrate(main, FakePrefs())
        assertTrue(main.all.isEmpty())
    }
}
