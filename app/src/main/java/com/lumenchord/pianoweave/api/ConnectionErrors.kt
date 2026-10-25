package com.lumenchord.pianoweave.api

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Plain words for a request that never got an answer. The system's own text ("Unable to resolve host ...:
 * No address associated with hostname") means nothing to most people, and with Wi-Fi off it even points
 * at the wrong cause.
 */
object ConnectionErrors {

    /**
     * What went wrong, or null if [e] is not a connection failure (an HTTP refusal has its own message).
     * [isOnline] is [com.lumenchord.pianoweave.util.Connectivity.isOnline]. [target] names what was
     * reached for, as in "Couldn't reach [target]"; [isCustomServer] adds a hint to check its address.
     */
    fun message(
        e: Throwable,
        isOnline: Boolean,
        target: String = "the server",
        isCustomServer: Boolean = false
    ): String? {
        val chain = generateSequence(e) { it.cause }.toList()
        if (chain.none { it is IOException }) return null
        if (!isOnline) return "You're offline. Turn on Wi-Fi or mobile data, then try again."

        val checkAddress = if (isCustomServer) " Check its address in Server Settings." else ""
        return when {
            chain.any { it is UnknownHostException } ->
                if (isCustomServer) "Couldn't find $target.$checkAddress"
                else "Couldn't reach $target. Check your internet connection and try again."
            chain.any { it is ConnectException || it is NoRouteToHostException } ->
                if (isCustomServer) "Couldn't connect to $target. Make sure it's running.$checkAddress"
                else "Couldn't connect to $target right now. Try again in a moment."
            chain.any { it is SSLException } ->
                "Couldn't make a secure connection to $target." +
                        if (isCustomServer) " Check whether its address should start with http:// or https://." else ""
            // SocketTimeoutException is one of these; an interrupted read is a timeout too
            chain.any { it is InterruptedIOException } ->
                "${target.replaceFirstChar { it.uppercase() }} took too long to answer. Try again in a moment."
            else -> "The connection was lost. Check your internet connection and try again."
        }
    }
}
