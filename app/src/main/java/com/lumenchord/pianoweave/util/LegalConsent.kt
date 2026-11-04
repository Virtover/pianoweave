package com.lumenchord.pianoweave.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Which Terms of Service apply, and whether the user has agreed to them (asked before transcribing) or been told
 * about an announced change (the Terms promise notice in the App 14 days ahead).
 *
 * Versions are effective dates, published in docs/terms_versions.json; the site picks the page by the same rule
 * (docs/terms.js). A version applies from its date (UTC), so announcing a change needs no app release.
 */
object LegalConsent {
    /** Built-in copy of docs/terms_versions.json, used until the published list has been fetched. */
    private val BUNDLED_VERSIONS = listOf("2026-10-28", "2026-11-20")

    /** The label stored before Terms were versioned by date; it stands for the October 28, 2026 Terms. */
    private const val LEGACY_ACCEPTED_VERSION = "2026-10-16"
    private const val LEGACY_ACCEPTED_AS = "2026-10-28"

    private val VERSION_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")

    /** Set on the first launch after a fresh install: such users get the Terms as they are, not change notices. */
    @Volatile private var isNewInstallSession = false
    @Volatile private var hasRefreshed = false

    private fun today(): String = LocalDate.now(ZoneOffset.UTC).toString()

    fun versions(context: Context): List<String> =
        AppPrefs.get(context).getString(AppPrefs.TERMS_VERSIONS, null)
            ?.split(',')?.filter { it.matches(VERSION_PATTERN) }?.sorted()?.takeIf { it.isNotEmpty() }
            ?: BUNDLED_VERSIONS

    /** The version in effect today (the earliest one if none is yet). */
    fun currentVersion(context: Context): String {
        val all = versions(context)
        val today = today()
        return all.lastOrNull { it <= today } ?: all.first()
    }

    /** The next announced version, if any. */
    fun upcomingVersion(context: Context): String? {
        val today = today()
        return versions(context).firstOrNull { it > today }
    }

    /** Whether the user agreed to the Terms in effect today; a new version asks again before the next transcription. */
    fun hasAcceptedTerms(context: Context): Boolean {
        val accepted = AppPrefs.get(context).getString(AppPrefs.ACCEPTED_TERMS_VERSION, null) ?: return false
        val normalized = if (accepted == LEGACY_ACCEPTED_VERSION) LEGACY_ACCEPTED_AS else accepted
        return normalized == currentVersion(context)
    }

    fun acceptTerms(context: Context) {
        AppPrefs.get(context).edit().putString(AppPrefs.ACCEPTED_TERMS_VERSION, currentVersion(context)).apply()
    }

    /**
     * Call once at startup, before [termsChangeToAnnounce]. Notices go to people who were using the App before a
     * change was announced; someone who just installed it starts on the Terms as they are, so the change is
     * marked as seen for them (and they agree to the new version before transcribing once it applies).
     */
    @Suppress("DEPRECATION")
    fun onAppStart(context: Context) {
        val prefs = AppPrefs.get(context)
        if (prefs.contains(AppPrefs.SEEN_TERMS_CHANGE_NOTICE)) return
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        isNewInstallSession = info != null && info.firstInstallTime == info.lastUpdateTime
        val seen = if (isNewInstallSession) upcomingVersion(context).orEmpty() else ""
        prefs.edit().putString(AppPrefs.SEEN_TERMS_CHANGE_NOTICE, seen).apply()
    }

    /** The announced version the user hasn't been told about yet, or null. */
    fun termsChangeToAnnounce(context: Context): String? {
        val upcoming = upcomingVersion(context) ?: return null
        val seen = AppPrefs.get(context).getString(AppPrefs.SEEN_TERMS_CHANGE_NOTICE, null).orEmpty()
        return upcoming.takeIf { it > seen }
    }

    fun markTermsChangeNoticeSeen(context: Context, version: String) {
        AppPrefs.get(context).edit().putString(AppPrefs.SEEN_TERMS_CHANGE_NOTICE, version).apply()
    }

    /**
     * Fetches the published version list, once per process. Returns true if it was updated. Offline or on any
     * error the last known (or built-in) list stays.
     */
    suspend fun refreshVersions(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (hasRefreshed) return@withContext false
        val fetched = runCatching {
            val connection = URL(AppLinks.TERMS_VERSIONS).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.useCaches = false
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val array = json.getJSONArray("versions")
                (0 until array.length()).map { array.getString(it) }.filter { it.matches(VERSION_PATTERN) }.sorted()
            } finally {
                connection.disconnect()
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return@withContext false
        hasRefreshed = true
        AppPrefs.get(context).edit().putString(AppPrefs.TERMS_VERSIONS, fetched.joinToString(",")).apply()
        if (isNewInstallSession) upcomingVersion(context)?.let { markTermsChangeNoticeSeen(context, it) }
        true
    }
}
