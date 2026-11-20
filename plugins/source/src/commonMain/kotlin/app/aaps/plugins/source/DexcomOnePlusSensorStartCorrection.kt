package app.aaps.plugins.source

/**
 * Pure rule for the time stamp of a `SENSOR_CHANGE` event this source writes.
 *
 * The sensor age of this source comes from the driver's own stored session start. The plugin writes
 * a `SENSOR_CHANGE` therapy event at that start, so the overview shows the same age. When the start
 * moves (the sensor reports its real start), the event is written again at the new time.
 */
object DexcomOnePlusSensorStartCorrection {

    /** Steps used to walk away from an occupied moment, and how far the walk may go. */
    private const val FREE_STEP_MS = 1_000L
    private const val FREE_MAX_STEPS = 60

    /**
     * A moment no `SENSOR_CHANGE` occupies yet, at or just after [desiredMs].
     *
     * The database refuses a second sensor change at a timestamp it already holds — and it looks for
     * that duplicate **without checking whether the existing row is still valid**. So an event that
     * was invalidated one line earlier still blocks the new one: nothing is inserted, the refusal is
     * silent, and the display is left with no valid sensor change at all, falling back to the
     * previous sensor or to nothing.
     *
     * Moving by one second is invisible in an age shown in hours, and it is the only thing that has
     * to give.
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
