package com.lumenchord.pianoweave.billing

import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode
import com.lumenchord.pianoweave.api.ActiveSubscription
import com.lumenchord.pianoweave.api.ServerErrors
import com.lumenchord.pianoweave.api.ServerSubscriptionPlan
import retrofit2.HttpException
import java.security.MessageDigest
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.Currency
import java.util.Locale

/**
 * Subscriptions: the pure rules the shop and the billing manager share, kept apart from Play Billing so they can
 * be tested. The plans themselves always come from the server's server-info, never from the app.
 */

/**
 * The obfuscated account id a subscription is bought with: lowercase hex SHA-256 of the server's user id. The
 * server refuses a subscription whose id belongs to another account, so one Play purchase can't serve two.
 */
fun accountTag(userId: String): String =
    MessageDigest.getInstance("SHA-256").digest(userId.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/** One of a subscription product's offers, as Play lists it, with the price of its recurring phase. */
data class PlanOffer(
    val basePlanId: String,
    /** Null (or empty) for the base plan itself; set for a promotional offer on top of it. */
    val offerId: String?,
    val offerToken: String,
    val priceMicros: Long? = null,
    val currencyCode: String? = null,
    val formattedPrice: String? = null,
    /** ISO 8601, e.g. "P1M". */
    val billingPeriod: String? = null
)

/**
 * The offer to buy for a plan: the plain base-plan offer (no offer id) of [basePlan], or of the first base plan
 * when the server names none. Promotional offers are left out, so the price shown is the one that renews.
 */
fun pickBasePlanOffer(offers: List<PlanOffer>, basePlan: String?): PlanOffer? {
    val basePlanOffers = offers.filter { it.offerId.isNullOrEmpty() }
    return if (basePlan.isNullOrBlank()) basePlanOffers.firstOrNull()
    else basePlanOffers.firstOrNull { it.basePlanId == basePlan }
}

/**
 * What choosing a plan does, given the plan the account has now. [Scheduled] is the plan a deferred change moves
 * to at the renewal: nothing more to do there.
 */
enum class PlanAction { Subscribe, Current, Upgrade, Switch, Scheduled }

fun planAction(current: ActiveSubscription?, productId: String, level: Int): PlanAction = when {
    current?.productId == null -> PlanAction.Subscribe
    current.productId == productId -> PlanAction.Current
    // Only while the plan renews into it: a cancelled plan's scheduled change never starts
    current.scheduledProductId == productId && current.subscriptionState != SubscriptionState.Canceled -> PlanAction.Scheduled
    level > (current.level ?: 0) -> PlanAction.Upgrade
    else -> PlanAction.Switch
}

/**
 * How Play replaces the current plan: an upgrade starts now and charges the difference for the rest of the
 * period; a move to a lower plan waits for the renewal, so nothing already paid for is taken away.
 */
fun replacementModeFor(action: PlanAction): Int? = when (action) {
    PlanAction.Upgrade -> ReplacementMode.CHARGE_PRORATED_PRICE
    PlanAction.Switch -> ReplacementMode.DEFERRED
    PlanAction.Subscribe, PlanAction.Current, PlanAction.Scheduled -> null
}

/** The server's plans worth showing: with a product id, lowest level first. */
fun usablePlans(plans: List<ServerSubscriptionPlan>?): List<ServerSubscriptionPlan> =
    plans.orEmpty()
        .filter { !it.productId.isNullOrBlank() }
        .sortedWith(compareBy({ it.isUnlimited }, { it.level ?: Int.MAX_VALUE }))

/** An unlimited plan has no minutes per period. */
val ServerSubscriptionPlan.isUnlimited: Boolean get() = unlimited == true || minutesPerPeriod == null

/** "month", "3 months", "year", "week" from an ISO 8601 billing period ("P1M"); null if it can't be read. */
fun billingPeriodText(period: String?): String? {
    val match = Regex("^P(\\d+)([DWMY])$").matchEntire(period?.trim()?.uppercase() ?: return null) ?: return null
    val count = match.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
    val unit = when (match.groupValues[2]) {
        "D" -> "day"
        "W" -> "week"
        "M" -> "month"
        else -> "year"
    }
    return if (count == 1) unit else "$count ${unit}s"
}

/**
 * A price as "16.99 PLN": the amount in [locale]'s number format, then the currency code. Built from Play's amount
 * and currency rather than its own text, which can mix decimal commas and dots between products. Null if the
 * currency is unknown, so the caller falls back to Play's text.
 */
fun formatMoney(amountMicros: Long, currencyCode: String, locale: Locale = Locale.getDefault()): String? = try {
    val currency = Currency.getInstance(currencyCode)
    val digits = currency.defaultFractionDigits.coerceAtLeast(0)
    val amount = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }.format(amountMicros / 1_000_000.0)
    "$amount ${currency.currencyCode}"
} catch (_: IllegalArgumentException) {
    null
}

