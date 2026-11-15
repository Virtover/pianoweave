package com.lumenchord.pianoweave.midi

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import com.lumenchord.pianoweave.util.AppPrefs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * Makes the phone itself a Bluetooth LE MIDI device, so a computer can connect to it: a web page through
 * Web Bluetooth (Chrome or Edge), or a DAW through the computer's Bluetooth MIDI support. Notes the computer sends play in the app like a MIDI keyboard's; the song the
 * app plays goes back to the computer ([MidiInputManager.sendNoteOn]).
 *
 * Why the phone has to be the visible one: a web page can only connect to Bluetooth devices (Web
 * Bluetooth is central-only), never be found itself, so a page can't pose as a keyboard.
 *
 * Safety: BLE MIDI has no pairing, so anyone nearby could connect while the phone is findable, and everyone
 * scanning sees its Bluetooth name. So the phone is never findable by default: only after "Using a computer? Connect"
 * on the MIDI & Bluetooth page, for [VISIBLE_MS] at most ([setDiscoverable]), ending early when a computer
 * connects or the page closes. Only one computer at a time is served: while it's connected, other devices can't
 * subscribe and their notes are ignored. All a connection can do is exchange MIDI notes; the page shows who is
 * connected, with Disconnect. A computer that connected stays connected after the page is closed.
 *
 * Android has no built-in "phone as Bluetooth MIDI" mode, so this is a small GATT server with the standard
 * BLE MIDI service, advertised so computers find it.
 */
@SuppressLint("MissingPermission") // every entry point checks BluetoothMidiPermissions first
object BleMidiPeripheral {
    private const val TAG = "BleMidiPeripheral"

    /** How long "Connect a computer" keeps the phone findable. */
    const val VISIBLE_MS = 2 * 60_000L

    /** On while computers can actually find the phone (advertising has started). */
    var isRunning by mutableStateOf(false)
        private set
    /** Whether the phone may expose itself as a Bluetooth MIDI device for computers. */
    var isEnabled by mutableStateOf(true)
        private set
    /** [SystemClock.elapsedRealtime] when the requested visibility ends, or 0 when not requested (observable). */
    var visibleUntil by mutableStateOf(0L)
        private set

    /** Visibility was asked for; the server outlives it only while a computer is connected. */
    @Volatile
    private var discoverable = false
    private val hideLater = Runnable { endVisibility() }

    /** A start is pending or advertising is on, so it's never asked twice (Android fails the second). */
    private var advertising = false

    /** The connected computer's name (or "A computer"), or null; observable from Compose. */
    var connectedCentralName by mutableStateOf<String?>(null)
        private set

    /** Why the phone couldn't be made visible, for the settings page; null when fine. */
    var problem by mutableStateOf<String?>(null)
        private set

    private var server: BluetoothGattServer? = null
    private var appContext: Context? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private val connected = ConcurrentHashMap.newKeySet<BluetoothDevice>()
    private val subscribers = ConcurrentHashMap.newKeySet<BluetoothDevice>()
    private val main = Handler(Looper.getMainLooper())
    private val parser = MidiNoteParser(MidiInputManager::receiveNoteOn, MidiInputManager::receiveNoteOff)

    // Android sends one notification at a time per connection (the next must wait for onNotificationSent),
    // so messages queue here and go out together: a chord becomes one packet.
    private val queue = ArrayDeque<ByteArray>()
    private var inFlight = 0
    private var inFlightSince = 0L
    private var payloadSize = 20 // ATT MTU 23 minus 3 until a central negotiates more

    /**
     * "Connect a computer": findable for [VISIBLE_MS] (asking again restarts the time). Turning it off stops
     * advertising; the server stays up for a computer that is still connected and closes when it leaves.
     * Main thread.
     */
    fun setDiscoverable(context: Context, on: Boolean) {
        if (on && (!isEnabled || !BluetoothMidi.isEnabled)) return
        main.removeCallbacks(hideLater)
        discoverable = on
        if (on) {
            visibleUntil = SystemClock.elapsedRealtime() + VISIBLE_MS
            main.postDelayed(hideLater, VISIBLE_MS)
            if (server == null) {
                // Couldn't start ([problem] says why): not visible after all
                if (!start(context)) { main.removeCallbacks(hideLater); discoverable = false; visibleUntil = 0L }
            } else {
                updateAdvertising()
            }
        } else {
            endVisibility()
        }
    }

    /** Time is up, a computer connected, or the page closed: nobody can find the phone any more. */
    private fun endVisibility() {
        main.removeCallbacks(hideLater)
        discoverable = false
        visibleUntil = 0L
        updateAdvertising()
        if (connected.isEmpty()) stop()
    }

