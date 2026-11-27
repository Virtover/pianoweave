package com.lumenchord.pianoweave.midi

import com.lumenchord.pianoweave.api.VideoMetadata
import com.lumenchord.pianoweave.api.placeholderMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random

/**
 * [MidiStorage.listIn] reads each record once and purges at most every 10 minutes. These tests hold it to
 * exactly what the library listing did before (the reference copy below) on every kind of record, broken
 * ones included, and check the purge and its throttle.
 */
class MidiStorageListTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val day = 24L * 60 * 60 * 1000
    private val minuteNs = 60L * 1_000_000_000
    private val now = 1_800_000_000_000L

    // ---- The listing as it was before (purge on every call, each record read three times) ----

    private fun legacyPurge(dir: File, nowMillis: Long) {
        dir.listFiles()
            ?.filter { ".tmp-" in it.name && nowMillis - it.lastModified() > 60L * 60 * 1000 }
            ?.forEach { it.delete() }
        dir.listFiles()
            ?.filter { it.extension == "meta" }
            ?.forEach { metaFile ->
                val (isDeleted, deletedAt, _) = legacyDeletion(metaFile)
                if (isDeleted && deletedAt > 0L && (nowMillis - deletedAt > 30 * day)) {
                    File(dir, metaFile.nameWithoutExtension + ".mid").delete()
                    metaFile.delete()
                }
            }
    }

    private fun legacyDeletion(metadataFile: File): Triple<Boolean, Long, String> {
        if (!metadataFile.exists()) return Triple(false, 0L, "")
        return try {
            val lines = metadataFile.readLines()
            val isDeleted = if (lines.size >= 13) lines[12].toBooleanStrictOrNull() ?: false else false
            val deletedAt = if (lines.size >= 14) lines[13].toLongOrNull() ?: 0L else 0L
            val cloudFileId = if (lines.size >= 15) lines[14].trim() else ""
            Triple(isDeleted, deletedAt, cloudFileId)
        } catch (_: Exception) {
            Triple(false, 0L, "")
        }
    }

    private fun legacyMetadata(metadataFile: File): VideoMetadata {
        val lines = runCatching { metadataFile.readLines() }.getOrNull() ?: return placeholderMetadata("Unknown source")
        val fallbackUrl = lines.firstOrNull()?.trim().orEmpty().ifBlank { "Unknown source" }
        if (lines.size < 12) return placeholderMetadata(fallbackUrl)
        return try {
            VideoMetadata(
                title = lines[1], author = lines[2], channel = lines[3], channel_id = lines[4],
                channel_url = lines[5], upload_date = lines[6], duration = lines[7].toFloat(),
                thumbnail = lines[8], webpage_url = lines[9], view_count = lines[10].toLong(),
                like_count = lines[11].toLong()
            )
        } catch (e: Exception) {
            placeholderMetadata(fallbackUrl)
        }
    }

    private fun legacyListAll(dir: File, nowMillis: Long): List<StoredMidi> {
        legacyPurge(dir, nowMillis)
        return dir.listFiles()
            ?.filter { it.extension == "mid" && it.exists() && it.length() > 0 }
            ?.mapNotNull { midiFile ->
                val metadataFile = File(dir, midiFile.nameWithoutExtension + ".meta")
                if (!metadataFile.exists()) return@mapNotNull null
                val lines = runCatching { metadataFile.readLines() }.getOrNull()
                if (lines.isNullOrEmpty()) return@mapNotNull null
                val (isDeleted, deletedAt, cloudFileId) = legacyDeletion(metadataFile)
                StoredMidi(
                    file = midiFile,
                    videoUrl = lines[0],
                    metadata = legacyMetadata(metadataFile),
                    isDeleted = isDeleted,
                    deletedAt = deletedAt,
                    cloudFileId = cloudFileId,
                    addedAt = lines.getOrNull(15)?.trim()?.toLongOrNull()?.takeIf { it > 0L } ?: midiFile.lastModified()
                )
            }
            ?.sortedWith(compareByDescending<StoredMidi> { it.addedAt }.thenBy { it.videoUrl })
            ?: emptyList()
    }

    // ---- Helpers ----

    private fun record(
        url: String,
        title: String = "Title",
        deleted: String = "false",
        deletedAt: String = "0",
        cloudId: String = "",
        addedAt: String = "1700000000000"
    ): List<String> = listOf(
        url, title, "Author", "Channel", "cid", "https://c", "20240101", "12.5", "thumb", url, "100", "7",
        deleted, deletedAt, cloudId, addedAt
    )

    private fun song(dir: File, id: String, lines: List<String>?, midiBytes: Int = 20, newline: String = "\n") {
        File(dir, "$id.mid").writeBytes(ByteArray(midiBytes) { 1 })
        File(dir, "$id.mid").setLastModified(now - 5 * day)
        lines?.let { File(dir, "$id.meta").writeText(it.joinToString(newline)) }
    }

    /** A record with every kind of value, valid or not, in every position, and any number of lines. */
    private fun randomRecord(r: Random, i: Int): List<String> {
        val pool = listOf(
            "", " ", "0", "-1", "12", "12.5", "1e3", "abc", "NaN", "true", "false", "TRUE", " true", "False",
            "9223372036854775807", "99999999999999999999", "  cloud-$i  ", "https://youtu.be/$i", "file://same.mid",
            "1700000000000", "${now - 31 * day}", "${now - 29 * day}", "Zażółć gęślą jaźń", "\t"
        )
        val full = record(
            url = listOf("https://youtu.be/$i", "file://same.mid", "import://$i/x.mid", "", " ").random(r),
            title = pool.random(r),
            deleted = listOf("true", "false", "TRUE", "", "yes", " true").random(r),
            deletedAt = pool.random(r),
            cloudId = pool.random(r),
            addedAt = pool.random(r)
        ).map { if (r.nextInt(6) == 0) pool.random(r) else it }
        return full.take(r.nextInt(0, 19)) + List(maxOf(0, r.nextInt(-10, 3))) { pool.random(r) }
    }

    private fun corpus(dir: File, seed: Int, count: Int) {
        val r = Random(seed)
        repeat(count) { i ->
            when (r.nextInt(20)) {
                0 -> song(dir, "s$i", null)                              // no record
                1 -> song(dir, "s$i", emptyList())                        // empty record
                2 -> song(dir, "s$i", record("https://youtu.be/$i"), midiBytes = 0) // empty MIDI
                else -> song(dir, "s$i", randomRecord(r, i), newline = if (r.nextBoolean()) "\n" else "\r\n")
            }
            if (r.nextInt(30) == 0) File(dir, "s$i.meta.tmp-$i").writeText("x")
            if (r.nextInt(30) == 0) File(dir, "s$i.meta.tmp-$i").apply { writeText("x"); setLastModified(now - 2 * day) }
        }
    }

    private fun List<StoredMidi>.byName() = map { it.copy(file = File(it.file.name)) }

    private fun names(dir: File) = dir.listFiles()!!.map { it.name }.sorted()

    // ---- Tests ----

    @Test
    fun listsExactlyWhatTheOldListingDid() {
        for (seed in 1..8) {
            val old = tmp.newFolder("old$seed")
            val new = tmp.newFolder("new$seed")
            corpus(old, seed, 400)
            corpus(new, seed, 400)

            val expected = legacyListAll(old, now)
            val actual = MidiStorage.listIn(new, nowNanos = 0L, nowMillis = now)

            assertEquals("seed $seed", expected.byName(), actual.byName())
            assertEquals("files left, seed $seed", names(old), names(new))
            assertTrue(expected.isNotEmpty())
        }
    }

    @Test
    fun readsAWellFormedRecordField_byField() {
        val dir = tmp.newFolder()
        song(dir, "a", record("https://youtu.be/a", title = "Psalm", deleted = "true", deletedAt = "${now - day}", cloudId = " id-1 ", addedAt = "1700000000123"))

        val song = MidiStorage.listIn(dir, nowNanos = 0L, nowMillis = now).single()

        assertEquals("https://youtu.be/a", song.videoUrl)
        assertEquals("Psalm", song.metadata.title)
        assertEquals(12.5f, song.metadata.duration)
        assertEquals(100L, song.metadata.view_count)
        assertTrue(song.isDeleted)
        assertEquals(now - day, song.deletedAt)
        assertEquals("id-1", song.cloudFileId)
        assertEquals(1700000000123L, song.addedAt)
    }

    @Test
    fun purgesOnlyExpiredDeletedSongsAndStaleTempFiles() {
        val dir = tmp.newFolder()
        song(dir, "expired", record("u1", deleted = "true", deletedAt = "${now - 31 * day}"))
        song(dir, "recent", record("u2", deleted = "true", deletedAt = "${now - 29 * day}"))
        song(dir, "noDate", record("u3", deleted = "true", deletedAt = "0"))
        song(dir, "kept", record("u4", deleted = "false", deletedAt = "${now - 300 * day}"))
        File(dir, "x.meta.tmp-1").apply { writeText("x"); setLastModified(now - 2 * 60 * 60 * 1000) }
        File(dir, "y.mid.tmp-2").apply { writeText("x"); setLastModified(now - 10 * 60 * 1000) }

        val listed = MidiStorage.listIn(dir, nowNanos = 0L, nowMillis = now)

        assertEquals(listOf("u2", "u3", "u4"), listed.map { it.videoUrl }.sorted())
        assertFalse(File(dir, "expired.mid").exists())
        assertFalse(File(dir, "expired.meta").exists())
        assertFalse(File(dir, "x.meta.tmp-1").exists())
        assertTrue(File(dir, "y.mid.tmp-2").exists())
    }

    @Test
    fun purgesAtMostEveryTenMinutes() {
        val dir = tmp.newFolder()
        val t0 = 5_000_000 * minuteNs
        song(dir, "keep", record("keep"))
        MidiStorage.listIn(dir, nowNanos = t0, nowMillis = now)

        // Expired after the first purge: still listed (as deleted) until the next one is due
        song(dir, "late", record("late", deleted = "true", deletedAt = "${now - 31 * day}"))
        val soon = MidiStorage.listIn(dir, nowNanos = t0 + 9 * minuteNs, nowMillis = now)
        assertTrue(soon.single { it.videoUrl == "late" }.isDeleted)
        assertTrue(File(dir, "late.mid").exists())

        val later = MidiStorage.listIn(dir, nowNanos = t0 + 11 * minuteNs, nowMillis = now)
        assertEquals(listOf("keep"), later.map { it.videoUrl })
        assertFalse(File(dir, "late.mid").exists())
    }

    @Test
    fun eachFolderHasItsOwnPurgeTime() {
        val a = tmp.newFolder()
        val b = tmp.newFolder()
        val t0 = 9_000_000 * minuteNs
        MidiStorage.listIn(a, nowNanos = t0, nowMillis = now)
        song(b, "old", record("old", deleted = "true", deletedAt = "${now - 40 * day}"))

        assertTrue(MidiStorage.listIn(b, nowNanos = t0 + minuteNs, nowMillis = now).isEmpty())
        assertFalse(File(b, "old.meta").exists())
    }

    @Test
    fun aSongSavedAgainIsNotPurged() {
        val dir = tmp.newFolder()
        // Deleted long ago, then transcribed again at the same key: the record says not deleted
        song(dir, "again", record("again", deleted = "false", deletedAt = "0"))
        File(dir, "again.meta").setLastModified(now - 60 * day)

        assertEquals(listOf("again"), MidiStorage.listIn(dir, nowNanos = 0L, nowMillis = now).map { it.videoUrl })
        assertTrue(File(dir, "again.mid").exists())
    }

    @Test
    fun aLibrarySyncedWithDriveKeepsItsDeletionsUntilASyncPushesThem() {
        val dir = tmp.newFolder()
        song(dir, "expired", record("u1", deleted = "true", deletedAt = "${now - 90 * day}", cloudId = "id-1"))
        File(dir, "x.meta.tmp-1").apply { writeText("x"); setLastModified(now - 2 * 60 * 60 * 1000) }

        val listed = MidiStorage.listIn(dir, nowNanos = 0L, nowMillis = now, keepTombstones = true)

        assertTrue(listed.single().isDeleted)
        assertTrue(File(dir, "expired.meta").exists())
        assertFalse(File(dir, "x.meta.tmp-1").exists())
    }
}
