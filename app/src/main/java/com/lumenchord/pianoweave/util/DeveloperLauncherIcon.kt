package com.lumenchord.pianoweave.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The launcher icon: the cat for a developer's account, the usual one otherwise. The manifest has one
 * launcher alias for each, and exactly one is enabled.
 *
 * Swapping them makes some launchers close the app, so [request] only remembers the wish, and [applyPending]
 * swaps when the app goes to the background. The wish changes only on a definite answer (the server's balance,
 * or signing out), never because the server couldn't be reached.
 */
object DeveloperLauncherIcon {
    private const val DEFAULT_ALIAS = "com.lumenchord.pianoweave.LauncherDefault"
    private const val DEVELOPER_ALIAS = "com.lumenchord.pianoweave.LauncherDeveloper"

    fun request(context: Context, developer: Boolean) {
        AppPrefs.get(context).edit().putBoolean(AppPrefs.WANTS_DEVELOPER_LAUNCHER_ICON, developer).apply()
    }

    fun applyPending(context: Context) {
        val developer = AppPrefs.get(context).getBoolean(AppPrefs.WANTS_DEVELOPER_LAUNCHER_ICON, false)
        val packageManager = context.packageManager
        val developerAlias = ComponentName(context.packageName, DEVELOPER_ALIAS)
        if (isEnabled(packageManager, developerAlias, enabledByDefault = false) == developer) return
        val defaultAlias = ComponentName(context.packageName, DEFAULT_ALIAS)
        // The new one first, so there is never a moment without a launcher entry
        val (on, off) = if (developer) developerAlias to defaultAlias else defaultAlias to developerAlias
        setEnabled(packageManager, on, true)
        setEnabled(packageManager, off, false)
    }

    private fun isEnabled(packageManager: PackageManager, component: ComponentName, enabledByDefault: Boolean): Boolean =
        when (packageManager.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> enabledByDefault
            else -> false
        }

    private fun setEnabled(packageManager: PackageManager, component: ComponentName, enabled: Boolean) {
        packageManager.setComponentEnabledSetting(
            component,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
