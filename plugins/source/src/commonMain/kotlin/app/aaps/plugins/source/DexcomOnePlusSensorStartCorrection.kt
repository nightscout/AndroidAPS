package app.aaps.plugins.source

/**
 * Pure rules behind "correct the insertion date" on the ONE+ Status screen.
 *
 * The sensor age of this source comes from the driver's own stored session start, not from the
 * `SENSOR_CHANGE` therapy event, and that clock is written once — at pairing, or at the first
 * reading. A sensor inserted hours before it was paired therefore carried a wrong age for its whole
 * life, and nothing in the app could move it: adding a `SENSOR_CHANGE` by hand does not reach this
 * clock, and an added event that is EARLIER than the one the plugin already wrote is not even the
 * "last sensor change" any more, so the overview ignores it too.
 *
 * [Verdict.Disabled]: the user switched the correction off in the settings, so nothing is read or
 * written.
 */
object DexcomOnePlusSensorStartCorrection {

    /** Verdict of [validate] — the screen turns each case into its own message. */
    sealed interface Verdict {

        data object Accepted : Verdict

        /** A sensor cannot have been inserted in the future. */
        data object InFuture : Verdict

        /** Older than a full sensor life plus its grace: this would be a different sensor. */
        data object TooOld : Verdict

        /** No sensor session is stored, so there is no age to correct. */
        data object NoSession : Verdict

        /** The setting is off. Nothing was read and nothing was written. */
        data object Disabled : Verdict
    }

    /**
     * @param newStartMs insertion time the user picked
     * @param currentStartMs session start stored today (0 when none)
     * @param nowMs current wall-clock time
     * @param maxAgeMs oldest insertion still plausible for the running sensor
     */
    fun validate(
        newStartMs: Long,
        currentStartMs: Long,
        nowMs: Long,
        maxAgeMs: Long = DexcomOnePlusStaging.SENSOR_LIFE_MS + DexcomOnePlusStaging.SENSOR_GRACE_MS,
    ): Verdict = when {
        currentStartMs <= 0L          -> Verdict.NoSession
        newStartMs > nowMs            -> Verdict.InFuture
        nowMs - newStartMs > maxAgeMs -> Verdict.TooOld
        else                          -> Verdict.Accepted
    }

    /**
     * Oldest moment whose `SENSOR_CHANGE` events belong to the session being corrected.
     *
     * Both the events the plugin wrote by itself and anything the user logged in between have to go,
     * or the newest of them keeps winning the overview's "last sensor change" lookup and the
     * corrected date stays invisible. Moving the date **earlier** has to reach back to the new date;
     * moving it **later** has to reach back to the old one.
     */
    fun cleanupFrom(newStartMs: Long, currentStartMs: Long): Long = minOf(newStartMs, currentStartMs)

    /** Steps used to walk away from an occupied moment, and how far the walk may go. */
    private const val FREE_STEP_MS = 1_000L
    private const val FREE_MAX_STEPS = 60

    /**
     * A moment no `SENSOR_CHANGE` occupies yet, at or just after [desiredMs].
     *
     * The database refuses a second sensor change at a timestamp it already holds — and it looks for
     * that duplicate **without checking whether the existing row is still valid**. So the event this
     * correction had just invalidated one line earlier still blocked the new one: nothing was
     * inserted, the refusal was silent, and the display was left with no valid sensor change at all,
     * falling back to the previous sensor or to nothing. The user then saw the corrected date on the
     * plugin screen and the old one everywhere else.
     *
     * Moving by one second is invisible in an age shown in hours, and it is the only thing that has
     * to give. The picker zeroes seconds and milliseconds, so two corrections to the same minute
     * collide exactly; without this they would collide for good.
     */
    fun freeTimestamp(desiredMs: Long, takenMs: Set<Long>): Long {
        var candidate = desiredMs
        repeat(FREE_MAX_STEPS) {
            if (candidate !in takenMs) return candidate
            candidate += FREE_STEP_MS
        }
        return candidate
    }
}
