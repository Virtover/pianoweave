package com.lumenchord.pianoweave.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.lumenchord.pianoweave.R
import com.lumenchord.pianoweave.cloud.AccountProgressSync
import com.lumenchord.pianoweave.ui.theme.AppThemeManager
import com.lumenchord.pianoweave.util.AccountProgress
import com.lumenchord.pianoweave.util.AppPrefs
import com.lumenchord.pianoweave.util.LauncherIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "AccountProgress"

/** Reads the progress the screens show (and the light theme, which needs its unlock) back from [AccountProgress]. */
internal fun PianoWeaveViewModel.reloadProgressImpl(context: Context) {
    isLightThemeUnlocked = AccountProgress.getFlag(context, AppPrefs.IS_LIGHT_THEME_UNLOCKED)
    val prefs = AppPrefs.get(context)
    AppThemeManager.isLight = isLightThemeUnlocked && prefs.getBoolean(AppPrefs.IS_LIGHT_THEME, false)
    selectedThemeId = selectedThemeFor(context, AppThemeManager.isLight)
    LauncherIcon.requestLight(context, AppThemeManager.isLight)
    showHowItWorksHint = !AccountProgress.getFlag(context, AppPrefs.HAS_SEEN_WELCOME_TUTORIAL)
}

/** Merges with the account's copy in Drive ([token] already checked); rereads what changed. Never fails the caller. */
internal suspend fun PianoWeaveViewModel.syncAccountProgressImpl(context: Context, token: String) {
    AccountProgressSync.sync(context, token)
        .onSuccess { changed -> if (changed) withContext(Dispatchers.Main) { reloadProgressImpl(context) } }
        .onFailure { Log.w(TAG, "Couldn't sync progress with the account", it) }
}

/** After a change here: sends it to the account's copy, when cloud sync is on (otherwise only Android's backup keeps it). */
internal fun PianoWeaveViewModel.pushAccountProgressImpl(context: Context) {
    if (!isCloudSyncEnabled || !isGoogleSignedIn || AccountProgress.isIgnoringAccount(context)) return
    viewModelScope.launch(Dispatchers.IO) {
        val token = ensureGoogleAuthTokenImpl(context)
        if (!token.isNullOrBlank()) syncAccountProgressImpl(context, token)
    }
}

internal fun PianoWeaveViewModel.setProgressFlag(context: Context, key: String, value: Boolean) {
    if (AccountProgress.getFlag(context, key) == value) return
    AccountProgress.setFlag(context, key, value)
    pushAccountProgressImpl(context)
}

/** Debug builds: the light themes hidden again, as for someone who never found them (back to the night first). */
internal fun PianoWeaveViewModel.resetLightThemeUnlockImpl(context: Context) {
    setLightThemeImpl(context, false)
    isLightThemeUnlocked = false
    setProgressFlag(context, AppPrefs.IS_LIGHT_THEME_UNLOCKED, false)
}

/** Debug builds: the Verse of Light as never opened (the longest hold again), here and on the account. */
internal fun PianoWeaveViewModel.resetVerseOfLightImpl(context: Context) {
    AccountProgress.resetCount(context, AppPrefs.VERSE_OF_LIGHT_OPEN_COUNT)
    pushAccountProgressImpl(context)
}

/** "Let there be light" (Genesis 1:3): the light themes, found by holding Lamplight. Returns whether it was new. */
internal fun PianoWeaveViewModel.unlockLightThemeImpl(context: Context): Boolean {
    if (isLightThemeUnlocked) return false
    isLightThemeUnlocked = true
    setProgressFlag(context, AppPrefs.IS_LIGHT_THEME_UNLOCKED, true)
    return true
}

internal fun PianoWeaveViewModel.setLightThemeImpl(context: Context, light: Boolean) {
    val toLight = light && isLightThemeUnlocked
    if (toLight == AppThemeManager.isLight) return
    val prefs = AppPrefs.get(context)
    val targetKey = if (toLight) AppPrefs.SELECTED_DAY_THEME_ID else AppPrefs.SELECTED_THEME_ID
    // The same theme on the other side, else its partner, else that side's last choice, else its default
    val next = AppThemeManager.counterpart(selectedThemeId, toLight, prefs.getString(targetKey, null))
    AppThemeManager.isLight = toLight
    selectedThemeId = next
    prefs.edit().putBoolean(AppPrefs.IS_LIGHT_THEME, toLight).putString(targetKey, next).apply()
    applyDaylightOutsideApp(context, toLight)
}

/** The theme chosen for one side, a merged theme followed to its replacement; that side's default if none yet. */
internal fun selectedThemeFor(context: Context, light: Boolean): String {
    val prefs = AppPrefs.get(context)
    val saved = prefs.getString(if (light) AppPrefs.SELECTED_DAY_THEME_ID else AppPrefs.SELECTED_THEME_ID, null)
    return AppThemeManager.resolveId(saved)?.takeIf { AppThemeManager.getTheme(it, light).id == it }
        ?: if (light) AppThemeManager.DEFAULT_DAY_ID else AppThemeManager.DEFAULT_NIGHT_ID
}

/**
 * The light beyond the app's own screens: the launcher icon (swapped when the app goes to the background, see
 * [LauncherIcon]) and, from Android 13, the splash screen of the next launches.
 */
internal fun applyDaylightOutsideApp(context: Context, light: Boolean) {
    LauncherIcon.requestLight(context, light)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        (context as? Activity)?.splashScreen?.setSplashScreenTheme(
            if (light) R.style.Theme_PianoWeave_Light else R.style.Theme_PianoWeave
        )
    }
}

/** Debug: progress apart from the account and this device (a newcomer's), or back to the real one. */
internal fun PianoWeaveViewModel.setIgnoringAccountProgressImpl(context: Context, ignore: Boolean) {
    AccountProgress.setIgnoringAccount(context, ignore)
    isIgnoringAccountProgress = ignore
    reloadProgressImpl(context)
    if (!ignore) pushAccountProgressImpl(context)
}
