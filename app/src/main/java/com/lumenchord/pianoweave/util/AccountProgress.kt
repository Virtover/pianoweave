package com.lumenchord.pianoweave.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * What someone has done or found in the app (tutorials finished, the light themes unlocked, the verse screen
 * found), kept with their Google account and not only on this device: in Android's backup of the app (the
 * progress has its own preferences file, which Auto Backup keeps with the device's Google account), and, with
 * cloud sync on, merged with a copy in the app's Drive folder ([com.lumenchord.pianoweave.cloud.AccountProgressSync]).
 *
 * Each value carries when it last changed. Merging keeps the later one, except counts, which only grow. Debug builds
 * can ignore all of it (Settings, [AppPrefs.IGNORE_ACCOUNT_PROGRESS]): progress then lives in a stand-in that
 * starts empty and never syncs.
 *
 * Only what was done or found (unlocks, tutorials) belongs here, never choices such as the theme or Dark / Light:
 * those stay with each device.
 */
object AccountProgress {

    /** Yes/no progress; merged by the later change, as the walkthrough's path can be brought back (Undo). */
    val flagKeys = listOf(
        AppPrefs.HAS_SEEN_PIANO_ROLL_TUTORIAL,
        AppPrefs.HAS_SEEN_WELCOME_TUTORIAL,
        AppPrefs.IS_LIGHT_THEME_UNLOCKED,
    )

    /** Counts; merged by the larger, unless one side was reset after the other's last change (see [resetCount]). */
    val countKeys = listOf(AppPrefs.VERSE_OF_LIGHT_OPEN_COUNT)

    /** Goes up whenever progress changes from outside the screen showing it (a merge, the debug switch): reread then. */
    var revision by mutableIntStateOf(0)
        private set

    private fun atKey(key: String) = key + AppPrefs.PROGRESS_CHANGED_AT_SUFFIX
    private fun resetKey(key: String) = key + AppPrefs.PROGRESS_RESET_AT_SUFFIX

    fun isIgnoringAccount(context: Context): Boolean =
        AppPrefs.get(context).getBoolean(AppPrefs.IGNORE_ACCOUNT_PROGRESS, false)

    fun getFlag(context: Context, key: String): Boolean = AppPrefs.progress(context).getBoolean(key, false)

    fun getCount(context: Context, key: String): Int = AppPrefs.progress(context).getInt(key, 0)

    fun setFlag(context: Context, key: String, value: Boolean) {
        AppPrefs.progress(context).edit().putBoolean(key, value).putLong(atKey(key), System.currentTimeMillis()).apply()
    }

    fun setCount(context: Context, key: String, value: Int) {
        AppPrefs.progress(context).edit().putInt(key, value).putLong(atKey(key), System.currentTimeMillis()).apply()
    }

    /**
     * Debug builds: [key]'s count back to 0. Counts otherwise only grow in a merge, so the reset carries its time:
     * newer than the other side's last change, it wins there too.
     */
    fun resetCount(context: Context, key: String) = resetCount(AppPrefs.progress(context), key, System.currentTimeMillis())

    internal fun resetCount(prefs: SharedPreferences, key: String, now: Long) {
        prefs.edit().putInt(key, 0).putLong(atKey(key), now).putLong(resetKey(key), now).commit()
        revision++
    }

    /**
     * Moves progress that older versions kept in the main preferences into the progress file, once. Writes the
     * main preferences only when there was something to move, so a new user's first launch stays without
     * preferences (see LegalConsent).
     */
    fun migrate(context: Context) = migrate(AppPrefs.get(context), AppPrefs.realProgress(context))

