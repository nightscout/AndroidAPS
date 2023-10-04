package app.aaps.core.interfaces.iob

import app.aaps.data.db.TrendArrow
import app.aaps.database.entities.GlucoseValue

/**
 * Simplified [app.aaps.database.entities.GlucoseValue] for storing in memory and calculations
 * It may correspond to GlucoseValue value in db
 * but because of 5 min recalculations and smoothing it may not
 */
class InMemoryGlucoseValue(
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
     * if true value is not corresponding to received value,
     * but it was recalculated to fill gap between BGs
     */
    var filledGap: Boolean = false,
    /**
     * Taken from GlucoseValue
     */
    var sourceSensor: GlucoseValue.SourceSensor
) {

    /**
     * Provide smoothed value if available,
     * non smoothed value as a fallback
     */
    val recalculated: Double get() = smoothed ?: value
}