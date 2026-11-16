package com.lumenchord.pianoweave.midi

/** What [BleMidiLink] needs from the Bluetooth stack: Android's BluetoothGatt in the app, a fake in tests. */
internal interface BleMidiGatt {
    enum class Subscribe { Started, NoDescriptor, Busy }
    enum class Bond { None, Bonding, Bonded }

    /** Opens the LE connection; false if Android refused right away. */
    fun open(): Boolean
    /** Ends the connection (also before a retry, which calls [open] again). */
    fun close()
    /** Asks for a fast connection and a bigger MTU; false if the request couldn't be sent. */
    fun requestMtu(mtu: Int): Boolean
    /** Forgets the service list Android kept for a paired device, so the next discovery reads it fresh. */
    fun refreshServiceCache()
    fun discoverServices(): Boolean
    fun readCharacteristic(): Boolean
    /** Turns notifications on and writes the subscription to the keyboard. */
    fun subscribe(): Subscribe
    fun write(packet: ByteArray, withResponse: Boolean): Boolean
    val bond: Bond
}

/** The link's sense of time: the main thread's Handler in the app, a virtual clock in tests. */
internal interface BleMidiClock {
    fun now(): Long
    fun schedule(delayMs: Long, task: () -> Unit)
    /** Drops everything scheduled (the link closed). */
    fun cancelAll()
}

/**
 * The steps of a Bluetooth LE MIDI keyboard connection, without Android in it (so tests can run every path):
 * connect → bigger MTU (going on without it after [MTU_WAIT_MS]) → find the MIDI service → read the
 * characteristic (as Android's MIDI service does; some keyboards ask to pair here) → subscribe → ready.
 * One GATT request at a time; events for a step that isn't current are ignored.
 *
 * - **Retry:** a first connection failing with an error (often 133 right after a scan) is tried once more.
 * - **Pairing:** while Android pairs, requests wait and [Listener.onPairing] says so; a refusal or failure
 *   ends with [Failure.Pairing]. A subscription refused right after pairing is tried again.
 * - **Stale services:** a paired keyboard's cached service list is dropped before discovery; reusing it
 *   once made every write "succeed" into the wrong place. A "services changed" from the keyboard redoes
 *   discovery and the subscription.
 * - **Sending:** messages queue in a [BleMidiOutbox] and go out one write at a time, a chord in one packet.
 *
 * Everything but [send] (and [maxSysExSize]) runs on the [clock]'s thread.
 */
