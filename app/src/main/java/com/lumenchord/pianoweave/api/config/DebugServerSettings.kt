package com.lumenchord.pianoweave.api.config

import android.content.Context
import com.lumenchord.pianoweave.util.AppPrefs
import com.lumenchord.pianoweave.util.SecretStore

/** The debug dev server option as the user set it: the switch, and the address, display name and password typed. */
data class DebugServerChoice(
    val enabled: Boolean,
    val url: String,
    val name: String,
    val password: String
)

/** Where the debug dev server option is kept; the app's is [PrefsDebugServerStore], tests use a map. */
interface DebugServerStore {
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getString(key: String): String?
    /** The password, which is kept encrypted. */
    fun getSecret(key: String): String?
    /** Writes everything at once. */
    fun write(enabled: Boolean, url: String, name: String, password: String)
}

/** [DebugServerStore] in the app's preferences file; the password goes through [SecretStore]. */
class PrefsDebugServerStore(private val context: Context) : DebugServerStore {
    override fun getBoolean(key: String, default: Boolean) = AppPrefs.get(context).getBoolean(key, default)
    override fun getString(key: String): String? = AppPrefs.get(context).getString(key, null)
    override fun getSecret(key: String): String? = SecretStore.get(context, key)
    override fun write(enabled: Boolean, url: String, name: String, password: String) {
        SecretStore.put(context, AppPrefs.DEBUG_SERVER_PASSWORD, password)
        AppPrefs.get(context).edit()
            .putBoolean(AppPrefs.DEBUG_SERVER_ENABLED, enabled)
            .putString(AppPrefs.DEBUG_SERVER_URL, url)
            .putString(AppPrefs.DEBUG_SERVER_NAME, name)
            .apply()
    }
}

/**
 * Saving and loading the debug dev server option. The settings card, the view model at startup and
 * [AppConfig.applyDebugServer] all go through here, so what is saved is what is read back and applied.
 */
object DebugServerSettings {

    /** What is saved; the address and name start from config.txt's optional dev defaults when never saved. */
    fun load(store: DebugServerStore, official: ServerConfig?): DebugServerChoice = DebugServerChoice(
        enabled = store.getBoolean(AppPrefs.DEBUG_SERVER_ENABLED, false),
        url = store.getString(AppPrefs.DEBUG_SERVER_URL) ?: official?.devBaseUrl.orEmpty(),
        name = store.getString(AppPrefs.DEBUG_SERVER_NAME) ?: official?.devName.orEmpty(),
        password = store.getSecret(AppPrefs.DEBUG_SERVER_PASSWORD).orEmpty()
    )

    /** Saves [choice] (address and name trimmed) and returns what was saved. */
    fun save(store: DebugServerStore, choice: DebugServerChoice): DebugServerChoice {
        val saved = choice.copy(url = choice.url.trim(), name = choice.name.trim())
        store.write(saved.enabled, saved.url, saved.name, saved.password)
        return saved
    }
}
