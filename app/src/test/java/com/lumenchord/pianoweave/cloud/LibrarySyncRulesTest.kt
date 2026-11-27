package com.lumenchord.pianoweave.cloud

import com.lumenchord.pianoweave.api.placeholderMetadata
import com.lumenchord.pianoweave.midi.StoredMidi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.File

/**
 * Deletions across devices. [Device.sync] runs the same steps as the app's sync
 * (PianoWeaveViewModelCloud.refreshCloudSyncImpl) on [LibrarySyncRules], and [Device.delete] those of
 * deleteSongImpl, against one in-memory [Drive].
 */
class LibrarySyncRulesTest {

    private class Clock(var now: Long = 1_000_000L) {
        fun tick(ms: Long = 1_000L): Long { now += ms; return now }
    }

    private class Drive(val clock: Clock) {
        val files = linkedMapOf<String, CloudMidi>()
        var reachable = true
        private var nextId = 0

        fun list(): List<CloudMidi> {
            check(reachable) { "offline" }
            return files.values.toList()
        }

        fun upload(song: StoredMidi): CloudMidi {
            check(reachable) { "offline" }
            val id = "f${nextId++}"
            val copy = CloudMidi(
                id = id, name = song.file.name, sizeBytes = 10, modifiedTime = clock.now,
                videoUrl = song.videoUrl, metadata = song.metadata, addedAt = song.addedAt, createdTime = clock.now
            )
            files[id] = copy
            return copy
        }

        /** Like deleteCloudMidi: a file already gone counts as deleted. */
        fun delete(id: String): Boolean {
            if (!reachable) return false
            files.remove(id)
            return true
        }
    }

    private class Device(private val drive: Drive, private val clock: Clock, var clockOffset: Long = 0L) {
        val records = linkedMapOf<String, StoredMidi>()
        private fun now() = clock.now + clockOffset

        val shown: List<String> get() = records.values.filterNot { it.isDeleted }.map { it.videoUrl }.sorted()

        /** A finished transcription: saved, then backed up at once if Drive can be reached (backUpNewSong). */
        fun add(key: String, backUp: Boolean = true) {
            val song = StoredMidi(file = File("$key.mid"), videoUrl = key, metadata = placeholderMetadata(key), addedAt = now())
            records[key] = song
            if (backUp && drive.reachable) records[key] = song.copy(cloudFileId = drive.upload(song).id)
        }

        fun delete(key: String) {
            val tombstone = records.getValue(key).copy(isDeleted = true, deletedAt = now())
            records[key] = tombstone
            val cloud = runCatching { drive.list() }.getOrNull() ?: return
            val copies = LibrarySyncRules.cloudCopiesDeletedBy(tombstone, cloud)
            if (copies.all { drive.delete(it.id) }) records.remove(key)
        }

        fun sync(turningOn: Boolean = false) {
            val cloud = drive.list().toMutableList()
            val local = records.values.toList()
            val deletions = LibrarySyncRules.deletions(local, cloud, LibrarySyncRules.appliesRemoteDeletions(turningOn, local, cloud))
            deletions.deletedRemotely.forEach { records.remove(it.videoUrl) }
            val stillInCloud = deletions.cloudCopies.filterNot { drive.delete(it.id) }.map { it.id }.toSet()
            cloud.removeAll(deletions.cloudCopies.toSet())
            deletions.tombstones.filter { (_, copies) -> copies.none { it.id in stillInCloud } }
                .forEach { (song, _) -> records.remove(song.videoUrl) }

            val transfers = LibrarySyncRules.transfers(records.values.filterNot { it.isDeleted }, cloud)
            transfers.link.forEach { (song, copy) -> records[song.videoUrl] = song.copy(cloudFileId = copy.id) }
            transfers.upload.forEach { song -> records[song.videoUrl] = song.copy(cloudFileId = drive.upload(song).id) }
            transfers.download.forEach { copy ->
                records[copy.videoUrl] = StoredMidi(
                    file = File(copy.name), videoUrl = copy.videoUrl, metadata = copy.metadata,
                    cloudFileId = copy.id, addedAt = copy.effectiveAddedAt
                )
            }
        }
    }

    private val clock = Clock()
    private val drive = Drive(clock)
    private val a = Device(drive, clock)
    private val b = Device(drive, clock)

