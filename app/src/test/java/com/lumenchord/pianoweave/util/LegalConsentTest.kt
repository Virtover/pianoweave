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
    // Bundled lists: Terms 2026-10-28 and 2026-11-20, Privacy Policy 2026-11-04, 2026-11-16 and 2026-11-23
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
        assertEquals(LegalChanges(terms = "2026-11-20", privacyPolicy = "2026-11-16"), LegalConsent.changesToAnnounce(prefs, today))
    }

    @Test
    fun userWhoOnlyTrackedTermsIsToldAboutBothInOneNotice() {
        val prefs = FakePrefs().apply { edit().putString(AppPrefs.SEEN_TERMS_CHANGE_NOTICE, "").apply() }
        LegalConsent.onAppStart(prefs, today)
        LegalConsent.applyFetchedVersions(prefs, published(privacy = listOf("2026-11-04", "2026-11-20")), today)
        assertEquals(LegalChanges(terms = "2026-11-20", privacyPolicy = "2026-11-20"), LegalConsent.changesToAnnounce(prefs, today))
    }

    @Test
    fun existingUserWhoMissedTheNoticePeriodIsToldTheChangeIsInEffect() {
        val prefs = FakePrefs().apply { edit().putString("theme", "gold").apply() }
        // Last opened before the change was announced: knew the October Terms
        prefs.edit().putString(AppPrefs.SEEN_TERMS_CHANGE_NOTICE, "2026-10-28").apply()
        val later = "2026-11-25" // next opened after the new Terms took effect
        LegalConsent.onAppStart(prefs, later)
        assertEquals(LegalChanges(terms = "2026-11-20", privacyPolicy = null), LegalConsent.changesToAnnounce(prefs, later))
    }

    @Test
    fun existingUserToldInAdvanceIsNotToldAgainWhenItTakesEffect() {
        val prefs = FakePrefs().apply { edit().putString("theme", "gold").apply() }
        LegalConsent.onAppStart(prefs, today)
        prefs.edit() // dismissed the notice
            .putString(AppPrefs.SEEN_TERMS_CHANGE_NOTICE, "2026-11-20")
            .putString(AppPrefs.SEEN_PRIVACY_CHANGE_NOTICE, "2026-11-23")
            .apply()
        assertNull(LegalConsent.changesToAnnounce(prefs, "2026-11-25"))
    }

    @Test
    fun missedChangeInEffectComesBeforeTheNextAnnouncedOne() {
        val prefs = FakePrefs().apply { edit().putString(AppPrefs.SEEN_TERMS_CHANGE_NOTICE, "2026-10-28").apply() }
        val later = "2026-11-25"
        LegalConsent.onAppStart(prefs, later)
        LegalConsent.applyFetchedVersions(prefs, mapOf(LegalDocument.TERMS to listOf("2026-10-28", "2026-11-20", "2026-12-20")), later)
        assertEquals("2026-11-20", LegalConsent.changesToAnnounce(prefs, later)?.terms)
        prefs.edit().putString(AppPrefs.SEEN_TERMS_CHANGE_NOTICE, "2026-11-20").apply()
        assertEquals("2026-12-20", LegalConsent.changesToAnnounce(prefs, later)?.terms)
    }

    @Test
    fun newUserAfterAChangeTookEffectGetsNoNotice() {
        val later = "2026-11-25"
        val prefs = FakePrefs()
        LegalConsent.onAppStart(prefs, later)
        assertNull(LegalConsent.changesToAnnounce(prefs, later))
        LegalConsent.applyFetchedVersions(prefs, published(privacy = listOf("2026-11-04", "2026-11-20")), later)
        assertNull(LegalConsent.changesToAnnounce(prefs, later))
        prefs.edit().putString("theme", "gold").apply()
        LegalConsent.onAppStart(prefs, "2026-12-01")
        assertNull(LegalConsent.changesToAnnounce(prefs, "2026-12-01"))
    }

    @Test
    fun emptySeenFromOlderReleasesCountsAsKnowingTheVersionInEffect() {
        // Older releases stored "" for a new user when nothing was announced; that must not become a notice
        val prefs = FakePrefs().apply {
            edit().putString(AppPrefs.SEEN_TERMS_CHANGE_NOTICE, "").putString(AppPrefs.SEEN_PRIVACY_CHANGE_NOTICE, "").apply()
        }
        val later = "2026-11-25"
        LegalConsent.onAppStart(prefs, later)
        assertNull(LegalConsent.changesToAnnounce(prefs, later))
    }

    private fun published(privacy: List<String>) = mapOf(
        LegalDocument.TERMS to listOf("2026-10-28", "2026-11-20"),
        LegalDocument.PRIVACY_POLICY to privacy
    )
}
