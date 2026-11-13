package com.lumenchord.pianoweave.api

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** How loudly an announcement is shown: [CRITICAL] before anything else, [WARNING] as a banner, [INFO] as a note. */
enum class AnnouncementSeverity(val apiValue: String, val rank: Int) {
    INFO("info", 0),
    WARNING("warning", 1),
    CRITICAL("critical", 2);

    companion object {
        /** An unknown severity (from a newer server) still shows, as the quietest kind. */
        fun from(value: String?): AnnouncementSeverity =
            entries.firstOrNull { it.apiValue == value?.trim()?.lowercase() } ?: INFO
    }
}

/** A run of text with one style. [link] is an `https` address, or null. */
data class AnnouncementSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val link: String? = null
)

/** The formatted text of an announcement, already parsed and checked by the server. */
sealed interface AnnouncementBlock {
    data class Paragraph(val spans: List<AnnouncementSpan>) : AnnouncementBlock

    /** [level] 1 is the larger. */
    data class Heading(val level: Int, val spans: List<AnnouncementSpan>) : AnnouncementBlock

    /** Bulleted, or numbered from [start] when [ordered]; each item is a run of spans. */
    data class ItemList(val ordered: Boolean, val start: Int, val items: List<List<AnnouncementSpan>>) : AnnouncementBlock
}

data class AnnouncementLink(val url: String, val label: String?)

/**
 * A message from a server's administrator (`GET /api/announcements`), for example how to keep using the app
 * once it is no longer in its store. [serverUrl] is the server it came from: ids are unique per server only.
 * [startsAt] and [endsAt] are Unix seconds.
 */
data class Announcement(
    val serverUrl: String,
    val id: String,
    val severity: AnnouncementSeverity,
    val title: String,
    val body: String,
    val blocks: List<AnnouncementBlock>,
    val link: AnnouncementLink?,
    val dismissible: Boolean,
    val startsAt: Long,
    val endsAt: Long?
) {
    /** Identifies it across servers; what dismissals are stored under. */
    val key: String get() = announcementKey(serverUrl, id)

    val isBlocking: Boolean get() = severity == AnnouncementSeverity.CRITICAL && !dismissible

    /**
     * The server lists only started ones, so the start isn't checked against this device's clock, which may be
     * wrong; a kept list can still outlive an announcement's end.
     */
    fun isActive(nowSec: Long): Boolean = endsAt == null || endsAt > nowSec
}

internal fun announcementKey(serverUrl: String, id: String) = "$serverUrl $id"

/**
 * Reads the server's announcement list. Lenient where a newer server may differ (unknown block types and
 * severities, missing optional fields) and strict where it matters: a link that isn't `https` is dropped,
 * so it can never open anything but the browser.
 */
object AnnouncementParser {

    /** The `announcements` array of a response, or null if [json] isn't a response. */
    fun parseResponse(json: String, serverUrl: String): List<Announcement>? =
        runCatching { JsonParser.parseString(json) }.getOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject?.get("announcements")
            ?.takeIf { it.isJsonArray }
            ?.let { parseList(it.asJsonArray, serverUrl) }

    fun parseList(array: JsonArray, serverUrl: String): List<Announcement> =
        array.mapNotNull { element -> element.obj()?.let { parseAnnouncement(it, serverUrl) } }

    private fun parseAnnouncement(o: JsonObject, serverUrl: String): Announcement? {
        val id = o.string("id")?.takeIf { it.isNotBlank() } ?: return null
        val title = o.string("title")?.trim().orEmpty()
        val body = o.string("body").orEmpty()
        if (title.isEmpty() && body.isBlank()) return null
        val severity = AnnouncementSeverity.from(o.string("severity"))
        val blocks = o.get("blocks")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.obj()?.let(::parseBlock) }
            .orEmpty()
            // A server that sends no formatting still has the plain text
            .ifEmpty { plainBlocks(body) }
        val link = o.get("link")?.obj()?.let { l ->
            safeHttpsUrl(l.string("url"))?.let { AnnouncementLink(it, l.string("label")?.trim()?.takeIf(String::isNotEmpty)) }
        }
        return Announcement(
            serverUrl = serverUrl,
            id = id,
            severity = severity,
            title = title,
            body = body,
            blocks = blocks,
            link = link,
            dismissible = o.bool("dismissible") ?: (severity != AnnouncementSeverity.CRITICAL),
            startsAt = o.long("starts_at") ?: 0L,
            endsAt = o.long("ends_at")
        )
    }

    private fun parseBlock(o: JsonObject): AnnouncementBlock? = when (o.string("type")) {
        "paragraph" -> spans(o.get("spans"))?.let { AnnouncementBlock.Paragraph(it) }
        "heading" -> spans(o.get("spans"))?.let { AnnouncementBlock.Heading((o.long("level") ?: 1L).toInt().coerceIn(1, 2), it) }
        "list" -> o.get("items")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull(::spans)
            ?.takeIf { it.isNotEmpty() }
            ?.let { AnnouncementBlock.ItemList(o.bool("ordered") ?: false, (o.long("start") ?: 1L).toInt(), it) }
        // A block type this app doesn't know: skipped, so a later one doesn't break it
        else -> null
    }

    private fun spans(element: JsonElement?): List<AnnouncementSpan>? =
        element?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.obj() }
            ?.mapNotNull { s ->
                val text = s.string("text") ?: return@mapNotNull null
                AnnouncementSpan(text, s.bool("bold") ?: false, s.bool("italic") ?: false, safeHttpsUrl(s.string("link")))
            }
            ?.filter { it.text.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }

    private fun plainBlocks(body: String): List<AnnouncementBlock> =
        body.split(Regex("\n\\s*\n")).map { it.trim('\n') }.filter { it.isNotBlank() }
            .map { AnnouncementBlock.Paragraph(listOf(AnnouncementSpan(it))) }

    /**
     * [url], normalized, if it is a well-formed `https` address, else null. Normalized because Android matches
     * intent schemes case-sensitively: `HTTPS://` would find no browser.
     */
    fun safeHttpsUrl(url: String?): String? {
        val trimmed = url?.trim() ?: return null
        if (!trimmed.startsWith("https://", ignoreCase = true)) return null
        return trimmed.toHttpUrlOrNull()?.takeIf { it.isHttps }?.toString()
    }

    private fun JsonElement.obj(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject.prim(name: String) = get(name)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
    private fun JsonObject.string(name: String): String? = prim(name)?.takeIf { it.isString }?.asString
    private fun JsonObject.bool(name: String): Boolean? = prim(name)?.takeIf { it.isBoolean }?.asBoolean
    private fun JsonObject.long(name: String): Long? = prim(name)?.takeIf { it.isNumber }?.let { runCatching { it.asLong }.getOrNull() }
}

/** Most severe first, then newest, as the server orders them; also for lists merged from two servers. */
internal fun List<Announcement>.inDisplayOrder(): List<Announcement> =
    sortedWith(compareByDescending<Announcement> { it.severity.rank }.thenByDescending { it.startsAt })

/**
 * [keys] (dismissed ones) without those of [serverUrl] that its fresh list no longer has: the server ended
 * them, so remembering them is pointless. Other servers' keys are kept.
 */
internal fun prunedKeys(keys: Set<String>, serverUrl: String, listed: List<Announcement>): Set<String> {
    val prefix = announcementKey(serverUrl, "")
    val listedKeys = listed.mapTo(HashSet()) { it.key }
    return keys.filterTo(LinkedHashSet()) { !it.startsWith(prefix) || it in listedKeys }
}
