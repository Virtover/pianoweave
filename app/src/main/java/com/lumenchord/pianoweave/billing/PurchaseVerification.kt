package com.lumenchord.pianoweave.billing

import com.lumenchord.pianoweave.api.PianoApi
import com.lumenchord.pianoweave.api.VerifyPurchaseRequest
import com.lumenchord.pianoweave.api.VerifyPurchaseResponse

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
