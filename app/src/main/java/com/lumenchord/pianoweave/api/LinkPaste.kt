package com.lumenchord.pianoweave.api

import java.net.URI

/**
 * Whether a change of the link field from [old] to [new] was a paste of a link: a valid link that arrived in one
 * change of at least [MIN_PASTED_CHARS] characters (into an empty field, over all of it, or over a part). Typing
 * changes a character at a time, so the field keeps its focus then; a paste of something that isn't a link keeps
 * it too, so it can be fixed. After a pasted link the field lets go of focus, and the read link's title shows.
 */
fun isPastedLink(old: String, new: String): Boolean =
    insertedLength(old, new) >= MIN_PASTED_CHARS && looksLikeLink(new)

private const val MIN_PASTED_CHARS = 5

/** How many characters [new] has that [old] didn't, between what the two share at the start and the end. */
internal fun insertedLength(old: String, new: String): Int {
    val prefix = old.commonPrefixWith(new).length
    val suffix = old.drop(prefix).commonSuffixWith(new.drop(prefix)).length
    return new.length - prefix - suffix
}

/** A web address: http(s), or a bare "site.tld/..." that the app completes with https, with a host and no spaces. */
internal fun looksLikeLink(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return false
    val withScheme = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(trimmed)) trimmed else "https://$trimmed"
    val uri = runCatching { URI(withScheme) }.getOrNull() ?: return false
    val host = uri.host ?: return false
    return uri.scheme.lowercase() in setOf("http", "https") && '.' in host.trim('.')
}
