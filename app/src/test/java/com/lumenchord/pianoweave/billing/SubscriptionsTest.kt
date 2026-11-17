package com.lumenchord.pianoweave.billing

import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode
import com.android.billingclient.api.BillingClient.BillingResponseCode
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
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

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
    fun statusTextsByState() {
        val phrase = { _: Long -> "on Dec 17, 2026" }
        val active = ActiveSubscription(productId = "p", periodEndsAt = 1, autoRenewing = true, state = "ACTIVE")
        assertEquals("Renews on Dec 17, 2026", subscriptionStatusText(active, phrase))
        assertEquals("Ends on Dec 17, 2026", subscriptionStatusText(active.copy(state = "CANCELED", autoRenewing = false), phrase))
        assertEquals("Ends today at 21:14", subscriptionStatusText(active.copy(state = "CANCELED"), { "today at 21:14" }))
        assertEquals(SubscriptionState.GracePeriod, active.copy(state = "IN_GRACE_PERIOD").subscriptionState)
        assertEquals("Payment issue — update it in Google Play", subscriptionStatusText(active.copy(state = "IN_GRACE_PERIOD"), phrase))
        // Not renewing without a state from the server still reads as ending
        assertEquals(SubscriptionState.Canceled, active.copy(state = null, autoRenewing = false).subscriptionState)
        assertNull(subscriptionStatusText(active.copy(periodEndsAt = null), phrase))
        assertEquals("Won't renew", subscriptionStatusText(active.copy(state = "CANCELED", periodEndsAt = null), phrase))
    }

    private val warsaw = TimeZone.getTimeZone("Europe/Warsaw")

    /** Epoch seconds of a Warsaw wall-clock time. */
    private fun warsawAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(warsaw).apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis / 1000

    @Test
    fun periodEndsUseTheAppsLanguageNotThePhones() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("pl-PL"))
            val now = warsawAt(2026, 11, 10, 12, 0) * 1000
            val end = warsawAt(2026, 11, 17, 21, 14)
            assertEquals("on Nov 17, 2026", periodEndPhrase(end, now, is24Hour = true, zone = warsaw))
            // Within a day: today or tomorrow, with the time, still in English
            val evening = warsawAt(2026, 11, 10, 21, 14)
            assertEquals("today at 21:14", periodEndPhrase(evening, now, is24Hour = true, zone = warsaw))
            assertEquals("today at 9:14 PM", periodEndPhrase(evening, now, is24Hour = false, zone = warsaw))
            val nextMorning = warsawAt(2026, 11, 11, 9, 5)
            assertEquals("tomorrow at 9:05", periodEndPhrase(nextMorning, now, is24Hour = true, zone = warsaw))
            assertEquals("tomorrow at 9:05 AM", periodEndPhrase(nextMorning, now, is24Hour = false, zone = warsaw))
            // A test subscription five minutes away
            assertEquals("today at 12:05", periodEndPhrase(warsawAt(2026, 11, 10, 12, 5), now, is24Hour = true, zone = warsaw))
            // More than a day away, or already past: the date
            assertEquals("on Nov 11, 2026", periodEndPhrase(warsawAt(2026, 11, 11, 12, 30), now, is24Hour = true, zone = warsaw))
            assertEquals("on Nov 10, 2026", periodEndPhrase(warsawAt(2026, 11, 10, 11, 0), now, is24Hour = true, zone = warsaw))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun playAnswersWithoutAPurchaseAreNotErrors() {
        assertEquals(PurchaseUpdateAction.Handle, purchaseUpdateAction(BillingResponseCode.OK, hasPurchases = true))
        // A subscription cancelled in Play comes back as OK with no purchases
        assertEquals(PurchaseUpdateAction.Recover, purchaseUpdateAction(BillingResponseCode.OK, hasPurchases = false))
        assertEquals(PurchaseUpdateAction.Recover, purchaseUpdateAction(BillingResponseCode.ITEM_ALREADY_OWNED, hasPurchases = false))
        assertEquals(PurchaseUpdateAction.Ignore, purchaseUpdateAction(BillingResponseCode.USER_CANCELED, hasPurchases = false))
        for (code in listOf(BillingResponseCode.ERROR, BillingResponseCode.SERVICE_UNAVAILABLE, BillingResponseCode.BILLING_UNAVAILABLE, BillingResponseCode.DEVELOPER_ERROR)) {
            assertEquals("$code", PurchaseUpdateAction.Error, purchaseUpdateAction(code, hasPurchases = false))
        }
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
        // A cap from an older server is read past, not trusted
        assertEquals(20, info.welcomeMinutes)

        val old = gson.fromJson("""{"billing_provider": "google_play", "offers": []}""", ServerInfoResponse::class.java)
        assertTrue(usablePlans(old.subscriptions).isEmpty())
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
        assertNull(old.unlimitedHeavyUse)
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
        // An older server's cap is ignored; heavy use is unknown there
        assertNull(balance.unlimitedHeavyUse)
        assertNull(balance.subscription!!.minutesPerPeriod)
        assertEquals(SubscriptionState.GracePeriod, balance.subscription!!.subscriptionState)
    }

    @Test
    fun heavyUseOfAnUnlimitedPlan() {
        val balance = gson.fromJson(
            """{"user_id": "u1", "minutes": 0, "unlimited": true, "unlimited_heavy_use": true,
               "subscription": {"product_id": "sub_unlimited", "minutes_per_period": null, "level": 2}}""",
            UserBalanceResponse::class.java
        )
        assertEquals(true, balance.unlimitedHeavyUse)
        val calm = gson.fromJson("""{"user_id": "u1", "minutes": 0, "unlimited": true, "unlimited_heavy_use": false}""", UserBalanceResponse::class.java)
        assertEquals(false, calm.unlimitedHeavyUse)
    }

    @Test
    fun aScheduledChangeIsReadAndOlderServersHaveNone() {
        val balance = gson.fromJson(
            """{"user_id": "u1", "minutes": 0, "unlimited": true,
               "subscription": {"product_id": "sub_unlimited", "name": "Unlimited", "level": 2, "period_ends_at": 1797465600,
                                "auto_renewing": true, "state": "ACTIVE", "next_product_id": "sub_pro", "next_name": "Pro"}}""",
            UserBalanceResponse::class.java
        )
        val sub = balance.subscription!!
        assertEquals("sub_pro", sub.scheduledProductId)
        assertEquals("Pro", sub.nextName)
        val none = gson.fromJson(
            """{"user_id": "u1", "minutes": 0, "subscription": {"product_id": "sub_pro", "next_product_id": null, "next_name": null}}""",
            UserBalanceResponse::class.java
        )
        assertNull(none.subscription!!.scheduledProductId)
        val old = gson.fromJson("""{"user_id": "u1", "minutes": 0, "subscription": {"product_id": "sub_pro"}}""", UserBalanceResponse::class.java)
        assertNull(old.subscription!!.scheduledProductId)
        assertNull(old.subscription!!.nextName)
    }

    @Test
    fun aScheduledChangeReadsAsASwitchAtTheRenewal() {
        val unlimited = ActiveSubscription(
            productId = "sub_unlimited", name = "Unlimited", level = 2, periodEndsAt = 1, autoRenewing = true,
            state = "ACTIVE", nextProductId = "sub_pro", nextName = "Pro"
        )
        assertEquals("Switches to Pro today at 21:36", subscriptionStatusText(unlimited) { "today at 21:36" })
        assertEquals("Switches to Pro on Dec 17, 2026", subscriptionStatusText(unlimited) { "on Dec 17, 2026" })
        assertEquals("Switches to your new plan on Dec 17, 2026", subscriptionStatusText(unlimited.copy(nextName = null)) { "on Dec 17, 2026" })
        // A failed payment comes first
        assertEquals(
            "Payment issue — update it in Google Play",
            subscriptionStatusText(unlimited.copy(state = "IN_GRACE_PERIOD")) { "on Dec 17, 2026" }
        )
        // Nothing scheduled: as before
        assertEquals("Renews on Dec 17, 2026", subscriptionStatusText(unlimited.copy(nextProductId = null)) { "on Dec 17, 2026" })

        assertEquals(PlanAction.Scheduled, planAction(unlimited, "sub_pro", 1))
        assertEquals(PlanAction.Current, planAction(unlimited, "sub_unlimited", 2))
        assertEquals(PlanAction.Upgrade, planAction(unlimited, "sub_max", 3))
        assertNull(replacementModeFor(PlanAction.Scheduled))
        assertEquals("Starts today at 21:36", scheduledStartText(unlimited) { "today at 21:36" })
        assertEquals("Starts at the next renewal", scheduledStartText(unlimited.copy(periodEndsAt = null)) { "x" })
    }

    @Test
    fun scheduledChangeConfirmation() {
        assertEquals(
            "Pro starts at your next renewal (today at 21:36). Until then you keep Unlimited.",
            scheduledChangeMessage("Pro", "Unlimited", "today at 21:36")
        )
        assertEquals("Pro starts at your next renewal.", scheduledChangeMessage("Pro", null, null))
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
