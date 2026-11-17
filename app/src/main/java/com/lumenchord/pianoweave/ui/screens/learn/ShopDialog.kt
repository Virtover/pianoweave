package com.lumenchord.pianoweave.ui.screens.learn

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.lumenchord.pianoweave.billing.GooglePlayBillingManager
import com.lumenchord.pianoweave.billing.CurrentPlanAction
import com.lumenchord.pianoweave.billing.PlanAction
import com.lumenchord.pianoweave.billing.currentPlanAction
import com.lumenchord.pianoweave.billing.SCHEDULED_CHANGE_HINT
import com.lumenchord.pianoweave.billing.scheduledProductId
import com.lumenchord.pianoweave.billing.SubscriptionState
import com.lumenchord.pianoweave.billing.longerVideosText
import com.lumenchord.pianoweave.billing.accountTag
import com.lumenchord.pianoweave.billing.freeCreditsPeriodText
import com.lumenchord.pianoweave.billing.isUnlimited
import com.lumenchord.pianoweave.billing.manageSubscriptionUrl
import com.lumenchord.pianoweave.billing.packPrice
import com.lumenchord.pianoweave.billing.periodEndPhrase
import com.lumenchord.pianoweave.billing.pickBasePlanOffer
import com.lumenchord.pianoweave.billing.planAction
import com.lumenchord.pianoweave.billing.planAllowanceText
import com.lumenchord.pianoweave.billing.planOffers
import com.lumenchord.pianoweave.billing.planPriceText
import com.lumenchord.pianoweave.billing.replacementModeFor
import com.lumenchord.pianoweave.billing.subscriptionState
import com.lumenchord.pianoweave.billing.scheduledChangeMessage
import com.lumenchord.pianoweave.billing.scheduledStartText
import com.lumenchord.pianoweave.billing.subscriptionStatusText
import com.lumenchord.pianoweave.ui.components.DialogHeader
import com.lumenchord.pianoweave.ui.components.DialogSurface
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.theme.PillShape
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import com.lumenchord.pianoweave.ui.viewmodel.PackVerification
import com.lumenchord.pianoweave.ui.viewmodel.SubscriptionVerification
import com.lumenchord.pianoweave.util.findActivity
import com.lumenchord.pianoweave.util.openUrl
import kotlinx.coroutines.launch

/** A message the shop shows over itself: a failed purchase, or what happens next with one. */
internal data class ShopMessage(val title: String, val text: String)

/** Keeps an undismissed message through a rotation; a dismissed one (null) stays dismissed. */
internal val ShopMessageSaver = listSaver<ShopMessage?, String>(
    save = { message -> if (message == null) emptyList() else listOf(message.title, message.text) },
    restore = { saved -> if (saved.size == 2) ShopMessage(saved[0], saved[1]) else null }
)

/** The plan change started in Play, kept through a rotation while Play's sheet is open. */
private val LaunchedPlanSaver = listSaver<Pair<PlanAction, String>?, String>(
    save = { plan -> if (plan == null) emptyList() else listOf(plan.first.name, plan.second) },
    restore = { saved ->
        if (saved.size == 2) PlanAction.entries.firstOrNull { it.name == saved[0] }?.let { it to saved[1] } else null
    }
)

/** After Play's own error in a purchase flow. */
private val PURCHASE_NOT_COMPLETED = ShopMessage(
    "Purchase not completed",
    "Google Play couldn't complete the purchase. You can try again later."
)

/** Shown when the server couldn't be reached to confirm a purchase: it is kept and confirmed later, never "failed". */
private val SUBSCRIPTION_LATER = ShopMessage(
    "Almost there",
    "Your subscription is paid and saved. We'll confirm it as soon as the server is back; there's nothing more to do."
)
private val PACK_LATER = ShopMessage(
    "Almost there",
    "Your purchase is paid and saved. We'll add the credits as soon as the server is back; there's nothing more to do."
)

/**
 * The shop with Play Billing behind it: loads prices for the server's plans and packs, buys them, and hands each
 * purchase to the server. What it shows is drawn by [ShopPanel] from a [ShopUiState].
 */
