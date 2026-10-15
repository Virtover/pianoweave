package com.lumenchord.pianoweave.api

/**
 * The audio and video formats the server accepts for uploads (its ALLOWED_FORMATS, as ffprobe format
 * names), as MIME types for the file picker and extensions for files a provider types loosely.
 */
object UploadFormats {

    private class Format(val mimeTypes: List<String>, val extensions: List<String>)

    private val formats = listOf(
        // aac
        Format(listOf("audio/aac", "audio/x-aac", "audio/aacp"), listOf("aac")),
        // aiff
        Format(listOf("audio/aiff", "audio/x-aiff"), listOf("aif", "aiff", "aifc")),
        // asf (WMA/WMV)
        Format(listOf("audio/x-ms-wma", "video/x-ms-wmv", "video/x-ms-asf"), listOf("wma", "wmv", "asf")),
        // flac
        Format(listOf("audio/flac", "audio/x-flac"), listOf("flac")),
        // m4a, mov, mp4 (one ffprobe family)
        Format(listOf("audio/mp4", "audio/x-m4a", "audio/m4a", "video/mp4", "video/quicktime"), listOf("m4a", "mp4", "mov")),
        // matroska
        Format(listOf("video/x-matroska", "audio/x-matroska"), listOf("mkv", "mka")),
        // mp3
        Format(listOf("audio/mpeg", "audio/mp3"), listOf("mp3")),
        // ogg (incl. Opus)
        Format(listOf("audio/ogg", "audio/opus", "application/ogg", "video/ogg"), listOf("ogg", "oga", "opus", "ogv")),
        // wav
        Format(listOf("audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave"), listOf("wav")),
        // webm
        Format(listOf("audio/webm", "video/webm"), listOf("webm")),
    )

    /** Offered in the file picker, so other files cannot be chosen. */
    val mimeTypes: Array<String> = formats.flatMap { it.mimeTypes }.toTypedArray()

    private val extensions: Set<String> = formats.flatMap { it.extensions }.toSet()

    /** Short list for the UI. */
    const val DISPLAY_NAMES = "MP3, WAV, FLAC, M4A, AAC, OGG, AIFF, WMA, MP4, MOV, MKV, WebM"

    /**
     * True if the picked file is one of the accepted formats, by its MIME type or, when the provider
     * only reports a generic type, by its extension. The server still checks the actual content.
     */
    fun isAllowed(mimeType: String?, fileName: String?): Boolean {
        val mime = mimeType?.lowercase()?.substringBefore(';')?.trim()
        if (mime != null && mime in mimeTypes) return true
        val extension = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        val isGenericType = mime == null || mime == "application/octet-stream" || mime.isEmpty()
        return extension in extensions && (isGenericType || mime!!.startsWith("audio/") || mime.startsWith("video/"))
    }
}
