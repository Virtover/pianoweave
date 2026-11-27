package com.lumenchord.pianoweave.api.config

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
}
