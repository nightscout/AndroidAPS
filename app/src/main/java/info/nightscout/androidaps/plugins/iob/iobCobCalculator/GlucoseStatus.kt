package info.nightscout.androidaps.plugins.iob.iobCobCalculator

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.Round
import java.util.*
import javax.inject.Inject
import kotlin.math.roundToLong

class GlucoseStatus(private val injector: HasAndroidInjector) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var iobCobCalculatorPlugin: IobCobCalculatorPlugin

    var glucose = 0.0
    var noise = 0.0
    var delta = 0.0
    var avgDelta = 0.0
    var shortAvgDelta = 0.0
    var longAvgDelta = 0.0
    var date = 0L

    init {
        injector.androidInjector().inject(this)
    }

    fun log(): String {
        return "Glucose: " + DecimalFormatter.to0Decimal(glucose) + " mg/dl " +
            "Noise: " + DecimalFormatter.to0Decimal(noise) + " " +
            "Delta: " + DecimalFormatter.to0Decimal(delta) + " mg/dl" +
            "Short avg. delta: " + " " + DecimalFormatter.to2Decimal(shortAvgDelta) + " mg/dl " +
            "Long avg. delta: " + DecimalFormatter.to2Decimal(longAvgDelta) + " mg/dl"
    }

    fun round(): GlucoseStatus {
        glucose = Round.roundTo(glucose, 0.1)
        noise = Round.roundTo(noise, 0.01)
        delta = Round.roundTo(delta, 0.01)
        avgDelta = Round.roundTo(avgDelta, 0.01)
        shortAvgDelta = Round.roundTo(shortAvgDelta, 0.01)
        longAvgDelta = Round.roundTo(longAvgDelta, 0.01)
        return this
    }

    val glucoseStatusData: GlucoseStatus?
        get() = getGlucoseStatusData(false)

    fun getGlucoseStatusData(allowOldData: Boolean): GlucoseStatus? {
        synchronized(iobCobCalculatorPlugin.dataLock) {
            val data = iobCobCalculatorPlugin.bgReadings
            val sizeRecords = data.size
            if (sizeRecords == 0) {
                aapsLogger.debug(LTag.GLUCOSE, "sizeRecords==0")
                return null
            }
            if (data[0].timestamp < DateUtil.now() - 7 * 60 * 1000L && !allowOldData) {
                aapsLogger.debug(LTag.GLUCOSE, "oldData")
                return null
            }
            val now = data[0]
            val nowDate = now.timestamp
            var change: Double
            if (sizeRecords == 1) {
                val status = GlucoseStatus(injector)
                status.glucose = now.value
                status.noise = 0.0
                status.shortAvgDelta = 0.0
                status.delta = 0.0
                status.longAvgDelta = 0.0
                status.avgDelta = 0.0 // for OpenAPS MA
                status.date = nowDate
                aapsLogger.debug(LTag.GLUCOSE, "sizeRecords==1")
                return status.round()
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
            val status = GlucoseStatus(injector)
            status.glucose = now.value
            status.date = nowDate
            status.noise = 0.0 //for now set to nothing as not all CGMs report noise
            status.shortAvgDelta = average(shortDeltas)
            if (lastDeltas.isEmpty()) {
                status.delta = status.shortAvgDelta
            } else {
                status.delta = average(lastDeltas)
            }
            status.longAvgDelta = average(longDeltas)
            status.avgDelta = status.shortAvgDelta // for OpenAPS MA
            aapsLogger.debug(LTag.GLUCOSE, status.log())
            return status.round()
        }
    }

    companion object {

        fun average(array: ArrayList<Double>): Double {
            var sum = 0.0
            if (array.size == 0) return 0.0
            for (value in array) {
                sum += value
            }
            return sum / array.size
        }
    }
}