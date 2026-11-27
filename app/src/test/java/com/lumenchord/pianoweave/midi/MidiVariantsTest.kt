package com.lumenchord.pianoweave.midi

import com.lumenchord.pianoweave.midi.MidiVariants.ADVANCED
import com.lumenchord.pianoweave.midi.MidiVariants.BEGINNER
import com.lumenchord.pianoweave.midi.MidiVariants.EASY
import com.lumenchord.pianoweave.midi.MidiVariants.FULL
import com.lumenchord.pianoweave.midi.MidiVariants.INTERMEDIATE
import com.lumenchord.pianoweave.midi.MidiVariants.RAW
import com.lumenchord.pianoweave.ui.screens.pianoroll.settings.difficultyHint
import com.lumenchord.pianoweave.ui.screens.pianoroll.settings.rawExplanation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class MidiVariantsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** A format 0 file with one note of [pitch]. */
    private fun midi(pitch: Int): ByteArray {
        val events = byteArrayOf(
            0x00, 0x90.toByte(), pitch.toByte(), 0x40,
            0x83.toByte(), 0x60, 0x80.toByte(), pitch.toByte(), 0x00,
            0x00, 0xFF.toByte(), 0x2F, 0x00
        )
        val header = byteArrayOf(0x4D, 0x54, 0x68, 0x64, 0, 0, 0, 6, 0, 0, 0, 1, 0x01, 0xE0.toByte())
        val track = byteArrayOf(0x4D, 0x54, 0x72, 0x6B, 0, 0, 0, events.size.toByte())
        return header + track + events
    }

    private val pitches = mapOf(FULL to 60, ADVANCED to 62, INTERMEDIATE to 64, EASY to 65, BEGINNER to 67, RAW to 36)
    private val all = pitches.mapValues { midi(it.value) }

    private fun notesOf(bytes: ByteArray) = SimpleMidiReader.parseSong(bytes.inputStream()).notes.map { it.pitch }

    @Test
    fun aBundleGivesBackEveryVariantInTheServersOrder() {
        val bundle = MidiVariants.bundle(all.entries.reversed().associate { it.toPair() })
        val read = MidiVariants.read(bundle)
        assertEquals(MidiVariants.ORDER, read.keys.toList())
        read.forEach { (name, bytes) -> assertArrayEquals(name, all.getValue(name), bytes) }
    }

    @Test
    fun everyReaderSeesTheFullVersion_olderAppVersionsToo() {
        val bundle = MidiVariants.bundle(all)
        assertEquals(listOf(60), notesOf(bundle))
        // Without full the first piano version is the one others see
        assertEquals(listOf(62), notesOf(MidiVariants.bundle(all - FULL)))
    }

    @Test
    fun aPlainFile_aSongWithOneResultOrAnOlderSong_hasNoVariants() {
        val raw = midi(36)
        assertTrue(MidiVariants.read(raw).isEmpty())
        assertArrayEquals(raw, MidiVariants.extract(raw, FULL))
        assertArrayEquals(raw, MidiVariants.bundle(mapOf(RAW to raw)))
        assertTrue(MidiVariants.read(byteArrayOf(1, 2, 3)).isEmpty())
        assertTrue(MidiVariants.read(File(tmp.root, "missing.mid")).isEmpty())
    }

    @Test
    fun extractFallsBackToThePrimary() {
        val bundle = MidiVariants.bundle(all)
        assertEquals(listOf(65), notesOf(MidiVariants.extract(bundle, EASY)))
        assertEquals(listOf(60), notesOf(MidiVariants.extract(bundle, "unknown")))
    }

    @Test
    fun aTruncatedBundleIsReadAsItsPrimaryOnly() {
        val bundle = MidiVariants.bundle(all)
        val cut = bundle.copyOf(bundle.size - 3)
        assertTrue(MidiVariants.read(cut).isEmpty())
        assertEquals(listOf(60), notesOf(cut))
    }

    @Test
    fun choice_keepsTheStoredOne_thenFull_thenTheHardestLevel_thenRaw() {
        val levels = MidiVariants.ORDER
        assertEquals(EASY, MidiVariants.choose(EASY, levels))
        assertEquals(FULL, MidiVariants.choose("", levels))
        assertEquals(FULL, MidiVariants.choose(BEGINNER, levels - BEGINNER))
        assertEquals(ADVANCED, MidiVariants.choose(FULL, listOf(RAW, ADVANCED)))
        assertEquals(INTERMEDIATE, MidiVariants.choose(null, listOf(BEGINNER, INTERMEDIATE, EASY)))
        assertEquals(RAW, MidiVariants.choose(FULL, listOf(RAW)))
        assertNull(MidiVariants.choose(FULL, emptyList()))
    }

    @Test
    fun aSimpleSong_offersExactlyTheLevelsListed() {
        // Full and easy only: two choices, full first
        val (bytes, asked) = download(listOf(FULL, EASY)) { _, _ -> null }
        assertEquals(listOf<String?>(FULL, EASY), asked)
        val offered = MidiVariants.read(bytes).keys.toList()
        assertEquals(listOf(FULL, EASY), offered)
        assertEquals(FULL, MidiVariants.choose(null, offered))
        // A stored level the song doesn't have falls back to full
        assertEquals(FULL, MidiVariants.choose(BEGINNER, offered))
    }

    @Test
    fun aSingleLevel_orDrumsAlone_isAPlainSongWithNoDifficulty() {
        for (only in listOf(FULL, RAW, EASY)) {
            val (bytes, asked) = download(listOf(only)) { _, _ -> null }
            assertEquals(listOf<String?>(null), asked)
            assertTrue("$only alone offers no choice", MidiVariants.read(bytes).isEmpty())
        }
    }

    @Test
    fun withoutFull_theHardestListedLevelIsTheDefault_andWhatOthersSee() {
        val (bytes, _) = download(listOf(EASY, INTERMEDIATE, RAW)) { _, _ -> null }
        val offered = MidiVariants.read(bytes).keys.toList()
        assertEquals(listOf(INTERMEDIATE, EASY, RAW), offered)
        assertEquals(INTERMEDIATE, MidiVariants.choose(null, offered))
        assertEquals(listOf(64), notesOf(bytes))
    }

    @Test
    fun hintsSayWhatEachLevelKeeps() {
        assertEquals("Every note, arranged for piano", difficultyHint(FULL))
        assertEquals("Melody and single bass notes", difficultyHint(BEGINNER))
        MidiVariants.ORDER.forEach { assertTrue(it, difficultyHint(it).isNotBlank()) }
    }

    @Test
    fun orderedKeepsOnlyKnownNamesOnce() {
        assertEquals(listOf(FULL, EASY, RAW), MidiVariants.ordered(listOf(RAW, "sparkly", EASY, FULL, RAW)))
    }

    private class Missing : IOException("404")

    private fun download(listed: List<String>?, fail: (String?, Int) -> Exception?): Pair<ByteArray, List<String?>> {
        val asked = mutableListOf<String?>()
        val bytes = runBlocking {
            MidiVariants.download(listed, { variant ->
                asked += variant
                fail(variant, asked.count { it == variant })?.let { throw it }
                variant?.let { all.getValue(it) } ?: midi(36)
            }, isMissing = { it is Missing })
        }
        return bytes to asked
    }

    @Test
    fun anOlderServer_withoutVariants_givesItsOneResult() {
        for (listed in listOf(null, emptyList(), listOf(RAW))) {
            val (bytes, asked) = download(listed) { _, _ -> null }
            assertEquals(listOf<String?>(null), asked)
            assertTrue(MidiVariants.read(bytes).isEmpty())
        }
    }

    @Test
    fun anEnsembleJob_downloadsEveryListedVariantIntoOneFile() {
        val (bytes, asked) = download(MidiVariants.ORDER.reversed()) { _, _ -> null }
        assertEquals(MidiVariants.ORDER, asked)
        assertEquals(MidiVariants.ORDER, MidiVariants.read(bytes).keys.toList())
    }

    @Test
    fun oneLevelFailing_neverCostsTheOthers() {
        val (bytes, asked) = download(MidiVariants.ORDER) { v, _ ->
            when (v) {
                EASY -> IOException("reset")
                ADVANCED -> Missing()
                else -> null
            }
        }
        assertEquals(2, asked.count { it == EASY })
        assertEquals(1, asked.count { it == ADVANCED })
        assertEquals(listOf(FULL, INTERMEDIATE, BEGINNER, RAW), MidiVariants.read(bytes).keys.toList())
    }

    @Test
    fun aLevelFailingOnceIsTriedAgain() {
        val (bytes, _) = download(MidiVariants.ORDER) { v, n -> if (v == BEGINNER && n == 1) IOException("reset") else null }
        assertTrue(BEGINNER in MidiVariants.read(bytes))
    }

    @Test
    fun theDefaultOrRawFailing_isThrown_soTheDownloadIsRetried() {
        for (required in listOf(FULL, RAW)) {
            try {
                download(MidiVariants.ORDER) { v, _ -> if (v == required) IOException("reset") else null }
                fail("$required must be retried")
            } catch (e: IOException) {
                assertEquals("reset", e.message)
            }
        }
    }

    @Test
    fun aMissingDefault_leavesTheRest_andNothingAtAllIsThrown() {
        val (bytes, _) = download(MidiVariants.ORDER) { v, _ -> if (v == FULL) Missing() else null }
        assertEquals(listOf(62), notesOf(bytes))
        try {
            download(MidiVariants.ORDER) { _, _ -> Missing() }
            fail("nothing downloaded")
        } catch (_: Missing) {
        }
    }

    @Test
    fun exportsAreNamedAfterTheSongAndTheVariant() {
        assertEquals("Hallelujah - full", MidiVariants.exportTitle("Hallelujah", FULL))
        assertEquals("Hallelujah - raw", MidiVariants.exportTitle("Hallelujah", RAW))
        assertEquals("Full", MidiVariants.label(FULL))
    }

    @Test
    fun theSongsOneFileCarriesEveryVariantThroughDrive() {
        // Upload sends the stored file, download stores the bytes: nothing else to carry
        val stored = File(tmp.root, "song.mid").apply { writeBytes(MidiVariants.bundle(all)) }
        val downloaded = File(tmp.newFolder(), "song.mid").apply { writeBytes(stored.readBytes()) }
        assertEquals(MidiVariants.ORDER, MidiVariants.read(downloaded).keys.toList())
    }

    @Test
    fun rawExplains_drumsOnlyWhereTheyPlay() {
        assertTrue(rawExplanation(true).contains("drums"))
        assertFalse(rawExplanation(false).contains("drums"))
    }

    // ---- Levels that arrive after completion ----

    private class LateStatus(val answers: List<List<String>?>, val failAt: Int = -1) {
        var reads = 0
        var waitedMs = 0L
        suspend fun refresh(): List<String>? {
            val i = reads++
            if (i == failAt) throw IOException("offline")
            return answers.getOrElse(i) { answers.last() }
        }
    }

    private fun await(listed: List<String>?, ensemble: Boolean, status: LateStatus) = runBlocking {
        MidiVariants.awaitLateLevels(listed, ensemble, refresh = status::refresh, pause = { status.waitedMs += it })
    }

    @Test
    fun anEnsembleWithOnlyRaw_waitsForItsLevels() {
        val status = LateStatus(listOf(listOf(RAW), listOf(RAW), MidiVariants.ORDER))
        assertEquals(MidiVariants.ORDER, await(listOf(RAW), ensemble = true, status))
        assertEquals(3, status.reads)
        assertEquals(6_000L, status.waitedMs)
    }

    @Test
    fun drumsAlone_waitAtMostTwentySeconds_thenTakeRaw() {
        val status = LateStatus(listOf(listOf(RAW)))
        assertEquals(listOf(RAW), await(listOf(RAW), ensemble = true, status))
        assertEquals(10, status.reads)
        assertEquals(20_000L, status.waitedMs)
    }

    @Test
    fun pianoJobs_olderServers_andListedLevels_neverWait() {
        for ((listed, ensemble) in listOf(listOf(RAW) to false, null to true, emptyList<String>() to true, listOf(FULL, RAW) to true)) {
            val status = LateStatus(listOf(MidiVariants.ORDER))
            assertEquals(listed, await(listed, ensemble, status))
            assertEquals(0, status.reads)
        }
    }

    @Test
    fun aFailedStatusRead_endsTheWaitWithWhatIsKnown() {
        val status = LateStatus(listOf(listOf(RAW)), failAt = 1)
        assertEquals(listOf(RAW), await(listOf(RAW), ensemble = true, status))
        assertEquals(2, status.reads)
    }

    @Test
    fun aLoneMelody_givesFullAndRaw_twoChoices() {
        val (bytes, _) = download(listOf(FULL, RAW)) { _, _ -> null }
        assertEquals(listOf(FULL, RAW), MidiVariants.read(bytes).keys.toList())
    }
}