/** "19.99 PLN / month"; the price alone when the period is unknown; null without a price. */
fun planPriceText(offer: PlanOffer?, locale: Locale = Locale.getDefault()): String? {
    offer ?: return null
    val price = offer.priceMicros?.let { micros -> offer.currencyCode?.let { formatMoney(micros, it, locale) } }
        ?: offer.formattedPrice?.takeIf { it.isNotBlank() }
        ?: return null
    val period = billingPeriodText(offer.billingPeriod)
    return if (period != null) "$price / $period" else price
}

/** "100 Credits a month", "100 Credits each period" when Play's period isn't known, or "Unlimited". */
fun planAllowanceText(minutesPerPeriod: Int?, unlimited: Boolean, period: String?): String {
    if (unlimited || minutesPerPeriod == null) return "Unlimited transcriptions"
    val credits = if (minutesPerPeriod == 1) "Credit" else "Credits"
    val every = billingPeriodText(period)?.let { if (it.first().isDigit()) "every $it" else "a $it" } ?: "each period"
    return "$minutesPerPeriod $credits $every"
}

/** How a subscription stands, for the shop. */
enum class SubscriptionState { Active, Canceled, GracePeriod }

val ActiveSubscription.subscriptionState: SubscriptionState
    get() = when (state?.uppercase()) {
        "CANCELED", "CANCELLED" -> SubscriptionState.Canceled
        "IN_GRACE_PERIOD" -> SubscriptionState.GracePeriod
        else -> if (autoRenewing == false) SubscriptionState.Canceled else SubscriptionState.Active
    }

/**
 * The app's language for dates and times. The app ships English only, so a phone set to another language must
 * not get "17 lis 2026" between English words.
 */
val APP_LOCALE: Locale = Locale.ENGLISH

/**
 * When a period ends, to follow "Renews" or "Ends": "today at 21:14" or "tomorrow at 9:05 PM" within a day (test
 * subscriptions renew every few minutes, real ones may be weekly), otherwise "on Dec 17, 2026". The time follows
 * the device's 12/24-hour choice ([is24Hour]); the words and month are in [locale].
 */
fun periodEndPhrase(
    endSeconds: Long,
    nowMillis: Long,
    is24Hour: Boolean,
    zone: TimeZone = TimeZone.getDefault(),
    locale: Locale = APP_LOCALE
): String {
    val endMillis = endSeconds * 1000
    val untilEnd = endMillis - nowMillis
    if (untilEnd in 0 until 86_400_000L) {
        val now = Calendar.getInstance(zone, locale).apply { timeInMillis = nowMillis }
        val end = Calendar.getInstance(zone, locale).apply { timeInMillis = endMillis }
        val sameDay = now.get(Calendar.YEAR) == end.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == end.get(Calendar.DAY_OF_YEAR)
        val time = SimpleDateFormat(if (is24Hour) "H:mm" else "h:mm a", locale).apply { timeZone = zone }
            .format(Date(endMillis))
        return "${if (sameDay) "today" else "tomorrow"} at $time"
    }
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).apply { timeZone = zone }.format(Date(endMillis))
    return "on $date"
}

/** The plan a deferred change moves to at the renewal; null when nothing is scheduled. */
val ActiveSubscription.scheduledProductId: String? get() = nextProductId?.takeIf { it.isNotBlank() && it != productId }

/** What the running plan's card offers. */
enum class CurrentPlanAction {
    /** Google Play's page for it: manage or cancel. */
    Manage,
    /** Cancelled with nothing scheduled: Play's page resubscribes it. */
    Resubscribe,
    /** A payment failed: Play's page fixes it. */
    FixPayment
}

fun currentPlanAction(sub: ActiveSubscription): CurrentPlanAction = when {
    sub.subscriptionState == SubscriptionState.GracePeriod -> CurrentPlanAction.FixPayment
    // After a scheduled change Play's page only offers the coming plan, and Play refuses to buy the running plan
    // as a replacement of itself: the card can only manage it, and says the plan can be upgraded after the switch
    sub.scheduledProductId != null -> CurrentPlanAction.Manage
    sub.subscriptionState == SubscriptionState.Canceled -> CurrentPlanAction.Resubscribe
    else -> CurrentPlanAction.Manage
}

/**
 * "Renews on Dec 17, 2026", "Ends today at 21:14", "Switches to Pro on Dec 17, 2026" (a change waiting for the
 * renewal), or the grace-period request; null when there's nothing to say.
 */
fun subscriptionStatusText(sub: ActiveSubscription, endPhrase: (Long) -> String): String? {
    val end = sub.periodEndsAt?.takeIf { it > 0 }?.let(endPhrase)
    if (sub.scheduledProductId != null && sub.subscriptionState == SubscriptionState.Active && end != null) {
        return "Switches to ${sub.nextName?.takeIf { it.isNotBlank() } ?: "your new plan"} $end"
    }
    return when (sub.subscriptionState) {
        SubscriptionState.GracePeriod -> "Payment issue — update it in Google Play"
        SubscriptionState.Canceled -> end?.let { "Ends $it" } ?: "Won't renew"
        SubscriptionState.Active -> end?.let { "Renews $it" }
    }
}

