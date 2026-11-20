package com.lumenchord.pianoweave.ui.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.lumenchord.pianoweave.api.DeveloperJobDetails
import com.lumenchord.pianoweave.api.PianoApiFactory
import com.lumenchord.pianoweave.api.TranscriptionStatusResponse
import com.lumenchord.pianoweave.util.AppPrefs
import com.lumenchord.pianoweave.util.LauncherIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * A job as a developer saw it last: the server's status plus [DeveloperJobDetails], kept for the developer panel.
 * [details] is null for a job the server didn't describe (an account that isn't a developer's, an older server).
 */
data class DeveloperJobSnapshot(
    val jobId: String,
    val status: String,
    val progress: Float,
    val backend: String?,
    val mode: String?,
    val errorCode: String?,
    val details: DeveloperJobDetails?
)

/**
 * Developer accounts: the server's administrator makes an account a developer's (admin web page, or
 * `python -m app.admin user <who> developer on`). The balance then says `developer: true`; the server never charges
 * its jobs, so the app shows unlimited credits, skips its own balance check, and offers the developer extras.
 */
internal fun PianoWeaveViewModel.loadDeveloperPreferences(context: Context) {
    isDeveloperBackgroundEnabled = AppPrefs.get(context).getBoolean(AppPrefs.IS_DEVELOPER_BACKGROUND_ENABLED, true)
}

internal fun PianoWeaveViewModel.setDeveloperBackgroundEnabledImpl(context: Context, enabled: Boolean) {
    isDeveloperBackgroundEnabled = enabled
    AppPrefs.get(context).edit().putBoolean(AppPrefs.IS_DEVELOPER_BACKGROUND_ENABLED, enabled).apply()
    if (isDeveloper) LauncherIcon.requestDeveloper(context, wantsDeveloperIcons)
}

/**
 * The developer look: the cat background, the cat logo and the cat launcher icon, together. A developer who picks
 * the regular background gets the regular icons too; the developer extras (unlimited credits, the panel) stay.
 */
val PianoWeaveViewModel.wantsDeveloperIcons: Boolean
    get() = isDeveloper && isDeveloperBackgroundEnabled

/** Called with every job status: a developer's panel shows the latest. */
internal fun PianoWeaveViewModel.rememberDeveloperJob(job: TranscriptionStatusResponse) {
    if (!isDeveloper) return
    developerLastJob = DeveloperJobSnapshot(
        jobId = job.job_id,
        status = job.status,
        progress = job.progress,
        backend = job.backend,
        mode = job.mode,
        errorCode = job.errorCode,
        details = job.developer
    )
}

/** Asks the server for its queues and workers. A 403 means the account is no longer a developer's. */
internal fun PianoWeaveViewModel.refreshDeveloperServerStateImpl(context: Context) {
    if (!isDeveloper || isLoadingDeveloperServerState) return
    val url = activeServerUrl
    isLoadingDeveloperServerState = true
    viewModelScope.launch {
        try {
            developerServerState = executeServerCallImpl(context, url) { authHeader ->
                PianoApiFactory.getApi(url).getDeveloperServerState(authHeader = authHeader)
            }
            developerServerError = null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is HttpException && e.code() == 403) {
                // Ended on the server meanwhile: the balance tells the rest of the app
                developerServerError = "This account is no longer a developer's on this server."
                showDeveloperPanel = false
                refreshUserBalanceImpl(context)
            } else {
                developerServerError = "Couldn't reach the server: ${e.message ?: e.javaClass.simpleName}"
            }
        } finally {
            isLoadingDeveloperServerState = false
        }
    }
}
