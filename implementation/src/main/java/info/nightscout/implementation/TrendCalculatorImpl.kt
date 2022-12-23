package info.nightscout.implementation

import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.aps.AutosensDataStore
import info.nightscout.interfaces.iob.InMemoryGlucoseValue
import info.nightscout.interfaces.utils.TrendCalculator
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.T
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrendCalculatorImpl @Inject constructor(
    private val repository: AppRepository,
    private val rh: ResourceHelper
) : TrendCalculator {

    override fun getTrendArrow(glucoseValue: GlucoseValue?): GlucoseValue.TrendArrow =
        when {
            glucoseValue?.trendArrow == null                        -> GlucoseValue.TrendArrow.NONE
            glucoseValue.trendArrow != GlucoseValue.TrendArrow.NONE -> glucoseValue.trendArrow
            else                                                    -> calculateDirection(InMemoryGlucoseValue(glucoseValue))
        }

    override fun getTrendArrow(glucoseValue: InMemoryGlucoseValue?): GlucoseValue.TrendArrow =
        when {
            glucoseValue?.trendArrow == null                        -> GlucoseValue.TrendArrow.NONE
            glucoseValue.trendArrow != GlucoseValue.TrendArrow.NONE -> glucoseValue.trendArrow
            else                                                    -> calculateDirection(glucoseValue)
        }

    override fun getTrendDescription(glucoseValue: GlucoseValue?): String =
        when (getTrendArrow(glucoseValue)) {
            GlucoseValue.TrendArrow.DOUBLE_DOWN     -> rh.gs(info.nightscout.core.ui.R.string.a11y_arrow_double_down)
            GlucoseValue.TrendArrow.SINGLE_DOWN     -> rh.gs(info.nightscout.core.ui.R.string.a11y_arrow_single_down)
            GlucoseValue.TrendArrow.FORTY_FIVE_DOWN -> rh.gs(info.nightscout.core.ui.R.string.a11y_arrow_forty_five_down)
            GlucoseValue.TrendArrow.FLAT            -> rh.gs(info.nightscout.core.ui.R.string.a11y_arrow_flat)
            GlucoseValue.TrendArrow.FORTY_FIVE_UP   -> rh.gs(info.nightscout.core.ui.R.string.a11y_arrow_forty_five_up)
            GlucoseValue.TrendArrow.SINGLE_UP       -> rh.gs(info.nightscout.core.ui.R.string.a11y_arrow_single_up)
            GlucoseValue.TrendArrow.DOUBLE_UP       -> rh.gs(info.nightscout.core.ui.R.string.a11y_arrow_double_up)
            GlucoseValue.TrendArrow.NONE            -> rh.gs(info.nightscout.core.ui.R.string.a11y_arrow_none)
            else                                    -> rh.gs(info.nightscout.core.ui.R.string.a11y_arrow_unknown)
        }

    private fun calculateDirection(glucoseValue: InMemoryGlucoseValue): GlucoseValue.TrendArrow {

        val toTime = glucoseValue.timestamp
        val readings = repository.compatGetBgReadingsDataFromTime(toTime - T.mins(10).msecs(), toTime, false).blockingGet()

        if (readings.size < 2)
            return GlucoseValue.TrendArrow.NONE
        val current = readings[0]
        val previous = readings[1]

        // Avoid division by 0
        val slope =
            if (current.timestamp == previous.timestamp) 0.0
            else (previous.value - current.value) / (previous.timestamp - current.timestamp)

        val slopeByMinute = slope * 60000

        return when {
            slopeByMinute <= -3.5 -> GlucoseValue.TrendArrow.DOUBLE_DOWN
            slopeByMinute <= -2   -> GlucoseValue.TrendArrow.SINGLE_DOWN
            slopeByMinute <= -1   -> GlucoseValue.TrendArrow.FORTY_FIVE_DOWN
            slopeByMinute <= 1    -> GlucoseValue.TrendArrow.FLAT
            slopeByMinute <= 2    -> GlucoseValue.TrendArrow.FORTY_FIVE_UP
            slopeByMinute <= 3.5  -> GlucoseValue.TrendArrow.SINGLE_UP
            slopeByMinute <= 40   -> GlucoseValue.TrendArrow.DOUBLE_UP
            else                  -> GlucoseValue.TrendArrow.NONE
        }
    }

    override fun getTrendArrow(autosensDataStore: AutosensDataStore): GlucoseValue.TrendArrow? {
        val data = autosensDataStore.getBucketedDataTableCopy() ?: return null
        if (data.size == 0) return null
        val glucoseValue = data[0]
        return when {
            glucoseValue.value != glucoseValue.recalculated         -> calculateDirection(data) // always recalculate after smoothing
            glucoseValue.trendArrow != GlucoseValue.TrendArrow.NONE -> glucoseValue.trendArrow
            else                                                    -> calculateDirection(data)
        }
    }

    override fun getTrendDescription(autosensDataStore: AutosensDataStore): String {
        return when (getTrendArrow(autosensDataStore)) {
            GlucoseValue.TrendArrow.DOUBLE_DOWN     -> rh.gs(info.nightscout.core.ui.R.string.a11y_arrow_double_down)
            GlucoseValue.TrendArrow.SINGLE_DOWN     -> rh.gs(info.nightscout.core.ui.R.string.a11y_arrow_single_down)
            GlucoseValue.TrendArrow.FORTY_FIVE_DOWN -> rh.gs(info.nightscout.core.ui.R.string.a11y_arrow_forty_five_down)
            GlucoseValue.TrendArrow.FLAT            -> rh.gs(info.nightscout.core.ui.R.string.a11y_arrow_flat)
            GlucoseValue.TrendArrow.FORTY_FIVE_UP   -> rh.gs(info.nightscout.core.ui.R.string.a11y_arrow_forty_five_up)
            GlucoseValue.TrendArrow.SINGLE_UP       -> rh.gs(info.nightscout.core.ui.R.string.a11y_arrow_single_up)
            GlucoseValue.TrendArrow.DOUBLE_UP       -> rh.gs(info.nightscout.core.ui.R.string.a11y_arrow_double_up)
            GlucoseValue.TrendArrow.NONE            -> rh.gs(info.nightscout.core.ui.R.string.a11y_arrow_none)
            else                                    -> rh.gs(info.nightscout.core.ui.R.string.a11y_arrow_unknown)
        }
    }

    private fun calculateDirection(readings: MutableList<InMemoryGlucoseValue>): GlucoseValue.TrendArrow {

        if (readings.size < 2)
            return GlucoseValue.TrendArrow.NONE
        val current = readings[0]
        val previous = readings[1]

        // Avoid division by 0
        val slope =
            if (current.timestamp == previous.timestamp) 0.0
            else (previous.recalculated - current.recalculated) / (previous.timestamp - current.timestamp)

        val slopeByMinute = slope * 60000

        return when {
            slopeByMinute <= -3.5 -> GlucoseValue.TrendArrow.DOUBLE_DOWN
            slopeByMinute <= -2   -> GlucoseValue.TrendArrow.SINGLE_DOWN
            slopeByMinute <= -1   -> GlucoseValue.TrendArrow.FORTY_FIVE_DOWN
            slopeByMinute <= 1    -> GlucoseValue.TrendArrow.FLAT
            slopeByMinute <= 2    -> GlucoseValue.TrendArrow.FORTY_FIVE_UP
            slopeByMinute <= 3.5  -> GlucoseValue.TrendArrow.SINGLE_UP
            slopeByMinute <= 40   -> GlucoseValue.TrendArrow.DOUBLE_UP
            else                  -> GlucoseValue.TrendArrow.NONE
        }
    }
}