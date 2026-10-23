package com.lumenchord.pianoweave.billing

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class PendingPurchase(
    val productId: String,
    val purchaseToken: String,
    val serverUrl: String,
    val userId: String,
    val timestamp: Long = System.currentTimeMillis()
)

object PendingPurchaseStore {
    private const val PREFS_NAME = "piano_weave_pending_purchases"
    private const val KEY_PENDING_PURCHASES = "pending_purchases_list"
    private val gson = Gson()

    @Synchronized
    fun addPendingPurchase(context: Context, purchase: PendingPurchase) {
        val list = getPendingPurchases(context).toMutableList()
        if (list.none { it.purchaseToken == purchase.purchaseToken }) {
            list.add(purchase)
            savePendingPurchases(context, list)
        }
    }

    @Synchronized
    fun removePendingPurchase(context: Context, purchaseToken: String) {
        val list = getPendingPurchases(context).filterNot { it.purchaseToken == purchaseToken }
        savePendingPurchases(context, list)
    }

    @Synchronized
    fun getPendingPurchases(context: Context): List<PendingPurchase> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PENDING_PURCHASES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<PendingPurchase>>() {}.type
            val list: List<PendingPurchase> = gson.fromJson(json, type) ?: emptyList()
            // Gson leaves fields it can't match null, e.g. entries an older release build stored with
            // field names renamed by R8; such an entry can't be verified, so it's skipped, not crashed on
            list.filter { (it.productId as String?) != null && (it.purchaseToken as String?) != null }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun savePendingPurchases(context: Context, list: List<PendingPurchase>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(list)
        prefs.edit().putString(KEY_PENDING_PURCHASES, json).apply()
    }
}
