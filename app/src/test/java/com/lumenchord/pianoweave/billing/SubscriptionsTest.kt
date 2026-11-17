package com.lumenchord.pianoweave.billing

import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lumenchord.pianoweave.api.ActiveSubscription
import com.lumenchord.pianoweave.api.ServerInfoResponse
import com.lumenchord.pianoweave.api.ServerSubscriptionPlan
import com.lumenchord.pianoweave.api.UserBalanceResponse
import com.lumenchord.pianoweave.api.VerifySubscriptionResponse
import com.lumenchord.pianoweave.api.httpError
import com.lumenchord.pianoweave.ui.screens.learn.notEnoughCreditsText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SubscriptionsTest {

    // --- Account binding ---

    @Test
    fun accountTagIsLowercaseHexSha256OfTheUserId() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", accountTag(""))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", accountTag("abc"))
        val tag = accountTag("google:1234567890")
        assertEquals(64, tag.length)
        assertTrue(tag.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun accountTagHashesUtf8() {
        // "zażółć" in UTF-8, not the platform charset
        assertEquals(accountTag(String("zażółć".toByteArray(Charsets.UTF_8), Charsets.UTF_8)), accountTag("zażółć"))
        assertEquals(64, accountTag("zażółć").length)
    }

    // --- Offer selection ---

    private val offers = listOf(
        PlanOffer("pro-yearly", null, "t-yearly"),
        PlanOffer("pro-monthly", "intro-50", "t-intro"),
        PlanOffer("pro-monthly", null, "t-monthly"),
        PlanOffer("pro-monthly", "", "t-monthly-empty-id")
    )

    @Test
    fun picksThePlainOfferOfTheNamedBasePlan() {
        assertEquals("t-monthly", pickBasePlanOffer(offers, "pro-monthly")?.offerToken)
        assertEquals("t-yearly", pickBasePlanOffer(offers, "pro-yearly")?.offerToken)
    }

    @Test
    fun neverPicksAPromotionalOffer() {
        assertNull(pickBasePlanOffer(listOf(PlanOffer("pro-monthly", "intro-50", "t")), "pro-monthly"))
    }

    @Test
    fun withoutABasePlanPicksTheFirstPlainOffer() {
        assertEquals("t-yearly", pickBasePlanOffer(offers, null)?.offerToken)
        assertEquals("t-yearly", pickBasePlanOffer(offers, "")?.offerToken)
    }

    @Test
    fun anUnknownBasePlanHasNoOffer() {
        assertNull(pickBasePlanOffer(offers, "unlimited-monthly"))
        assertNull(pickBasePlanOffer(emptyList(), null))
    }

    // --- Plan changes ---

    private val pro = ActiveSubscription(productId = "sub_pro", level = 1)

    @Test
    fun planActionsFollowTheLevels() {
        assertEquals(PlanAction.Subscribe, planAction(null, "sub_pro", 1))
        assertEquals(PlanAction.Subscribe, planAction(ActiveSubscription(productId = null), "sub_pro", 1))
        assertEquals(PlanAction.Current, planAction(pro, "sub_pro", 1))
        assertEquals(PlanAction.Upgrade, planAction(pro, "sub_unlimited", 2))
        val unlimited = ActiveSubscription(productId = "sub_unlimited", level = 2)
        assertEquals(PlanAction.Switch, planAction(unlimited, "sub_pro", 1))
    }

    @Test
    fun upgradesChargeTheDifferenceNowAndDowngradesWaitForTheRenewal() {
        assertEquals(ReplacementMode.CHARGE_PRORATED_PRICE, replacementModeFor(PlanAction.Upgrade))
        assertEquals(ReplacementMode.DEFERRED, replacementModeFor(PlanAction.Switch))
        assertNull(replacementModeFor(PlanAction.Subscribe))
        assertNull(replacementModeFor(PlanAction.Current))
    }

    @Test
    fun usablePlansDropThoseWithoutAProductAndPutUnlimitedLast() {
        val plans = usablePlans(
            listOf(
                ServerSubscriptionPlan(productId = "sub_unlimited", unlimited = true, level = 2),
                ServerSubscriptionPlan(productId = null, level = 1),
                ServerSubscriptionPlan(productId = "sub_pro", minutesPerPeriod = 100, unlimited = false, level = 1)
            )
        )
        assertEquals(listOf("sub_pro", "sub_unlimited"), plans.map { it.productId })
        assertTrue(usablePlans(null).isEmpty())
    }

    // --- Texts ---

    @Test
    fun billingPeriodsInWords() {
        assertEquals("month", billingPeriodText("P1M"))
        assertEquals("3 months", billingPeriodText("P3M"))
        assertEquals("year", billingPeriodText("P1Y"))
        assertEquals("week", billingPeriodText("P1W"))
        assertEquals("4 weeks", billingPeriodText("P4W"))
        assertNull(billingPeriodText(null))
        assertNull(billingPeriodText("1M"))
        assertNull(billingPeriodText("P0M"))
    }

    @Test
    fun planPriceIsPerPeriod() {
        val offer = PlanOffer("pro-monthly", null, "t", 19_990_000, "PLN", "19,99 zł", "P1M")
        assertEquals("19.99 PLN / month", planPriceText(offer, Locale.US))
        assertEquals("19,99 PLN / month", planPriceText(offer, Locale.GERMANY))
        // Without a period, the price alone; with an unknown currency, Play's own text
        assertEquals("19.99 PLN", planPriceText(offer.copy(billingPeriod = null), Locale.US))
        assertEquals("19,99 zł / month", planPriceText(offer.copy(currencyCode = "XXQ"), Locale.US))
        assertNull(planPriceText(null))
        assertNull(planPriceText(PlanOffer("b", null, "t")))
    }

    @Test
    fun formatMoneyUsesTheCurrencysDigits() {
        assertEquals("1,000 JPY", formatMoney(1_000_000_000, "JPY", Locale.US))
        assertEquals("4.99 USD", formatMoney(4_990_000, "USD", Locale.US))
        assertNull(formatMoney(1, "nope", Locale.US))
    }

    @Test
    fun allowanceTexts() {
        assertEquals("100 Credits a month", planAllowanceText(100, false, "P1M"))
        assertEquals("100 Credits every 3 months", planAllowanceText(100, false, "P3M"))
        assertEquals("1 Credit a week", planAllowanceText(1, false, "P1W"))
        assertEquals("100 Credits each period", planAllowanceText(100, false, null))
        assertEquals("Unlimited transcriptions", planAllowanceText(null, true, "P1M"))
        assertEquals("Unlimited transcriptions", planAllowanceText(null, false, "P1M"))
    }

    @Test
    fun fairUseNote() {
        assertEquals("Fair use: up to 300 minutes of transcription a day", fairUseText(300))
        assertNull(fairUseText(null))
        assertNull(fairUseText(0))
    }

    @Test
    fun statusTextsByState() {
        val date = { _: Long -> "17 Dec 2026" }
        val active = ActiveSubscription(productId = "p", periodEndsAt = 1, autoRenewing = true, state = "ACTIVE")
        assertEquals("Renews on 17 Dec 2026", subscriptionStatusText(active, date))
        assertEquals("Ends on 17 Dec 2026", subscriptionStatusText(active.copy(state = "CANCELED", autoRenewing = false), date))
        assertEquals(SubscriptionState.GracePeriod, active.copy(state = "IN_GRACE_PERIOD").subscriptionState)
        assertEquals("Payment issue — update it in Google Play", subscriptionStatusText(active.copy(state = "IN_GRACE_PERIOD"), date))
        // Not renewing without a state from the server still reads as ending
        assertEquals(SubscriptionState.Canceled, active.copy(state = null, autoRenewing = false).subscriptionState)
        assertNull(subscriptionStatusText(active.copy(periodEndsAt = null), date))
        assertEquals("Won't renew", subscriptionStatusText(active.copy(state = "CANCELED", periodEndsAt = null), date))
    }

    @Test
    fun manageLinkNamesTheProductAndPackage() {
        assertEquals(
            "https://play.google.com/store/account/subscriptions?sku=sub_pro&package=com.lumenchord.pianoweave",
            manageSubscriptionUrl("sub_pro", "com.lumenchord.pianoweave")
        )
        assertEquals(
            "https://play.google.com/store/account/subscriptions?package=com.lumenchord.pianoweave",
            manageSubscriptionUrl(null, "com.lumenchord.pianoweave")
        )
    }

    @Test
    fun fairUseRefusalIsKind() {
        val body = """{"detail": {"message": "x", "error_code": "fair_use_limit", "required_minutes": 5, "retry_after": 7200}}"""
        assertEquals(7200L, fairUseRetryAfter(httpError(429, body)))
        assertEquals(60L, fairUseRetryAfter(httpError(429, body, "Retry-After", "60")))
        assertNull(fairUseRetryAfter(httpError(429, "")))
        val text = fairUseMessage(300, 7200)
        assertTrue(text, text.startsWith("Unlimited includes up to 300 minutes of transcription a day"))
        assertTrue(text, text.endsWith("You can start this one in about 2 hours."))
        assertTrue(fairUseMessage(null, null).endsWith("Try again a little later."))
        assertEquals("a minute", approximateWait(30))
        assertEquals("about 20 minutes", approximateWait(1_150))
        assertEquals("about an hour", approximateWait(3_700))
    }

    // --- Verification answers ---

    @Test
    fun verificationOutcomes() {
        for (code in listOf(401, 408, 429, 500, 502, 503)) assertEquals("$code", VerificationOutcome.Retry, verificationOutcome(code, null))
        assertEquals(VerificationOutcome.Drop, verificationOutcome(400, null))
        assertEquals(VerificationOutcome.Drop, verificationOutcome(409, null))
        assertEquals(VerificationOutcome.Drop, verificationOutcome(404, null))
        assertEquals(VerificationOutcome.OwnedByAnotherAccount, verificationOutcome(409, SUBSCRIPTION_OWNED))
    }

    @Test
    fun verificationOutcomeReadsTheErrorCode() {
        val owned = """{"detail": {"message": "This subscription belongs to another account", "error_code": "subscription_owned"}}"""
        assertEquals(VerificationOutcome.OwnedByAnotherAccount, verificationOutcome(httpError(409, owned)))
        assertEquals(VerificationOutcome.Drop, verificationOutcome(httpError(400, """{"detail": "Unknown subscription"}""")))
        assertEquals(VerificationOutcome.Drop, verificationOutcome(httpError(409, """{"detail": "Google Play billing is disabled"}""")))
        assertEquals(VerificationOutcome.Retry, verificationOutcome(httpError(429, "", "Retry-After", "30")))
        assertFalse(isVerificationRetryable(httpError(409, owned)))
    }

    // --- Parsing ---

    private val gson = Gson()

    @Test
    fun serverInfoReadsPlansAndOlderServersHaveNone() {
        val info = gson.fromJson(
            """{"billing_provider": "google_play", "offers": [], "welcome_minutes": 20, "unlimited_daily_minutes": 300,
               "subscriptions": [
                 {"product_id": "sub_pro", "base_plan": "pro-monthly", "name": "Pro", "minutes_per_period": 100, "unlimited": false, "level": 1},
                 {"product_id": "sub_unlimited", "base_plan": null, "name": "Unlimited", "minutes_per_period": null, "unlimited": true, "level": 2}
               ]}""",
            ServerInfoResponse::class.java
        )
        val plans = usablePlans(info.subscriptions)
        assertEquals(2, plans.size)
        assertEquals("pro-monthly", plans[0].basePlan)
        assertEquals(100, plans[0].minutesPerPeriod)
        assertFalse(plans[0].isUnlimited)
        assertTrue(plans[1].isUnlimited)
        assertNull(plans[1].basePlan)
        assertEquals(20, info.welcomeMinutes)
        assertEquals(300, info.unlimitedDailyMinutes)

        val old = gson.fromJson("""{"billing_provider": "google_play", "offers": []}""", ServerInfoResponse::class.java)
        assertTrue(usablePlans(old.subscriptions).isEmpty())
        assertNull(old.unlimitedDailyMinutes)
        assertNull(old.welcomeMinutes)
    }

    @Test
    fun balanceReadsTheSubscriptionAndOlderServersHaveNone() {
        val balance = gson.fromJson(
            """{"user_id": "u1", "minutes": 124, "monthly_minutes": 100, "paid_minutes": 24, "unlimited": false,
               "unlimited_minutes_left": null,
               "subscription": {"product_id": "sub_pro", "name": "Pro", "minutes_per_period": 100, "level": 1,
                                "period_ends_at": 1797465600, "auto_renewing": false, "state": "CANCELED"}}""",
            UserBalanceResponse::class.java
        )
        val sub = balance.subscription!!
        assertEquals("sub_pro", sub.productId)
        assertEquals(1797465600L, sub.periodEndsAt)
        assertEquals(SubscriptionState.Canceled, sub.subscriptionState)
        assertEquals(false, balance.unlimited)

        val old = gson.fromJson("""{"user_id": "u1", "minutes": 5}""", UserBalanceResponse::class.java)
        assertNull(old.subscription)
        assertNull(old.unlimited)
        assertNull(old.unlimitedMinutesLeft)
    }

    @Test
    fun unlimitedBalance() {
        val balance = gson.fromJson(
            """{"user_id": "u1", "minutes": 3, "unlimited": true, "unlimited_minutes_left": 250,
               "subscription": {"product_id": "sub_unlimited", "name": "Unlimited", "minutes_per_period": null, "level": 2,
                                "period_ends_at": 1797465600, "auto_renewing": true, "state": "IN_GRACE_PERIOD"}}""",
            UserBalanceResponse::class.java
        )
        assertEquals(true, balance.unlimited)
        assertEquals(250, balance.unlimitedMinutesLeft)
        assertNull(balance.subscription!!.minutesPerPeriod)
        assertEquals(SubscriptionState.GracePeriod, balance.subscription!!.subscriptionState)
    }

    @Test
    fun verifyResponseCarriesTheBalance() {
        val response = gson.fromJson(
            """{"user_id": "u1", "status": "started", "granted_minutes": 100, "acknowledged": true,
               "balance": {"user_id": "u1", "minutes": 100, "subscription": {"product_id": "sub_pro", "level": 1}}}""",
            VerifySubscriptionResponse::class.java
        )
        assertEquals("started", response.status)
        assertEquals(100, response.balance!!.minutes)
        assertEquals("sub_pro", response.balance!!.subscription!!.productId)
    }

    @Test
    fun pendingPurchasesStoredBeforeSubscriptionsAreProducts() {
        val type = object : TypeToken<List<PendingPurchase>>() {}.type
        val old: List<PendingPurchase> = gson.fromJson(
            """[{"productId": "credits_100", "purchaseToken": "t", "serverUrl": "https://s/", "userId": "u", "timestamp": 1}]""",
            type
        )
        assertFalse(old.single().isSubscription)
        val stored = gson.toJson(listOf(PendingPurchase("sub_pro", "t2", "https://s/", "u", type = PendingPurchase.TYPE_SUBSCRIPTION)))
        val back: List<PendingPurchase> = gson.fromJson(stored, type)
        assertTrue(back.single().isSubscription)
    }

    @Test
    fun notEnoughCreditsMentionsPlansOnlyWhenThereAreSome() {
        assertTrue(notEnoughCreditsText(5, 1).endsWith("Would you like to buy more credits in the shop?"))
        val withPlans = notEnoughCreditsText(5, 1, plansAvailable = true)
        assertTrue(withPlans, withPlans.endsWith("The shop has credit packs, and plans that give you credits every period."))
    }
}
