package com.lumenchord.pianoweave.cloud

import com.lumenchord.pianoweave.midi.StoredMidi
import com.lumenchord.pianoweave.midi.isSharedSourceKey

/**
 * What a library sync decides, from one listing of the device's library and one of Drive. Pure, so the
 * rules that keep a deleted song deleted can be tested across devices (LibrarySyncRulesTest).
 *
 * A song is one Drive file, so there is no index to keep consistent: a song
 * is in the account while its file is, and deleting the file is the deletion every device sees. A
 * deletion that couldn't reach Drive stays here as a tombstone (the soft-deleted record) and is pushed
 * first at every later sync, before anything is uploaded or downloaded.
 */
internal object LibrarySyncRules {

    /**
     * The cloud copies [tombstone] deletes: the one it was linked to, and for a key that names one song
     * (not a shared import key), every copy of that key added to the library before the deletion: one
     * uploaded by a sync or backup this device never heard back from (a crash before it was linked), or
     * one an older version uploaded twice. A copy of the key added after the deletion is the song added
     * again, on any device, and wins: it stays, and comes to this device.
     *
     * "Added" is the song's own date, carried in each copy's description (Drive's creation time for older
     * copies), so a copy uploaded late still counts as the song that was deleted. The dates come from
     * different devices' clocks; a clock that is off moves the line by that much, which matters only for a
     * song deleted here and added again elsewhere within that difference. A copy without a date predates them.
     */
    fun cloudCopiesDeletedBy(tombstone: StoredMidi, cloud: List<CloudMidi>): List<CloudMidi> =
        cloud.filter { copy ->
            val added = copy.addedAt.takeIf { it > 0L } ?: copy.createdTime
            copy.id == tombstone.cloudFileId || (
                !tombstone.videoUrl.isSharedSourceKey &&
                    copy.videoUrl == tombstone.videoUrl &&
                    (added <= 0L || tombstone.deletedAt <= 0L || added <= tombstone.deletedAt)
                )
        }

    /** A song linked to a cloud copy that is gone, with no other copy of its key: deleted on another device. */
    fun isDeletedRemotely(song: StoredMidi, cloud: List<CloudMidi>): Boolean =
        song.cloudFileId.isNotBlank() &&
            cloud.none { it.id == song.cloudFileId } &&
            (song.videoUrl.isSharedSourceKey || cloud.none { it.videoUrl == song.videoUrl })

    /**
     * Whether songs whose cloud copy is gone are deleted here. Always, except when sync is being turned on
     * and none of this library's links are in the folder at all: then the folder is not the one these songs
     * were synced with (emptied, or another one), nothing in it says they were deleted, and they upload.
     */
    fun appliesRemoteDeletions(turningOn: Boolean, local: List<StoredMidi>, cloud: List<CloudMidi>): Boolean {
        if (!turningOn) return true
        val ids = cloud.map { it.id }.toSet()
        val links = local.map { it.cloudFileId }.filter { it.isNotBlank() }
        return links.isEmpty() || links.any { it in ids }
    }

    data class Deletions(
        /** Active songs deleted on another device: removed here. */
        val deletedRemotely: List<StoredMidi>,
        /** Each tombstone with the cloud copies it deletes (maybe none: then it is simply forgotten). */
        val tombstones: List<Pair<StoredMidi, List<CloudMidi>>>
    ) {
        val cloudCopies: List<CloudMidi> get() = tombstones.flatMap { it.second }.distinctBy { it.id }
    }

    fun deletions(local: List<StoredMidi>, cloud: List<CloudMidi>, applyRemoteDeletions: Boolean): Deletions =
        Deletions(
            deletedRemotely = if (applyRemoteDeletions) local.filter { !it.isDeleted && isDeletedRemotely(it, cloud) } else emptyList(),
            tombstones = local.filter { it.isDeleted }.map { it to cloudCopiesDeletedBy(it, cloud) }
        )

    data class Transfers(
        val upload: List<StoredMidi>,
        val link: List<Pair<StoredMidi, CloudMidi>>,
        val download: List<CloudMidi>
    )

    /**
     * Both directions from the same snapshot; they are disjoint, so they can run at the same time. [active]
     * holds no tombstones, and [cloud] no copy a tombstone deletes (not even one whose deletion failed: it
     * is retried next time, never downloaded). Keys shared by several songs (older imports) match by id only.
     */
    fun transfers(active: List<StoredMidi>, cloud: List<CloudMidi>): Transfers {
        val cloudById = cloud.associateBy { it.id }
        val cloudByUrl = cloud.filterNot { it.videoUrl.isSharedSourceKey }.associateBy { it.videoUrl }
        val localIds = active.map { it.cloudFileId }.filter { it.isNotBlank() }.toSet()
        val localUrls = active.map { it.videoUrl }.filterNot { it.isSharedSourceKey }.toSet()
        fun sharedOrMissing(song: StoredMidi) = song.videoUrl.isSharedSourceKey || song.videoUrl !in cloudByUrl
        return Transfers(
            upload = active.filter { it.cloudFileId !in cloudById && sharedOrMissing(it) },
            link = active.filter { it.cloudFileId !in cloudById && !sharedOrMissing(it) }
                .map { it to cloudByUrl.getValue(it.videoUrl) },
            download = cloud.filter { it.id !in localIds && (it.videoUrl.isSharedSourceKey || it.videoUrl !in localUrls) }
        )
    }
}
