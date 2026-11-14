package com.lumenchord.pianoweave.util

import android.content.Context
import android.content.SharedPreferences
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
 * Change notices are only for people who were using the App when the change was announced. A new user starts on
 * the documents as they are, never with a notice: see [onAppStart] (a regression of this was fixed twice; keep
 * LegalConsentTest passing).
 *
 * Versions are effective dates, published in docs/terms_versions.json; the site picks the page by the same rule
 * (docs/terms.js). A version applies from its date (UTC), so announcing a change needs no app release.
 */
object LegalConsent {
    /** The label stored before Terms were versioned by date; it stands for the October 28, 2026 Terms. */
    private const val LEGACY_ACCEPTED_VERSION = "2026-10-16"
    private const val LEGACY_ACCEPTED_AS = "2026-10-28"

    private val VERSION_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")

    @Volatile private var hasRefreshed = false

    private fun today(): String = LocalDate.now(ZoneOffset.UTC).toString()

    fun versions(context: Context, document: LegalDocument = LegalDocument.TERMS): List<String> =
        versions(AppPrefs.get(context), document)

    internal fun versions(prefs: SharedPreferences, document: LegalDocument): List<String> =
        prefs.getString(document.versionsPref, null)
            ?.split(',')?.filter { it.matches(VERSION_PATTERN) }?.sorted()?.takeIf { it.isNotEmpty() }
            ?: document.bundledVersions

    /** The version in effect today (the earliest one if none is yet). */
    fun currentVersion(context: Context, document: LegalDocument = LegalDocument.TERMS): String {
        val all = versions(context, document)
        val today = today()
        return all.lastOrNull { it <= today } ?: all.first()
    }

    /** The next announced version, if any. */
    fun upcomingVersion(context: Context, document: LegalDocument = LegalDocument.TERMS): String? =
        upcomingVersion(AppPrefs.get(context), document, today())

    private fun upcomingVersion(prefs: SharedPreferences, document: LegalDocument, today: String): String? =
        versions(prefs, document).firstOrNull { it > today }

    /** Whether the user agreed to the Terms in effect today; a new version asks again before the next transcription. */
    fun hasAcceptedTerms(context: Context): Boolean {
        val accepted = AppPrefs.get(context).getString(AppPrefs.ACCEPTED_TERMS_VERSION, null) ?: return false
        val normalized = if (accepted == LEGACY_ACCEPTED_VERSION) LEGACY_ACCEPTED_AS else accepted
        return normalized == currentVersion(context)
    }

    fun acceptTerms(context: Context) {
        AppPrefs.get(context).edit().putString(AppPrefs.ACCEPTED_TERMS_VERSION, currentVersion(context)).apply()
    }

    /** Call once at startup, before anything else writes preferences, and before [changesToAnnounce]. */
    fun onAppStart(context: Context) = onAppStart(AppPrefs.get(context), today())

    /**
     * A new user is recognised by having no preferences at all yet, not by install and update times: those differ
     * when the App is updated before its first launch (a Play auto-update, or two installs in a row while testing),
     * which used to show new users a change notice. A restored backup brings preferences, so it counts as returning.
     *
     * New users get every announced change marked as seen, and stay "new" ([AppPrefs.LEGAL_NEW_USER]) until the
     * published lists are first fetched, so a change announced before they installed, which an offline first
     * launch didn't know about, isn't shown to them later either. Someone already using the App gets "" for a
     * document it starts tracking (an app update), so a change already announced for it is still told.
     */
    internal fun onAppStart(prefs: SharedPreferences, today: String) {
        val isFirstLaunch = prefs.all.isEmpty()
        val untracked = LegalDocument.entries.filter { !prefs.contains(it.seenNoticePref) }
        if (!isFirstLaunch && untracked.isEmpty()) return
        val isNewUser = isFirstLaunch || prefs.getBoolean(AppPrefs.LEGAL_NEW_USER, false)
        val editor = prefs.edit()
        if (isFirstLaunch) editor.putBoolean(AppPrefs.LEGAL_NEW_USER, true)
        untracked.forEach { document ->
            val seen = if (isNewUser) upcomingVersion(prefs, document, today).orEmpty() else ""
            editor.putString(document.seenNoticePref, seen)
        }
        editor.apply()
    }

    private fun changeToAnnounce(prefs: SharedPreferences, document: LegalDocument, today: String): String? {
        val upcoming = upcomingVersion(prefs, document, today) ?: return null
        val seen = prefs.getString(document.seenNoticePref, null).orEmpty()
        return upcoming.takeIf { it > seen }
    }

    /** The announced versions the user hasn't been told about yet, or null if there are none. */
    fun changesToAnnounce(context: Context): LegalChanges? = changesToAnnounce(AppPrefs.get(context), today())

    internal fun changesToAnnounce(prefs: SharedPreferences, today: String): LegalChanges? {
        // Until the first fetch a new user may not know every change announced before they installed
        if (prefs.getBoolean(AppPrefs.LEGAL_NEW_USER, false)) return null
        val changes = LegalChanges(
            terms = changeToAnnounce(prefs, LegalDocument.TERMS, today),
            privacyPolicy = changeToAnnounce(prefs, LegalDocument.PRIVACY_POLICY, today)
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
            document to (0 until array.length()).map { array.optString(it) }
        }.toMap()
        val updated = applyFetchedVersions(AppPrefs.get(context), fetched, today())
        if (updated) hasRefreshed = true
        updated
    }

    /** Stores fetched lists (invalid dates dropped); a new user is told none of the changes announced so far. */
    internal fun applyFetchedVersions(
        prefs: SharedPreferences,
        fetched: Map<LegalDocument, List<String>>,
        today: String
    ): Boolean {
        val valid = fetched.mapValues { (_, list) -> list.filter { it.matches(VERSION_PATTERN) }.sorted() }
            .filterValues { it.isNotEmpty() }
        if (valid.isEmpty()) return false
        val editor = prefs.edit()
        valid.forEach { (document, list) -> editor.putString(document.versionsPref, list.joinToString(",")) }
        editor.apply()
        if (prefs.getBoolean(AppPrefs.LEGAL_NEW_USER, false)) {
            val seenEditor = prefs.edit()
            LegalDocument.entries.forEach { document ->
                upcomingVersion(prefs, document, today)?.let { seenEditor.putString(document.seenNoticePref, it) }
            }
            seenEditor.remove(AppPrefs.LEGAL_NEW_USER).apply()
        }
        return true
    }
}
