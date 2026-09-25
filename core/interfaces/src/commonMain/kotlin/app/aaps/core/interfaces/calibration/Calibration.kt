package app.aaps.core.interfaces.calibration

import app.aaps.core.data.iob.InMemoryGlucoseValue

interface Calibration {

    /**
     * Apply calibration override to in-memory glucose values.
     *
     * Implementations populate [InMemoryGlucoseValue.calibrated] for each entry
     * where the override should take effect. Consumers read the corrected value
     * via [InMemoryGlucoseValue.recalculated], which falls back through
     * smoothed -> calibrated -> value.
     *
     * The default plugin (no calibration) returns the input list unchanged.
     *
     * @param data    input list ([0] is the most recent reading)
     * @param context optional hints such as sensor session boundary
     * @return the same list with [InMemoryGlucoseValue.calibrated] populated where applicable
     */
    suspend fun calibrate(
        data: MutableList<InMemoryGlucoseValue>,
        context: CalibrationContext = CalibrationContext.NONE
    ): MutableList<InMemoryGlucoseValue>

    /**
     * Persist a new fingerstick entry as a calibration input.
     * The default plugin treats this as a no-op and returns [AddEntryResult.Accepted].
     *
     * @param bgMgdl    fingerstick value in mg/dL
     * @param timestamp the submission moment in epoch ms — typically `dateUtil.now()`.
     *                  Pre-conditions like warm-up and pair lookback are evaluated relative
     *                  to this timestamp, so it MUST be close to the current time.
     *                  Historical re-entry (e.g. from a backup import) is not supported here.
     * @return [AddEntryResult.Accepted] if the entry was persisted, or a
     *         [AddEntryResult.Rejected] variant describing why it was not
     */
    suspend fun addEntry(bgMgdl: Double, timestamp: Long): AddEntryResult

    /**
     * Check whether [addEntry] would currently be accepted. Lets callers (e.g. the
     * calibration dialog) gate UI affordances before the user picks a BG value,
     * instead of letting them confirm and then silently rejecting. The default
     * plugin always returns [AddEntryResult.Accepted].
     *
     * Conditions can change between this call and [addEntry], so [addEntry] still
     * re-evaluates everything. This is a UX hint, not a contract.
     */
    suspend fun checkPreconditions(): AddEntryResult
}
