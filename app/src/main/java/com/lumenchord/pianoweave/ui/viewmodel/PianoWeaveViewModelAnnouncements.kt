package com.lumenchord.pianoweave.ui.viewmodel

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParser
import com.lumenchord.pianoweave.api.Announcements
import com.lumenchord.pianoweave.api.PianoApiFactory
import com.lumenchord.pianoweave.api.config.AppConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import java.util.Locale

private const val TAG = "Announcements"

/** A return to the app within this long after a fetch doesn't fetch again; the server updates within ~15 s. */
private const val REFETCH_AFTER_MS = 30_000L

/** Typing a custom server's address changes the server on every key; only the settled one is asked. */
private const val SERVER_CHANGE_SETTLE_MS = 1_000L

/** Per server; a slow one is asked again on the next return to the app. */
private const val FETCH_TIMEOUT_MS = 15_000L

/**
 * Asks the followed servers for their announcements: at launch, on every return to the app, and when the
 * selected server changes. A custom server's users also follow the official server, which is where a notice
 * about the app itself (say, leaving Google Play) would come from.
 */
internal fun PianoWeaveViewModel.refreshAnnouncementsImpl(context: Context, force: Boolean = false) {
    val app = context.applicationContext
    Announcements.load(app)
    val servers = followedServers()
    Announcements.follow(servers)

    // A fetch already under way for these servers is left to finish: cancelling it on every resume or
    // reconnect could keep a slow server from ever answering
    val running = announcementsJob?.isActive == true
    if (running && announcementsJobServers == servers && !force) return
    val last = lastAnnouncementsFetch
    val serversChanged = last?.first != servers
    if (!force && !running && last != null && !serversChanged &&
        SystemClock.elapsedRealtime() - last.second < REFETCH_AFTER_MS
    ) return

    // A pending fetch for other servers (the user switched again) is no longer wanted
    announcementsJob?.cancel()
    announcementsJobServers = servers
    announcementsJob = viewModelScope.launch {
        if (last != null && serversChanged) delay(SERVER_CHANGE_SETTLE_MS)
        lastAnnouncementsFetch = servers to SystemClock.elapsedRealtime()
        // Side by side, each with its own time limit, so a hung custom server can't hold up the official one
        servers.map { server ->
            launch { withTimeoutOrNull(FETCH_TIMEOUT_MS) { fetchAnnouncements(app, server) } }
        }.joinAll()
    }
}

/** The official server first: its notices are about the app itself. */
private fun PianoWeaveViewModel.followedServers(): List<String> {
    val official = AppConfig.getConfigOrNull()?.baseUrl?.trim()?.takeIf { it.isNotEmpty() }?.withSlash()
    return listOfNotNull(official, activeServerUrl.trim().withSlash()).distinct()
}

/** Keeps the last list on any failure: a 404 (older server), 503, or no connection never clears it. */
private suspend fun fetchAnnouncements(context: Context, server: String) {
    try {
        val response = PianoApiFactory.getApi(server).getAnnouncements(
            platform = "android",
            appVersion = appVersionCode(context),
            lang = languageTag()
        )
        val body = response.body()?.use { it.string() }
        if (!response.isSuccessful || body == null) {
            Log.i(TAG, "Kept the last list: HTTP ${response.code()}")
            return
        }
        val array = runCatching { JsonParser.parseString(body).asJsonObject.getAsJsonArray("announcements") }.getOrNull()
        if (array == null) {
            Log.w(TAG, "Kept the last list: not an announcement list")
            return
        }
        Announcements.onFetched(context, server, array)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.i(TAG, "Kept the last list: ${e.javaClass.simpleName}")
    }
}

/**
 * Language, script and region only: Android's regional preferences add `-u-…` extensions (calendar, units)
 * that can make the tag longer than the server accepts.
 */
private fun languageTag(): String =
    runCatching { Locale.Builder().setLocale(Locale.getDefault()).clearExtensions().build().toLanguageTag() }
        .getOrDefault("en")

private fun appVersionCode(context: Context): Long? = runCatching {
    PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
}.getOrNull()

private fun String.withSlash() = if (endsWith("/")) this else "$this/"