    internal fun migrate(main: SharedPreferences, progressPrefs: SharedPreferences) {
        val progress = progressPrefs.edit()
        val mainEdit = main.edit()
        var moved = false
        for (key in flagKeys) if (main.contains(key)) {
            progress.putBoolean(key, main.getBoolean(key, false)).putLong(atKey(key), 0L)
            mainEdit.remove(key)
            moved = true
        }
        val legacyOpened = main.getBoolean(AppPrefs.HAS_OPENED_VERSE_OF_LIGHT, false)
        if (main.contains(AppPrefs.VERSE_OF_LIGHT_OPEN_COUNT) || legacyOpened) {
            val count = main.getInt(AppPrefs.VERSE_OF_LIGHT_OPEN_COUNT, if (legacyOpened) 1 else 0)
            progress.putInt(AppPrefs.VERSE_OF_LIGHT_OPEN_COUNT, count).putLong(atKey(AppPrefs.VERSE_OF_LIGHT_OPEN_COUNT), 0L)
            mainEdit.remove(AppPrefs.VERSE_OF_LIGHT_OPEN_COUNT).remove(AppPrefs.HAS_OPENED_VERSE_OF_LIGHT)
            moved = true
        }
        if (moved) {
            progress.commit()
            mainEdit.apply()
        }
    }

    /** Turns the debug switch on or off; on, the stand-in starts empty every time. */
    fun setIgnoringAccount(context: Context, ignore: Boolean) {
        if (ignore) AppPrefs.clearIgnoredProgress(context)
        AppPrefs.get(context).edit().putBoolean(AppPrefs.IGNORE_ACCOUNT_PROGRESS, ignore).apply()
        revision++
    }

    /** The progress as stored in Drive: {"version": 1, "values": {key: {"value": …, "at": epoch ms}}}. */
    fun toJson(context: Context): String = toJson(AppPrefs.progress(context))

    internal fun toJson(prefs: SharedPreferences): String {
        val values = JsonObject()
        for (key in flagKeys + countKeys) {
            if (!prefs.contains(key)) continue
            values.add(key, JsonObject().apply {
                if (key in countKeys) addProperty("value", prefs.getInt(key, 0)) else addProperty("value", prefs.getBoolean(key, false))
                addProperty("at", prefs.getLong(atKey(key), 0L))
                if (key in countKeys) prefs.getLong(resetKey(key), 0L).takeIf { it > 0L }?.let { addProperty("reset", it) }
            })
        }
        return JsonObject().apply {
            addProperty("version", 1)
            add("values", values)
        }.toString()
    }

    /**
     * Merges the account's copy ([json], as [toJson] writes it) into this device's progress. Unknown keys and
     * malformed values are skipped, so a newer version's file never breaks an older one. Returns whether
     * anything here changed.
     */
    fun mergeJson(context: Context, json: String): Boolean = mergeJson(AppPrefs.progress(context), json)

    internal fun mergeJson(prefs: SharedPreferences, json: String): Boolean {
        val values = runCatching { JsonParser.parseString(json).asJsonObject.getAsJsonObject("values") }.getOrNull()
            ?: return false
        val edit = prefs.edit()
        var changed = false
        for (key in flagKeys + countKeys) {
            val entry = runCatching { values.getAsJsonObject(key) }.getOrNull() ?: continue
            val at = runCatching { entry.get("at").asLong }.getOrDefault(0L)
            if (key in countKeys) {
                val remote = runCatching { entry.get("value").asInt }.getOrNull() ?: continue
                val remoteReset = runCatching { entry.get("reset").asLong }.getOrDefault(0L)
                val local = prefs.getInt(key, 0)
                val localAt = prefs.getLong(atKey(key), 0L)
                val localReset = prefs.getLong(resetKey(key), 0L)
                when {
                    // Reset there after anything here: theirs, as it is now
                    remoteReset > localAt && remoteReset > localReset -> {
                        if (remote != local || localReset != remoteReset) {
                            edit.putInt(key, remote).putLong(atKey(key), at).putLong(resetKey(key), remoteReset)
                            changed = true
                        }
                    }
                    // Reset here after their last change: ours stands
                    localReset > 0L && localReset >= at -> Unit
                    remote > local -> {
                        edit.putInt(key, remote).putLong(atKey(key), maxOf(at, localAt))
                        changed = true
                    }
                }
            } else {
                val remote = runCatching { entry.get("value").asBoolean }.getOrNull() ?: continue
                val isNewer = !prefs.contains(key) || at > prefs.getLong(atKey(key), 0L)
                if (isNewer && remote != prefs.getBoolean(key, false)) {
                    edit.putBoolean(key, remote).putLong(atKey(key), at)
                    changed = true
                }
            }
        }
        if (changed) {
            edit.commit()
            revision++
        }
        return changed
    }
}
