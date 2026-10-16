package com.lumenchord.pianoweave.cloud

import android.content.Context
import com.lumenchord.pianoweave.midi.StoredMidi
import com.lumenchord.pianoweave.util.AppPrefs

/**
 * Songs whose title or artist was edited on this device but not yet written to their cloud copy.
 * While a song is listed here its local details win during sync; once pushed, the cloud copy's
 * details win again, which is how an edit made on another device reaches this one.
 */
object PendingDetailEdits {

    private val lock = Any()

    private fun key(song: StoredMidi) = song.file.name

    fun add(context: Context, song: StoredMidi) = change(context) { it += key(song) }

    fun remove(context: Context, song: StoredMidi) = change(context) { it -= key(song) }

    fun contains(context: Context, song: StoredMidi): Boolean =
        key(song) in read(context)

    /** Forgets songs that are no longer in the library. */
    fun retainOnly(context: Context, songs: List<StoredMidi>) {
        val names = songs.map(::key).toSet()
        change(context) { it.retainAll(names) }
    }

    private fun read(context: Context): Set<String> =
        AppPrefs.get(context).getStringSet(AppPrefs.PENDING_DETAIL_EDITS, null).orEmpty()

    private fun change(context: Context, block: (MutableSet<String>) -> Unit) {
        synchronized(lock) {
            // getStringSet's result must not be modified in place.
            val updated = read(context).toMutableSet().also(block)
            AppPrefs.get(context).edit().putStringSet(AppPrefs.PENDING_DETAIL_EDITS, updated).apply()
        }
    }
}
