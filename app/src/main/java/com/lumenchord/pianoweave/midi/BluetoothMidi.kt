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
import android.media.midi.MidiDevice
import android.media.midi.MidiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
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
 * Bluetooth MIDI: finding and connecting Bluetooth LE MIDI keyboards (Android's MIDI service does the BLE
 * MIDI protocol; the opened device is handed to [MidiInputManager] like a USB one), reconnecting the last
 * one on start, and stopping [BleMidiPeripheral] (the phone as a device for computers) when Bluetooth
 * is switched off.
 */
@SuppressLint("MissingPermission") // every entry point checks BluetoothMidiPermissions first
object BluetoothMidi {
    private const val SCAN_MS = 15_000L
    private const val CONNECT_TIMEOUT_MS = 20_000L

    data class FoundKeyboard(val address: String, val name: String, val rssi: Int)

    /** Keyboards found by the current or last search, strongest signal first. */
    val found = mutableStateListOf<FoundKeyboard>()
    var isScanning by mutableStateOf(false)
        private set
    var connectingAddress by mutableStateOf<String?>(null)
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
    /** Whether Bluetooth MIDI keyboard and computer connections are enabled. */
    var isEnabled by mutableStateOf(true)
        private set

    private val main = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var openedDevice: MidiDevice? = null
    private var initialized = false
    private var scanCallback: ScanCallback? = null
    /**
     * The scanner [scanCallback] was started on. Each context's BluetoothManager has its own adapter and
     * scanner, and stopping on another one silently does nothing: the search keeps running in the system and
     * every later start fails with SCAN_FAILED_ALREADY_STARTED (error 1) until the app restarts.
     */
    private var activeScanner: BluetoothLeScanner? = null
    private val stopScanLater = Runnable { stopScan(reportNothingFound = true) }

    /** Called once at app start, after [MidiInputManager.initialize]. */
    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        val app = context.applicationContext
        appContext = app
        isEnabled = AppPrefs.get(app).getBoolean(AppPrefs.IS_BLE_MIDI_ENABLED, true)
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                        stopScan()
                        BleMidiPeripheral.stop()
                        connectedAddress = null
                    }
                    BluetoothAdapter.STATE_ON -> {
                        reconnectLast(app) // back on: look for the last keyboard
                        bluetoothRestarts++ // and an open MIDI page searches and becomes findable again
                    }
                }
            }
        }, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)

        reconnectLast(app)
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
        found.clear()
        // Keep the one already connected on the list, so it can be disconnected from here
        connectedAddress?.let { address ->
            MidiInputManager.connectedName?.let { found.add(FoundKeyboard(address, it, 0)) }
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
            disconnect(context)
            BleMidiPeripheral.setEnabled(context, false)
        } else {
            BleMidiPeripheral.setEnabled(context, true)
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

    fun connect(context: Context, address: String, quiet: Boolean = false) {
        if (!isEnabled) return
        if (!quiet) message = null
        if (connectingAddress != null) return
        if (!BluetoothMidiPermissions.has(context, BluetoothMidiPermissions.forKeyboards())) return
        val adapter = adapter(context)?.takeIf { it.isEnabled } ?: return
        val midi = context.getSystemService(MidiManager::class.java) ?: return
        stopScan()
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return
        connectingAddress = address
        val timeout = Runnable {
            if (connectingAddress == address) {
                connectingAddress = null
                if (!quiet) message = "The keyboard didn't answer. Is it on and close by?"
            }
        }
        main.postDelayed(timeout, CONNECT_TIMEOUT_MS)
        midi.openBluetoothDevice(device, { opened ->
            main.removeCallbacks(timeout)
            if (connectingAddress != address) { opened?.close(); return@openBluetoothDevice } // gave up already
            connectingAddress = null
            if (opened == null || !MidiInputManager.attach(opened)) {
                opened?.close()
                if (!quiet) message = "Couldn't connect. Is it on and close by?"
                return@openBluetoothDevice
            }
            openedDevice?.takeIf { it !== opened }?.let { runCatching { it.close() } }
            openedDevice = opened
            connectedAddress = address
            if (!quiet) message = null
            AppPrefs.get(context).edit().putString(AppPrefs.BLUETOOTH_MIDI_LAST_KEYBOARD, address).apply()
        }, main)
    }

    /** Disconnects the Bluetooth keyboard and forgets it, so it isn't reconnected on the next start. */
    fun disconnect(context: Context) {
        AppPrefs.get(context).edit().remove(AppPrefs.BLUETOOTH_MIDI_LAST_KEYBOARD).apply()
        if (MidiInputManager.isAttached(openedDevice)) MidiInputManager.disconnectActive()
        else runCatching { openedDevice?.close() }
        openedDevice = null
        connectedAddress = null
    }

    /** Whether the Bluetooth keyboard this app opened is still the one in use (it may have been unplugged/replaced). */
    fun isKeyboardConnected(): Boolean =
        connectedAddress != null && MidiInputManager.connectedTransport == MidiInputManager.Transport.Bluetooth

    private fun reconnectLast(context: Context) {
        if (!isEnabled) return
        if (MidiInputManager.connectedTransport != null) return // something's connected already (e.g. USB)
        val last = AppPrefs.get(context).getString(AppPrefs.BLUETOOTH_MIDI_LAST_KEYBOARD, null) ?: return
        connect(context, last, quiet = true)
    }

}
