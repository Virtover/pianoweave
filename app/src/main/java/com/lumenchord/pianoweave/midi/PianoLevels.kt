package com.lumenchord.pianoweave.midi

import android.content.SharedPreferences
import com.lumenchord.pianoweave.util.AppPrefs

/**
 * Which servers make difficulty levels for piano jobs (server-info `piano_levels: true`), by URL as the app
 * uses it. Written at each server-info check, read where a completed piano job is saved, the worker included.
 */
object PianoLevels {

    private fun key(url: String) = url.trim().trimEnd('/')

    fun remember(prefs: SharedPreferences, serverUrl: String, makesThem: Boolean) {
        val known = prefs.getStringSet(AppPrefs.PIANO_LEVELS_SERVERS, emptySet()).orEmpty()
        val next = if (makesThem) known + key(serverUrl) else known - key(serverUrl)
        if (next != known) prefs.edit().putStringSet(AppPrefs.PIANO_LEVELS_SERVERS, HashSet(next)).apply()
    }

    fun isMadeBy(prefs: SharedPreferences, serverUrl: String): Boolean =
        key(serverUrl) in prefs.getStringSet(AppPrefs.PIANO_LEVELS_SERVERS, emptySet()).orEmpty()
}
