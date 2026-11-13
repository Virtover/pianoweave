package com.lumenchord.pianoweave.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneOffset

/** A legal document published in dated versions, listed in docs/terms_versions.json under [jsonKey]. */
enum class LegalDocument(
    val jsonKey: String,
    /** Built-in copy of its list in docs/terms_versions.json, used until the published list has been fetched. */
    val bundledVersions: List<String>,
    val versionsPref: String,
    val seenNoticePref: String
) {
    // "versions" is the Terms' key because app releases from before the Privacy Policy was versioned read it
    TERMS("versions", listOf("2026-10-28", "2026-11-20"), AppPrefs.TERMS_VERSIONS, AppPrefs.SEEN_TERMS_CHANGE_NOTICE),
    PRIVACY_POLICY("privacy_policy", listOf("2026-11-04"), AppPrefs.PRIVACY_VERSIONS, AppPrefs.SEEN_PRIVACY_CHANGE_NOTICE)
}

/** Announced versions the user hasn't been told about yet (at least one is set), told together in one notice. */
data class LegalChanges(val terms: String?, val privacyPolicy: String?)

/**
 * Which Terms of Service and Privacy Policy apply, whether the user has agreed to the Terms (asked before
 * transcribing), and whether they have been told about an announced change to either (the Terms promise notice in
 * the App 14 days ahead). "Above all" needs nothing here: each Terms version links the version it applies.
 *
 * Versions are effective dates, published in docs/terms_versions.json; the site picks the page by the same rule
 * (docs/terms.js). A version applies from its date (UTC), so announcing a change needs no app release.
 */
object LegalConsent {
    /** The label stored before Terms were versioned by date; it stands for the October 28, 2026 Terms. */
    private const val LEGACY_ACCEPTED_VERSION = "2026-10-16"
    private const val LEGACY_ACCEPTED_AS = "2026-10-28"

    private val VERSION_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")

    /** Set on the first launch after a fresh install: such users get the documents as they are, not change notices. */
    @Volatile private var isNewInstallSession = false
    @Volatile private var hasRefreshed = false

    private fun today(): String = LocalDate.now(ZoneOffset.UTC).toString()

    fun versions(context: Context, document: LegalDocument = LegalDocument.TERMS): List<String> =
        AppPrefs.get(context).getString(document.versionsPref, null)
            ?.split(',')?.filter { it.matches(VERSION_PATTERN) }?.sorted()?.takeIf { it.isNotEmpty() }
            ?: document.bundledVersions

    /** The version in effect today (the earliest one if none is yet). */
    fun currentVersion(context: Context, document: LegalDocument = LegalDocument.TERMS): String {
        val all = versions(context, document)
        val today = today()
        return all.lastOrNull { it <= today } ?: all.first()
    }

    /** The next announced version, if any. */
    fun upcomingVersion(context: Context, document: LegalDocument = LegalDocument.TERMS): String? {
        val today = today()
        return versions(context, document).firstOrNull { it > today }
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
     * Call once at startup, before [changesToAnnounce]. Notices go to people who were using the App before a
     * change was announced; someone who just installed it starts on the documents as they are, so the change is
     * marked as seen for them (and they agree to new Terms before transcribing once they apply). A document first
     * tracked by an app update counts as never told, so a change already announced for it is still shown.
     */
    @Suppress("DEPRECATION")
    fun onAppStart(context: Context) {
        val prefs = AppPrefs.get(context)
        val untracked = LegalDocument.entries.filter { !prefs.contains(it.seenNoticePref) }
        if (untracked.isEmpty()) return
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        isNewInstallSession = info != null && info.firstInstallTime == info.lastUpdateTime
        val editor = prefs.edit()
        untracked.forEach { document ->
            val seen = if (isNewInstallSession) upcomingVersion(context, document).orEmpty() else ""
            editor.putString(document.seenNoticePref, seen)
        }
        editor.apply()
    }

    private fun changeToAnnounce(context: Context, document: LegalDocument): String? {
        val upcoming = upcomingVersion(context, document) ?: return null
        val seen = AppPrefs.get(context).getString(document.seenNoticePref, null).orEmpty()
        return upcoming.takeIf { it > seen }
    }

    /** The announced versions the user hasn't been told about yet, or null if there are none. */
    fun changesToAnnounce(context: Context): LegalChanges? {
        val changes = LegalChanges(
            terms = changeToAnnounce(context, LegalDocument.TERMS),
            privacyPolicy = changeToAnnounce(context, LegalDocument.PRIVACY_POLICY)
        )
        return changes.takeIf { it.terms != null || it.privacyPolicy != null }
    }

    fun markChangesSeen(context: Context, changes: LegalChanges) {
        val editor = AppPrefs.get(context).edit()
        changes.terms?.let { editor.putString(LegalDocument.TERMS.seenNoticePref, it) }
        changes.privacyPolicy?.let { editor.putString(LegalDocument.PRIVACY_POLICY.seenNoticePref, it) }
        editor.apply()
    }

    /**
     * Fetches the published version lists, once per process. Returns true if any was updated. Offline or on any
     * error the last known (or built-in) lists stay, as does the list of a document the file doesn't have.
     */
    suspend fun refreshVersions(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (hasRefreshed) return@withContext false
        val json = runCatching {
            val connection = URL(AppLinks.TERMS_VERSIONS).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.useCaches = false
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
        }.getOrNull() ?: return@withContext false
        val fetched = LegalDocument.entries.mapNotNull { document ->
            val array = json.optJSONArray(document.jsonKey) ?: return@mapNotNull null
            val list = (0 until array.length()).map { array.optString(it) }.filter { it.matches(VERSION_PATTERN) }.sorted()
            if (list.isEmpty()) null else document to list
        }
        if (fetched.isEmpty()) return@withContext false
        hasRefreshed = true
        val editor = AppPrefs.get(context).edit()
        fetched.forEach { (document, list) -> editor.putString(document.versionsPref, list.joinToString(",")) }
        editor.apply()
        if (isNewInstallSession) {
            val seenEditor = AppPrefs.get(context).edit()
            fetched.forEach { (document, _) ->
                upcomingVersion(context, document)?.let { seenEditor.putString(document.seenNoticePref, it) }
            }
            seenEditor.apply()
        }
        true
    }
}