/** Under the current plan while it renews into a scheduled lower plan. */
const val SCHEDULED_CHANGE_HINT = "Changed your mind? You can upgrade again after the switch."

/** The scheduled plan's own line: "Starts today at 21:36", "Starts on Dec 17, 2026". */
fun scheduledStartText(sub: ActiveSubscription, endPhrase: (Long) -> String): String =
    sub.periodEndsAt?.takeIf { it > 0 }?.let { "Starts ${endPhrase(it)}" } ?: "Starts at the next renewal"

/** The confirmation after a move to a lower plan, which Play makes at the renewal. */
fun scheduledChangeMessage(newPlan: String, currentPlan: String?, endPhrase: String?): String {
    val start = endPhrase?.let { "at your next renewal ($it)" } ?: "at your next renewal"
    val keep = currentPlan?.let { " Until then you keep $it." } ?: ""
    return "$newPlan starts $start.$keep"
}

/** Play's subscription page for [productId] of this app; the user cancels, resubscribes or fixes payment there. */
fun manageSubscriptionUrl(productId: String?, packageName: String): String =
    if (productId.isNullOrBlank()) "https://play.google.com/store/account/subscriptions?package=$packageName"
    else "https://play.google.com/store/account/subscriptions?sku=$productId&package=$packageName"

/** The server refused a subscription because another account of the app bought it. */
const val SUBSCRIPTION_OWNED = "subscription_owned"

/** The server's answer to a verification that didn't succeed: what to do with the stored purchase. */
enum class VerificationOutcome {
    /** No answer about the purchase yet: keep it and try again later. */
    Retry,
    /** A real answer (unknown product or token, ended): forget it. */
    Drop,
    /** Bought by another account of the app: forget it and tell the user. */
    OwnedByAnotherAccount
}

/** The server's words for a subscription token Google doesn't know (expired or never valid). */
const val UNKNOWN_SUBSCRIPTION_PURCHASE = "Unknown subscription purchase"

/**
 * What to do with a subscription the server didn't record. Only two answers are final: another account owns it
 * (409 subscription_owned), or Google doesn't know the token (400 "Unknown subscription purchase"). Everything
 * else, a 409 "billing is disabled" (also briefly without the auth header), a 400 for a plan missing from the
 * server's settings for a moment, a 5xx, is kept and tried again: dropping a paid purchase the server never recorded
 * would get it refunded by Google after three days.
 */
fun verificationOutcome(code: Int, errorCode: String?, detail: String? = null): VerificationOutcome = when {
    code == 409 && errorCode == SUBSCRIPTION_OWNED -> VerificationOutcome.OwnedByAnotherAccount
    code == 400 && detail == UNKNOWN_SUBSCRIPTION_PURCHASE -> VerificationOutcome.Drop
    else -> VerificationOutcome.Retry
}

fun verificationOutcome(e: HttpException): VerificationOutcome {
    val body = ServerErrors.errorBody(e)
    return verificationOutcome(e.code(), ServerErrors.errorCode(body), ServerErrors.detailMessage(body))
}

/**
 * The server's final refusal of a credit pack: a product it doesn't sell. Not "Purchase is not completed": that is a
 * payment still pending at Google, credited once it completes, so it is kept and tried again.
 */
val FINAL_PACK_REFUSALS = setOf("Unknown product")

/**
 * What to do with a credit pack the server didn't credit: forgotten (and never sent again) only after a 400 with
 * one of [FINAL_PACK_REFUSALS]; any other answer keeps it for another try, for the same reason as subscriptions.
 */
fun packVerificationOutcome(code: Int, detail: String?): VerificationOutcome =
    if (code == 400 && detail in FINAL_PACK_REFUSALS) VerificationOutcome.Drop else VerificationOutcome.Retry

fun packVerificationOutcome(e: HttpException): VerificationOutcome =
    packVerificationOutcome(e.code(), ServerErrors.detailMessage(ServerErrors.errorBody(e)))

/** What a refused subscription says to the user. */
const val SUBSCRIPTION_OWNED_MESSAGE =
    "This Google Play subscription belongs to another account of Piano Weave. Sign in with that account to use it, " +
        "or manage the subscription in Google Play."

/** "Videos up to 120 min" on a plan that allows longer videos than the server's general limit; else null. */
fun longerVideosText(planMinutes: Int?, generalMinutes: Float?): String? {
    val minutes = planMinutes?.takeIf { it > 0 } ?: return null
    if (generalMinutes != null && minutes <= generalMinutes) return null
    return "Videos up to $minutes min"
}
