package com.lumenchord.pianoweave.update

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.lumenchord.pianoweave.util.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Google Play in-app updates. A new version is offered once per app start as a flexible update: Play asks,
 * it downloads in the background while the app is used, then a "Restart" snackbar installs it. A release
 * given in-app update priority 4 or 5 (set through the Play Developer API, not the Console UI) is required
 * instead, with Play's full-screen immediate flow. Declining a flexible update keeps that version from being
 * offered again for [DECLINE_COOLDOWN_MS].
 *
 * Only installs from Google Play (internal app sharing included) get anything; elsewhere the check fails
 * and nothing is shown. Debug builds can [simulate] the flexible flow with Play's [FakeAppUpdateManager].
 */
object AppUpdates {
    private const val TAG = "AppUpdates"
    private const val HIGH_PRIORITY = 4
    private const val DECLINE_COOLDOWN_MS = 3L * 24 * 60 * 60 * 1000

    sealed interface State {
        data object Idle : State
        /** Debug: the stand-in for Play's "Update available" sheet during a [simulate]d update. */
        data object SimulatedPrompt : State
        /** Accepted and downloading in the background. */
        data object Downloading : State
        /** Downloaded; [completeUpdate] installs it and restarts the app. */
        data object Downloaded : State
        /** A [simulate]d update was "installed" (a real one would have restarted the app). */
        data object SimulatedInstall : State
    }

    var state by mutableStateOf<State>(State.Idle)
        private set

    private var playManager: AppUpdateManager? = null
    private var fake: FakeAppUpdateManager? = null
    private var offeredThisRun = false
    private var immediateStarted = false
    private var offeredVersionCode = 0

    private val listener = InstallStateUpdatedListener { install ->
        state = when (install.installStatus()) {
            InstallStatus.PENDING, InstallStatus.DOWNLOADING -> State.Downloading
            InstallStatus.DOWNLOADED -> State.Downloaded
            else -> if (state == State.SimulatedInstall || state == State.SimulatedPrompt) state else State.Idle
        }
    }

    private fun manager(context: Context): AppUpdateManager = fake ?: playManager
        ?: AppUpdateManagerFactory.create(context.applicationContext).also {
            it.registerListener(listener)
            playManager = it
        }

    /** Checks Play for a new version and offers it, once per app start. Call when startup prompts are done. */
    fun offerUpdate(activity: Activity, launcher: ActivityResultLauncher<IntentSenderRequest>) {
        if (offeredThisRun) return
        offeredThisRun = true
        val manager = manager(activity)
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    state = State.Downloaded
                    return@addOnSuccessListener
                }
                if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return@addOnSuccessListener
                val type = when {
                    info.updatePriority() >= HIGH_PRIORITY && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) ->
                        AppUpdateType.IMMEDIATE
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) &&
                        !recentlyDeclined(activity, info.availableVersionCode()) -> AppUpdateType.FLEXIBLE
                    else -> return@addOnSuccessListener
                }
                if (!activity.isDestroyed) startFlow(manager, info, type, launcher)
            }
            // Expected outside Play installs (debug builds, emulators without Play Store)
            .addOnFailureListener { Log.i(TAG, "Update check unavailable: ${it.message}") }
    }

    /**
     * On every return to the app: shows the Restart prompt for an update that finished downloading while
     * away, and reopens a required (immediate) update that was left before it finished.
     */
    fun onResume(activity: Activity, launcher: ActivityResultLauncher<IntentSenderRequest>) {
        if (fake != null) return
        val manager = manager(activity)
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                state = State.Downloaded
            } else if (immediateStarted && !activity.isDestroyed &&
                info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
            ) {
                startFlow(manager, info, AppUpdateType.IMMEDIATE, launcher)
            }
        }
    }

    /** The result of Play's update dialog; a declined flexible update rests for a few days. */
    fun onFlowResult(context: Context, result: ActivityResult) {
        if (result.resultCode != Activity.RESULT_OK && !immediateStarted && offeredVersionCode != 0) {
            AppPrefs.get(context).edit()
                .putInt(AppPrefs.DECLINED_UPDATE_VERSION_CODE, offeredVersionCode)
                .putLong(AppPrefs.DECLINED_UPDATE_AT, System.currentTimeMillis())
                .apply()
        }
    }

    /** Installs a downloaded update; Play restarts the app. */
    fun completeUpdate() {
        val simulated = fake
        if (simulated != null) {
            simulated.completeUpdate()
            simulated.installCompletes()
            simulated.unregisterListener(listener)
            fake = null
            state = State.SimulatedInstall
        } else {
            playManager?.completeUpdate()
        }
    }

    /** Clears [State.SimulatedInstall] once it was shown. */
    fun acknowledgeSimulatedInstall() {
        if (state == State.SimulatedInstall) state = State.Idle
    }

    /**
     * Debug builds: runs the flexible flow against a fake Play that has a newer version, so the app's side
     * (download in the background, then the Restart snackbar) can be checked without a Play release. The fake
     * doesn't show Play's "Update available" sheet, so [State.SimulatedPrompt] asks for a stand-in dialog
     * whose answer goes to [answerSimulatedPrompt].
     */
    fun simulate(activity: Activity, launcher: ActivityResultLauncher<IntentSenderRequest>) {
        fake?.unregisterListener(listener)
        val simulated = FakeAppUpdateManager(activity.applicationContext)
        simulated.registerListener(listener)
        simulated.setUpdateAvailable(currentVersionCode(activity) + 1)
        fake = simulated
        simulated.appUpdateInfo.addOnSuccessListener { info ->
            startFlow(simulated, info, AppUpdateType.FLEXIBLE, launcher)
            state = State.SimulatedPrompt
        }
    }

    /** The stand-in sheet's answer: Update downloads the fake version, No thanks ends the simulation. */
    fun answerSimulatedPrompt(accept: Boolean) {
        val simulated = fake ?: return
        if (!accept) {
            simulated.userRejectsUpdate()
            simulated.unregisterListener(listener)
            fake = null
            state = State.Idle
            return
        }
        simulated.userAcceptsUpdate()
        CoroutineScope(Dispatchers.Main).launch {
            simulated.downloadStarts()
            val total = 10_000_000L
            simulated.setTotalBytesToDownload(total)
            for (step in 1..5) {
                delay(600)
                simulated.setBytesDownloaded(total * step / 5)
            }
            simulated.downloadCompletes()
        }
    }

    private fun startFlow(
        manager: AppUpdateManager,
        info: AppUpdateInfo,
        type: Int,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        immediateStarted = type == AppUpdateType.IMMEDIATE
        offeredVersionCode = info.availableVersionCode()
        try {
            manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(type).build())
        } catch (e: Exception) {
            Log.w(TAG, "Could not start the update flow", e)
        }
    }

    private fun recentlyDeclined(context: Context, versionCode: Int): Boolean {
        val prefs = AppPrefs.get(context)
        if (prefs.getInt(AppPrefs.DECLINED_UPDATE_VERSION_CODE, 0) != versionCode) return false
        return System.currentTimeMillis() - prefs.getLong(AppPrefs.DECLINED_UPDATE_AT, 0) < DECLINE_COOLDOWN_MS
    }

    private fun currentVersionCode(context: Context): Int {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt() else info.versionCode
    }
}
