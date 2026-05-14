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
     * The default plugin treats this as a no-op.
     *
     * @param bgMgdl    fingerstick value in mg/dL
     * @param timestamp time of the fingerstick measurement (epoch ms)
     */
    suspend fun addEntry(bgMgdl: Double, timestamp: Long)
}
