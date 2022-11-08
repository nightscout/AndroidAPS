package info.nightscout.androidaps.utils

import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.entities.GlucoseValue.TrendArrow.*
import info.nightscout.androidaps.extensions.rawOrSmoothed
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrendCalculator @Inject constructor(
    private val repository: AppRepository,
    private val rh: ResourceHelper,
    private val sp: SP
) {

    fun getTrendArrow(glucoseValue: GlucoseValue?): GlucoseValue.TrendArrow =
        when {
            glucoseValue?.trendArrow == null -> NONE
            glucoseValue.trendArrow != NONE  -> glucoseValue.trendArrow
            else                             -> calculateDirection(glucoseValue)
        }

    fun getTrendDescription(glucoseValue: GlucoseValue?): String =
        when (getTrendArrow(glucoseValue)) {
            DOUBLE_DOWN     -> rh.gs(R.string.a11y_arrow_double_down)
            SINGLE_DOWN     -> rh.gs(R.string.a11y_arrow_single_down)
            FORTY_FIVE_DOWN -> rh.gs(R.string.a11y_arrow_forty_five_down)
            FLAT            -> rh.gs(R.string.a11y_arrow_flat)
            FORTY_FIVE_UP   -> rh.gs(R.string.a11y_arrow_forty_five_up)
            SINGLE_UP       -> rh.gs(R.string.a11y_arrow_single_up)
            DOUBLE_UP       -> rh.gs(R.string.a11y_arrow_double_up)
            NONE            -> rh.gs(R.string.a11y_arrow_none)
            else            -> rh.gs(R.string.a11y_arrow_unknown)
        }

    private fun calculateDirection(glucoseValue: GlucoseValue): GlucoseValue.TrendArrow {

        val toTime = glucoseValue.timestamp
        val readings = repository.compatGetBgReadingsDataFromTime(toTime - T.mins(10).msecs(), toTime, false).blockingGet()

        if (readings.size < 2)
            return NONE
        val current = readings[0]
        val previous = readings[1]

        // Avoid division by 0
        val slope =
            if (current.timestamp == previous.timestamp) 0.0
            else (previous.rawOrSmoothed(sp) - current.rawOrSmoothed(sp)) / (previous.timestamp - current.timestamp)

        val slopeByMinute = slope * 60000

        return when {
            slopeByMinute <= -3.5 -> DOUBLE_DOWN
            slopeByMinute <= -2   -> SINGLE_DOWN
            slopeByMinute <= -1   -> FORTY_FIVE_DOWN
            slopeByMinute <= 1    -> FLAT
            slopeByMinute <= 2    -> FORTY_FIVE_UP
            slopeByMinute <= 3.5  -> SINGLE_UP
            slopeByMinute <= 40   -> DOUBLE_UP
            else                  -> NONE
        }
    }
}
