package app.aaps.core.interfaces.utils

import app.aaps.core.data.model.TrendArrow

/**
 *  Convert BG direction value to trend arrow or calculate it if not provided
 *  If calculation is necessary more values are loaded from DB
 */
interface TrendCalculator {

    /**
     * Provide or calculate trend from newest bucketed data
     *
     * @return TrendArrow
     */
    fun getTrendArrow(): TrendArrow?

    /**
     * Provide or calculate trend from newest bucketed data
     *
     * @return string description of TrendArrow
     */
    fun getTrendDescription(): String
}