package com.lumenchord.pianoweave.ui.viewmodel

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.Purchase
import com.lumenchord.pianoweave.api.PianoApiFactory
import com.lumenchord.pianoweave.billing.GooglePlayBillingManager
import com.lumenchord.pianoweave.billing.PendingPurchase
import com.lumenchord.pianoweave.billing.PendingPurchaseStore
import com.lumenchord.pianoweave.billing.SUBSCRIPTION_OWNED_MESSAGE
import com.lumenchord.pianoweave.billing.VerificationOutcome
import com.lumenchord.pianoweave.billing.accountTag
import com.lumenchord.pianoweave.billing.verificationOutcome
import com.lumenchord.pianoweave.billing.verifySubscription
import com.lumenchord.pianoweave.util.AppPrefs
import com.lumenchord.pianoweave.worker.PurchaseVerificationWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/** How a subscription's verification ended. */
enum class SubscriptionVerification {
    /** A paid period started now: its minutes are in the balance. */
    Started,
    /** The server knew it already, or it is pending or ended. */
    Recorded,
    /** No answer yet: kept, and retried in the background. */
    Pending,
    /** The server doesn't know this subscription or token: dropped. */
    Refused,
    /** Another account of the app bought it: dropped, and the user told. */
    OwnedByAnotherAccount,
    /** Not sent: already in flight, verified a moment ago, or refused before. */
    Skipped
}

/** A recovered subscription verified this recently isn't sent again when the shop reopens. */
private const val RECOVERY_RECHECK_MS = 10 * 60_000L

/**
 * Sends subscription [purchaseToken] to the server, which records the plan (or its renewal) and acknowledges it
 * with Play. Safe to repeat. [fromRecovery] marks a purchase found again rather than just bought: it is skipped when
 * it was refused or verified a moment ago (unless [recheck]: Play said it changed), since both verify endpoints are
 * rate limited per account.
 */
internal suspend fun PianoWeaveViewModel.verifyGooglePlaySubscriptionImpl(
    context: Context,
    productId: String,
    purchaseToken: String,
    fromRecovery: Boolean,
    recheck: Boolean = false
): SubscriptionVerification {
    val refusedKey = billingAccountId?.let { "${accountTag(it)}:$purchaseToken" }
    if (fromRecovery) {
        if (refusedKey != null && refusedKey in refusedSubscriptions(context)) return SubscriptionVerification.Skipped
        val last = subscriptionVerifiedAt[purchaseToken]
        if (!recheck && last != null && SystemClock.elapsedRealtime() - last < RECOVERY_RECHECK_MS) return SubscriptionVerification.Skipped
    }
    if (!subscriptionVerificationsInFlight.add(purchaseToken)) return SubscriptionVerification.Skipped

    val purchase = PendingPurchase(
        productId = productId,
        purchaseToken = purchaseToken,
        serverUrl = activeServerUrl,
        userId = getOrCreateUserIdImpl(context),
        type = PendingPurchase.TYPE_SUBSCRIPTION
    )
    // A new purchase is kept first, so a process killed meanwhile still sends it on the next start. The worker
    // is started only when the server can't answer now, so it doesn't send the same token in parallel.
    if (!fromRecovery) PendingPurchaseStore.addPendingPurchase(context, purchase)

    return try {
        val response = withContext(Dispatchers.IO) {
            executeServerCallImpl(context, purchase.serverUrl) { authHeader ->
                PianoApiFactory.getApi(purchase.serverUrl).verifySubscription(purchase, authHeader)
            }
        }
        PendingPurchaseStore.removePendingPurchase(context, purchaseToken)
        subscriptionVerifiedAt[purchaseToken] = SystemClock.elapsedRealtime()
        response.balance?.let { applyBalance(context, it) }
        if (response.status == "started") SubscriptionVerification.Started else SubscriptionVerification.Recorded
    } catch (e: HttpException) {
        when (verificationOutcome(e)) {
            VerificationOutcome.Retry -> {
                PurchaseVerificationWorker.scheduleVerification(context, purchase)
                SubscriptionVerification.Pending
            }
            VerificationOutcome.Drop -> {
                PendingPurchaseStore.removePendingPurchase(context, purchaseToken)
                refusedKey?.let { rememberRefusedSubscription(context, it) }
                SubscriptionVerification.Refused
            }
            VerificationOutcome.OwnedByAnotherAccount -> {
                PendingPurchaseStore.removePendingPurchase(context, purchaseToken)
                refusedKey?.let { rememberRefusedSubscription(context, it) }
                billingNotice = SUBSCRIPTION_OWNED_MESSAGE
                SubscriptionVerification.OwnedByAnotherAccount
            }
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        // No answer at all (offline, server down): the worker keeps trying in the background
        PurchaseVerificationWorker.scheduleVerification(context, purchase)
        SubscriptionVerification.Pending
    } finally {
        subscriptionVerificationsInFlight.remove(purchaseToken)
    }
}

/**
 * Once per run and account, after its first balance: sends the subscriptions this device's Play account holds to
 * the server, which records renewals and restores a plan bought on another device. The shop does the same on
 * opening.
 */
internal fun PianoWeaveViewModel.recoverSubscriptionsOnce(context: Context) {
    val account = billingAccountId ?: return
    if (!isBilledServer || subscriptionsRecoveredFor == account) return
    subscriptionsRecoveredFor = account
    val appContext = context.applicationContext
    val manager = GooglePlayBillingManager(appContext, onPurchaseCompleted = { _, _, _ -> })
    manager.queryOwnedSubscriptions(onFailed = { manager.destroy() }) { purchases ->
        manager.destroy()
        for (purchase in purchases) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            val productId = purchase.products.firstOrNull() ?: continue
            viewModelScope.launch {
                verifyGooglePlaySubscriptionImpl(appContext, productId, purchase.purchaseToken, fromRecovery = true)
            }
        }
    }
}

private fun refusedSubscriptions(context: Context): Set<String> =
    AppPrefs.get(context).getStringSet(AppPrefs.REFUSED_SUBSCRIPTIONS, null).orEmpty()

private fun rememberRefusedSubscription(context: Context, key: String) {
    val prefs = AppPrefs.get(context)
    // A copy: the set SharedPreferences returns must not be changed
    val keys = refusedSubscriptions(context).toMutableSet().apply { add(key) }
    prefs.edit().putStringSet(AppPrefs.REFUSED_SUBSCRIPTIONS, keys).apply()
}
