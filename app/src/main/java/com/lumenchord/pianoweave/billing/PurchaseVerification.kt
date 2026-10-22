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
 * True if [e] is not a real answer about the purchase: the server is restarting or starting up (502-504
 * from it or a proxy in front of it). The purchase must then stay pending and be verified again later.
 */
fun isServerUnavailable(e: HttpException): Boolean = e.code() in 502..504
