package app.aaps.core.data.iob

import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow

/**
 * Simplified [app.aaps.core.data.model.GV] for storing in memory and calculations
 * It may correspond to GlucoseValue value in db
 * but because of 5 min recalculations and smoothing it may not
 */
data class InMemoryGlucoseValue(
    var timestamp: Long = 0L,
    /**
     * Value in mg/dl
     */
    var value: Double = 0.0,
    var trendArrow: TrendArrow = TrendArrow.NONE,
    /**
     * Smoothed value. Value is added by smoothing plugin
     * or null if smoothing was not done
     */
    var smoothed: Double? = null,
    /**
     * Calibration-overridden value. Set by the active calibration plugin
     * when a per-sensor correction is applied on top of the factory-calibrated
     * sensor value. `null` when no override is in effect.
     */
    var calibrated: Double? = null,
    /**
     * if true value is not corresponding to received value,
     * but it was recalculated to fill gap between BGs
     */
    var filledGap: Boolean = false,
    /**
     * Taken from GlucoseValue
     */
    var sourceSensor: SourceSensor = SourceSensor.UNKNOWN
) {

    /**
     * Preferred value for downstream consumers (loop, graph, status).
     * Falls back through smoothed -> calibrated -> raw sensor value.
     */
    val recalculated: Double get() = smoothed ?: calibrated ?: value

    /**
     * Value to feed into pipeline stages that run after calibration but before smoothing.
     * Returns the calibration-corrected value when present, otherwise the raw sensor value.
     * Smoothing plugins read this so their output already incorporates the calibration override.
     */
    val calibratedOrValue: Double get() = calibrated ?: value

    companion object
}