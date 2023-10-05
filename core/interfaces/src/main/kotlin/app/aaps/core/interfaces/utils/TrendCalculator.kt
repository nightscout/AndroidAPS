package app.aaps.core.interfaces.utils

import app.aaps.core.data.db.TrendArrow
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.database.entities.GlucoseValue

/**
 *  Convert BG direction value to trend arrow or calculate it if not provided
 *  If calculation is necessary more values are loaded from DB
 */
interface TrendCalculator {

    /**
     * Provide or calculate trend
     *
     * @param glucoseValue BG
     * @return TrendArrow
     */
    fun getTrendArrow(glucoseValue: GlucoseValue?): TrendArrow

    /**
     * Provide or calculate trend
     *
     * @param glucoseValue BG
     * @return TrendArrow
     */
    fun getTrendArrow(glucoseValue: InMemoryGlucoseValue?): TrendArrow

    /**
     * Provide or calculate trend from newest bucketed data
     *
     * @return TrendArrow
     */
    fun getTrendArrow(): TrendArrow?

    /**
     * Provide or calculate trend
     *
     * @param glucoseValue BG
     * @return string description of TrendArrow
     */
    fun getTrendDescription(glucoseValue: GlucoseValue?): String

    /**
     * Provide or calculate trend from newest bucketed data
     *
     * @return string description of TrendArrow
     */
    fun getTrendDescription(): String
}