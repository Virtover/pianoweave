package com.lumenchord.pianoweave.api

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lumenchord.pianoweave.util.AppPrefs

/**
 * The announcements of the servers the app follows (the selected one, and the official one too when a
 * custom server is selected), observable from Compose.
 *
 * Each server's last good list is kept on the device and stays shown while that server can't be reached,
 * answers 503, or has no announcements endpoint: an announcement may be what tells people how to keep
 * using the app. Dismissed ones are remembered by [Announcement.key] until their server stops listing them.
 */
object Announcements {

    /** Every followed server's announcements, most severe first; filter with [visible]. */
    var all by mutableStateOf<List<Announcement>>(emptyList())
        private set

    var dismissed by mutableStateOf<Set<String>>(emptySet())
        private set

    /**
     * Critical announcements the user hid although they can't be dismissed, after a warning. They leave the
     * full screen for a banner that stays.
     */
    var hiddenCritical by mutableStateOf<Set<String>>(emptySet())
        private set

    /**
     * Hidden critical announcements the user then moved off the Learn screen as well: listed at the bottom of
     * Settings, so they can always be read again.
     */
    var archivedCritical by mutableStateOf<Set<String>>(emptySet())
        private set

    /**
     * Dismissable critical announcements whose pop-up the user closed without dismissing them: not popped up
     * again while the app runs, but still in the banner.
     */
    var poppedUp by mutableStateOf<Set<String>>(emptySet())
        private set

    fun markPoppedUp(announcement: Announcement) {
        poppedUp = poppedUp + announcement.key
    }

    private val lists = mutableMapOf<String, List<Announcement>>()
    private var cache = JsonObject()
    private var servers: List<String> = emptyList()
    private var loaded = false

    /** Reads the kept lists and dismissals, once. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = AppPrefs.get(context)
        dismissed = prefs.getStringSet(AppPrefs.DISMISSED_ANNOUNCEMENTS, null).orEmpty().toSet()
        hiddenCritical = prefs.getStringSet(AppPrefs.HIDDEN_CRITICAL_ANNOUNCEMENTS, null).orEmpty().toSet()
        archivedCritical = prefs.getStringSet(AppPrefs.ARCHIVED_CRITICAL_ANNOUNCEMENTS, null).orEmpty().toSet()
        cache = prefs.getString(AppPrefs.ANNOUNCEMENTS_CACHE, null)
            ?.let { runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull() }
            ?: JsonObject()
        for ((server, list) in cache.entrySet()) {
            if (list.isJsonArray) lists[server] = AnnouncementParser.parseList(list.asJsonArray, server)
        }
        publish()
    }

    /** Shows the announcements of [serverUrls] (normalized base URLs) from now on. */
    fun follow(serverUrls: List<String>) {
        if (serverUrls == servers) return
        servers = serverUrls
        publish()
    }

    /** A server's fresh list ([array] as received): replaces the kept one and forgets ended dismissals. */
    fun onFetched(context: Context, serverUrl: String, array: JsonArray) {
        val list = AnnouncementParser.parseList(array, serverUrl)
        lists[serverUrl] = list
        cache.add(serverUrl, array)
        dismissed = prunedKeys(dismissed, serverUrl, list)
        hiddenCritical = prunedKeys(hiddenCritical, serverUrl, list)
        archivedCritical = prunedKeys(archivedCritical, serverUrl, list)
        AppPrefs.get(context).edit()
            .putString(AppPrefs.ANNOUNCEMENTS_CACHE, cache.toString())
            .putStringSet(AppPrefs.DISMISSED_ANNOUNCEMENTS, dismissed)
            .putStringSet(AppPrefs.HIDDEN_CRITICAL_ANNOUNCEMENTS, hiddenCritical)
            .putStringSet(AppPrefs.ARCHIVED_CRITICAL_ANNOUNCEMENTS, archivedCritical)
            .apply()
        publish()
    }

    fun dismiss(context: Context, announcement: Announcement) {
        if (!announcement.dismissible) return
        dismissed = dismissed + announcement.key
        AppPrefs.get(context).edit().putStringSet(AppPrefs.DISMISSED_ANNOUNCEMENTS, dismissed).apply()
    }

    /** Moves a critical, undismissable announcement from the full screen to a banner; see [hiddenCritical]. */
    fun hideCritical(context: Context, announcement: Announcement) {
        hiddenCritical = hiddenCritical + announcement.key
        AppPrefs.get(context).edit().putStringSet(AppPrefs.HIDDEN_CRITICAL_ANNOUNCEMENTS, hiddenCritical).apply()
    }

    /** Moves a hidden critical announcement from the Learn screen to Settings ([archived]), or back. */
    fun setArchived(context: Context, announcement: Announcement, archived: Boolean) {
        archivedCritical = if (archived) archivedCritical + announcement.key else archivedCritical - announcement.key
        AppPrefs.get(context).edit().putStringSet(AppPrefs.ARCHIVED_CRITICAL_ANNOUNCEMENTS, archivedCritical).apply()
    }

    /**
     * What the Learn screen's banner shows: everything but critical ones still on the full screen and those
     * moved to Settings.
     */
    fun forBanner(nowSec: Long): List<Announcement> =
        visible(nowSec).filter { (!it.isBlocking || it.key in hiddenCritical) && it.key !in archivedCritical }

    /** Critical announcements moved to Settings, still active. */
    fun inSettings(nowSec: Long): List<Announcement> =
        visible(nowSec).filter { it.isBlocking && it.key in archivedCritical }

    /**
     * Active, not dismissed announcements, most severe first. A dismissal counts only while the announcement
     * is dismissible: one the server later makes undismissable under the same id shows again.
     */
    fun visible(nowSec: Long): List<Announcement> =
        all.filter { it.isActive(nowSec) && (!it.dismissible || it.key !in dismissed) }

    private fun publish() {
        all = servers.flatMap { lists[it].orEmpty() }.distinctBy { it.key }.inDisplayOrder()
    }
}
