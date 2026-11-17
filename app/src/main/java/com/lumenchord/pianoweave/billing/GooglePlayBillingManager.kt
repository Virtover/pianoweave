package com.lumenchord.pianoweave.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Play Billing for credit packs (one-time products, consumed by the server) and subscriptions (acknowledged by
 * the server). Every paid purchase goes to [onPurchaseCompleted], which hands it to the server.
 */
class GooglePlayBillingManager(
    private val context: Context,
    private val onPurchaseCompleted: (purchaseToken: String, productId: String, isSubscription: Boolean) -> Unit,
    private val onPurchaseError: (errorMessage: String) -> Unit = {},
    /** A payment Play hasn't confirmed yet (e.g. cash at a store); it is credited once it clears. */
    private val onPurchasePending: () -> Unit = {}
) : PurchasesUpdatedListener {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        // Required by the library; subscriptions' pending transactions need nothing more
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private var isConnected = false

    /** Products known to be subscriptions, so a purchase coming back from Play goes to the right verification. */
    private val subscriptionProductIds = mutableSetOf<String>()

    fun startConnection(onFailed: (() -> Unit)? = null, onConnectedCallback: (() -> Unit)? = null) {
        if (isConnected) {
            onConnectedCallback?.invoke()
            return
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    isConnected = true
                    onConnectedCallback?.invoke()
                } else {
                    // e.g. no Play Store, or not signed in to it: nothing will load, so say so
                    coroutineScope.launch { onFailed?.invoke() }
                }
            }

            override fun onBillingServiceDisconnected() {
                isConnected = false
            }
        })
    }

    /** Details of credit packs ([productIds]) and of subscriptions ([subscriptionIds]); what Play doesn't know is left out. */
    fun queryProductDetails(
        productIds: List<String>,
        subscriptionIds: List<String> = emptyList(),
        onResult: (List<ProductDetails>) -> Unit
    ) {
        if (productIds.isEmpty() && subscriptionIds.isEmpty()) {
            onResult(emptyList())
            return
        }
        subscriptionProductIds += subscriptionIds

        startConnection {
            // One query per product type: Play refuses a list that mixes them
            queryDetails(productIds, BillingClient.ProductType.INAPP) { packs ->
                queryDetails(subscriptionIds, BillingClient.ProductType.SUBS) { plans ->
                    coroutineScope.launch { onResult(packs + plans) }
                }
            }
        }
    }

    private fun queryDetails(ids: List<String>, type: String, onResult: (List<ProductDetails>) -> Unit) {
        if (ids.isEmpty()) {
            onResult(emptyList())
            return
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(ids.distinct().map { id ->
                QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(type).build()
            })
            .build()
        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
            onResult(
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) productDetailsResult.productDetailsList
                else emptyList()
            )
        }
    }

    /**
     * Hands every paid purchase to [onPurchaseCompleted] again. A pack paid while the shop wasn't open (a pending
     * payment that cleared later, or a closed app) never reached the listener, and Google refunds one that isn't
     * consumed within three days; the server consumes a pack once credited, so only uncredited ones are listed.
     * Subscriptions stay listed while they run: sending them again records renewals and restores them on a new
     * device, which the server answers safely.
     */
    fun recoverUncreditedPurchases(includeSubscriptions: Boolean = true) {
        startConnection {
            queryPurchases(BillingClient.ProductType.INAPP) { purchases -> handOver(purchases, isSubscription = false) }
            if (includeSubscriptions) {
                queryPurchases(BillingClient.ProductType.SUBS) { purchases -> handOver(purchases, isSubscription = true) }
            }
        }
    }

    /** The subscriptions this device's Play account holds; [onFailed] if Play can't be asked. */
    fun queryOwnedSubscriptions(onFailed: () -> Unit = {}, onResult: (List<Purchase>) -> Unit) {
        startConnection(onFailed = onFailed) {
            queryPurchases(
                BillingClient.ProductType.SUBS,
                onFailed = { coroutineScope.launch { onFailed() } }
            ) { purchases -> coroutineScope.launch { onResult(purchases) } }
        }
    }

    private fun queryPurchases(type: String, onFailed: () -> Unit = {}, onResult: (List<Purchase>) -> Unit) {
        val params = QueryPurchasesParams.newBuilder().setProductType(type).build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) onResult(purchases) else onFailed()
        }
    }

    private fun handOver(purchases: List<Purchase>, isSubscription: Boolean) {
        purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { purchase ->
            val productId = purchase.products.firstOrNull() ?: return@forEach
            if (isSubscription) subscriptionProductIds += productId
            coroutineScope.launch { onPurchaseCompleted(purchase.purchaseToken, productId, isSubscription) }
        }
    }

    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails): Boolean {
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        return launch(activity, billingFlowParams)
    }

    /**
     * Buys subscription [productDetails] with [offerToken], bound to the app account [accountTag] (see
     * [com.lumenchord.pianoweave.billing.accountTag]). With [replacing], the new plan replaces that purchase's plan
     * in [replacementMode] (see [replacementModeFor]).
     */
    fun launchSubscriptionFlow(
        activity: Activity,
        productDetails: ProductDetails,
        offerToken: String,
        accountTag: String,
        replacing: Purchase? = null,
        replacementMode: Int? = null
    ): Boolean {
        subscriptionProductIds += productDetails.productId
        val oldProductId = replacing?.products?.firstOrNull()
        val isReplacement = replacing != null && oldProductId != null && replacementMode != null
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
        if (isReplacement) {
            productParams.setSubscriptionProductReplacementParams(
                BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.newBuilder()
                    .setOldProductId(oldProductId!!)
                    .setReplacementMode(replacementMode!!)
                    .build()
            )
        }
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams.build()))
            .setObfuscatedAccountId(accountTag)
        if (isReplacement) {
            flow.setSubscriptionUpdateParams(
                BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                    .setOldPurchaseToken(replacing!!.purchaseToken)
                    .build()
            )
        }
        return launch(activity, flow.build())
    }

    private fun launch(activity: Activity, params: BillingFlowParams): Boolean {
        val result = billingClient.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            val msg = if (result.debugMessage.isNotBlank()) result.debugMessage else "Failed to launch purchase flow (code: ${result.responseCode})"
            onPurchaseError(msg)
            return false
        }
        return true
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                val productId = purchase.products.firstOrNull() ?: ""
                when (purchase.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> {
                        val token = purchase.purchaseToken
                        val isSubscription = productId in subscriptionProductIds
                        coroutineScope.launch { onPurchaseCompleted(token, productId, isSubscription) }
                    }
                    Purchase.PurchaseState.PENDING -> coroutineScope.launch { onPurchasePending() }
                }
            }
        } else if (billingResult.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            val msg = if (billingResult.debugMessage.isNotBlank()) billingResult.debugMessage else "Purchase failed (code: ${billingResult.responseCode})"
            coroutineScope.launch {
                onPurchaseError(msg)
            }
        }
    }

    fun destroy() {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }
}

/** A subscription product's offers, each with the price of its last (recurring) phase. */
fun ProductDetails.planOffers(): List<PlanOffer> =
    subscriptionOfferDetails.orEmpty().map { offer ->
        val phase = offer.pricingPhases.pricingPhaseList.lastOrNull()
        PlanOffer(
            basePlanId = offer.basePlanId,
            offerId = offer.offerId,
            offerToken = offer.offerToken,
            priceMicros = phase?.priceAmountMicros,
            currencyCode = phase?.priceCurrencyCode,
            formattedPrice = phase?.formattedPrice,
            billingPeriod = phase?.billingPeriod
        )
    }

/** A credit pack's price as [formatMoney] writes it, or Play's own text for an unknown currency. */
fun ProductDetails.packPrice(): String? {
    val offer = oneTimePurchaseOfferDetails ?: return null
    return formatMoney(offer.priceAmountMicros, offer.priceCurrencyCode) ?: offer.formattedPrice
}
