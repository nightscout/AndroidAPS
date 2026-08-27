package app.aaps.implementation.utils

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.ui.CoreUiStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

// Metro builds this now, and Dagger gets it through a @Provides delegate in `:app`. Scoped with
// Metro's @SingleIn, not javax @Singleton: the graph is generated in `:app`, which has no Dagger
// interop, so a javax scope there is ignored and every read would build a new one.
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class TrendCalculatorImpl @Inject constructor(
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

    override fun getTrendDescription(autosensDataStore: AutosensDataStore): String {
        return when (getTrendArrow(autosensDataStore)) {
            TrendArrow.DOUBLE_DOWN     -> rh.gs(CoreUiStrings.a11y_arrow_double_down)
            TrendArrow.SINGLE_DOWN     -> rh.gs(CoreUiStrings.a11y_arrow_single_down)
            TrendArrow.FORTY_FIVE_DOWN -> rh.gs(CoreUiStrings.a11y_arrow_forty_five_down)
            TrendArrow.FLAT            -> rh.gs(CoreUiStrings.a11y_arrow_flat)
            TrendArrow.FORTY_FIVE_UP   -> rh.gs(CoreUiStrings.a11y_arrow_forty_five_up)
            TrendArrow.SINGLE_UP       -> rh.gs(CoreUiStrings.a11y_arrow_single_up)
            TrendArrow.DOUBLE_UP       -> rh.gs(CoreUiStrings.a11y_arrow_double_up)
            TrendArrow.NONE            -> rh.gs(CoreUiStrings.a11y_arrow_none)
            else                       -> rh.gs(CoreUiStrings.a11y_arrow_unknown)
        }
    }

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