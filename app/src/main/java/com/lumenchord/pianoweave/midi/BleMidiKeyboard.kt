package com.lumenchord.pianoweave.midi

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * One Bluetooth LE MIDI keyboard, connected by the app itself (the phone as the central): Android's
 * BluetoothGatt wired to [BleMidiLink], which holds the steps and their rules.
 *
 * Why not Android's MIDI service (`MidiManager.openBluetoothDevice`): it connects without choosing a
 * transport, so for a device the phone knows as dual-mode (a piano that also does Bluetooth audio) with a
 * public address it goes over classic Bluetooth and fails with GATT 133. Here the link is always LE.
 *
 * Notes received go to [MidiInputManager]. Callbacks run on the main thread; [onClosed] comes once.
 */
@SuppressLint("MissingPermission") // BluetoothMidi checks the permissions before creating one
internal class BleMidiKeyboard(
    private val device: BluetoothDevice,
    val name: String,
    /**
     * Wait in the background until the keyboard shows up (Android's own "auto connect": no time limit and
     * no battery-hungry search), instead of trying once now. For getting a known keyboard back.
     */
    private val waitForIt: Boolean,
    private val onReady: (BleMidiKeyboard) -> Unit,
    private val onPairing: (Boolean) -> Unit,
    private val onClosed: (BleMidiKeyboard, wasReady: Boolean, failure: BleMidiLink.Failure?) -> Unit,
) {
    val address: String = device.address
    val isReady: Boolean get() = link.isReady

    private val main = Handler(Looper.getMainLooper())
    private var context: Context? = null
    private var gatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private var loggedWrite = false
    private val parser = MidiNoteParser(MidiInputManager::receiveNoteOn, MidiInputManager::receiveNoteOff)

    private val clock = object : BleMidiClock {
        override fun now() = SystemClock.uptimeMillis()
        override fun schedule(delayMs: Long, task: () -> Unit) { main.postDelayed(task, delayMs) }
        override fun cancelAll() = main.removeCallbacksAndMessages(null)
    }

    private val port = object : BleMidiGatt {
        override fun open(): Boolean {
            val ctx = context ?: return false
            gatt = runCatching { device.connectGatt(ctx, waitForIt, callback, BluetoothDevice.TRANSPORT_LE) }.getOrNull()
            return gatt != null
        }

        override fun close() {
            gatt?.let { g -> runCatching { g.disconnect() }; runCatching { g.close() } }
            gatt = null
            characteristic = null
        }

        override fun requestMtu(mtu: Int): Boolean {
            val g = gatt ?: return false
            runCatching { g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) }
            return runCatching { g.requestMtu(mtu) }.getOrDefault(false)
        }

        /** BluetoothGatt.refresh(): hidden, but long used by BLE libraries for exactly this; harmless if missing. */
        @SuppressLint("DiscouragedPrivateApi")
        override fun refreshServiceCache() {
            val g = gatt ?: return
            val ok = runCatching { g.javaClass.getMethod("refresh").invoke(g) as? Boolean }.getOrNull()
            Log.i(TAG, "service cache refresh: $ok")
        }

        override fun discoverServices(): Boolean = gatt?.discoverServices() == true

        override fun readCharacteristic(): Boolean {
            val g = gatt ?: return false
            val ch = characteristic ?: return false
            return runCatching { g.readCharacteristic(ch) }.getOrDefault(false)
        }

        override fun subscribe(): BleMidiGatt.Subscribe {
            val g = gatt ?: return BleMidiGatt.Subscribe.Busy
            val ch = characteristic ?: return BleMidiGatt.Subscribe.Busy
            g.setCharacteristicNotification(ch, true)
            val cccd = ch.getDescriptor(BleMidiPacket.CCCD_UUID) ?: return BleMidiGatt.Subscribe.NoDescriptor
            val started = runCatching {
                val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(cccd, value) == BluetoothGatt.GATT_SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = value
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(cccd)
                }
            }.getOrDefault(false)
            return if (started) BleMidiGatt.Subscribe.Started else BleMidiGatt.Subscribe.Busy
        }

        override fun write(packet: ByteArray, withResponse: Boolean): Boolean {
            val g = gatt ?: return false
            val ch = characteristic ?: return false
            val type = if (withResponse) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            val ok = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeCharacteristic(ch, packet, type) == BluetoothGatt.GATT_SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    ch.value = packet
                    ch.writeType = type
                    @Suppress("DEPRECATION")
                    g.writeCharacteristic(ch)
                }
            }.getOrDefault(false)
            if (!loggedWrite) { Log.i(TAG, "first write, ${packet.size} bytes: ${if (ok) "sent" else "busy"}"); loggedWrite = ok }
            return ok
        }

        override val bond: BleMidiGatt.Bond
            get() = when (device.bondState) {
                BluetoothDevice.BOND_BONDING -> BleMidiGatt.Bond.Bonding
                BluetoothDevice.BOND_BONDED -> BleMidiGatt.Bond.Bonded
                else -> BleMidiGatt.Bond.None
            }
    }

    private val link: BleMidiLink = BleMidiLink(port, clock, object : BleMidiLink.Listener {
        override fun onReady() {
            Log.i(TAG, "ready, payload ${link.payloadSize}")
            onReady(this@BleMidiKeyboard)
        }
        override fun onPairing(on: Boolean) = onPairing.invoke(on)
        override fun onMidi(bytes: ByteArray) = parser.feed(bytes, 0, bytes.size)
        override fun onClosed(wasReady: Boolean, failure: BleMidiLink.Failure?) {
            Log.i(TAG, "closed (failure $failure)")
            context?.let { runCatching { it.unregisterReceiver(bondReceiver) } }
            onClosed(this@BleMidiKeyboard, wasReady, failure)
        }
    })

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            @Suppress("DEPRECATION")
            val changed = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            if (changed.address != address) return
            val state = when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)) {
                BluetoothDevice.BOND_BONDING -> BleMidiGatt.Bond.Bonding
                BluetoothDevice.BOND_BONDED -> BleMidiGatt.Bond.Bonded
                else -> BleMidiGatt.Bond.None
            }
            val previous = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)
            Log.i(TAG, "bond state $previous -> $state")
            link.onBond(state, wasBonding = previous == BluetoothDevice.BOND_BONDING)
        }
    }

    /** The largest SysEx message that fits one packet. */
    fun maxSysExSize(): Int = link.maxSysExSize()

    fun connect(context: Context) {
        val app = context.applicationContext
        this.context = app
        // The system's own broadcast; it has to be exported to reach the app
        ContextCompat.registerReceiver(
            app, bondReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED
        )
        link.start()
    }

    /** Sends one MIDI message; false if the keyboard isn't ready. Any thread. */
    fun send(message: ByteArray): Boolean = link.send(message)

    /** Disconnects; what was just queued (e.g. note offs) goes out first. Any thread. */
    fun close() {
        main.post { link.close() }
    }

    /** Runs [event] on the main thread if [g] is still the current connection (not one replaced by a retry). */
    private inline fun on(g: BluetoothGatt, crossinline event: () -> Unit) {
        main.post { if (g === gatt && !link.isClosed) event() }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) = on(g) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                link.onConnected()
            } else {
                Log.i(TAG, "disconnected (status $status)")
                link.onDisconnected(status)
            }
        }

        override fun onServiceChanged(g: BluetoothGatt) = on(g) {
            Log.i(TAG, "services changed: subscribing again")
            characteristic = null
            link.onServicesChanged()
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) = on(g) {
            link.onMtu(if (status == BluetoothGatt.GATT_SUCCESS) mtu else null)
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) = on(g) {
            val midiServices = g.services.filter { it.uuid == BleMidiPacket.SERVICE_UUID }
            Log.i(TAG, "services: ${g.services.size}, MIDI: ${midiServices.size} (handles ${midiServices.map { it.instanceId }})")
            val ch = g.getService(BleMidiPacket.SERVICE_UUID)?.getCharacteristic(BleMidiPacket.CHARACTERISTIC_UUID)
            if (ch == null) Log.w(TAG, "no MIDI service (status $status)")
            characteristic = ch
            link.onServices(
                ch?.let {
                    BleMidiLink.Characteristic(
                        canRead = it.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0,
                        writeWithoutResponse = it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0,
                    )
                }
            )
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) on(g) { link.onRead() }
        }

        override fun onCharacteristicRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray, status: Int) =
            on(g) { link.onRead() }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) = on(g) {
            if (status != BluetoothGatt.GATT_SUCCESS) Log.w(TAG, "subscribing refused ($status)")
            link.onSubscribed(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) = on(g) {
            if (status != BluetoothGatt.GATT_SUCCESS) Log.w(TAG, "write failed ($status)")
            link.onWriteDone()
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) ch.value?.let { v -> on(g) { link.onNotification(v) } }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) =
            on(g) { link.onNotification(value) }
    }

    private companion object {
        const val TAG = "BleMidiKeyboard"
    }
}
