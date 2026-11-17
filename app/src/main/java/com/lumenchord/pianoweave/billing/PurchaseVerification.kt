package com.lumenchord.pianoweave.billing

import com.lumenchord.pianoweave.api.PianoApi
import com.lumenchord.pianoweave.api.VerifyPurchaseRequest
import com.lumenchord.pianoweave.api.VerifyPurchaseResponse
import com.lumenchord.pianoweave.api.VerifySubscriptionResponse
import com.lumenchord.pianoweave.api.ServerErrors
import retrofit2.HttpException

/**
 * Asks the server to verify, credit and consume [purchase]. Signed-in users are identified by
 * their auth header; anonymous users fall back to the purchase's stored user id.
 */
suspend fun PianoApi.verify(purchase: PendingPurchase, authHeader: String?): VerifyPurchaseResponse =
    verifyPurchase(
        authHeader = authHeader,
        userId = if (authHeader == null) purchase.userId.ifBlank { null } else null,
        request = VerifyPurchaseRequest(purchase.productId, purchase.purchaseToken)
    )

/**
 * Asks the server to record subscription [purchase] for the signed-in account; the server acknowledges it with
 * Play itself. Subscriptions need a signed-in account, so there is no anonymous fallback.
 */
suspend fun PianoApi.verifySubscription(purchase: PendingPurchase, authHeader: String?): VerifySubscriptionResponse =
    verifySubscription(
        authHeader = authHeader,
        request = VerifyPurchaseRequest(purchase.productId, purchase.purchaseToken)
    )

/**
 * True if [e] is not a final answer about a credit pack's purchase, so it must stay pending and be verified again
 * later (see [packVerificationOutcome]). Dropping it then would leave it paid for and never credited.
 */
fun isVerificationRetryable(e: HttpException): Boolean = packVerificationOutcome(e) == VerificationOutcome.Retry

/** A recovered purchase verified this recently isn't sent again (the server credits and consumes it once). */
const val RECOVERY_RECHECK_MS = 10 * 60_000L

/**
 * Whether a credit pack found again in Play's purchases is worth sending to the server. Not if the server refused
 * its token before, nor if it was verified a moment ago, nor (when the server's offers are known, [offered]
 * non-null) if the server doesn't sell that product at all: old purchases of products another server sold would
 * otherwise be refused on every recovery and use up the per-account verify limit.
 */
fun shouldRecoverPack(productId: String, offered: Set<String>?, refusedBefore: Boolean, verifiedAgoMs: Long?): Boolean = when {
    refusedBefore -> false
    verifiedAgoMs != null && verifiedAgoMs in 0 until RECOVERY_RECHECK_MS -> false
    offered != null && productId !in offered -> false
    else -> true
}

/** How long to wait before verifying again after [e]: a 429's Retry-After (at least a minute), else no wait. */
fun retryDelaySeconds(e: HttpException): Long =
    if (e.code() == 429) (ServerErrors.retryAfterSeconds(e.response()?.headers()) ?: 60L).coerceIn(60L, 3_600L) else 0L

/** The key a refused purchase is remembered by: the server, the account and the token. */
fun refusedPurchaseKey(serverUrl: String, account: String, purchaseToken: String): String =
    "${serverUrl.trimEnd('/')}|${accountTag(account)}|$purchaseToken"
