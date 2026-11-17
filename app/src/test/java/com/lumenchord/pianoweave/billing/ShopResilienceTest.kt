package com.lumenchord.pianoweave.billing

import com.lumenchord.pianoweave.ui.screens.learn.unlimitedBalanceLines
import com.lumenchord.pianoweave.ui.viewmodel.keepsServerInfoOnFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shop through a server restart and a screen rotation. */
class ShopResilienceTest {

    @Test
    fun aFailedServerInfoReadKeepsWhatTheSameServerSaid() {
        // The server restarting, a proxy error, a timeout: plans, packs, balance and subscription stay shown
        assertTrue(keepsServerInfoOnFailure(sameServer = true, passwordRequired = false))
        // Another server, never read: nothing to keep
        assertFalse(keepsServerInfoOnFailure(sameServer = false, passwordRequired = false))
        // The server now wants a password: what it said before no longer applies
        assertFalse(keepsServerInfoOnFailure(sameServer = true, passwordRequired = true))
    }

    @Test
    fun aSubscriptionIsToldFromAPackByThePurchaseItself() {
        val subscription = """{"orderId": "GPA.1", "packageName": "p", "productId": "sub_pro", "purchaseTime": 1,
            "purchaseState": 0, "purchaseToken": "t", "quantity": 1, "autoRenewing": true, "acknowledged": false}"""
        val cancelled = subscription.replace("\"autoRenewing\": true", "\"autoRenewing\": false")
        val pack = """{"orderId": "GPA.2", "packageName": "p", "productId": "credits_100", "purchaseTime": 1,
            "purchaseState": 0, "purchaseToken": "t", "quantity": 1, "acknowledged": false}"""
        assertTrue(isSubscriptionPurchaseJson(subscription))
        assertTrue(isSubscriptionPurchaseJson(cancelled))
        assertFalse(isSubscriptionPurchaseJson(pack))
        assertFalse(isSubscriptionPurchaseJson(null))
        assertFalse(isSubscriptionPurchaseJson("not json"))
        assertFalse(isSubscriptionPurchaseJson("[]"))
    }

    @Test
    fun theUnlimitedLineHasAShortFormForPhones() {
        val (long, short) = unlimitedBalanceLines("Unlimited", "Renews on Dec 17, 2026", 1830)
        assertEquals("Unlimited plan · Renews on Dec 17, 2026 · 1830 purchased credits kept", long)
        assertEquals("Unlimited plan · 1830 credits kept", short)
        assertEquals("Unlimited plan · 1 credit kept", unlimitedBalanceLines("Unlimited", null, 1).second)
        // No purchased credits: just the plan
        assertEquals("Unlimited plan", unlimitedBalanceLines("Unlimited", "Renews on Dec 17, 2026", 0).second)
        assertEquals("" to "", unlimitedBalanceLines(null, null, null))
    }
}
