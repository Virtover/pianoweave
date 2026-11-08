package com.lumenchord.pianoweave.billing

/**
 * The server's minute balance can be negative: a purchase was refunded after its minutes were used. The
 * user then owes those minutes, and buying more pays them back first.
 */

/** Minutes the user owes; 0 unless [balance] is negative or null. */
fun owedMinutes(balance: Int?): Int = if (balance != null && balance < 0) -balance else 0

/** The server turns away a balance under one minute, so that is the least any transcription needs. */
const val MIN_BALANCE_TO_TRANSCRIBE = 1

/** True if [balance] covers [cost]; with the cost unknown, if it reaches the server's minimum. */
fun canAfford(balance: Int?, cost: Int?): Boolean =
    balance != null && balance >= maxOf(cost ?: 0, MIN_BALANCE_TO_TRANSCRIBE)

/** "12 Credits", "— Credits" when unknown (not downloaded yet or not signed in), or "Owes 3 Credits" for a negative balance. */
fun creditsLabel(balance: Int?, symbol: String = "—"): String {
    if (balance == null) return "$symbol Credits"
    val owed = owedMinutes(balance)
    return if (owed > 0) "Owes $owed ${credits(owed)}" else "$balance ${credits(balance)}"
}

private fun credits(n: Int) = if (n == 1) "Credit" else "Credits"


private val PERIOD_UNITS = mapOf('s' to 1L, 'm' to 60L, 'h' to 3_600L, 'd' to 86_400L, 'w' to 604_800L)

/**
 * The server's free-credit period ("30d", "12h", "1w", "1.5h": a number and one of s m h d w, as the server's
 * FREE_MINUTES_PERIOD takes it) as words: "every 30 days", "every week", "every 90 minutes". Null if it can't be
 * read, so the app never names a period the server didn't set.
 */
fun freeCreditsPeriodText(period: String?): String? {
    val text = period?.trim()?.lowercase()?.takeIf { it.length >= 2 } ?: return null
    val unitSeconds = PERIOD_UNITS[text.last()] ?: return null
    val amount = text.dropLast(1).toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() } ?: return null
    val seconds = Math.round(amount * unitSeconds).takeIf { it > 0 } ?: return null
    // The largest unit that divides it evenly, so 14d reads "2 weeks" but 30d stays "30 days"
    val (name, size) = listOf("week" to 604_800L, "day" to 86_400L, "hour" to 3_600L, "minute" to 60L, "second" to 1L)
        .first { seconds % it.second == 0L }
    val count = seconds / size
    return if (count == 1L) "every $name" else "every $count ${name}s"
}