    /**
     * Advertises only while the phone is wanted findable and no computer is connected: once one is, nobody else
     * nearby can find the phone (or see its name) until it leaves. Main thread.
     */
    private fun updateAdvertising() {
        val want = discoverable && server != null && subscribers.isEmpty()
        if (want) {
            startAdvertising()
        } else if (advertising || isRunning) {
            runCatching { advertiser?.stopAdvertising(advertiseCallback) }
            advertising = false
            isRunning = false
        }
    }

    /** Enables or disables the phone-side Bluetooth MIDI peripheral. */
    fun setEnabled(context: Context, enabled: Boolean) {
        isEnabled = enabled
        AppPrefs.get(context).edit().putBoolean(AppPrefs.IS_BLE_MIDI_ENABLED, enabled).apply()
        if (!enabled) stop()
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        isEnabled = BluetoothMidi.isEnabled
    }

    /** Ends the connection with the computer (the "Disconnect" on its row). */
    fun disconnectComputer() {
        connected.forEach { device -> runCatching { server?.cancelConnection(device) } }
        // Some phones ignore cancelConnection for a link the computer opened, so close the server too.
        // Do not reopen it: explicit Disconnect must prevent an immediate computer reconnect.
        stop()
    }

    private fun start(context: Context): Boolean {
        if (server != null) return true
        appContext = context.applicationContext
        problem = null
        if (!BluetoothMidiPermissions.has(context, BluetoothMidiPermissions.forVisibility())) {
            problem = "Bluetooth permission needed"
            return false
        }
        val manager = context.getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter
        if (manager == null || adapter == null) { problem = "This phone has no Bluetooth"; return false }
        if (!adapter.isEnabled) { problem = "Bluetooth is off"; return false }
        val leAdvertiser = adapter.bluetoothLeAdvertiser
        if (leAdvertiser == null || !adapter.isMultipleAdvertisementSupported) {
            problem = "This phone can't act as a Bluetooth MIDI device"
            return false
        }
        val gatt = manager.openGattServer(context.applicationContext, callback)
        if (gatt == null) { problem = "Bluetooth didn't respond, try again"; return false }

        val midi = BluetoothGattCharacteristic(
            BleMidiPacket.CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        midi.addDescriptor(
            BluetoothGattDescriptor(
                BleMidiPacket.CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
        )
        val service = BluetoothGattService(BleMidiPacket.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(midi)
        server = gatt
        characteristic = midi
        advertiser = leAdvertiser
        // Advertising starts once the service is really there (onServiceAdded), or a quick computer
        // could connect and find nothing.
        if (!gatt.addService(service)) {
            stop()
            problem = "Bluetooth didn't respond, try again"
            return false
        }
        return true
    }

    fun stop() {
        main.removeCallbacks(hideLater)
        discoverable = false
        visibleUntil = 0L
        advertising = false
        runCatching { advertiser?.stopAdvertising(advertiseCallback) }
        runCatching { server?.close() }
        server = null
        characteristic = null
        advertiser = null
        connected.clear()
        subscribers.clear()
        synchronized(queue) { queue.clear(); inFlight = 0 }
        isRunning = false
        if (connectedCentralName != null) {
            connectedCentralName = null
            MidiInputManager.releaseExternalNotes()
        }
    }

    /** A computer is connected and subscribed to the phone's notes. */
    val hasListeners: Boolean get() = subscribers.isNotEmpty()

    /** The largest SysEx message that fits one packet (header, timestamp, message, timestamp before F7). */
    fun maxSysExSize(): Int = payloadSize - 3

    /** Sends one MIDI message to the connected computer; false if none is listening. */
    fun send(message: ByteArray): Boolean {
        if (subscribers.isEmpty()) return false
        synchronized(queue) { queue.add(message) }
        main.post(::flush)
        return true
    }

    private fun flush() {
        val srv = server ?: return
        val ch = characteristic ?: return
        val packet: ByteArray
        synchronized(queue) {
            // A notification that never reported back (link hiccup) mustn't block the queue for good
            if (inFlight > 0 && SystemClock.uptimeMillis() - inFlightSince > 250) inFlight = 0
            if (inFlight > 0 || queue.isEmpty()) return
            val messages = ArrayList<ByteArray>()
            var size = 1 // header
            while (queue.isNotEmpty() && size + BleMidiPacket.encodedSize(queue.peek()!!) <= payloadSize) {
                val m = queue.poll()!!
                messages.add(m)
                size += BleMidiPacket.encodedSize(m)
            }
            if (messages.isEmpty()) { queue.poll(); return } // a message too big for any packet
            packet = BleMidiPacket.encodeAll(messages)
            inFlight = subscribers.size
            inFlightSince = SystemClock.uptimeMillis()
        }
        subscribers.forEach { device ->
            val ok = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    srv.notifyCharacteristicChanged(device, ch, false, packet) == BluetoothGatt.GATT_SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    ch.value = packet
                    @Suppress("DEPRECATION")
                    srv.notifyCharacteristicChanged(device, ch, false)
                }
            }.getOrDefault(false)
            if (!ok) synchronized(queue) { inFlight = (inFlight - 1).coerceAtLeast(0) }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            main.post { if (advertising) { isRunning = true; problem = null } }
        }

        override fun onStartFailure(errorCode: Int) {
            Log.w(TAG, "Advertising failed: $errorCode")
            if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE) {
                // A long phone name doesn't fit next to the service in the scan response: go without it
                startAdvertising(includeName = false, retry = true)
                return
            }
            if (errorCode == ADVERTISE_FAILED_ALREADY_STARTED) {
                main.post { isRunning = true }
                return
            }
            main.post {
                problem = "Couldn't make the phone visible (error $errorCode)"
                isRunning = false
                advertising = false
                endVisibility()
            }
        }
    }

