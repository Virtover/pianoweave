package com.lumenchord.pianoweave.midi

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

/**
 * An ensemble job's results: the piano versions by difficulty and the raw multi-track (server
 * `GET /transcriptions/{id}/midi?variant=`, listed in the job status's `variants`).
 *
 * A song keeps all of them in its one .mid file, so it stays one song everywhere: one card, one Drive
 * file, one delete, one rename (the details live in its .meta). The file is the primary variant's MIDI
 * as the server sent it (full where there is one), followed by chunks of a type of our own: an index
 * chunk naming the primary, then one chunk per other variant holding its complete MIDI file. MIDI
 * readers skip chunks they don't know, and this app's reader (in every released version) stops after
 * the header's track count, so every reader, older app versions on other devices included, sees the
 * primary variant: a two-hand piano version. A song with one result is a plain MIDI file, as before.
 */
object MidiVariants {

    const val FULL = "full"
    const val ADVANCED = "advanced"
    const val INTERMEDIATE = "intermediate"
    const val EASY = "easy"
    const val BEGINNER = "beginner"
    const val RAW = "raw"

    /** The server's order, and the order they are offered in. */
    val ORDER = listOf(FULL, ADVANCED, INTERMEDIATE, EASY, BEGINNER, RAW)

    /**
     * What a variant is called on screen. A piano song's raw is Transkun's own result, the notes as the pianist
     * played them: "Original" there. The stored and sent name stays "raw" everywhere.
     */
    fun label(variant: String, isEnsemble: Boolean = true): String =
        if (variant == RAW && !isEnsemble) "Original" else variant.replaceFirstChar { it.uppercase() }

    /** An exported variant's file name, without ".mid": "Song - full", a piano song's raw "Song - original". */
    fun exportTitle(songTitle: String, variant: String, isEnsemble: Boolean = true): String =
        "$songTitle - ${label(variant, isEnsemble).lowercase()}"

    /**
     * The order a song's default is looked for in, per mode: the one place to change it. Ensemble opens on the
     * arranged full version (else the hardest level, else raw); piano on the transcription as played.
     */
    fun defaultOrder(isEnsemble: Boolean): List<String> =
        if (isEnsemble) ORDER else listOf(RAW) + ORDER.filter { it != RAW }

    private const val INDEX_CHUNK = 0x50577669 // "PWvi"
    private const val VARIANT_CHUNK = 0x50577672 // "PWvr"
    private const val HEADER_CHUNK = 0x4D546864 // "MThd"

    /** The known names of [names], once each, in [ORDER]. Names from a newer server are left out. */
    fun ordered(names: Collection<String>): List<String> = ORDER.filter { it in names }

    /** The variant a song opens with when nothing is stored; also the one other readers see (see [bundle]). */
    fun primaryOf(names: Collection<String>, isEnsemble: Boolean = true): String? =
        defaultOrder(isEnsemble).firstOrNull { it in names }

    /**
     * The variant shown for a song that has [available], which is exactly what the server listed: for a simple
     * song only the levels that differ, for drums alone only raw. The stored choice while it exists, then the
     * mode's default ([defaultOrder]).
     */
    fun choose(stored: String?, available: List<String>, isEnsemble: Boolean = true): String? {
        val names = ordered(available)
        return when {
            names.isEmpty() -> null
            stored != null && stored in names -> stored
            else -> primaryOf(names, isEnsemble)
        }
    }

    /** One file of [variants] (name to MIDI bytes); a single variant stays a plain MIDI file. */
    fun bundle(variants: Map<String, ByteArray>, isEnsemble: Boolean = true): ByteArray {
        val names = ordered(variants.keys)
        require(names.isNotEmpty()) { "No variants" }
        // What every other reader sees, older app versions too: a piano song stays the transcription as played
        val primary = primaryOf(names, isEnsemble)!!
        if (names.size == 1) return variants.getValue(primary)
        val out = ByteArrayOutputStream()
        out.write(variants.getValue(primary))
        writeChunk(out, INDEX_CHUNK, primary.toByteArray(Charsets.UTF_8))
        for (name in names - primary) {
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            writeChunk(out, VARIANT_CHUNK, byteArrayOf(nameBytes.size.toByte()) + nameBytes + variants.getValue(name))
        }
        return out.toByteArray()
    }

