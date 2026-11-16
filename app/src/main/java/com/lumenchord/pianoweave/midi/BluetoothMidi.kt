package com.lumenchord.pianoweave.midi

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.lumenchord.pianoweave.audio.ConnectionChime
import com.lumenchord.pianoweave.util.AppPrefs

/** The runtime permissions Bluetooth MIDI needs, which differ before and after Android 12. */
object BluetoothMidiPermissions {
    /** Finding and connecting to Bluetooth keyboards. Before Android 12, scanning needs location. */
    fun forKeyboards(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    /** Being visible to computers as a Bluetooth MIDI device (nothing to ask before Android 12). */
    fun forVisibility(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyArray()
        }

    /** Everything the MIDI & Bluetooth page needs, asked for in one go. */
    fun forAll(): Array<String> = (forKeyboards() + forVisibility()).distinct().toTypedArray()

    fun has(context: Context, permissions: Array<String>): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * Bluetooth MIDI: finding and connecting Bluetooth LE MIDI keyboards (the app's own LE connection,
 * [BleMidiKeyboard], handed to [MidiInputManager]), getting the last one back by itself (see [watchLast]), and stopping [BleMidiPeripheral] (the phone as a device for computers) when Bluetooth
 * is switched off.
 */
@SuppressLint("MissingPermission") // every entry point checks BluetoothMidiPermissions first
object BluetoothMidi {
    private const val SCAN_MS = 15_000L
    private const val CONNECT_TIMEOUT_MS = 20_000L
    private const val PAIRING_TIMEOUT_MS = 60_000L
    /** How long a quick search for the remembered keyboard runs while the app is open (see [quickFind]). */
    private const val QUICK_FIND_MS = 20_000L
    /** Before waiting for the remembered keyboard again after the wait itself failed (e.g. Bluetooth hiccup). */
    private const val WATCH_AGAIN_MS = 10_000L

    data class FoundKeyboard(val address: String, val name: String, val rssi: Int)

    /** Keyboards found by the current or last search, strongest signal first. */
    val found = mutableStateListOf<FoundKeyboard>()
    var isScanning by mutableStateOf(false)
        private set
    var connectingAddress by mutableStateOf<String?>(null)
        private set
    /** The keyboard being connected is pairing: the person has to accept Android's request (observable). */
    var isPairing by mutableStateOf(false)
        private set
    /** The Bluetooth keyboard this app opened, while it's the attached device. */
    var connectedAddress by mutableStateOf<String?>(null)
        private set
    /** A one-line problem to show under the search (permission, Bluetooth off, couldn't connect). */
    var message by mutableStateOf<String?>(null)
        private set
    /** Goes up each time Bluetooth comes back on, so an open MIDI page can start over (observable). */
    var bluetoothRestarts by mutableStateOf(0)
        private set
    /** Something to tell the person wherever they are: the remembered keyboard came back or went away. */
    class Notice(val id: Int, val text: String)

    /** The latest [Notice] (observable); shown once, by whichever screen claims it ([claimNotice]). */
    var notice by mutableStateOf<Notice?>(null)
        private set
    private var noticeCount = 0
    private var claimedNotice = 0

    /** True the first time a screen asks to show notice [id]. */
    fun claimNotice(id: Int): Boolean = (id > claimedNotice).also { if (it) claimedNotice = id }

    private fun announce(text: String) { notice = Notice(++noticeCount, text) }

    /** The keyboard that went away, so its return reads "reconnected". */
    private var lostAddress: String? = null

    /**
     * Whether Bluetooth MIDI keyboard and computer connections are enabled. Off until the person turns it on
     * (on the MIDI & Bluetooth page): no Bluetooth searching or connecting for people who never use it.
     */
    var isEnabled by mutableStateOf(false)
        private set

    private val main = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    /** The keyboard being connected or connected. */
    private var keyboard: BleMidiKeyboard? = null
    /** Devices from the last search, kept so a connection uses what the scan saw (incl. the address type). */
    private val seenDevices = HashMap<String, BluetoothDevice>()
    private var initialized = false
    private var scanCallback: ScanCallback? = null
    /**
     * The scanner [scanCallback] was started on. Each context's BluetoothManager has its own adapter and
     * scanner, and stopping on another one silently does nothing: the search keeps running in the system and
     * every later start fails with SCAN_FAILED_ALREADY_STARTED (error 1) until the app restarts.
     */
    private var activeScanner: BluetoothLeScanner? = null
    private val stopScanLater = Runnable { stopScan(reportNothingFound = true) }
    /** The background connection waiting for the remembered keyboard to be on and in range. */
    private var waiter: BleMidiKeyboard? = null
    private val watchAgain = Runnable { appContext?.let(::watchLast) }

    /** Whether the app is on screen; only then is a full-power search worth its battery. */
    private var isVisible = false
    private var quickScan: ScanCallback? = null
    private var quickScanner: BluetoothLeScanner? = null
    private val stopQuickFindLater = Runnable { stopQuickFind() }

    /** The soft "lost" / "back" bells, only while the app is on screen. */
    private val cues = KeyboardCues(
        schedule = { delayMs, task ->
            val runnable = Runnable(task)
            main.postDelayed(runnable, delayMs)
            KeyboardCues.Cancel { main.removeCallbacks(runnable) }
        },
        isAudible = { isVisible },
        play = { rising -> appContext?.let { ConnectionChime.play(it, rising) } },
    )

    /** Called once at app start, after [MidiInputManager.initialize]. */
    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        val app = context.applicationContext
        appContext = app
        val prefs = AppPrefs.get(app)
        // Off by default. Someone who already connected a keyboard (when it was on by default) keeps it on,
        // so their keyboard still comes back by itself.
        isEnabled = prefs.getBoolean(
            AppPrefs.IS_BLE_MIDI_ENABLED,
            prefs.getString(AppPrefs.BLUETOOTH_MIDI_LAST_KEYBOARD, null) != null
        )
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                        stopScan()
                        BleMidiPeripheral.stop()
                        closeConnections()
                    }
                    BluetoothAdapter.STATE_ON -> {
                        watchLast(app) // back on: wait for the last keyboard
                        bluetoothRestarts++ // and an open MIDI page searches again
                    }
                }
            }
        }, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)

        watchLast(app)
    }

    private fun adapter(context: Context): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    fun isBluetoothOn(context: Context): Boolean = adapter(context)?.isEnabled == true

    /** The phone's Bluetooth name, which computers list it by when they connect. */
    fun phoneName(context: Context): String? =
        if (!BluetoothMidiPermissions.has(context, BluetoothMidiPermissions.forVisibility())) null
        else runCatching { adapter(context)?.name }.getOrNull()?.takeIf(String::isNotBlank)

    fun hasBluetoothLe(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)

    // ------------------------------------------------------------------ keyboards

    fun startScan(context: Context) {
        message = null
        if (!isEnabled) return
        if (!BluetoothMidiPermissions.has(context, BluetoothMidiPermissions.forKeyboards())) {
            message = "Allow Bluetooth access to find keyboards"
            return
        }

        val scanner = adapter(context)?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            message = "Turn Bluetooth on to find keyboards"
            return
        }
        if (isScanning) return
        // Keep the one connected or connecting on the list, so it can be disconnected or followed from here
        val keep = setOfNotNull(connectedAddress, connectingAddress)
        found.removeAll { it.address !in keep }
        connectedAddress?.let { address ->
            if (found.none { it.address == address }) {
                MidiInputManager.connectedName?.let { found.add(FoundKeyboard(address, it, 0)) }
            }
        }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(BleMidiPacket.SERVICE_UUID)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        // A previous scan can outlive its callback after the settings page is closed.
        // Clear that scanner session before starting a new one.
        scanCallback?.let { previous -> runCatching { (activeScanner ?: scanner).stopScan(previous) } }
        lateinit var callback: ScanCallback
        callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (scanCallback !== callback) return
                val address = result.device.address
                val name = result.scanRecord?.deviceName?.takeIf(String::isNotBlank)
                    ?: runCatching { result.device.name }.getOrNull()?.takeIf(String::isNotBlank)
                    ?: "Bluetooth MIDI keyboard"
                main.post {
                    if (scanCallback !== callback) return@post
                    seenDevices[address] = result.device
                    if (waiter?.address == address) appContext?.let { connectNow(it, result.device) }
                    val keyboard = FoundKeyboard(address, name, result.rssi)
                    val index = found.indexOfFirst { it.address == address }
                    if (index >= 0) found[index] = keyboard else found.add(keyboard)
                    found.sortByDescending { it.rssi }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                main.post {
                    if (scanCallback !== callback) return@post
                    stopScan()
                    message = "Couldn't search for keyboards (error $errorCode)"
                }
            }
        }
        scanCallback = callback
        activeScanner = scanner
        runCatching { scanner.startScan(listOf(filter), settings, callback) }
            .onSuccess {
                isScanning = true
                main.postDelayed(stopScanLater, SCAN_MS)
            }
            .onFailure {
                if (scanCallback === callback) { scanCallback = null; activeScanner = null }
                message = "Couldn't search for keyboards"
            }
    }

    /** Enables or disables all Bluetooth MIDI connections, including keyboards and computers. */
    fun setEnabled(context: Context, enabled: Boolean) {
        isEnabled = enabled
        AppPrefs.get(context).edit().putBoolean(AppPrefs.IS_BLE_MIDI_ENABLED, enabled).apply()
        if (!enabled) {
            stopScan()
            closeConnections() // the keyboard stays remembered for when it's turned back on
            BleMidiPeripheral.setEnabled(context, false)
        } else {
            BleMidiPeripheral.setEnabled(context, true)
            watchLast(context)
        }
    }

    /** Stops searching; [reportNothingFound] (the search ran its full time) explains an empty list. */
    fun stopScan(reportNothingFound: Boolean = false) {
        main.removeCallbacks(stopScanLater)
        val wasScanning = isScanning
        isScanning = false
        val callback = scanCallback
        val scanner = activeScanner
        scanCallback = null
        activeScanner = null
        if (callback != null) {
            runCatching { (scanner ?: appContext?.let { adapter(it)?.bluetoothLeScanner })?.stopScan(callback) }
        }
        if (wasScanning && reportNothingFound && found.isEmpty() && message == null) {
            message = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                "No keyboard found. Is its Bluetooth on? (On this Android version Location must be on too.)"
            } else {
                "No keyboard found. Is its Bluetooth on, and not connected to another device?"
            }
        }
    }

    /** Connects to a keyboard now (tapped in the list): tries for a while and says what went wrong. */
    fun connect(context: Context, address: String) {
        if (!isEnabled) return
        message = null
        if (connectingAddress != null) return
        if (!BluetoothMidiPermissions.has(context, BluetoothMidiPermissions.forKeyboards())) return
        val adapter = adapter(context)?.takeIf { it.isEnabled } ?: return
        stopScan()
        val device = seenDevices[address] ?: runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return
        stopWatching() // this attempt replaces the background wait (one connection per keyboard)
        connectingAddress = address
        isPairing = false
        lateinit var timeout: Runnable
        val pending = BleMidiKeyboard(
            device, nameOf(address, device), waitForIt = false,
            onReady = { ready ->
                main.removeCallbacks(timeout)
                connectingAddress = null
                isPairing = false
                cues.reset() // tapped: the person sees it connect
                adopt(context, ready)
            },
            onPairing = { on ->
                if (connectingAddress == address) {
                    isPairing = on
                    // Accepting can take a while (Android often asks in a notification): wait for the person
                    main.removeCallbacks(timeout)
                    main.postDelayed(timeout, if (on) PAIRING_TIMEOUT_MS else CONNECT_TIMEOUT_MS)
                }
            },
            onClosed = { closed, wasReady, failure ->
                main.removeCallbacks(timeout)
                if (connectingAddress == address && !wasReady) {
                    connectingAddress = null
                    isPairing = false
                    message = when (failure) {
                        BleMidiLink.Failure.Pairing ->
                            "Pairing didn't finish. Tap the keyboard to try again, and accept the request."
                        BleMidiLink.Failure.NotMidi -> "That device isn't a MIDI keyboard."
                        else -> "Couldn't connect. Is it on and close by?"
                    }
                }
                onKeyboardClosed(context, closed, failure)
            },
        )
        timeout = Runnable {
            if (connectingAddress == address) {
                connectingAddress = null
                isPairing = false
                message = "The keyboard didn't answer. Is it on and close by?"
                pending.close()
            }
        }
        main.postDelayed(timeout, CONNECT_TIMEOUT_MS)
        pending.connect(context)
    }

    /**
     * Waits in the background for the remembered keyboard: from app start, after Bluetooth (or Bluetooth MIDI)
     * comes back on, and after the keyboard went away. It connects as soon as the keyboard is on and in range,
     * so nobody has to tap it again. Quiet: no "Connecting…", no errors.
     */
    private fun watchLast(context: Context) {
        main.removeCallbacks(watchAgain)
        if (!isEnabled || keyboard != null || waiter != null || connectingAddress != null) return
        if (!BluetoothMidiPermissions.has(context, BluetoothMidiPermissions.forKeyboards())) return
        val adapter = adapter(context)?.takeIf { it.isEnabled } ?: return
        val address = AppPrefs.get(context).getString(AppPrefs.BLUETOOTH_MIDI_LAST_KEYBOARD, null) ?: return
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return
        startWaiter(context, device, inBackground = true)
        if (isVisible) quickFind(context, address)
    }

    /**
     * The app came on screen: if the remembered keyboard is only being waited for in the background, look for
     * it properly for a while (Android's background wait listens in short slices to save battery, which took
     * 7-25 s; a full search finds a keyboard that's on in about a second). Called from the activity.
     */
    fun onAppVisible(context: Context) {
        isVisible = true
        val address = waiter?.takeIf { it.isWaitingInBackground }?.address ?: return
        quickFind(context, address)
    }

    fun onAppHidden() {
        isVisible = false
        stopQuickFind()
    }

    private fun quickFind(context: Context, address: String) {
        if (quickScan != null) return
        val scanner = adapter(context)?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return
        lateinit var callback: ScanCallback
        callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                main.post {
                    if (quickScan !== callback) return@post
                    stopQuickFind()
                    seenDevices[address] = result.device
                    connectNow(context, result.device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                main.post { if (quickScan === callback) { quickScan = null; quickScanner = null } }
            }
        }
        val filter = ScanFilter.Builder().setDeviceAddress(address).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        quickScan = callback
        quickScanner = scanner
        runCatching { scanner.startScan(listOf(filter), settings, callback) }
            .onSuccess { main.postDelayed(stopQuickFindLater, QUICK_FIND_MS) }
            .onFailure { quickScan = null; quickScanner = null }
    }

    private fun stopQuickFind() {
        main.removeCallbacks(stopQuickFindLater)
        val callback = quickScan ?: return
        runCatching { quickScanner?.stopScan(callback) }
        quickScan = null
        quickScanner = null
    }

    /** The remembered keyboard was just seen: replace the slow background wait by connecting right now. */
    private fun connectNow(context: Context, device: BluetoothDevice) {
        val slow = waiter?.takeIf { it.isWaitingInBackground && it.address == device.address } ?: return
        waiter = null
        slow.close() // ends quietly (no failure), so nothing else reacts to it
        startWaiter(context, device, inBackground = false)
    }

    /** The quiet connection to the remembered keyboard: in the background (no time limit), or right now. */
    private fun startWaiter(context: Context, device: BluetoothDevice, inBackground: Boolean) {
        val address = device.address
        val pending = BleMidiKeyboard(
            device, nameOf(address, device), waitForIt = inBackground,
            onReady = { ready ->
                if (waiter === ready) waiter = null
                adopt(context, ready)
                cues.onBack()
                // It connected by itself: say so, as nobody tapped anything
                announce(if (lostAddress == ready.address) "${ready.name} reconnected" else "${ready.name} connected")
                lostAddress = null
            },
            onPairing = {},
            onClosed = { closed, _, failure -> onKeyboardClosed(context, closed, failure) },
            // Connected: a search still running would only slow the setup down (one radio)
            onLinkUp = ::stopQuickFind,
        )
        waiter = pending
        pending.connect(context)
    }

    private fun stopWatching() {
        main.removeCallbacks(watchAgain)
        stopQuickFind()
        waiter?.let { waiter = null; it.close() }
    }

    /** A keyboard is ready: it becomes the one in use, and is remembered for next time. */
    private fun adopt(context: Context, ready: BleMidiKeyboard) {
        stopQuickFind() // found already, however it got here
        keyboard?.takeIf { it !== ready }?.close()
        keyboard = ready
        connectedAddress = ready.address
        message = null
        // Connected by itself (the background wait): show it even if no search has listed it
        if (found.none { it.address == ready.address }) found.add(0, FoundKeyboard(ready.address, ready.name, 0))
        MidiInputManager.attachBluetooth(ready)
        AppPrefs.get(context).edit().putString(AppPrefs.BLUETOOTH_MIDI_LAST_KEYBOARD, ready.address).apply()
    }

    private fun onKeyboardClosed(context: Context, closed: BleMidiKeyboard, failure: BleMidiLink.Failure?) {
        val role = when {
            keyboard === closed -> KeyboardReconnect.Role.InUse
            waiter === closed -> KeyboardReconnect.Role.Waiting
            else -> KeyboardReconnect.Role.Tapped
        }
        if (keyboard === closed) {
            keyboard = null
            if (connectedAddress == closed.address) connectedAddress = null
            if (failure == BleMidiLink.Failure.Lost) {
                message = "${closed.name} disconnected. It reconnects when it's back."
                announce(message!!)
                lostAddress = closed.address
                if (isVisible) ConnectionChime.prepare(context) // ready by the time "lost" may sound
                cues.onLost()
            }
        }
        if (waiter === closed) waiter = null
        when (KeyboardReconnect.afterClose(role, failure)) {
            KeyboardReconnect.Action.WaitNow -> main.post { watchLast(context) }
            KeyboardReconnect.Action.WaitLater -> main.postDelayed(watchAgain, WATCH_AGAIN_MS)
            KeyboardReconnect.Action.None -> Unit
        }
        MidiInputManager.detachBluetooth(closed)
    }

    private fun nameOf(address: String, device: BluetoothDevice): String =
        found.firstOrNull { it.address == address }?.name
            ?: runCatching { device.name }.getOrNull()?.takeIf(String::isNotBlank)
            ?: "Bluetooth MIDI keyboard"

    /** Ends the keyboard connection and the background wait (Bluetooth off, Bluetooth MIDI off); remembers it. */
    private fun closeConnections() {
        cues.reset()
        stopWatching()
        keyboard?.let { MidiInputManager.detachBluetooth(it); it.close() }
        keyboard = null
        connectedAddress = null
    }

    /** Disconnects the Bluetooth keyboard and forgets it, so it isn't reconnected by itself any more. */
    fun disconnect(context: Context) {
        AppPrefs.get(context).edit().remove(AppPrefs.BLUETOOTH_MIDI_LAST_KEYBOARD).apply()
        closeConnections()
    }

    /** Whether the Bluetooth keyboard this app opened is still the one in use (it may have been unplugged/replaced). */
    fun isKeyboardConnected(): Boolean =
        connectedAddress != null && MidiInputManager.connectedTransport == MidiInputManager.Transport.Bluetooth

}

/** What to do about the remembered keyboard after a connection ended (kept apart from Android so it's tested). */
internal object KeyboardReconnect {
    /** The connection that ended: the keyboard in use, the background wait for it, or a tapped attempt. */
    enum class Role { InUse, Waiting, Tapped }
    enum class Action { None, WaitNow, WaitLater }

    fun afterClose(role: Role, failure: BleMidiLink.Failure?): Action = when {
        failure == null -> Action.None // ended on purpose (Disconnect, Bluetooth off, replaced)
        role == Role.InUse -> Action.WaitNow // switched off or out of range: back as soon as it's on again
        role == Role.Tapped -> Action.WaitNow // that attempt failed: keep waiting for the remembered one
        // The wait broke (e.g. a Bluetooth hiccup): again in a bit. A refused pairing or a device without MIDI
        // would only fail again, so stop there until the person taps it.
        failure == BleMidiLink.Failure.Connect -> Action.WaitLater
        else -> Action.None
    }
}