    private fun startAdvertising(includeName: Boolean = true, retry: Boolean = false) {
        val adv = advertiser ?: return
        if (advertising && !retry) return
        advertising = true
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        // The 128-bit service UUID fills most of the 31-byte advertisement; the name goes in the scan response
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(BleMidiPacket.SERVICE_UUID))
            .build()
        val scanResponse = AdvertiseData.Builder().setIncludeDeviceName(includeName).build()
        runCatching { adv.startAdvertising(settings, data, scanResponse, advertiseCallback) }
            .onFailure { main.post { problem = "Couldn't make the phone visible" } }
    }

    /**
     * A computer counts as connected once it subscribes to the phone's notes, as every MIDI client does.
     * Not on the link alone: Android tells a GATT server about every LE link to the phone, including the
     * one to a Bluetooth keyboard the phone connected to itself, which then showed up as a "computer" too.
     */
    private fun updateCentralName() {
        val first = subscribers.firstOrNull()
        val name = first?.let { runCatching { it.name }.getOrNull()?.takeIf(String::isNotBlank) ?: "A computer" }
        main.post {
            val wasConnected = connectedCentralName != null
            connectedCentralName = name
            if (wasConnected && name == null) MidiInputManager.releaseExternalNotes()
        }
    }

    /** The connected computer, or anyone while no computer is connected. */
    private fun isAllowed(device: BluetoothDevice): Boolean = subscribers.isEmpty() || device in subscribers

    private const val GATT_WRITE_NOT_PERMITTED = 3

    private val callback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            if (status == BluetoothGatt.GATT_SUCCESS) main.post(::updateAdvertising)
            else main.post { problem = "Bluetooth didn't respond, try again"; stop() }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connected.add(device)
                // Without a connect() of its own, Android's cancelConnection() can't end a link the
                // computer opened ("Disconnect" did nothing); this registers the link with our server.
                runCatching { server?.connect(device, false) }
            } else {
                connected.remove(device)
                subscribers.remove(device)
                // The page closed earlier and the last computer just left: nothing to keep the server for.
                // Otherwise it's findable again only if "Connect a computer" is still counting down.
                main.post { if (!discoverable && connected.isEmpty()) stop() else updateAdvertising() }
            }
            updateCentralName()
        }

        override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
            payloadSize = (mtu - 3).coerceIn(20, 200)
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, ch: BluetoothGattCharacteristic) {
            // The BLE MIDI spec: a read returns no payload
            server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, ByteArray(0))
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, ch: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?
        ) {
            // While a computer is connected, notes from any other device nearby are ignored
            if (value != null && ch.uuid == BleMidiPacket.CHARACTERISTIC_UUID && isAllowed(device)) {
                val midi = BleMidiPacket.decode(value)
                synchronized(parser) { parser.feed(midi, 0, midi.size) }
            }
            if (responseNeeded) server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }

        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
            val value = if (device in subscribers) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?
        ) {
            if (descriptor.uuid == BleMidiPacket.CCCD_UUID) {
                // Bit 0: notifications on (what a MIDI central asks for to hear the phone)
                if (value != null && value.isNotEmpty() && value[0].toInt() and 1 == 1) {
                    if (!isAllowed(device)) {
                        // One computer at a time: a second one is turned away, not silently given the song
                        if (responseNeeded) server?.sendResponse(device, requestId, GATT_WRITE_NOT_PERMITTED, 0, null)
                        runCatching { server?.cancelConnection(device) }
                        return
                    }
                    if (subscribers.add(device)) {
                        main.post(::endVisibility) // connected: no longer findable by others
                        MidiInputManager.onNewListener()
                    }
                } else if (subscribers.remove(device)) {
                    main.post(::updateAdvertising)
                }
                updateCentralName()
            }
            if (responseNeeded) server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }

        override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
            synchronized(queue) { inFlight = (inFlight - 1).coerceAtLeast(0) }
            main.post(::flush)
        }
    }
}
