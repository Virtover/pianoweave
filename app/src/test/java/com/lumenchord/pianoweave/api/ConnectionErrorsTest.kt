package com.lumenchord.pianoweave.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class ConnectionErrorsTest {

    private val noAddress = UnknownHostException("Unable to resolve host \"example.org\": No address associated with hostname")

    @Test
    fun wifiOffSaysOfflineInsteadOfTheHostError() {
        assertEquals(
            "You're offline. Turn on Wi-Fi or mobile data, then try again.",
            ConnectionErrors.message(noAddress, isOnline = false)
        )
    }

    @Test
    fun unknownHostWhileOnlinePointsACustomServerToItsAddress() {
        val message = ConnectionErrors.message(noAddress, isOnline = true, target = "this server", isCustomServer = true)!!
        assertTrue(message, message.startsWith("Couldn't find this server."))
        assertTrue(message, "Server Settings" in message)
    }

    @Test
    fun unknownHostWhileOnlineOnTheOfficialServerAsksToCheckTheConnection() {
        assertEquals(
            "Couldn't reach the server. Check your internet connection and try again.",
            ConnectionErrors.message(noAddress, isOnline = true)
        )
    }

    @Test
    fun refusedConnectionAndTimeoutAndTls() {
        assertEquals(
            "Couldn't connect to Google Drive right now. Try again in a moment.",
            ConnectionErrors.message(ConnectException("Failed to connect"), isOnline = true, target = "Google Drive")
        )
        assertEquals(
            "The server took too long to answer. Try again in a moment.",
            ConnectionErrors.message(SocketTimeoutException("timeout"), isOnline = true)
        )
        assertTrue(ConnectionErrors.message(SSLHandshakeException("bad cert"), isOnline = true)!!.startsWith("Couldn't make a secure connection"))
    }

    @Test
    fun wrappedCauseIsFound() {
        val wrapped = RuntimeException("upload failed", IOException("io", noAddress))
        assertEquals(
            "Couldn't reach the server. Check your internet connection and try again.",
            ConnectionErrors.message(wrapped, isOnline = true)
        )
    }

    @Test
    fun otherErrorsAreNotConnectionErrors() {
        assertNull(ConnectionErrors.message(IllegalStateException("bad state"), isOnline = false))
    }
}
