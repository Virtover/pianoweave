package com.lumenchord.pianoweave.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lumenchord.pianoweave.api.PianoApiFactory
import com.lumenchord.pianoweave.api.config.AppConfig
import com.lumenchord.pianoweave.api.executeServerCall
import com.lumenchord.pianoweave.billing.PendingPurchase
import com.lumenchord.pianoweave.billing.PendingPurchaseStore
import com.lumenchord.pianoweave.billing.verify
import com.lumenchord.pianoweave.util.AppPrefs
import retrofit2.HttpException
import java.util.concurrent.TimeUnit

/** Retries a purchase verification in the background until the server gives any answer. */
class PurchaseVerificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val purchase = PendingPurchase(
            productId = inputData.getString(KEY_PRODUCT_ID) ?: return Result.failure(),
            purchaseToken = inputData.getString(KEY_PURCHASE_TOKEN) ?: return Result.failure(),
            serverUrl = inputData.getString(KEY_SERVER_URL) ?: DEFAULT_SERVER_URL,
            userId = inputData.getString(KEY_USER_ID) ?: ""
        )
        // Needed to tell the official server apart (and send the token only there) in a cold process.
        runCatching { AppConfig.initialize(applicationContext) }
        val api = PianoApiFactory.getApi(purchase.serverUrl)

        return try {
            val response = executeServerCall(applicationContext, purchase.serverUrl) { header ->
                api.verify(purchase, header)
            }
            PendingPurchaseStore.removePendingPurchase(applicationContext, purchase.purchaseToken)
            AppPrefs.get(applicationContext).edit().putInt(AppPrefs.USER_CREDITS, response.minutes).apply()
            Result.success()
        } catch (_: HttpException) {
            // Any HTTP status (4xx/5xx) counts as an answer from the server.
            PendingPurchaseStore.removePendingPurchase(applicationContext, purchase.purchaseToken)
            Result.success()
        } catch (_: Exception) {
            // Network error / server down / no response: retry until the server answers.
            Result.retry()
        }
    }

    companion object {
        const val KEY_PRODUCT_ID = "product_id"
        const val KEY_PURCHASE_TOKEN = "purchase_token"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_USER_ID = "user_id"

        private const val DEFAULT_SERVER_URL = "http://localhost:8000/"

        fun scheduleVerification(context: Context, purchase: PendingPurchase) {
            PendingPurchaseStore.addPendingPurchase(context, purchase)
            val workData = workDataOf(
                KEY_PRODUCT_ID to purchase.productId,
                KEY_PURCHASE_TOKEN to purchase.purchaseToken,
                KEY_SERVER_URL to purchase.serverUrl,
                KEY_USER_ID to purchase.userId
            )
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<PurchaseVerificationWorker>()
                .setInputData(workData)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "verify_purchase_${purchase.purchaseToken}",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun scheduleAllPending(context: Context) {
            PendingPurchaseStore.getPendingPurchases(context).forEach { scheduleVerification(context, it) }
        }
    }
}
