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

    /**
     * Brings both aliases to the wish, whatever state they are in: also after the process died between the two
     * changes (two icons, or with the new one first never none). Never throws: an icon is not worth a crash.
     */
    fun applyPending(context: Context) {
        try {
            val developer = AppPrefs.get(context).getBoolean(AppPrefs.WANTS_DEVELOPER_LAUNCHER_ICON, false)
            val packageManager = context.packageManager
            val developerAlias = ComponentName(context.packageName, DEVELOPER_ALIAS)
            val defaultAlias = ComponentName(context.packageName, DEFAULT_ALIAS)
            // The wanted one first, so there is never a moment without a launcher entry
            val changes = changes(
                wantDeveloper = developer,
                defaultEnabled = isEnabled(packageManager, defaultAlias, enabledByDefault = true),
                developerEnabled = isEnabled(packageManager, developerAlias, enabledByDefault = false)
            )
            for ((isDeveloperAlias, enable) in changes) {
                setEnabled(packageManager, if (isDeveloperAlias) developerAlias else defaultAlias, enable)
            }
        } catch (e: RuntimeException) {
            android.util.Log.w("DeveloperLauncherIcon", "Couldn't switch the launcher icon", e)
        }
    }

    /**
     * The changes, in order, that leave exactly the wanted alias enabled: (is the developer alias, enable). The
     * wanted one is enabled before the other is disabled, so no step leaves the app without a launcher entry.
     */
    internal fun changes(wantDeveloper: Boolean, defaultEnabled: Boolean, developerEnabled: Boolean): List<Pair<Boolean, Boolean>> {
        val wantedEnabled = if (wantDeveloper) developerEnabled else defaultEnabled
        val otherEnabled = if (wantDeveloper) defaultEnabled else developerEnabled
        return buildList {
            if (!wantedEnabled) add(wantDeveloper to true)
            if (otherEnabled) add(!wantDeveloper to false)
        }
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
