package app.aaps.implementation.utils

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.ui.extensions.directionToDescription
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class TrendCalculatorImpl(
    private val rh: TextResolver
) : TrendCalculator {

    override fun getTrendArrow(autosensDataStore: AutosensDataStore): TrendArrow? {
        val data = autosensDataStore.getBucketedDataTableCopy() ?: return null
        if (data.isEmpty()) return null
        /* Change 4.0.0 - always calculate from bucketed data
        val glucoseValue = data[0]
        val trend = when {
            glucoseValue.value != glucoseValue.recalculated -> calculateDirection(data) // always recalculate after smoothing
            glucoseValue.trendArrow != TrendArrow.NONE      -> glucoseValue.trendArrow
            else                                            -> calculateDirection(data)
        }
         */
        return calculateDirection(data)
    }

    override fun getTrendDescription(autosensDataStore: AutosensDataStore): String =
        rh.gs(getTrendArrow(autosensDataStore).directionToDescription())

    private fun calculateDirection(readings: MutableList<InMemoryGlucoseValue>): TrendArrow {

        if (readings.size < 2)
            return TrendArrow.NONE
        val current = readings[0]
        val previous = readings[1]

        // Avoid division by 0
        val slope =
            if (current.timestamp == previous.timestamp) 0.0
            else (previous.recalculated - current.recalculated) / (previous.timestamp - current.timestamp)

        val slopeByMinute = slope * 60000

        return when {
            slopeByMinute <= -3.5 -> TrendArrow.DOUBLE_DOWN
            slopeByMinute <= -2   -> TrendArrow.SINGLE_DOWN
            slopeByMinute <= -1   -> TrendArrow.FORTY_FIVE_DOWN
            slopeByMinute <= 1    -> TrendArrow.FLAT
            slopeByMinute <= 2    -> TrendArrow.FORTY_FIVE_UP
            slopeByMinute <= 3.5  -> TrendArrow.SINGLE_UP
            slopeByMinute <= 40   -> TrendArrow.DOUBLE_UP
            else                  -> TrendArrow.NONE
        }
    }
}
