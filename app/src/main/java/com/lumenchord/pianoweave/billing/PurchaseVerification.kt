package com.lumenchord.pianoweave.billing

import com.lumenchord.pianoweave.api.PianoApi
import com.lumenchord.pianoweave.api.VerifyPurchaseRequest
import com.lumenchord.pianoweave.api.VerifyPurchaseResponse
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
 * True if [e] is not a real answer about the purchase, so it must stay pending and be verified again
 * later: the server is restarting or failing (5xx), busy (408, 429), or turned the request away before
 * looking at the purchase (401: an expired Google sign-in, or a server password the app lacks). Dropping
 * the purchase then would leave it paid for and never credited.
 */
fun isVerificationRetryable(e: HttpException): Boolean = e.code() >= 500 || e.code() in setOf(401, 408, 429)