internal class BleMidiLink(
    private val gatt: BleMidiGatt,
    private val clock: BleMidiClock,
    private val listener: Listener,
) {
    interface Listener {
        fun onReady()
        fun onPairing(on: Boolean)
        /** Plain MIDI bytes from the keyboard (BLE headers and timestamps removed). */
        fun onMidi(bytes: ByteArray)
        /** Once, at the end; [failure] is null when [close] was called. */
        fun onClosed(wasReady: Boolean, failure: Failure?)
    }

    /** Why the link ended without being asked to. */
    enum class Failure { Connect, NotMidi, Pairing, Lost }

    /** What the keyboard's MIDI characteristic allows. */
    class Characteristic(val canRead: Boolean, val writeWithoutResponse: Boolean)

    private enum class Step { Connecting, Mtu, Discovering, Reading, Subscribing, Ready, Closed }

    @Volatile
    private var step = Step.Connecting
    val isReady: Boolean get() = step == Step.Ready
    val isClosed: Boolean get() = step == Step.Closed

    @Volatile
    var payloadSize = 20 // ATT MTU 23 minus 3 until a bigger one is agreed
        private set
    var isPairing = false
        private set

    private val outbox = BleMidiOutbox()
    private var connectRetries = 1
    private var subscribeRetries = SUBSCRIBE_RETRIES
    private var canRead = false
    private var withResponse = false
    private var afterPairing: (() -> Unit)? = null

    /** The largest SysEx message that fits one packet (header, timestamp, message, timestamp before F7). */
    fun maxSysExSize(): Int = payloadSize - 3

    fun start() {
        if (!gatt.open()) finish(Failure.Connect)
    }

    // ---------------------------------------------------------------- connection steps

    fun onConnected() {
        if (step != Step.Connecting) return
        step = Step.Mtu
        // Some keyboards never answer the MTU request: go on without it after a while
        if (gatt.requestMtu(MAX_MTU)) clock.schedule(MTU_WAIT_MS) { if (step == Step.Mtu) discover() }
        else discover()
    }

    /** The MTU answer; null if the keyboard refused. */
    fun onMtu(mtu: Int?) {
        if (mtu != null) payloadSize = (mtu - 3).coerceIn(20, MAX_PAYLOAD)
        if (step == Step.Mtu) discover()
    }

    private fun discover() {
        step = Step.Discovering
        if (gatt.bond == BleMidiGatt.Bond.Bonded) gatt.refreshServiceCache()
        if (!gatt.discoverServices()) finish(Failure.Connect)
    }

    /** Discovery finished; [characteristic] is null when the device has no MIDI service. */
    fun onServices(characteristic: Characteristic?) {
        if (step != Step.Discovering) return
        if (characteristic == null) { finish(Failure.NotMidi); return }
        canRead = characteristic.canRead
        withResponse = !characteristic.writeWithoutResponse
        step = Step.Reading
        whenNotPairing(::read)
    }

    private fun read() {
        if (step != Step.Reading) return
        if (!canRead || !gatt.readCharacteristic()) subscribe()
    }

    fun onRead() {
        if (step == Step.Reading) subscribe()
    }

    private fun subscribe() {
        if (step == Step.Closed || step == Step.Ready) return
        step = Step.Subscribing
        if (gatt.bond == BleMidiGatt.Bond.Bonding) { whenNotPairing(::subscribe); return }
        when (gatt.subscribe()) {
            BleMidiGatt.Subscribe.Started -> Unit // onSubscribed goes on
            BleMidiGatt.Subscribe.NoDescriptor -> becomeReady() // can't subscribe; sending still works
            BleMidiGatt.Subscribe.Busy ->
                if (subscribeRetries-- > 0) clock.schedule(BUSY_RETRY_MS) { subscribe() } else becomeReady()
        }
    }

    fun onSubscribed(success: Boolean) {
        if (step != Step.Subscribing) return
        when {
            success -> becomeReady()
            // Refused until paired: again once it is
            gatt.bond == BleMidiGatt.Bond.Bonding -> whenNotPairing(::subscribe)
            subscribeRetries-- > 0 -> clock.schedule(REFUSED_RETRY_MS) { subscribe() }
            else -> becomeReady() // notes from the keyboard may not arrive, but sending works
        }
    }

    private fun becomeReady() {
        if (step == Step.Closed || step == Step.Ready) return
        // (After onServicesChanged this comes a second time, so whoever listens hears the song from the start.)
        step = Step.Ready
        listener.onReady()
    }

    /**
     * The keyboard says its services changed (firmware update, or a simulator restarted). Android may even
     * have reconnected underneath without a disconnect: the old subscription is gone either way, so find the
     * service and subscribe again. Sending pauses meanwhile; ready is reported again at the end.
     */
    fun onServicesChanged() {
        if (step == Step.Closed || step == Step.Connecting || step == Step.Mtu) return
        outbox.clear()
        subscribeRetries = SUBSCRIBE_RETRIES
        afterPairing = null
        discover()
    }

    // ---------------------------------------------------------------- pairing

    /** Runs [next] now, or once a running pairing has finished (GATT requests fail meanwhile). */
    private fun whenNotPairing(next: () -> Unit) {
        if (gatt.bond == BleMidiGatt.Bond.Bonding) {
            setPairing(true)
            afterPairing = next
        } else {
            next()
        }
    }

    /** Android's bond state for this keyboard changed; [wasBonding] tells a failed pairing from "never paired". */
    fun onBond(state: BleMidiGatt.Bond, wasBonding: Boolean) {
        if (step == Step.Closed) return
        when (state) {
            BleMidiGatt.Bond.Bonding -> setPairing(true)
            BleMidiGatt.Bond.Bonded -> {
                setPairing(false)
                afterPairing?.let { afterPairing = null; it() }
            }
            BleMidiGatt.Bond.None -> if (wasBonding) {
                setPairing(false)
                if (step != Step.Ready) finish(Failure.Pairing)
            }
        }
    }

    private fun setPairing(on: Boolean) {
        if (isPairing == on) return
        isPairing = on
        listener.onPairing(on)
    }

    // ---------------------------------------------------------------- the link ending

    /** The connection failed or ended ([status] is the GATT status, 0 for a normal disconnect). */
    fun onDisconnected(status: Int) {
        if (step == Step.Closed) return
        if (step == Step.Connecting && !isPairing && status != 0 && connectRetries-- > 0) {
            gatt.close()
            clock.schedule(CONNECT_RETRY_MS) { if (step == Step.Connecting && !gatt.open()) finish(Failure.Connect) }
            return
        }
        finish(
            when {
                step == Step.Ready -> Failure.Lost
                isPairing || gatt.bond == BleMidiGatt.Bond.Bonding -> Failure.Pairing
                else -> Failure.Connect
            }
        )
    }

    /** Disconnects; what was just queued (e.g. note offs) goes out first. */
    fun close() {
        if (step == Step.Closed) return
        if (step == Step.Ready && outbox.hasPending()) clock.schedule(CLOSE_GRACE_MS) { finish(null) }
        else finish(null)
    }

    private fun finish(failure: Failure?) {
        if (step == Step.Closed) return
        val wasReady = step == Step.Ready
        step = Step.Closed
        clock.cancelAll()
        afterPairing = null
        if (isPairing) setPairing(false)
        gatt.close()
        outbox.clear()
        listener.onClosed(wasReady, failure)
    }

    // ---------------------------------------------------------------- MIDI

    /** Queues one MIDI message; false if not ready. Any thread. */
    fun send(message: ByteArray): Boolean {
        if (step != Step.Ready) return false
        outbox.add(message)
        clock.schedule(0) { flush() }
        return true
    }

    private fun flush() {
        if (step != Step.Ready) return
        val packet = outbox.next(payloadSize, clock.now()) ?: return
        if (!gatt.write(packet, withResponse)) {
            outbox.notSent()
            clock.schedule(BUSY_WRITE_RETRY_MS) { flush() } // the stack is busy: try again shortly
        }
    }

    /** The last write finished (or failed): the next packet can go. */
    fun onWriteDone() {
        outbox.done()
        flush()
    }

    fun onNotification(packet: ByteArray) {
        if (step != Step.Ready) return
        val midi = BleMidiPacket.decode(packet)
        if (midi.isNotEmpty()) listener.onMidi(midi)
    }

    companion object {
        const val MAX_MTU = 517
        const val MAX_PAYLOAD = 200
        /** The MTU answer can take a few seconds while the connection settles. */
        const val MTU_WAIT_MS = 6_000L
        const val CONNECT_RETRY_MS = 400L
        const val BUSY_RETRY_MS = 300L
        const val REFUSED_RETRY_MS = 500L
        const val BUSY_WRITE_RETRY_MS = 10L
        const val CLOSE_GRACE_MS = 200L
        const val SUBSCRIBE_RETRIES = 5
    }
}

