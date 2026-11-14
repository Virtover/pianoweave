package com.lumenchord.pianoweave.util

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Who gets the Terms / Privacy Policy change notice. Above all: a new user never does, however the install
 * happened (this regressed once already).
 */
class LegalConsentTest {
    // Bundled lists: Terms 2026-10-28 and 2026-11-20, Privacy Policy 2026-11-04
    private val today = "2026-11-14"

    @Test
    fun newUserGetsNoNoticeForBundledUpcomingTerms() {
        val prefs = FakePrefs()
        LegalConsent.onAppStart(prefs, today)
        assertNull(LegalConsent.changesToAnnounce(prefs, today))
        LegalConsent.applyFetchedVersions(prefs, published(privacy = listOf("2026-11-04")), today)
        assertNull(LegalConsent.changesToAnnounce(prefs, today))
    }

    @Test
    fun newUserGetsNoNoticeForChangesFoundOnlyByTheFirstFetch() {
        val prefs = FakePrefs()
        LegalConsent.onAppStart(prefs, today)
        LegalConsent.applyFetchedVersions(prefs, published(privacy = listOf("2026-11-04", "2026-11-28")), today)
        assertNull(LegalConsent.changesToAnnounce(prefs, today))
    }

    @Test
    fun newUserOfflineOnFirstLaunchGetsNoNoticeLater() {
        val prefs = FakePrefs()
        LegalConsent.onAppStart(prefs, today)
        prefs.edit().putString("theme", "gold").apply() // the first session leaves other settings behind
        // Next launch, now online: the change was announced before they installed
        LegalConsent.onAppStart(prefs, today)
        assertNull(LegalConsent.changesToAnnounce(prefs, today))
        LegalConsent.applyFetchedVersions(prefs, published(privacy = listOf("2026-11-04", "2026-11-28")), today)
        assertNull(LegalConsent.changesToAnnounce(prefs, today))
    }

    @Test
    fun newUserIsToldAboutChangesAnnouncedAfterTheyStarted() {
        val prefs = FakePrefs()
        LegalConsent.onAppStart(prefs, today)
        LegalConsent.applyFetchedVersions(prefs, published(privacy = listOf("2026-11-04")), today)
        LegalConsent.onAppStart(prefs, today)
        LegalConsent.applyFetchedVersions(prefs, published(privacy = listOf("2026-11-04", "2026-11-28")), today)
        assertEquals(LegalChanges(terms = null, privacyPolicy = "2026-11-28"), LegalConsent.changesToAnnounce(prefs, today))
    }

    @Test
    fun existingUserIsToldAboutUpcomingTerms() {
        val prefs = FakePrefs().apply { edit().putString("theme", "gold").apply() } // had the App before
        LegalConsent.onAppStart(prefs, today)
        assertEquals(LegalChanges(terms = "2026-11-20", privacyPolicy = null), LegalConsent.changesToAnnounce(prefs, today))
    }

    @Test
    fun userWhoOnlyTrackedTermsIsToldAboutBothInOneNotice() {
        val prefs = FakePrefs().apply { edit().putString(AppPrefs.SEEN_TERMS_CHANGE_NOTICE, "").apply() }
        LegalConsent.onAppStart(prefs, today)
        LegalConsent.applyFetchedVersions(prefs, published(privacy = listOf("2026-11-04", "2026-11-20")), today)
        assertEquals(LegalChanges(terms = "2026-11-20", privacyPolicy = "2026-11-20"), LegalConsent.changesToAnnounce(prefs, today))
    }

    private fun published(privacy: List<String>) = mapOf(
        LegalDocument.TERMS to listOf("2026-10-28", "2026-11-20"),
        LegalDocument.PRIVACY_POLICY to privacy
    )

    /** In-memory [SharedPreferences]; edits apply on apply()/commit(). */
    private class FakePrefs : SharedPreferences {
        val values = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String, defValue: String?) = values[key] as String? ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?) =
            @Suppress("UNCHECKED_CAST") (values[key] as MutableSet<String>?) ?: defValues
        override fun getInt(key: String, defValue: Int) = values[key] as Int? ?: defValue
        override fun getLong(key: String, defValue: Long) = values[key] as Long? ?: defValue
        override fun getFloat(key: String, defValue: Float) = values[key] as Float? ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = values[key] as Boolean? ?: defValue
        override fun contains(key: String) = key in values
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            val changes = mutableMapOf<String, Any?>()
            val removed = mutableSetOf<String>()
            var clear = false
            override fun putString(key: String, value: String?) = apply { changes[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?) = apply { changes[key] = values }
            override fun putInt(key: String, value: Int) = apply { changes[key] = value }
            override fun putLong(key: String, value: Long) = apply { changes[key] = value }
            override fun putFloat(key: String, value: Float) = apply { changes[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
            override fun remove(key: String) = apply { removed += key }
            override fun clear() = apply { clear = true }
            override fun commit(): Boolean {
                if (clear) values.clear()
                removed.forEach { values.remove(it) }
                values.putAll(changes)
                return true
            }
            override fun apply() {
                commit()
            }
        }
    }
}
