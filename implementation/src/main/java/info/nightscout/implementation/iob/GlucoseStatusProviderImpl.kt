package info.nightscout.implementation.iob

import dagger.Reusable
import info.nightscout.annotations.OpenForTesting
import info.nightscout.core.iob.asRounded
import info.nightscout.core.iob.log
import info.nightscout.interfaces.iob.GlucoseStatus
import info.nightscout.interfaces.iob.GlucoseStatusProvider
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.utils.DateUtil
import javax.inject.Inject
import kotlin.math.roundToLong

@Reusable
@OpenForTesting
class GlucoseStatusProviderImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val iobCobCalculator: IobCobCalculator,
    private val dateUtil: DateUtil
) : GlucoseStatusProvider {

    override val glucoseStatusData: GlucoseStatus?
        get() = getGlucoseStatusData()

    override fun getGlucoseStatusData(allowOldData: Boolean): GlucoseStatus? {
        val data = iobCobCalculator.ads.getBucketedDataTableCopy() ?: return null
        val sizeRecords = data.size
        if (sizeRecords == 0) {
            aapsLogger.debug(LTag.GLUCOSE, "sizeRecords==0")
            return null
        }
        if (data[0].timestamp < dateUtil.now() - 7 * 60 * 1000L && !allowOldData) {
            aapsLogger.debug(LTag.GLUCOSE, "oldData")
            return null
        }
        val now = data[0]
        val nowDate = now.timestamp
        var change: Double
        if (sizeRecords == 1) {
            aapsLogger.debug(LTag.GLUCOSE, "sizeRecords==1")
            return GlucoseStatus(
                glucose = now.recalculated,
                noise = 0.0,
                delta = 0.0,
                shortAvgDelta = 0.0,
                longAvgDelta = 0.0,
                date = nowDate
            ).asRounded()
        }
        val lastDeltas = ArrayList<Double>()
        val shortDeltas = ArrayList<Double>()
        val longDeltas = ArrayList<Double>()

        // Use the latest sgv value in the now calculations
        for (i in 1 until sizeRecords) {
            if (data[i].recalculated > 38) {
                val then = data[i]
                val thenDate = then.timestamp

                val minutesAgo = ((nowDate - thenDate) / (1000.0 * 60)).roundToLong()
                // multiply by 5 to get the same units as delta, i.e. mg/dL/5m
                change = now.recalculated - then.recalculated
                val avgDel = change / minutesAgo * 5
                aapsLogger.debug(LTag.GLUCOSE, "$then minutesAgo=$minutesAgo avgDelta=$avgDel")

                // use the average of all data points in the last 2.5m for all further "now" calculations
                // if (0 < minutesAgo && minutesAgo < 2.5) {
                //     // Keep and average all values within the last 2.5 minutes
                //     nowValueList.add(then.recalculated)
                //     now.value = average(nowValueList)
                //     // short_deltas are calculated from everything ~5-15 minutes ago
                // } else
                if (2.5 < minutesAgo && minutesAgo < 17.5) {
                    shortDeltas.add(avgDel)
                    // last_deltas are calculated from everything ~5 minutes ago
                    if (2.5 < minutesAgo && minutesAgo < 7.5) {
                        lastDeltas.add(avgDel)
                    }
                    // long_deltas are calculated from everything ~20-40 minutes ago
                } else if (17.5 < minutesAgo && minutesAgo < 42.5) {
                    longDeltas.add(avgDel)
                } else {
                    // Do not process any more records after >= 42.5 minutes
                    break
                }
            }
        }
        val shortAverageDelta = average(shortDeltas)
        val delta = if (lastDeltas.isEmpty()) {
            shortAverageDelta
        } else {
            average(lastDeltas)
        }
        return GlucoseStatus(
            glucose = now.recalculated,
            date = nowDate,
            noise = 0.0, //for now set to nothing as not all CGMs report noise
            shortAvgDelta = shortAverageDelta,
            delta = delta,
            longAvgDelta = average(longDeltas),
        ).also { aapsLogger.debug(LTag.GLUCOSE, it.log()) }.asRounded()
    }

    /* Real BG (previous) version
           override fun getGlucoseStatusData(allowOldData: Boolean): GlucoseStatus? {
            val data = iobCobCalculator.ads.getBgReadingsDataTableCopy()
            val sizeRecords = data.size
            if (sizeRecords == 0) {
                aapsLogger.debug(LTag.GLUCOSE, "sizeRecords==0")
                return null
            }
            if (data[0].timestamp < dateUtil.now() - 7 * 60 * 1000L && !allowOldData) {
                aapsLogger.debug(LTag.GLUCOSE, "oldData")
                return null
            }
            val now = data[0]
            val nowDate = now.timestamp
            var change: Double
            if (sizeRecords == 1) {
                aapsLogger.debug(LTag.GLUCOSE, "sizeRecords==1")
                return GlucoseStatus(
                    glucose = now.value,
                    noise = 0.0,
                    delta = 0.0,
                    shortAvgDelta = 0.0,
                    longAvgDelta = 0.0,
                    date = nowDate
                ).asRounded()
            }
            val nowValueList = ArrayList<Double>()
            val lastDeltas = ArrayList<Double>()
            val shortDeltas = ArrayList<Double>()
            val longDeltas = ArrayList<Double>()

            // Use the latest sgv value in the now calculations
            nowValueList.add(now.value)
            for (i in 1 until sizeRecords) {
                if (data[i].value > 38) {
                    val then = data[i]
                    val thenDate = then.timestamp

                    val minutesAgo = ((nowDate - thenDate) / (1000.0 * 60)).roundToLong()
                    // multiply by 5 to get the same units as delta, i.e. mg/dL/5m
                    change = now.value - then.value
                    val avgDel = change / minutesAgo * 5
                    aapsLogger.debug(LTag.GLUCOSE, "$then minutesAgo=$minutesAgo avgDelta=$avgDel")

                    // use the average of all data points in the last 2.5m for all further "now" calculations
                    if (0 < minutesAgo && minutesAgo < 2.5) {
                        // Keep and average all values within the last 2.5 minutes
                        nowValueList.add(then.value)
                        now.value = average(nowValueList)
                        // short_deltas are calculated from everything ~5-15 minutes ago
                    } else if (2.5 < minutesAgo && minutesAgo < 17.5) {
                        //console.error(minutesAgo, avgDelta);
                        shortDeltas.add(avgDel)
                        // last_deltas are calculated from everything ~5 minutes ago
                        if (2.5 < minutesAgo && minutesAgo < 7.5) {
                            lastDeltas.add(avgDel)
                        }
                        // long_deltas are calculated from everything ~20-40 minutes ago
                    } else if (17.5 < minutesAgo && minutesAgo < 42.5) {
                        longDeltas.add(avgDel)
                    } else {
                        // Do not process any more records after >= 42.5 minutes
                        break
                    }
                }
            }
            val shortAverageDelta = average(shortDeltas)
            val delta = if (lastDeltas.isEmpty()) {
                shortAverageDelta
            } else {
                average(lastDeltas)
            }
            return GlucoseStatus(
                glucose = now.value,
                date = nowDate,
                noise = 0.0, //for now set to nothing as not all CGMs report noise
                shortAvgDelta = shortAverageDelta,
                delta = delta,
                longAvgDelta = average(longDeltas),
            ).also { aapsLogger.debug(LTag.GLUCOSE, it.log()) }.asRounded()
        }

    */
    companion object {

        fun average(array: ArrayList<Double>): Double {
            var sum = 0.0
            if (array.isEmpty()) return 0.0
            for (value in array) {
                sum += value
            }
            return sum / array.size
        }
    }
}