/**
 * MIDI messages waiting for the keyboard. Android takes one write at a time, so they queue here and go out
 * together: a chord becomes one packet. Thread-safe.
 */
internal class BleMidiOutbox(private val staleMs: Long = 250) {
    private val queue = ArrayDeque<ByteArray>()
    private var inFlightSince: Long? = null
    private var inFlight: ByteArray? = null
    /** A packet the stack was too busy to take; it goes again before anything newer. */
    private var unsent: ByteArray? = null

    @Synchronized
    fun add(message: ByteArray) { queue.add(message) }

    @Synchronized
    fun hasPending(): Boolean = queue.isNotEmpty() || inFlightSince != null || unsent != null

    /**
     * The next packet (as many messages as fit [payloadSize]), now in flight; null while one is in flight
     * or nothing waits. A write that never reported back stops counting after [staleMs], so a link hiccup
     * can't block the queue for good; a message too big for any packet is dropped.
     */
    @Synchronized
    fun next(payloadSize: Int, now: Long): ByteArray? {
        inFlightSince?.let { if (now - it > staleMs) inFlightSince = null else return null }
        unsent?.let { packet ->
            unsent = null
            inFlight = packet
            inFlightSince = now
            return packet
        }
        while (queue.isNotEmpty()) {
            val messages = ArrayList<ByteArray>()
            var size = 1 // header
            while (queue.isNotEmpty() && size + BleMidiPacket.encodedSize(queue.first()) <= payloadSize) {
                val m = queue.removeFirst()
                messages.add(m)
                size += BleMidiPacket.encodedSize(m)
            }
            if (messages.isEmpty()) { queue.removeFirst(); continue }
            inFlightSince = now
            return BleMidiPacket.encodeAll(messages).also { inFlight = it }
        }
        return null
    }

    /** The packet in flight was written. */
    @Synchronized
    fun done() { inFlightSince = null; inFlight = null }

    /** The write couldn't start (stack busy): the same packet goes next. */
    @Synchronized
    fun notSent() { unsent = inFlight; done() }

    @Synchronized
    fun clear() { queue.clear(); unsent = null; done() }
}
