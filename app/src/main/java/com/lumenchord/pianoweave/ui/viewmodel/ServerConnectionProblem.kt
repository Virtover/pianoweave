package com.lumenchord.pianoweave.ui.viewmodel

import com.lumenchord.pianoweave.api.MaintenanceNotice

/** Why the server couldn't be used when the app opened; picks what the startup connection dialog says. */
sealed interface ServerConnectionProblem {
    /** Wi-Fi and mobile data are off. */
    data object NoInternet : ServerConnectionProblem

    /** The server announced a restart or shutdown that has begun. */
    data class Maintenance(val notice: MaintenanceNotice) : ServerConnectionProblem

    /** Reachable, but it has a password and the app has none or the wrong one. */
    data object PasswordRequired : ServerConnectionProblem

    /**
     * Online, but the server gave no API answer. [detail] is the plain-words reason from
     * [com.lumenchord.pianoweave.api.ConnectionErrors] (timeout, unknown address, …), null when
     * something answered but it wasn't the API (a proxy in front of a down server).
     */
    data class Unreachable(val detail: String?) : ServerConnectionProblem
}
