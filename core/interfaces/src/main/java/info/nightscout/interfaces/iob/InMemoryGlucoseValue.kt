package info.nightscout.interfaces.iob

import info.nightscout.database.entities.GlucoseValue

/**
 * Simplified [GlucoseValue] for storing in memory and calculations
 * It may correspond to GlucoseValue value in db
 * but because of 5 min recalculations and smoothing it may not
 */
class InMemoryGlucoseValue constructor(
    var timestamp: Long = 0L,
    /**
     * Value in mg/dl
     */
    var value: Double = 0.0,
    var trendArrow: GlucoseValue.TrendArrow = GlucoseValue.TrendArrow.NONE,
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

    constructor(gv: GlucoseValue) : this(timestamp = gv.timestamp, value = gv.value, trendArrow = gv.trendArrow, sourceSensor = gv.sourceSensor)

    /**
     * Provide smoothed value if available,
     * non smoothed value as a fallback
     */
    val recalculated: Double get() = smoothed ?: value
}