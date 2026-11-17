package com.lumenchord.pianoweave.ui.screens.learn

import androidx.compose.runtime.Immutable
import com.lumenchord.pianoweave.billing.PlanAction
import com.lumenchord.pianoweave.billing.SubscriptionState

/**
 * What the shop shows, as plain data: [ShopDialog] builds it from the server's answers and Play's prices, and
 * [ShopPanel] draws it, so the panel can be drawn (and tested) without Play Billing.
 */
@Immutable
data class ShopUiState(
    val balance: ShopBalance,
    /** Lowest level first; the current plan is always among them. */
    val plans: List<ShopPlan> = emptyList(),
    val packs: List<ShopPack> = emptyList(),
    /** Prices are still loading from Play. */
    val isLoading: Boolean = false,
    /** Play Billing can't be used (no Play Store, not signed in to it): nothing can be bought. */
    val unavailableMessage: String? = null,
    /** A purchase of this product is being confirmed with the server; everything else waits. */
    val busyProductId: String? = null,
    /** The free credits a plan's replace (0: the server gives none). */
    val freeMinutes: Int = 0,
    /** The running plan's product, for Google Play's subscription page; null without a plan. */
    val subscribedProductId: String? = null,
    /** Why switching plans isn't possible from here, e.g. the plan was bought with another Play account. */
    val switchNote: String? = null
) {
    val isEmpty: Boolean get() = !isLoading && plans.isEmpty() && packs.isEmpty()
}

@Immutable
data class ShopBalance(
    val total: Int?,
    /** Plan or free credits left of [total]; null when the server doesn't split it. */
    val periodCredits: Int? = null,
    /** Purchased credits left of [total]; negative while a refund is owed. */
    val paidCredits: Int? = null,
    /** The plan the period credits come from; null for free credits. */
    val planName: String? = null,
    /** Credits a plan gives each period; for the explanation. */
    val planMinutes: Int? = null,
    val unlimited: Boolean = false,
    val unlimitedMinutesLeft: Int? = null,
    val unlimitedDailyMinutes: Int? = null,
    val developer: Boolean = false,
    val freeMinutes: Int = 0,
    /** "every 30 days", from the server's period. */
    val freeEvery: String? = null,
    /** "They reset in 3 days, 4 hrs.", for the explanation of free credits. */
    val renewal: String = "",
    /** Servers from before monthly minutes: "Free 30 credits renewal in: …". */
    val legacyRenewal: String? = null
)

@Immutable
data class ShopPlan(
    val productId: String,
    val name: String,
    /** "100 Credits a month" or "Unlimited transcriptions". */
    val allowance: String,
    /** "19.99 PLN / month"; null while Play hasn't given a price. */
    val price: String?,
    val action: PlanAction,
    val isUnlimited: Boolean = false,
    /** The fair-use note under an unlimited plan. */
    val note: String? = null,
    /** For the current plan: "Renews on …", "Ends on …" or the payment issue. */
    val status: String? = null,
    val state: SubscriptionState? = null,
    /** Play can sell it now (details and an offer loaded, nothing else in progress). */
    val canBuy: Boolean = false
)

@Immutable
data class ShopPack(
    val productId: String,
    val credits: Int,
    /** Null while Play hasn't given a price. */
    val price: String?,
    val canBuy: Boolean = false
)
