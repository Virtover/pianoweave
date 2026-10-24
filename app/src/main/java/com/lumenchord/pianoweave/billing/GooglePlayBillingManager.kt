package com.lumenchord.pianoweave.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GooglePlayBillingManager(
    private val context: Context,
    private val onPurchaseCompleted: (purchaseToken: String, productId: String) -> Unit,
    private val onPurchaseError: (errorMessage: String) -> Unit = {}
) : PurchasesUpdatedListener {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private var isConnected = false

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

    fun queryProductDetails(
        productIds: List<String>,
        onResult: (List<ProductDetails>) -> Unit
    ) {
        if (productIds.isEmpty()) {
            onResult(emptyList())
            return
        }

        startConnection {
            val productList = productIds.map { id ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            }

            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()

            billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    onResult(productDetailsResult.productDetailsList)
                } else {
                    onResult(emptyList())
                }
            }
        }
    }

    /**
     * Hands every paid purchase that was never credited to [onPurchaseCompleted] again. One paid while the
     * shop wasn't open (a pending payment that cleared later, or a closed app) never reached the listener,
     * and Google refunds a purchase that isn't consumed within three days. The server consumes a purchase
     * once it has credited it, so only uncredited ones are still listed here.
     */
    fun recoverUncreditedPurchases() {
        startConnection {
            val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
            billingClient.queryPurchasesAsync(params) { result, purchases ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
                purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { purchase ->
                    val productId = purchase.products.firstOrNull() ?: return@forEach
                    coroutineScope.launch { onPurchaseCompleted(purchase.purchaseToken, productId) }
                }
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails): Boolean {
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val result = billingClient.launchBillingFlow(activity, billingFlowParams)
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
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    val productId = purchase.products.firstOrNull() ?: ""
                    val token = purchase.purchaseToken
                    coroutineScope.launch {
                        onPurchaseCompleted(token, productId)
                    }
                }
            }
        } else if (billingResult.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            val msg = if (billingResult.debugMessage.isNotBlank()) billingResult.debugMessage else "Purchase failed (code: ${billingResult.responseCode})"
            coroutineScope.launch {
                onPurchaseError(msg)
            }
        }
    }

    //DONE ON SERVER
//    fun consumePurchase(purchaseToken: String, onConsumed: (() -> Unit)? = null) {
//        val consumeParams = ConsumeParams.newBuilder()
//            .setPurchaseToken(purchaseToken)
//            .build()
//
//        billingClient.consumeAsync(consumeParams) { result, _ ->
//            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
//                onConsumed?.invoke()
//            }
//        }
//    }

    fun destroy() {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }
}