@Composable
internal fun ShopDialog(
    viewModel: PianoWeaveViewModel,
    context: Context,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val activity = LocalContext.current.findActivity() ?: context.findActivity()
    var productDetailsMap by remember { mutableStateOf<Map<String, ProductDetails>>(emptyMap()) }
    var isLoadingProducts by remember { mutableStateOf(true) }
    var isBillingConnected by remember { mutableStateOf(false) }
    var isBillingUnavailable by remember { mutableStateOf(false) }
    // Saveable: a rotation keeps a message not yet dismissed, and never brings back a dismissed one
    var message by rememberSaveable(stateSaver = ShopMessageSaver) { mutableStateOf(null) }
    // From Google confirming a purchase until the server has credited it
    var verifyingProductId by remember { mutableStateOf<String?>(null) }
    // The subscriptions this device's Play account holds: null until Play has answered
    var ownedSubscriptions by remember { mutableStateOf<List<Purchase>?>(null) }
    // The last plan change started here, to explain a switch that waits for the renewal
    var launchedPlan by rememberSaveable(stateSaver = LaunchedPlanSaver) { mutableStateOf(null) }
    // Google Play's page or purchase sheet was opened: what happened there is looked at on the way back
    var awayInPlay by rememberSaveable { mutableStateOf(false) }

    lateinit var billingManager: GooglePlayBillingManager

    /** Asks Play for the plans this device holds and sends each to the server: renewals, restores, lost answers. */
    fun refreshOwnedSubscriptions(recheck: Boolean = false) {
        billingManager.queryOwnedSubscriptions(onFailed = { ownedSubscriptions = emptyList() }) { purchases ->
            ownedSubscriptions = purchases
            purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { purchase ->
                val productId = purchase.products.firstOrNull() ?: return@forEach
                coroutineScope.launch {
                    viewModel.verifyGooglePlaySubscription(context, productId, purchase.purchaseToken, fromRecovery = true, recheck = recheck)
                }
            }
        }
    }

    /** Says once, calmly, that a move to a lower plan waits for the renewal; the balance then shows it on the cards. */
    fun confirmScheduledChange(newPlan: String) {
        val current = viewModel.activeSubscription
        val end = current?.periodEndsAt?.takeIf { it > 0 }?.let {
            periodEndPhrase(it, System.currentTimeMillis(), android.text.format.DateFormat.is24HourFormat(context))
        }
        val keep = current?.name?.takeIf { current.productId != null && it.isNotBlank() && it != newPlan }
        message = ShopMessage("Plan change scheduled", scheduledChangeMessage(newPlan, keep, end))
    }

    suspend fun creditSubscription(productId: String, token: String) {
        verifyingProductId = productId
        val result = viewModel.verifyGooglePlaySubscription(context, productId, token)
        verifyingProductId = null
        val launched = launchedPlan
        launchedPlan = null
        when (result) {
            SubscriptionVerification.Started, SubscriptionVerification.Recorded -> {
                refreshOwnedSubscriptions()
                if (launched?.first == PlanAction.Switch) confirmScheduledChange(launched.second)
            }
            // Kept and retried in the background; said once per purchase, not again on every recovery
            SubscriptionVerification.Pending -> if (viewModel.purchaseNoticesShown.add(token)) message = SUBSCRIPTION_LATER
            SubscriptionVerification.Refused -> message = ShopMessage(
                "Subscription not confirmed",
                "The server couldn't confirm this subscription. If you were charged, Google Play refunds a subscription that isn't confirmed."
            )
            // The view model tells the user (billingNotice); a recheck in flight has nothing to add
            SubscriptionVerification.OwnedByAnotherAccount, SubscriptionVerification.Skipped -> Unit
        }
    }

    billingManager = remember {
        GooglePlayBillingManager(
            context = context,
            onPurchaseCompleted = { token, productId, isSubscription, fromRecovery ->
                coroutineScope.launch {
                    if (isSubscription) {
                        creditSubscription(productId, token)
                        return@launch
                    }
                    // The view model sends a token once at a time, and leaves out recovered packs this server
                    // refused before, doesn't sell, or verified a moment ago
                    if (!fromRecovery) verifyingProductId = productId
                    val result = viewModel.verifyGooglePlayPurchase(context, productId, token, fromRecovery)
                    if (!fromRecovery) verifyingProductId = null
                    when (result) {
                        PackVerification.Done -> viewModel.refreshUserBalance(context)
                        // Paid, and kept for the server: not a failure, and said once per purchase
                        PackVerification.Pending -> if (viewModel.purchaseNoticesShown.add(token)) message = PACK_LATER
                        PackVerification.Skipped -> Unit
                    }
                }
            },
            onPurchaseError = { _ ->
                verifyingProductId = null
                launchedPlan = null
                // Play has usually shown its own error already: said once, calmly, without its technical details
                message = PURCHASE_NOT_COMPLETED
            },
            onPurchasesChanged = {
                // e.g. the plan was cancelled in Google Play: show how it stands now
                refreshOwnedSubscriptions(recheck = true)
                viewModel.refreshUserBalance(context)
                // A deferred change Play accepted without handing back a purchase: the recheck above sends its token
                val launched = launchedPlan
                launchedPlan = null
                if (launched?.first == PlanAction.Switch) confirmScheduledChange(launched.second)
            },
            onPurchasePending = {
                message = ShopMessage(
                    "Payment pending",
                    "Google Play is still waiting for your payment. Your purchase is added once it goes through."
                )
            }
        )
    }

    DisposableEffect(Unit) {
        billingManager.startConnection(
            onFailed = {
                isLoadingProducts = false
                isBillingUnavailable = true
            }
        ) {
            isBillingConnected = true
            // Credits any earlier pack that was paid for but never reached the server; plans come next
            billingManager.recoverUncreditedPurchases(includeSubscriptions = false)
            refreshOwnedSubscriptions()
        }
        onDispose { billingManager.destroy() }
    }

    // Back from Google Play (a cancel, a resubscribe, a closed purchase sheet): the plan may have changed there, so
    // it is checked again now, past the few minutes recovered plans otherwise rest. Once per return; a plan that
    // ended is gone from Play's list, and the balance then says so.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (awayInPlay && isBillingConnected) {
            awayInPlay = false
            // A pack bought while the shop was re-created reached no listener: recovered here
            billingManager.recoverUncreditedPurchases(includeSubscriptions = false)
            refreshOwnedSubscriptions(recheck = true)
            viewModel.refreshUserBalance(context)
        }
    }

    // Job and purchase answers carry only the total, so the free/purchased split may be an estimate
    LaunchedEffect(Unit) { viewModel.refreshUserBalance(context) }

    LaunchedEffect(isBillingConnected, viewModel.serverOffers, viewModel.subscriptionPlans) {
        if (!isBillingConnected) return@LaunchedEffect
        val packIds = viewModel.serverOffers.map { it.productId }
        val planIds = viewModel.subscriptionPlans.mapNotNull { it.productId }
        if (packIds.isEmpty() && planIds.isEmpty()) {
            isLoadingProducts = false
            return@LaunchedEffect
        }
        isLoadingProducts = true
        billingManager.queryProductDetails(packIds, planIds) { detailsList ->
            productDetailsMap = detailsList.associateBy { it.productId }
            isLoadingProducts = false
        }
    }

    val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
    val state = shopState(
        viewModel = viewModel,
        details = productDetailsMap,
        owned = ownedSubscriptions,
        canLaunch = activity != null,
        isLoading = isLoadingProducts,
        unavailable = isBillingUnavailable,
        busyProductId = verifyingProductId,
        endPhrase = { periodEndPhrase(it, System.currentTimeMillis(), is24Hour) }
    )

    fun openManage(productId: String?) {
        awayInPlay = true
        context.openUrl(manageSubscriptionUrl(productId, context.packageName))
    }

    fun onPlan(plan: ShopPlan) {
        // Managing, resubscribing and fixing a payment happen on Google Play's own page
        if (plan.action == PlanAction.Current) return openManage(plan.productId)
        val act = activity ?: return
        val details = productDetailsMap[plan.productId] ?: return
        val serverPlan = viewModel.subscriptionPlans.firstOrNull { it.productId == plan.productId } ?: return
        val offer = pickBasePlanOffer(details.planOffers(), serverPlan.basePlan) ?: return
        val account = viewModel.billingAccountId
        if (account == null) {
            // The plan is bound to the account the balance names; without it, the server couldn't tell whose it is
            viewModel.refreshUserBalance(context)
            message = ShopMessage("One moment", "Your account is still loading. Try again in a moment.")
            return
        }
        // The running purchase in Play: after a scheduled change it already names the coming plan
        val replacing = if (plan.action == PlanAction.Subscribe) null else runningPurchase(ownedSubscriptions) ?: return
        val action = plan.action
        launchedPlan = action to plan.name
        awayInPlay = true
        billingManager.launchSubscriptionFlow(
            activity = act,
            productDetails = details,
            offerToken = offer.offerToken,
            accountTag = accountTag(account),
            replacing = replacing,
            replacementMode = replacementModeFor(action)
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            ShopPanel(
                state = state,
                onPlan = ::onPlan,
                onPack = { pack ->
                    val details = productDetailsMap[pack.productId]
                    if (activity != null && details != null) {
                        awayInPlay = true
                        billingManager.launchPurchaseFlow(activity, details)
                    }
                },
                onManage = { openManage(state.subscribedProductId) },
                onClose = onDismiss,
                selectedTab = viewModel.shopSelectedTab,
                onSelectedTab = { viewModel.shopSelectedTab = it },
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .heightIn(max = 720.dp)
                    .wrapContentHeight()
            )
        }
    }

    message?.let { shown ->
        BillingMessageDialog(title = shown.title, text = shown.text, onDismiss = { message = null })
    }
}