    /** The variants in [bytes] by name, in [ORDER]; empty for a plain MIDI file (one result, or an older song). */
    fun read(bytes: ByteArray): Map<String, ByteArray> {
        val chunks = chunksOf(bytes) ?: return emptyMap()
        val index = chunks.firstOrNull { it.type == INDEX_CHUNK } ?: return emptyMap()
        val primary = String(bytes, index.start, index.length, Charsets.UTF_8)
        // A damaged index can't say what the primary is: the file is then what every other reader sees, one song
        if (primary !in ORDER) return emptyMap()
        val found = linkedMapOf(primary to bytes.copyOfRange(0, index.start - 8))
        for (chunk in chunks.filter { it.type == VARIANT_CHUNK }) {
            if (chunk.length < 1) continue
            val nameLength = bytes[chunk.start].toInt() and 0xFF
            if (nameLength + 1 > chunk.length) continue
            val name = String(bytes, chunk.start + 1, nameLength, Charsets.UTF_8)
            val midi = bytes.copyOfRange(chunk.start + 1 + nameLength, chunk.start + chunk.length)
            // A level whose MIDI is damaged is left out; the primary and the others stay
            if (chunksOf(midi) == null) continue
            found.putIfAbsent(name, midi)
        }
        val variants = ordered(found.keys).associateWith { found.getValue(it) }
        // One level left is no choice: the song plays as the plain file it then is
        return if (variants.size < 2) emptyMap() else variants
    }

    fun read(file: File): Map<String, ByteArray> = runCatching { read(file.readBytes()) }.getOrDefault(emptyMap())

    /** The MIDI of [variant] in [bytes]; a plain file is its own only variant, whatever it is called. */
    fun extract(bytes: ByteArray, variant: String?): ByteArray {
        val variants = read(bytes)
        if (variants.isEmpty()) return bytes
        return variants[variant] ?: variants.getValue(primaryOf(variants.keys)!!)
    }

    private class Chunk(val type: Int, val start: Int, val length: Int)

    /** Every chunk of an SMF file, or null if it isn't one or a chunk runs past the end. */
    private fun chunksOf(bytes: ByteArray): List<Chunk>? {
        val buffer = ByteBuffer.wrap(bytes)
        if (bytes.size < 14 || buffer.getInt(0) != HEADER_CHUNK) return null
        val chunks = mutableListOf<Chunk>()
        var at = 0
        while (at + 8 <= bytes.size) {
            val type = buffer.getInt(at)
            val length = buffer.getInt(at + 4)
            if (length < 0 || at + 8 + length > bytes.size) return null
            chunks += Chunk(type, at + 8, length)
            at += 8 + length
        }
        return chunks
    }

    private fun writeChunk(out: ByteArrayOutputStream, type: Int, payload: ByteArray) {
        out.write(ByteBuffer.allocate(8).putInt(type).putInt(payload.size).array())
        out.write(payload)
    }

    /**
     * A job completed with only raw listed may still get its levels: a RunPod endpoint on an older image sends
     * raw, and the server adds the levels a few seconds later. So while [mayGetLevels] its status is read again
     * every [intervalMs] for up to [maxWaitMs], and whatever is listed then is taken (drums alone stay raw only).
     * A piano job only where the server says it makes piano levels ([mayGetLevels]): elsewhere raw is a piano
     * job's one result. Never for an older server that lists nothing. A failed read ends the wait.
     */
    /** Whether a completed job listing only raw may still get levels: an ensemble's always, a piano job's where the server makes them. */
    fun mayGetLevels(isEnsemble: Boolean, serverMakesPianoLevels: Boolean): Boolean = isEnsemble || serverMakesPianoLevels

    suspend fun awaitLateLevels(
        listed: List<String>?,
        mayGetLevels: Boolean,
        refresh: suspend () -> List<String>?,
        pause: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
        intervalMs: Long = 2_000L,
        maxWaitMs: Long = 20_000L
    ): List<String>? {
        var current = listed
        var waited = 0L
        while (mayGetLevels && current == listOf(RAW) && waited < maxWaitMs) {
            pause(intervalMs)
            waited += intervalMs
            current = try {
                refresh() ?: current
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                return current
            }
        }
        return current
    }

    /**
     * Downloads a finished job's results as one song file. [listed] is the status's `variants` (null from
     * older servers): with fewer than two, the plain download is the one result. Otherwise each is fetched
     * by name. The default one and raw are retried as any download is (their failure is thrown, and the
     * caller tries again); a missing one (404) is left out, and the others are tried twice and left out if
     * they still fail, so one variant never costs the song the rest. Throws only if none could be had.
     */
    suspend fun download(
        listed: List<String>?,
        fetch: suspend (variant: String?) -> ByteArray,
        isMissing: (Throwable) -> Boolean,
        isEnsemble: Boolean = true
    ): ByteArray {
        val names = ordered(listed.orEmpty())
        if (names.size < 2) return fetch(null)
        val required = setOfNotNull(primaryOf(names, isEnsemble), RAW.takeIf { it in names })
        val got = linkedMapOf<String, ByteArray>()
        var lastMissing: Throwable? = null
        for (name in names) {
            val attempts = if (name in required) 1 else 2
            for (attempt in 1..attempts) {
                try {
                    got[name] = fetch(name).also { check(it.isNotEmpty()) { "Empty $name MIDI" } }
                    break
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    if (isMissing(e)) { lastMissing = e; break }
                    if (name in required) throw e
                }
            }
        }
        if (got.isEmpty()) throw lastMissing ?: IllegalStateException("No result could be downloaded")
        return bundle(got, isEnsemble)
    }
}
