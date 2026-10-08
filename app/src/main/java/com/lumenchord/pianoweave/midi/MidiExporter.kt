package com.lumenchord.pianoweave.midi

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.InputStream

enum class ExportState { Idle, Exporting, Done, Failed }

object MidiExporter {

    /** Copies the stream from [openSource] into Downloads. Call from Dispatchers.IO. */
    fun exportToDownloads(
        context: Context,
        title: String,
        openSource: () -> InputStream
    ): Result<String> = runCatching {

        val fileName = title
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .ifBlank { "song" }
            .let { if (it.endsWith(".mid", true) || it.endsWith(".midi", true)) it else "$it.mid" }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // No permission needed; MediaStore renames on collision: "song (1).mid"
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "audio/midi")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create file in Downloads")
            try {
                resolver.openOutputStream(uri)!!.use { out ->
                    openSource().use { it.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null) // don't leave a half-written file
                throw e
            }
        } else {
            // Android 9 and below: needs WRITE_EXTERNAL_STORAGE (maxSdkVersion="28")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            var target = File(dir, fileName)
            var n = 1
            while (target.exists()) {
                target = File(dir, "${fileName.substringBeforeLast('.')} ($n).${fileName.substringAfterLast('.')}")
                n++
            }
            openSource().use { input -> target.outputStream().use { input.copyTo(it) } }
        }
        fileName
    }
}

object ExportNotifier {
    private const val CHANNEL_ID = "midi_export"

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, "Exports", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "MIDI export progress" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED &&
                NotificationManagerCompat.from(context).areNotificationsEnabled()

    @SuppressLint("MissingPermission") // guarded by canNotify
    private fun post(context: Context, id: Int, build: NotificationCompat.Builder.() -> Unit) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val n = NotificationCompat.Builder(context, CHANNEL_ID).apply(build).build()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    private fun idFor(key: String) = key.hashCode()

    fun started(context: Context, key: String, title: String) = post(context, idFor(key)) {
        setSmallIcon(android.R.drawable.stat_sys_download)
        setContentTitle(title)
        setContentText("Exporting to Downloads…")
        setProgress(0, 0, true)
        setOngoing(true)
        setOnlyAlertOnce(true)
    }

    fun finished(context: Context, key: String, title: String, fileName: String) =
        post(context, idFor(key)) {
            val openDownloads = PendingIntent.getActivity(
                context, idFor(key),
                Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            setSmallIcon(android.R.drawable.stat_sys_download_done)
            setContentTitle(title)
            setContentText("Saved to Downloads: $fileName")
            setProgress(0, 0, false)
            setOngoing(false)
            setAutoCancel(true)
            setContentIntent(openDownloads)
        }

    fun failed(context: Context, key: String, title: String, reason: String?) =
        post(context, idFor(key)) {
            setSmallIcon(android.R.drawable.stat_notify_error)
            setContentTitle(title)
            setContentText("Export failed${reason?.let { ": $it" } ?: ""}")
            setProgress(0, 0, false)
            setOngoing(false)
            setAutoCancel(true)
        }
}