/** The shop's content from the view model's billing state and Play's product details. */
private fun shopState(
    viewModel: PianoWeaveViewModel,
    details: Map<String, ProductDetails>,
    owned: List<Purchase>?,
    canLaunch: Boolean,
    isLoading: Boolean,
    unavailable: Boolean,
    busyProductId: String?,
    endPhrase: (Long) -> String
): ShopUiState {
    val current = viewModel.activeSubscription
    // Switching needs the running plan's purchase on this device's Play account
    val currentPurchase = current?.let { runningPurchase(owned) }
    val canSwitch = currentPurchase != null
    val plans = viewModel.subscriptionPlans.map { plan ->
        val id = plan.productId.orEmpty()
        val offer = details[id]?.let { pickBasePlanOffer(it.planOffers(), plan.basePlan) }
        val action = planAction(current, id, plan.level ?: 0)
        val isCurrent = action == PlanAction.Current
        ShopPlan(
            productId = id,
            name = plan.name?.takeIf { it.isNotBlank() } ?: id,
            allowance = planAllowanceText(plan.minutesPerPeriod, plan.isUnlimited, offer?.billingPeriod),
            price = planPriceText(offer),
            action = action,
            isUnlimited = plan.isUnlimited,
            status = when {
                current == null -> null
                isCurrent -> subscriptionStatusText(current, endPhrase)
                action == PlanAction.Scheduled -> scheduledStartText(current, endPhrase)
                else -> null
            },
            state = if (isCurrent) current?.subscriptionState else null,
            currentAction = if (isCurrent && current != null) currentPlanAction(current) else null,
            hint = if (isCurrent && current != null && current.scheduledProductId != null &&
                current.subscriptionState == SubscriptionState.Active
            ) SCHEDULED_CHANGE_HINT else null,
            // A plan that allows longer videos than the server's general limit says so
            note = longerVideosText(plan.maxVideoLengthMinutes, viewModel.serverMaxVideoLengthMinutes),
            canBuy = canLaunch && offer != null && (action == PlanAction.Subscribe || canSwitch)
        )
    }.toMutableList()
    // A running plan the server no longer offers is still shown, so its state and the way to manage it stay
    if (current != null && plans.none { it.action == PlanAction.Current }) {
        val unlimited = current.minutesPerPeriod == null
        plans.add(
            0,
            ShopPlan(
                productId = current.productId.orEmpty(),
                name = current.name?.takeIf { it.isNotBlank() } ?: "Your plan",
                allowance = planAllowanceText(current.minutesPerPeriod, unlimited, null),
                price = null,
                action = PlanAction.Current,
                isUnlimited = unlimited,
                status = subscriptionStatusText(current, endPhrase),
                state = current.subscriptionState,
                currentAction = currentPlanAction(current)
            )
        )
    }
    val packs = viewModel.serverOffers.map { offer ->
        val pack = details[offer.productId]
        ShopPack(offer.productId, offer.transcriptionMinutes, pack?.packPrice(), canBuy = canLaunch && pack != null)
    }

    val fm = viewModel.freeMinutes
    val grantSec = viewModel.freeMinutesSecondsUntilNextGrant
    val paid = viewModel.paidCredits
    val credits = viewModel.userCredits
    val renewal = when {
        // The server waits with the renewal until a refund is paid back
        paid != null && paid < 0 -> "They reset once nothing is owed."
        grantSec == null -> ""
        grantSec <= 0 -> "They're resetting now."
        else -> "They reset in ${formatRenewalTime(grantSec)}."
    }
    // Servers from before monthly minutes top one balance up to fm
    val legacyRenewal = if (viewModel.freeCredits == null && grantSec != null && credits != null && credits <= fm && fm > 0) {
        if (grantSec <= 0) "Free $fm credits renewal ready" else "Free $fm credits renewal in: ${formatRenewalTime(grantSec)}"
    } else null
    val planMinutes = current?.minutesPerPeriod
    val balance = ShopBalance(
        total = credits,
        periodCredits = viewModel.freeCredits,
        paidCredits = paid,
        planName = current?.name?.takeIf { it.isNotBlank() } ?: current?.let { "your plan" },
        planMinutes = planMinutes,
        unlimited = viewModel.isUnlimitedPlan,
        planStatus = current?.let { subscriptionStatusText(it, endPhrase) },
        heavyUse = viewModel.isUnlimitedHeavyUse,
        developer = viewModel.isDeveloper,
        freeMinutes = fm,
        freeEvery = freeCreditsPeriodText(viewModel.freeMinutesPeriod),
        renewal = renewal,
        legacyRenewal = legacyRenewal
    )
    return ShopUiState(
        balance = balance,
        plans = plans,
        packs = packs,
        isLoading = isLoading,
        unavailableMessage = if (unavailable) "Google Play Billing isn't available. Check that you're signed in to the Play Store." else null,
        busyProductId = busyProductId,
        freeMinutes = fm,
        subscribedProductId = current?.productId,
        switchNote = if (current != null && owned != null && !canSwitch && plans.any { it.action == PlanAction.Upgrade || it.action == PlanAction.Switch }) {
            "Your plan was bought with another Google Play account. To change it, use Google Play on that account."
        } else null
    )
}

