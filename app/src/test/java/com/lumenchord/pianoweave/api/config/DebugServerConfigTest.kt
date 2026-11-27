package com.lumenchord.pianoweave.api.config

import com.lumenchord.pianoweave.util.AppPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The debug build's switch of the official server: what it makes of the typed address and name. */
class DebugServerConfigTest {

    private val official = ServerConfig(
        baseUrl = "https://server.example.com/",
        name = "Official",
        supportMeLink = "https://support.example.com",
        serverPassword = "secret"
    )

    @Test
    fun offLeavesTheOfficialServer() {
        assertEquals(official, debugServerConfig(official, enabled = false, url = "https://dev.example.com", name = "Dev"))
    }

    @Test
    fun onReplacesAddressAndNameAndNeverSendsTheOfficialPassword() {
        val dev = debugServerConfig(official, enabled = true, url = "dev.example.com", name = " My dev ")
        assertEquals("https://dev.example.com/", dev.baseUrl)
        assertEquals("My dev", dev.name)
        assertNull(dev.serverPassword)
        // The rest of the app's configuration is kept
        assertEquals(official.supportMeLink, dev.supportMeLink)
    }

    @Test
    fun aBlankNameIsMarkedAsDev() {
        assertEquals("Official (dev)", debugServerConfig(official, true, "http://10.0.0.5:8000", "").name)
    }

    @Test
    fun withoutAValidAddressTheOfficialServerStays() {
        assertEquals(official, debugServerConfig(official, enabled = true, url = "  ", name = "Dev"))
        assertEquals(official, debugServerConfig(official, enabled = true, url = "https://", name = "Dev"))
    }

    @Test
    fun addressesAreNormalized() {
        assertEquals("http://localhost:8000/", normalizeDevServerUrl("http://localhost:8000"))
        assertEquals("https://dev.example.com/api/", normalizeDevServerUrl(" dev.example.com/api "))
        assertNull(normalizeDevServerUrl(""))
    }

    @Test
    fun theDevServerGetsItsOwnPasswordAndNeverTheOfficialOne() {
        val dev = debugServerConfig(official, true, "https://dev.example.com", "Dev", password = "dev-pass")
        assertEquals("dev-pass", dev.serverPassword)
        assertNull(debugServerConfig(official, true, "https://dev.example.com", "Dev", password = "").serverPassword)
    }

    @Test
    fun theDevPasswordIsNotUsedWhileTheSwitchIsOff() {
        val config = debugServerConfig(official, false, "https://dev.example.com", "Dev", password = "dev-pass")
        assertEquals("secret", config.serverPassword)
    }

    // --- Saving and reading back, through the code the settings card and the startup use ---

    private class MapStore : DebugServerStore {
        val values = mutableMapOf<String, Any>()
        override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
        override fun getString(key: String) = values[key] as? String
        override fun getSecret(key: String) = values[key + "#secret"] as? String
        override fun write(enabled: Boolean, url: String, name: String, password: String) {
            values[AppPrefs.DEBUG_SERVER_ENABLED] = enabled
            values[AppPrefs.DEBUG_SERVER_URL] = url
            values[AppPrefs.DEBUG_SERVER_NAME] = name
            values[AppPrefs.DEBUG_SERVER_PASSWORD + "#secret"] = password
        }
    }

    @Test
    fun savedChoiceIsReadBack() {
        val store = MapStore()
        assertEquals(DebugServerChoice(false, "", "", ""), DebugServerSettings.load(store, official))
        DebugServerSettings.save(store, DebugServerChoice(true, " dev.example.com ", " My dev ", "pw"))
        assertEquals(DebugServerChoice(true, "dev.example.com", "My dev", "pw"), DebugServerSettings.load(store, official))
    }

    @Test
    fun neverSavedFieldsStartFromTheConfigsDevDefaults() {
        val withDefaults = official.copy(devBaseUrl = "https://d.example.com/", devName = "D")
        val loaded = DebugServerSettings.load(MapStore(), withDefaults)
        assertEquals("https://d.example.com/", loaded.url)
        assertEquals("D", loaded.name)
    }

    @Test
    fun theActiveConfigSwitchesToTheDevAddressAfterSavingAndBackWhenOff() {
        AppConfig.setBaseForTest(official)
        try {
            val store = MapStore()
            AppConfig.applyDebugServer(store, debuggable = true)
            assertEquals("https://server.example.com/", AppConfig.getConfig().baseUrl)

            DebugServerSettings.save(store, DebugServerChoice(true, "dev.example.com", "Dev", "pw"))
            AppConfig.applyDebugServer(store, debuggable = true)
            assertEquals("https://dev.example.com/", AppConfig.getConfig().baseUrl)
            assertEquals("Dev", AppConfig.getConfig().name)
            assertEquals("pw", AppConfig.getConfig().serverPassword)

            // A release build ignores whatever is saved
            AppConfig.applyDebugServer(store, debuggable = false)
            assertEquals("https://server.example.com/", AppConfig.getConfig().baseUrl)
            assertEquals("secret", AppConfig.getConfig().serverPassword)

            DebugServerSettings.save(store, DebugServerChoice(false, "dev.example.com", "Dev", "pw"))
            AppConfig.applyDebugServer(store, debuggable = true)
            assertEquals("https://server.example.com/", AppConfig.getConfig().baseUrl)
        } finally {
            AppConfig.setBaseForTest(null)
        }
    }
}
