package com.lumenchord.pianoweave.billing

/**
 * The server's minute balance can be negative: a purchase was refunded after its minutes were used. The
 * user then owes those minutes, and buying more pays them back first.
 */

/** Minutes the user owes; 0 unless [balance] is negative. */
fun owedMinutes(balance: Int): Int = if (balance < 0) -balance else 0

/** The server turns away a balance under one minute, so that is the least any transcription needs. */
const val MIN_BALANCE_TO_TRANSCRIBE = 1

/** True if [balance] covers [cost]; with the cost unknown, if it reaches the server's minimum. */
fun canAfford(balance: Int, cost: Int?): Boolean =
    balance >= maxOf(cost ?: 0, MIN_BALANCE_TO_TRANSCRIBE)

/** "12 Credits", or "Owes 3 Credits" for a negative balance. */
fun creditsLabel(balance: Int): String {
    val owed = owedMinutes(balance)
    return if (owed > 0) "Owes $owed ${credits(owed)}" else "$balance ${credits(balance)}"
}

private fun credits(n: Int) = if (n == 1) "Credit" else "Credits"
