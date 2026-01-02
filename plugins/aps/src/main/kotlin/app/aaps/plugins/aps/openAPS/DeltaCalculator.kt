package app.aaps.plugins.aps.openAPS

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dagger.Reusable
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

@Reusable
class DeltaCalculator @Inject constructor(
    private val aapsLogger: AAPSLogger
) {

    /**
     * Holds the results from the delta calculations.
     */
    data class DeltaResult(
        val delta: Double,
        val shortAvgDelta: Double,
        val longAvgDelta: Double
    )

    /**
     * Calculates delta, short- and long-average delta from InMemoryGlucoseValue.
     *
     * @param data A list of historical glucose data, sorted from newest to oldest.
     * @return A [DeltaResult] containing the calculated deltas.
     */
    fun calculateDeltas(data:  MutableList<InMemoryGlucoseValue>): DeltaResult {
        if (data.size < 2) {
            return DeltaResult(0.0, 0.0, 0.0)
        }

        var change: Double
        val lastDeltas = mutableListOf<Double>()
        val shortDeltas = mutableListOf<Double>()
        val longDeltas = mutableListOf<Double>()

        val now = data[0]
        val nowDate = now.timestamp
        // start at data[1] as data[0] is the value used in the now calculations
        for (i in 1 until data.size) {
            if (data[i].recalculated > minBgValue) {
                val then = data[i]
                val thenDate = then.timestamp
                val minutesAgo = (nowDate - thenDate).milliseconds.toDouble(DurationUnit.MINUTES)
                change = now.recalculated - then.recalculated
                val avgDel = change / minutesAgo * 5 // multiply by 5 to get the same units as delta, i.e. mg/dL/5m
                // aapsLogger.debug(LTag.GLUCOSE, "$then Bucketed=$minutesAgo valueAgo=${then.value} recalcAgo=${then.recalculated} smooth=${then.smoothed} filled=${then.filledGap} avgDelta=$avgDel")

                // use the average of all data points in the last 2.5m for all further "now" calculations
                // if (0 < minutesAgo && minutesAgo < 2.5) {
                //     // Keep and average all values within the last 2.5 minutes
                //     nowValueList.add(then.recalculated)
                //     now.value = average(nowValueList)

                // values that are too recent are not considered (this check had been commented out before; now it's just being logged.)
                if (minutesAgo in 0.0 .. minLastDeltaMinutes) {
                    aapsLogger.debug(LTag.GLUCOSE, "$avgDel from $minutesAgo minutes ago is too recent to be considered.")
                }

                // last_deltas are calculated from minLastDeltaMinutes to maxLastDeltaMinutes
                if (minutesAgo in minLastDeltaMinutes .. maxLastDeltaMinutes) { //currently min: 2.5 max 7.5
//                    aapsLogger.debug(LTag.GLUCOSE, "$avgDel from $minutesAgo minutes ago added to lastDeltas")
                    lastDeltas.add(avgDel)
                }
                // short_deltas are calculated from minShortDeltaMinutes to maxShortDeltaMinutes
                if (minutesAgo in minShortDeltaMinutes .. maxShortDeltaMinutes) { //currently min: 2.5 max 17.5
//                    aapsLogger.debug(LTag.GLUCOSE, "$avgDel from $minutesAgo minutes ago added to shortDeltas")
                    shortDeltas.add(avgDel)
                }
                // long_deltas are calculated from minLongDeltaMinutes to maxLongDeltaMinutes
                if (minutesAgo in minLongDeltaMinutes .. maxLongDeltaMinutes) { //currently min: 17.5 max 42.5
//                    aapsLogger.debug(LTag.GLUCOSE, "$avgDel from $minutesAgo minutes ago added to longDeltas")
                    longDeltas.add(avgDel)
                } else if ( minutesAgo > maxLongDeltaMinutes){ //currently 42.5
                    break // Do not process any more records after maxLongDeltaMinutes
                }
            }
        }
        val shortAverageDelta = average(shortDeltas)
        val delta =
            if (lastDeltas.isEmpty()) {
                shortAverageDelta
            } else {
                average(lastDeltas)
            }

        return DeltaResult(
            delta = delta,
            shortAvgDelta = shortAverageDelta,
            longAvgDelta = average(longDeltas)
        )
    }

    companion object {

        fun average(array: List<Double>): Double {
            if (array.isEmpty()) return 0.0
            return array.sum() / array.size
        }

        private const val minBgValue = 39.0
        private const val minShortDeltaMinutes = 2.5
        private const val maxShortDeltaMinutes = 17.5
        private const val minLastDeltaMinutes = 2.5
        private const val maxLastDeltaMinutes = 7.5
        private const val minLongDeltaMinutes = 17.5
        private const val maxLongDeltaMinutes = 42.5
    }
}