    private val song1 = "https://youtu.be/one#ensemble"
    private val song2 = "https://youtu.be/two#ensemble"
    private val pianoOnB = "https://youtu.be/three"

    /** The reported sequence, with B syncing or not after its own transcription. */
    private fun reportedSequence(bSyncsEarly: Boolean, aCanDelete: Boolean) {
        a.add(song1); clock.tick()
        a.add(song2); clock.tick()
        b.add(pianoOnB, backUp = false); clock.tick()
        if (bSyncsEarly) { b.sync(); clock.tick() }
        a.sync(); clock.tick()
        drive.reachable = aCanDelete
        a.delete(song1); a.delete(song2); clock.tick()
        drive.reachable = true
    }

    @Test
    fun reportedSequence_aDeletionReachesDriveBeforeBSyncs() {
        for (bSyncsEarly in listOf(false, true)) {
            setUp()
            reportedSequence(bSyncsEarly, aCanDelete = true)

            b.sync()
            assertEquals("B, synced early: $bSyncsEarly", listOf(pianoOnB), b.shown)
            a.sync()
            assertEquals(listOf(pianoOnB), a.shown)
            b.sync()
            assertEquals(listOf(pianoOnB), b.shown)
            assertEquals(listOf(pianoOnB), drive.files.values.map { it.videoUrl })
            assertTrue(a.records.values.none { it.isDeleted })
        }
    }

    @Test
    fun reportedSequence_aDeletionThatFailedIsPushedFirstAtANextSync() {
        reportedSequence(bSyncsEarly = false, aCanDelete = false)
        assertEquals(emptyList<String>(), a.shown)
        assertEquals(2, a.records.values.count { it.isDeleted })

        // A's next sync, before B's: the deletion goes first, nothing of it comes back
        a.sync()
        assertEquals(emptyList<String>(), a.shown)
        assertTrue(drive.files.isEmpty())
        b.sync()
        assertEquals(listOf(pianoOnB), b.shown)
        a.sync()
        assertEquals(listOf(pianoOnB), a.shown)
    }

    @Test
    fun reportedSequence_bSyncingWhileADeletionIsStuck_dropsTheSongsAtItsNextSync() {
        reportedSequence(bSyncsEarly = true, aCanDelete = false)
        // Drive still has them and nothing on it says otherwise: B can't know yet
        b.sync()
        assertEquals(listOf(pianoOnB, song1, song2).sorted(), b.shown)

        a.sync()
        assertEquals(listOf(pianoOnB), a.shown)
        assertTrue(a.records.values.none { it.isDeleted })
        b.sync()
        assertEquals(listOf(pianoOnB), b.shown)
        a.sync()
        assertEquals(listOf(pianoOnB), a.shown)
    }

    @Test
    fun aSongDeletedBeforeItWasEverSynced_neverReachesDrive() {
        drive.reachable = false
        a.add(song1)
        a.delete(song1)
        drive.reachable = true
        a.sync()
        b.sync()

        assertTrue(drive.files.isEmpty())
        assertTrue(a.records.isEmpty())
        assertEquals(emptyList<String>(), b.shown)
    }

    @Test
    fun aCopyUploadedButNeverLinked_isDeletedWithTheSong() {
        // A sync or backup uploaded the song, and the app stopped before linking it
        a.add(song1, backUp = false)
        drive.upload(a.records.getValue(song1))
        clock.tick()
        drive.reachable = false
        a.delete(song1)
        drive.reachable = true

        a.sync()
        b.sync()
        assertTrue(drive.files.isEmpty())
        assertEquals(emptyList<String>(), b.shown)
    }

    @Test
    fun aCopyUploadedLateDuringTheDeletion_isDeletedToo() {
        // Deleted while its first upload ran; the copy is created after the deletion, and is still the deleted song
        a.add(song1, backUp = false); clock.tick()
        a.records[song1] = a.records.getValue(song1).copy(isDeleted = true, deletedAt = clock.now)
        clock.tick()
        drive.upload(a.records.getValue(song1).copy(isDeleted = false))

        a.sync()
        assertTrue(drive.files.isEmpty())
    }

