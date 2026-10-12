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
import com.lumenchord.pianoweave.api.VerifyPurchaseRequest
import com.lumenchord.pianoweave.api.config.AppConfig
import com.lumenchord.pianoweave.auth.GoogleAuthManager
import com.lumenchord.pianoweave.billing.PendingPurchase
import com.lumenchord.pianoweave.billing.PendingPurchaseStore
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit

class PurchaseVerificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val productId = inputData.getString(KEY_PRODUCT_ID) ?: return Result.failure()
        val purchaseToken = inputData.getString(KEY_PURCHASE_TOKEN) ?: return Result.failure()
        val serverUrl = inputData.getString(KEY_SERVER_URL) ?: "http://localhost:8000/"
        val userId = inputData.getString(KEY_USER_ID)

        val api = PianoApiFactory.getApi(serverUrl)

        val clientId = try {
            AppConfig.initialize(applicationContext)
            AppConfig.getConfig().webClientId ?: ""
        } catch (_: Exception) {
            ""
        }
        val prefs = applicationContext.getSharedPreferences("piano_weave_prefs", Context.MODE_PRIVATE)
        val customClientId = prefs.getString("custom_google_client_id", "") ?: ""
        val activeClientId = if (customClientId.isNotBlank()) customClientId else clientId

        var token = GoogleAuthManager.getSavedToken(applicationContext)
        var authHeader = GoogleAuthManager.getAuthHeader(token)

        suspend fun <T> executeWithWorkerAuthRetry(apiCall: suspend (String?) -> T): T {
            try {
                return apiCall(authHeader)
            } catch (e: HttpException) {
                if (e.code() == 401 || e.code() == 503) {
                    if (activeClientId.isNotBlank()) {
                        val refreshResult = GoogleAuthManager.silentRefresh(applicationContext, activeClientId)
                        if (refreshResult.isSuccess) {
                            val user = refreshResult.getOrNull()
                            if (user != null) {
                                token = user.token
                                authHeader = GoogleAuthManager.getAuthHeader(token)
                                return apiCall(authHeader)
                            }
                        }
                    }
                }
                throw e
            }
        }

        return try {
            val response = executeWithWorkerAuthRetry { header ->
                api.verifyPurchase(
                    authHeader = header,
                    userId = if (header == null) userId else null,
                    request = VerifyPurchaseRequest(productId, purchaseToken)
                )
            }

            // Successfully received answer from server!
            PendingPurchaseStore.removePendingPurchase(applicationContext, purchaseToken)
            prefs.edit().putInt("user_credits", response.minutes).apply()
            Result.success()
        } catch (e: HttpException) {
            // Received an HTTP status code from server (4xx/5xx). That counts as an answer from server!
            PendingPurchaseStore.removePendingPurchase(applicationContext, purchaseToken)
            Result.success()
        } catch (e: IOException) {
            // Network error / server down / no response. Retry until any answer from server!
            Result.retry()
        } catch (e: Exception) {
            // Any other connection/network error. Retry until any answer from server!
            Result.retry()
        }
    }

    companion object {
        const val KEY_PRODUCT_ID = "product_id"
        const val KEY_PURCHASE_TOKEN = "purchase_token"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_USER_ID = "user_id"

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
            val pendingList = PendingPurchaseStore.getPendingPurchases(context)
            for (p in pendingList) {
                scheduleVerification(context, p)
            }
        }
    }
}
