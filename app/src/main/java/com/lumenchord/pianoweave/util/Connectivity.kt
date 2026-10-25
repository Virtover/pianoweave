package com.lumenchord.pianoweave.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object Connectivity {

    /**
     * True if the device has a network meant to reach the internet (Wi-Fi, mobile data, ethernet, VPN).
     * It may still have no real connection (e.g. a captive portal); this only tells "turned off" apart.
     * Unknown counts as online, so an error is never blamed on a connection that may be fine.
     */
    fun isOnline(context: Context): Boolean = try {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager?.activeNetwork
        if (manager == null) true
        else network != null && manager.getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (_: Exception) {
        true
    }
}
