package com.lumenchord.pianoweave.midi

import com.lumenchord.pianoweave.midi.BleMidiGatt.Bond
import com.lumenchord.pianoweave.midi.BleMidiGatt.Subscribe
import com.lumenchord.pianoweave.midi.BleMidiLink.Failure
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleMidiLinkTest {

    private class FakeGatt : BleMidiGatt {
        var opens = 0
        var closes = 0
        var mtuRequests = 0
        var refreshes = 0
        var discoveries = 0
        var reads = 0
        var subscribes = 0
        var openOk = true
        var mtuOk = true
        var readOk = true
        var writeOk = true
        val subscribeResults = ArrayDeque<Subscribe>()
        val writes = mutableListOf<Pair<ByteArray, Boolean>>()
        override var bond = Bond.None

        override fun open(): Boolean { opens++; return openOk }
        override fun close() { closes++ }
        override fun requestMtu(mtu: Int): Boolean { mtuRequests++; return mtuOk }
        override fun refreshServiceCache() { refreshes++ }
        override fun discoverServices(): Boolean { discoveries++; return true }
        override fun readCharacteristic(): Boolean { reads++; return readOk }
        override fun subscribe(): Subscribe { subscribes++; return subscribeResults.removeFirstOrNull() ?: Subscribe.Started }
        override fun write(packet: ByteArray, withResponse: Boolean): Boolean {
            if (writeOk) writes += packet to withResponse
            return writeOk
        }
    }

    /** Virtual time: tasks run only when [advance] passes them. */
    private class FakeClock : BleMidiClock {
        var time = 0L
        private val tasks = mutableListOf<Pair<Long, () -> Unit>>()
        override fun now() = time
        override fun schedule(delayMs: Long, task: () -> Unit) { tasks += (time + delayMs) to task }
        override fun cancelAll() = tasks.clear()
        fun advance(ms: Long) {
            val end = time + ms
            while (true) {
                val next = tasks.filter { it.first <= end }.minByOrNull { it.first } ?: break
                tasks.remove(next)
                time = next.first
                next.second()
            }
            time = end
        }
    }

    private class Events : BleMidiLink.Listener {
        var ready = 0
        var linkUp = 0
        val pairing = mutableListOf<Boolean>()
        val midi = mutableListOf<ByteArray>()
        val closed = mutableListOf<Pair<Boolean, Failure?>>()
        override fun onLinkUp() { linkUp++ }
        override fun onReady() { ready++ }
        override fun onPairing(on: Boolean) { pairing += on }
        override fun onMidi(bytes: ByteArray) { midi += bytes }
        override fun onClosed(wasReady: Boolean, failure: Failure?) { closed += wasReady to failure }
    }

    private val gatt = FakeGatt()
    private val clock = FakeClock()
    private val events = Events()
    private val link = BleMidiLink(gatt, clock, events)
    private val midiChar = BleMidiLink.Characteristic(canRead = true, writeWithoutResponse = true)

    /** Runs the whole setup to "ready" (no pairing, MTU 247). */
    private fun connectReady() {
        link.start()
        link.onConnected()
        link.onMtu(247)
        link.onServices(midiChar)
        link.onRead()
        link.onSubscribed(true)
    }

    private fun noteOn(pitch: Int) = byteArrayOf(0x90.toByte(), pitch.toByte(), 100)

    // ------------------------------------------------------------------ setup

    @Test
    fun everyStepRunsOnceInOrderAndEndsReady() {
        connectReady()
        assertEquals(listOf(1, 1, 1, 1, 1), listOf(gatt.opens, gatt.mtuRequests, gatt.discoveries, gatt.reads, gatt.subscribes))
        assertTrue(link.isReady)
        assertEquals(1, events.ready)
        assertEquals(1, events.linkUp)
        assertFalse(link.isConnecting)
        assertEquals(200, link.payloadSize) // 247 - 3, capped
        assertEquals(197, link.maxSysExSize())
    }

    @Test
    fun anUnansweredMtuRequestIsGivenUpAfterAWhileAndALateAnswerDoesNotDiscoverTwice() {
        link.start()
        link.onConnected()
        clock.advance(BleMidiLink.MTU_WAIT_MS - 1)
        assertEquals(0, gatt.discoveries)
        clock.advance(1)
        assertEquals(1, gatt.discoveries)
        link.onMtu(100) // arrives late
        assertEquals(1, gatt.discoveries)
        assertEquals(97, link.payloadSize)
    }

    @Test
    fun anMtuRequestThatCannotBeSentGoesStraightToDiscovery() {
        gatt.mtuOk = false
        link.start()
        link.onConnected()
        assertEquals(1, gatt.discoveries)
        link.onMtu(null)
        assertEquals(20, link.payloadSize)
        assertEquals(1, gatt.discoveries)
    }

    @Test
    fun aPairedKeyboardsCachedServicesAreDroppedBeforeDiscovery() {
        gatt.bond = Bond.Bonded
        connectReady()
        assertEquals(1, gatt.refreshes)
    }

    @Test
    fun anUnpairedKeyboardIsDiscoveredWithoutDroppingAnything() {
        connectReady()
        assertEquals(0, gatt.refreshes)
    }

    @Test
    fun aDeviceWithoutTheMidiServiceEndsAsNotMidi() {
        link.start(); link.onConnected(); link.onMtu(247)
        link.onServices(null)
        assertEquals(listOf(false to Failure.NotMidi), events.closed)
        assertEquals(1, gatt.closes)
    }

    @Test
    fun anUnreadableCharacteristicIsSubscribedWithoutReading() {
        link.start(); link.onConnected(); link.onMtu(247)
        link.onServices(BleMidiLink.Characteristic(canRead = false, writeWithoutResponse = true))
        assertEquals(0, gatt.reads)
        assertEquals(1, gatt.subscribes)
    }

    @Test
    fun aReadThatCannotStartGoesOnToSubscribing() {
        gatt.readOk = false
        link.start(); link.onConnected(); link.onMtu(247)
        link.onServices(midiChar)
        assertEquals(1, gatt.subscribes)
    }

    @Test
    fun itCountsAsConnectingOnlyUntilTheLinkIsUp() {
        link.start()
        assertTrue(link.isConnecting)
        link.onDisconnected(133) // retrying is still connecting
        assertTrue(link.isConnecting)
        clock.advance(BleMidiLink.CONNECT_RETRY_MS)
        link.onConnected()
        assertFalse(link.isConnecting)
    }

    @Test
    fun anAndroidRefusalToConnectEndsAtOnce() {
        gatt.openOk = false
        link.start()
        assertEquals(listOf(false to Failure.Connect), events.closed)
    }

    // ------------------------------------------------------------------ retries

    @Test
    fun aFailedFirstConnectionIsTriedOnceMore() {
        link.start()
        link.onDisconnected(133)
        assertEquals(1, gatt.closes)
        clock.advance(BleMidiLink.CONNECT_RETRY_MS)
        assertEquals(2, gatt.opens)
        assertTrue(events.closed.isEmpty())
        link.onDisconnected(133)
        assertEquals(listOf(false to Failure.Connect), events.closed)
    }

    @Test
    fun aRefusedSubscriptionIsTriedAgainThenWorks() {
        link.start(); link.onConnected(); link.onMtu(247); link.onServices(midiChar); link.onRead()
        link.onSubscribed(false)
        assertFalse(link.isReady)
        clock.advance(BleMidiLink.REFUSED_RETRY_MS)
        assertEquals(2, gatt.subscribes)
        link.onSubscribed(true)
        assertTrue(link.isReady)
    }

    @Test
    fun aSubscriptionTheStackKeepsRefusingStillEndsReadySoSendingWorks() {
        repeat(BleMidiLink.SUBSCRIBE_RETRIES + 1) { gatt.subscribeResults.add(Subscribe.Busy) }
        link.start(); link.onConnected(); link.onMtu(247); link.onServices(midiChar); link.onRead()
        clock.advance(BleMidiLink.BUSY_RETRY_MS * (BleMidiLink.SUBSCRIBE_RETRIES + 1))
        assertEquals(BleMidiLink.SUBSCRIBE_RETRIES + 1, gatt.subscribes)
        assertTrue(link.isReady)
    }

    @Test
    fun aCharacteristicWithoutSubscriptionDescriptorIsReadyAtOnce() {
        gatt.subscribeResults.add(Subscribe.NoDescriptor)
        link.start(); link.onConnected(); link.onMtu(247); link.onServices(midiChar); link.onRead()
        assertTrue(link.isReady)
    }

    @Test
    fun changedServicesAreFoundAndSubscribedAgainAndReadyIsReportedAgain() {
        gatt.bond = Bond.Bonded
        connectReady()
        link.onServicesChanged()
        assertFalse(link.isReady)
        assertFalse(link.send(noteOn(60)))
        assertEquals(2, gatt.discoveries)
        assertEquals(2, gatt.refreshes)
        link.onServices(midiChar); link.onRead(); link.onSubscribed(true)
        assertEquals(2, events.ready)
        assertTrue(link.send(noteOn(60)))
    }

    @Test
    fun changedServicesBeforeDiscoveryChangeNothing() {
        link.start(); link.onConnected()
        link.onServicesChanged()
        assertEquals(0, gatt.discoveries)
    }

    // ------------------------------------------------------------------ pairing

    @Test
    fun stepsWaitWhileAndroidPairsAndGoOnOncePaired() {
        link.start(); link.onConnected(); link.onMtu(247)
        gatt.bond = Bond.Bonding
        link.onServices(midiChar)
        assertEquals(0, gatt.reads)
        assertEquals(listOf(true), events.pairing)
        gatt.bond = Bond.Bonded
        link.onBond(Bond.Bonded, wasBonding = true)
        assertEquals(1, gatt.reads)
        assertEquals(listOf(true, false), events.pairing)
        link.onRead(); link.onSubscribed(true)
        assertTrue(link.isReady)
    }

    @Test
    fun aSubscriptionRefusedDuringPairingIsSentAgainOncePaired() {
        link.start(); link.onConnected(); link.onMtu(247); link.onServices(midiChar); link.onRead()
        gatt.bond = Bond.Bonding
        link.onSubscribed(false)
        assertEquals(1, gatt.subscribes)
        gatt.bond = Bond.Bonded
        link.onBond(Bond.Bonded, wasBonding = true)
        assertEquals(2, gatt.subscribes)
        link.onSubscribed(true)
        assertTrue(link.isReady)
    }

    @Test
    fun aDeclinedPairingEndsAsPairing() {
        link.start(); link.onConnected()
        link.onBond(Bond.Bonding, wasBonding = false)
        link.onBond(Bond.None, wasBonding = true)
        assertEquals(listOf(true, false), events.pairing)
        assertEquals(listOf(false to Failure.Pairing), events.closed)
    }

    @Test
    fun theKeyboardHangingUpDuringPairingEndsAsPairingWithoutARetry() {
        link.start()
        link.onBond(Bond.Bonding, wasBonding = false)
        link.onDisconnected(133)
        assertEquals(1, gatt.opens)
        assertEquals(listOf(false to Failure.Pairing), events.closed)
        assertEquals(listOf(true, false), events.pairing) // pairing reported over at the end
    }

    @Test
    fun aBondRemovedThatWasNeverPairingChangesNothing() {
        connectReady()
        link.onBond(Bond.None, wasBonding = false)
        assertTrue(link.isReady)
    }

    // ------------------------------------------------------------------ sending and receiving

    @Test
    fun nothingIsSentBeforeReady() {
        link.start()
        assertFalse(link.send(noteOn(60)))
    }

    @Test
    fun aChordGoesOutAsOnePacketAndTheNextWaitsForTheWriteToFinish() {
        connectReady()
        listOf(60, 64, 67).forEach { link.send(noteOn(it)) }
        clock.advance(0)
        assertEquals(1, gatt.writes.size)
        assertArrayEquals(byteArrayOf(0x90.toByte(), 60, 100, 0x90.toByte(), 64, 100, 0x90.toByte(), 67, 100),
            BleMidiPacket.decode(gatt.writes[0].first))
        assertFalse(gatt.writes[0].second) // write without response
        link.send(noteOn(72))
        clock.advance(0)
        assertEquals(1, gatt.writes.size) // still in flight
        link.onWriteDone()
        assertEquals(2, gatt.writes.size)
    }

    @Test
    fun aBusyStackGetsTheWriteAgainShortly() {
        connectReady()
        gatt.writeOk = false
        link.send(noteOn(60))
        clock.advance(0)
        gatt.writeOk = true
        clock.advance(BleMidiLink.BUSY_WRITE_RETRY_MS)
        assertEquals(1, gatt.writes.size)
    }

    @Test
    fun aKeyboardWithoutWriteWithoutResponseGetsWritesWithResponse() {
        link.start(); link.onConnected(); link.onMtu(247)
        link.onServices(BleMidiLink.Characteristic(canRead = true, writeWithoutResponse = false))
        link.onRead(); link.onSubscribed(true)
        link.send(noteOn(60))
        clock.advance(0)
        assertTrue(gatt.writes.single().second)
    }

    @Test
    fun notesFromTheKeyboardArriveAsPlainMidi() {
        link.onNotification(BleMidiPacket.encode(noteOn(60))) // before ready: ignored
        connectReady()
        link.onNotification(BleMidiPacket.encode(noteOn(60)))
        assertArrayEquals(noteOn(60), events.midi.single())
    }

    // ------------------------------------------------------------------ ending

    @Test
    fun losingAReadyKeyboardEndsAsLost() {
        connectReady()
        link.onDisconnected(8)
        assertEquals(listOf(true to Failure.Lost), events.closed)
        assertEquals(1, gatt.opens) // no retry once it was connected
    }

    @Test
    fun closingSendsWhatIsQueuedFirstAndReportsOnce() {
        connectReady()
        link.send(noteOn(60))
        link.close()
        assertTrue(events.closed.isEmpty())
        clock.advance(0) // the queued note goes out
        assertEquals(1, gatt.writes.size)
        clock.advance(BleMidiLink.CLOSE_GRACE_MS)
        assertEquals(listOf(true to null), events.closed)
        link.close()
        link.onDisconnected(0)
        assertEquals(1, events.closed.size)
        assertEquals(1, gatt.closes)
    }

    @Test
    fun eventsAfterTheEndAreIgnored() {
        link.start()
        link.close()
        link.onConnected(); link.onMtu(247); link.onServices(midiChar); link.onSubscribed(true)
        clock.advance(BleMidiLink.MTU_WAIT_MS)
        assertEquals(0, gatt.mtuRequests)
        assertFalse(link.isReady)
        assertEquals(listOf(false to null), events.closed)
    }

    // ------------------------------------------------------------------ outbox

    @Test
    fun theOutboxFillsPacketsToThePayloadSizeAndDropsWhatNeverFits() {
        val outbox = BleMidiOutbox()
        outbox.add(ByteArray(30) { 0x10 }.also { it[0] = 0xF0.toByte(); it[29] = 0xF7.toByte() }) // 32 bytes encoded
        repeat(6) { outbox.add(noteOn(60 + it)) } // 4 bytes each
        val first = outbox.next(payloadSize = 20, now = 0)!! // the SysEx can't fit 20: dropped
        assertEquals(1 + 4 * 4, first.size)
        assertNull(outbox.next(20, 0)) // in flight
        outbox.done()
        assertEquals(1 + 2 * 4, outbox.next(20, 0)!!.size)
        outbox.done()
        assertNull(outbox.next(20, 0))
        assertFalse(outbox.hasPending())
    }

    @Test
    fun aPacketTheStackRefusedGoesAgainBeforeNewerOnes() {
        val outbox = BleMidiOutbox()
        outbox.add(noteOn(60))
        val refused = outbox.next(20, 0)!!
        outbox.notSent()
        outbox.add(noteOn(61))
        assertArrayEquals(refused, outbox.next(20, 0))
        outbox.done()
        assertArrayEquals(noteOn(61), BleMidiPacket.decode(outbox.next(20, 0)!!))
    }

    @Test
    fun aWriteThatNeverReportsBackStopsBlockingTheOutbox() {
        val outbox = BleMidiOutbox(staleMs = 250)
        outbox.add(noteOn(60)); outbox.next(20, now = 0)
        outbox.add(noteOn(61))
        assertNull(outbox.next(20, now = 250))
        assertTrue(outbox.next(20, now = 251) != null)
        outbox.clear()
        assertFalse(outbox.hasPending())
    }

    // ------------------------------------------------------------------ reconnecting

    @Test
    fun theRememberedKeyboardIsWaitedForAgainOnlyWhenThatCanHelp() {
        val r = KeyboardReconnect
        val none = KeyboardReconnect.Action.None
        val now = KeyboardReconnect.Action.WaitNow
        val later = KeyboardReconnect.Action.WaitLater
        // Ended on purpose: nothing, whatever it was
        KeyboardReconnect.Role.entries.forEach { assertEquals(none, r.afterClose(it, null)) }
        assertEquals(now, r.afterClose(KeyboardReconnect.Role.InUse, Failure.Lost))
        Failure.entries.forEach { assertEquals(now, r.afterClose(KeyboardReconnect.Role.Tapped, it)) }
        assertEquals(later, r.afterClose(KeyboardReconnect.Role.Waiting, Failure.Connect))
        assertEquals(none, r.afterClose(KeyboardReconnect.Role.Waiting, Failure.Pairing))
        assertEquals(none, r.afterClose(KeyboardReconnect.Role.Waiting, Failure.NotMidi))
    }
}
