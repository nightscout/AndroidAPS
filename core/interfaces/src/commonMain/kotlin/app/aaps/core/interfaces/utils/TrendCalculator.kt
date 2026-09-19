package app.aaps.core.interfaces.utils

import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.aps.AutosensDataStore

/**
 *  Convert BG direction value to trend arrow or calculate it if not provided
 *  If calculation is necessary more values are loaded from DB
 */
interface TrendCalculator {

    /**
     * Provide or calculate trend from newest bucketed data
     *
     * @param autosensDataStore bucketed data
     * @return TrendArrow
     */
    fun getTrendArrow(autosensDataStore: AutosensDataStore): TrendArrow?

    /**
     * Provide or calculate trend from newest bucketed data
     *
     * @param autosensDataStore bucketed data
     * @return string description of TrendArrow
     */
    fun getTrendDescription(autosensDataStore: AutosensDataStore): String
}