private fun formatRenewalTime(seconds: Long): String {
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        days > 0 -> "$days day${if (days > 1) "s" else ""}, $hours hr${if (hours > 1) "s" else ""}"
        hours > 0 -> "$hours hr${if (hours > 1) "s" else ""}, $mins min${if (mins > 1) "s" else ""}"
        mins > 0 -> "$mins min${if (mins > 1) "s" else ""}, $secs sec"
        else -> "$secs second${if (secs != 1L) "s" else ""}"
    }
}

/** A short billing message with one OK: a failed purchase, a scheduled plan change, a subscription refused. */
@Composable
internal fun BillingMessageDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    icon: ImageVector = Icons.Default.ShoppingCart
) {
    val appTheme = LocalAppTheme.current
    val gold = appTheme.primaryColor
    val slate = MaterialTheme.colorScheme.tertiaryContainer
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            DialogSurface(
                light = gold,
                modifier = Modifier.fillMaxWidth(0.98f).wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, slate)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DialogHeader(title = title, icon = icon, onClose = onDismiss, titleColor = gold)
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        lineHeight = 22.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = gold,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Text(text = "OK", fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * The subscription purchase Play holds now: the newest paid one. After a scheduled change it is the token Play
 * replaced the plan with, and names the coming plan, so it can't be found by the current plan's product id.
 */
private fun runningPurchase(owned: List<Purchase>?): Purchase? =
    owned?.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }?.maxByOrNull { it.purchaseTime }
