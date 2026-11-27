package com.lumenchord.pianoweave.api

/**
 * What the Link source shows of a pasted link before it is transcribed, from the server's cost check
 * ([BillingCostResponse]); the app never asks the video site itself. Every part is optional: an older
 * server sends only the length.
 */
data class LinkPreview(
    val title: String? = null,
    val channel: String? = null,
    /** An https image URL, or null: anything else is never loaded. */
    val thumbnail: String? = null,
    val durationSeconds: Float? = null
) {
    /** The muted line under the title: "Elevation Worship · 5:44", either part alone, or null. */
    val detailLine: String?
        get() = listOfNotNull(channel, durationSeconds?.let(::formatDuration)).joinToString(" · ").ifEmpty { null }

    val isEmpty: Boolean get() = title == null && detailLine == null

    /** Read out instead of the row's parts, so a screen reader says it in one go. */
    val contentDescription: String
        get() = listOfNotNull(title?.let { "Video: $it" }, channel?.let { "by $it" }, durationSeconds?.let { "length ${formatDuration(it)}" })
            .joinToString(", ")
}

fun BillingCostResponse.toLinkPreview(): LinkPreview = LinkPreview(
    title = title?.trim()?.takeIf { it.isNotEmpty() },
    channel = channel?.trim()?.takeIf { it.isNotEmpty() },
    thumbnail = thumbnail?.trim()?.takeIf { it.startsWith("https://", ignoreCase = true) },
    durationSeconds = durationSeconds.takeIf { it > 0f && it.isFinite() }
)

/** "5:44", "1:02:03". */
fun formatDuration(seconds: Float): String {
    val total = seconds.toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