    @Test
    fun aSongAddedAgainAfterTheDeletion_wins() {
        a.add(song1); clock.tick()
        b.sync(); clock.tick()
        drive.reachable = false
        a.delete(song1); clock.tick()
        drive.reachable = true
        // B deletes and transcribes it again, after A's deletion
        b.delete(song1); clock.tick()
        b.add(song1); clock.tick()

        a.sync()
        assertEquals(listOf(song1), a.shown)
        assertEquals(b.records.getValue(song1).cloudFileId, a.records.getValue(song1).cloudFileId)
        b.sync()
        assertEquals(listOf(song1), b.shown)
    }

    @Test
    fun aSongAddedElsewhereBeforeTheDeletion_isDeleted() {
        // B transcribed the same link before A deleted it (A had never seen B's copy)
        a.add(song1, backUp = false); clock.tick()
        b.add(song1); clock.tick()
        drive.reachable = false
        a.delete(song1); clock.tick()
        drive.reachable = true

        a.sync()
        b.sync()
        assertEquals(emptyList<String>(), a.shown)
        assertEquals(emptyList<String>(), b.shown)
    }

    @Test
    fun clockDifferences_neverKeepALinkedCopy() {
        val skewed = Device(drive, clock, clockOffset = -3_600_000L) // an hour behind
        skewed.add(song1); clock.tick()
        b.sync(); clock.tick()
        skewed.delete(song1)
        b.sync()
        assertEquals(emptyList<String>(), b.shown)
    }

    @Test
    fun deletingOneImportSharingAKey_keepsTheOther() {
        val shared = "file://same.mid"
        a.add(shared); clock.tick()
        val other = drive.upload(a.records.getValue(shared).copy(cloudFileId = "")).id // another device's import, same key
        drive.reachable = false
        a.delete(shared)
        drive.reachable = true
        a.sync()

        assertEquals(listOf(other), drive.files.keys.toList())
    }

    @Test
    fun aSongNeverSynced_isUploadedNotDeleted() {
        a.add(song1); clock.tick()
        b.add(pianoOnB, backUp = false)
        b.sync()
        assertEquals(listOf(pianoOnB, song1).sorted(), b.shown)
        assertEquals(2, drive.files.size)
    }

    @Test
    fun turningSyncOn_appliesDeletionsFromTheSameFolder_butKeepsSongsOfAnEmptiedOne() {
        a.add(song1); a.add(song2); clock.tick()
        b.sync(); clock.tick()
        a.delete(song1)
        b.sync(turningOn = true)
        assertEquals(listOf(song2), b.shown)

        drive.files.clear() // The app's Drive data removed by the user, or another folder
        b.sync(turningOn = true)
        assertEquals(listOf(song2), b.shown)
        assertEquals(listOf(song2), drive.files.values.map { it.videoUrl })
    }

    @Test
    fun aDriveRefusingTheDelete_throwsHttpException_soAnExpiredTokenIsRefreshed() {
        fun response(code: Int): Response<Any> =
            if (code in 200..299) Response.success(code, Unit)
            else Response.error(code, "{}".toResponseBody("application/json".toMediaType()))

        GoogleDriveManager.requireDeleted(response(204))
        GoogleDriveManager.requireDeleted(response(404))
        for (code in listOf(401, 403, 500)) {
            try {
                GoogleDriveManager.requireDeleted(response(code))
                fail("$code must throw")
            } catch (e: HttpException) {
                assertEquals(code, e.code())
            }
        }
    }

    @Test
    fun rules_matchByIdOrSongKey() {
        val tomb = StoredMidi(File("x.mid"), song1, placeholderMetadata(song1), isDeleted = true, deletedAt = 100L, cloudFileId = "f9")
        fun copy(id: String, url: String, added: Long) =
            CloudMidi(id, "x.mid", 1, 0, url, placeholderMetadata(url), addedAt = added, createdTime = added)
        val cloud = listOf(copy("f9", "other", 500), copy("f1", song1, 50), copy("f2", song1, 150), copy("f3", song2, 50))
        assertEquals(listOf("f9", "f1"), LibrarySyncRules.cloudCopiesDeletedBy(tomb, cloud).map { it.id })
        assertFalse(LibrarySyncRules.isDeletedRemotely(tomb.copy(isDeleted = false), cloud))
        assertTrue(LibrarySyncRules.isDeletedRemotely(tomb.copy(isDeleted = false, cloudFileId = "gone"), emptyList()))
    }

    private fun setUp() {
        drive.files.clear()
        drive.reachable = true
        a.records.clear()
        b.records.clear()
    }
}
