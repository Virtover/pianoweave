package com.lumenchord.pianoweave.util

import android.content.Context
import android.content.SharedPreferences

/** The app's main SharedPreferences file and every key stored in it. */
object AppPrefs {
    private const val NAME = "piano_weave_prefs"

    // Identity & auth
    const val USER_ID = "user_id"
    const val GOOGLE_TOKEN = "google_token"
    const val GOOGLE_USER_EMAIL = "google_user_email"
    const val CUSTOM_GOOGLE_CLIENT_ID = "custom_google_client_id"
    const val LAST_BILLED_SERVER_UNAUTHENTICATED = "last_billed_server_unauthenticated"

    // Server
    const val USE_CUSTOM_SERVER = "use_custom_server"
    const val CUSTOM_SERVER_URL = "custom_server_url"
    const val USER_CREDITS = "user_credits"

    // Transcription
    const val ACTIVE_JOB_ID = "active_job_id"
    const val ACTIVE_JOB_URL = "active_job_url"

    // Cloud sync
    const val IS_CLOUD_SYNC_ENABLED = "is_cloud_sync_enabled"
    const val HAS_CLOUD_SYNC_ERROR = "has_cloud_sync_error"
    const val CLOUD_SYNC_ERROR = "cloud_sync_error"
    const val HAS_SHOWN_CLOUD_BACKUP_DIALOG = "has_shown_cloud_backup_dialog"

    // UI & playback
    const val SELECTED_THEME_ID = "selected_theme_id"
    const val TOP_BAR_SPEED = "top_bar_speed"
    const val IS_STRIKE_OVERLAY_ENABLED = "is_strike_overlay_enabled"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
