package info.nightscout.androidaps.utils

import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrendCalculator @Inject constructor(
    private val repository: AppRepository
) {

    fun getTrendArrow(glucoseValue: GlucoseValue?): GlucoseValue.TrendArrow =
        when {
            glucoseValue?.trendArrow == null                        -> GlucoseValue.TrendArrow.NONE
            glucoseValue.trendArrow != GlucoseValue.TrendArrow.NONE -> glucoseValue.trendArrow
            else                                                    -> calculateDirection(glucoseValue)
        }

    private fun calculateDirection(glucoseValue: GlucoseValue): GlucoseValue.TrendArrow {

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
}