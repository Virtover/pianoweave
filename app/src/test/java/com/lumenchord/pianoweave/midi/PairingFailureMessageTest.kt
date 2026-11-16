package com.lumenchord.pianoweave.midi

import org.junit.Assert.assertTrue
import org.junit.Test

class PairingFailureMessageTest {

    @Test
    fun theFirstFailureAsksToAcceptTheRequest() {
        assertTrue(pairingFailureMessage(1).contains("accept the request"))
    }

    @Test
    fun failingAgainSaysToForgetTheOldPairing() {
        for (failures in listOf(2, 3, 10)) {
            val message = pairingFailureMessage(failures)
            assertTrue(message, message.contains("Remove this device from the keyboard's Bluetooth list"))
        }
    }
}
