package com.lumenchord.pianoweave.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The launcher icon: the cat for a developer's account, the sunlit one for someone using the light themes, the
 * usual one otherwise. The manifest has one launcher alias for each, and exactly one is enabled.
 *
 * Swapping them makes some launchers close the app, so [requestDeveloper] and [requestLight] only remember the
 * wish, and [applyPending] swaps when the app goes to the background. The developer wish changes only on a definite
 * answer (the server's balance, or signing out), never because the server couldn't be reached.
 */
object LauncherIcon {
    /** The aliases, in the order of [Alias]: the first is enabled by the manifest. */
    private val ALIASES = listOf(
        "com.lumenchord.pianoweave.LauncherDefault",
        "com.lumenchord.pianoweave.LauncherDeveloper",
        "com.lumenchord.pianoweave.LauncherLight",
    )

    enum class Alias { Default, Developer, Light }

    fun requestDeveloper(context: Context, developer: Boolean) {
        AppPrefs.get(context).edit().putBoolean(AppPrefs.WANTS_DEVELOPER_LAUNCHER_ICON, developer).apply()
    }

    fun requestLight(context: Context, light: Boolean) {
        AppPrefs.get(context).edit().putBoolean(AppPrefs.WANTS_LIGHT_LAUNCHER_ICON, light).apply()
    }

    /** The cat wins over the sunlit icon: a developer's look is chosen on purpose, in Settings. */
    internal fun wanted(developer: Boolean, light: Boolean): Alias = when {
        developer -> Alias.Developer
        light -> Alias.Light
        else -> Alias.Default
    }

    /**
     * Brings every alias to the wish, whatever state they are in: also after the process died between two
     * changes (two icons, or with the new one first never none). Never throws: an icon is not worth a crash.
     */
    fun applyPending(context: Context) {
        try {
            val prefs = AppPrefs.get(context)
            val want = wanted(
                developer = prefs.getBoolean(AppPrefs.WANTS_DEVELOPER_LAUNCHER_ICON, false),
                light = prefs.getBoolean(AppPrefs.WANTS_LIGHT_LAUNCHER_ICON, false)
            )
            val packageManager = context.packageManager
            val components = ALIASES.map { ComponentName(context.packageName, it) }
            val enabled = components.mapIndexed { i, component -> isEnabled(packageManager, component, enabledByDefault = i == 0) }
            // The wanted one first, so there is never a moment without a launcher entry
            for ((alias, enable) in changes(want, enabled)) {
                setEnabled(packageManager, components[alias.ordinal], enable)
            }
        } catch (e: RuntimeException) {
            android.util.Log.w("LauncherIcon", "Couldn't switch the launcher icon", e)
        }
    }

    /**
     * The changes, in order, that leave exactly the wanted alias enabled: (alias, enable), for aliases now
     * [enabled] as listed in [Alias]. The wanted one is enabled before any other is disabled, so no step leaves the
     * app without a launcher entry.
     */
    internal fun changes(want: Alias, enabled: List<Boolean>): List<Pair<Alias, Boolean>> = buildList {
        if (!enabled[want.ordinal]) add(want to true)
        Alias.entries.filter { it != want && enabled[it.ordinal] }.forEach { add(it to false) }
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
