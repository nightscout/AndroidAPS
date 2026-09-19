package app.aaps.plugins.aps.loop.runningMode

/**
 * Computes the pump TBR duration to issue when enforcing a zero-delivery running mode.
 *
 * Pumps accept TBR durations only in discrete steps. When the remaining window is shorter
 * than the step, or not a multiple of it, we round up — the RunningModeExpiryWorker cancels
 * the overhang at the exact mode end. Rounding down would leave the pump delivering while
 * AAPS considers the mode still active.
 */
object DurationRounding {

    sealed interface Result {
        data object Skip : Result
        data class Issue(val minutes: Int) : Result
    }

    /**
     * @param remainingMinutes minutes left in the running-mode window (≤ 0 means expired)
     * @param pumpStepMinutes pump's TBR duration step; ≤ 0 means pump does not support TBR
     * @param pumpMaxDurationMinutes pump's maximum TBR duration; ≤ 0 treated as no cap
     */
    fun roundUpToPumpStep(
        remainingMinutes: Int,
        pumpStepMinutes: Int,
        pumpMaxDurationMinutes: Int
    ): Result {
        if (pumpStepMinutes <= 0) return Result.Skip
        if (remainingMinutes <= 0) return Result.Skip
        val roundedUp = ((remainingMinutes.toLong() + pumpStepMinutes - 1) / pumpStepMinutes) * pumpStepMinutes
        val capped = if (pumpMaxDurationMinutes > 0) roundedUp.coerceAtMost(pumpMaxDurationMinutes.toLong()) else roundedUp
        return Result.Issue(capped.toInt())
    }
}
