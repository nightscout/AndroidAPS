package info.nightscout.androidaps.plugins.iob.iobCobCalculator

import dagger.Reusable
import info.nightscout.androidaps.interfaces.IobCobCalculator
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import org.spongycastle.asn1.x500.style.RFC4519Style
import java.util.*
import javax.inject.Inject
import kotlin.math.roundToLong
import org.spongycastle.asn1.x500.style.RFC4519Style.c




@Reusable
class GlucoseStatusProvider @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val iobCobCalculator: IobCobCalculator,
    private val dateUtil: DateUtil
) {

    val glucoseStatusData: GlucoseStatus?
        get() = getGlucoseStatusData()

    fun getGlucoseStatusData(allowOldData: Boolean = false): GlucoseStatus? {
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
                date = nowDate,
                // mod 7: append 2 variables for 5% range
                dura_ISF_minutes = 0.0,
                dura_ISF_average = now.value,
                // mod 8: append 3 variables for deltas based on regression analysis
                slope05 = 0.0, // wait for longer history
                slope15 = 0.0, // wait for longer history
                slope40 = 0.0, // wait for longer history
                // mod 14f: append results from best fitting parabola
                dura_p = 0.0,
                delta_pl = 0.0,
                delta_pn = 0.0,
                bg_acceleration = 0.0,
                a_0 = 0.0,
                a_1 = 0.0,
                a_2 = 0.0,
                r_squ = 0.0
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

        // mod 7: calculate 2 variables for 5% range
        // initially just test the handling of arguments
        // status.dura05 = 11d;
        // status.avg05 = 47.11d;
        //  mod 7a: now do the real maths
        val bw = 0.05 // used for Eversense; may be lower for Dexcom
        var sumBG: Double = now.value
        var oldavg: Double = now.value
        var minutesdur = Math.round(0L / (1000.0 * 60))
        for (i in 1 until sizeRecords) {
            val then = data[i]
            val thenDate: Long = then.timestamp
            //  mod 7c: stop the series if there was a CGM gap greater than 13 minutes, i.e. 2 regular readings
            if (Math.round((nowDate - thenDate) / (1000.0 * 60)) - minutesdur > 13) {
                break
            }
            if (then.value > oldavg * (1 - bw) && then.value < oldavg * (1 + bw)) {
                sumBG += then.value
                oldavg = sumBG / (i + 1)
                minutesdur = Math.round((nowDate - thenDate) / (1000.0 * 60))
            } else {
                break
            }
        }
        var autoISFAverage = oldavg
        var autoISFDuration = minutesdur.toDouble()

        // mod 8: calculate 3 variables for deltas based on linear regression
        // initially just test the handling of arguments
        var slope05 = 1.05
        var slope15 = 1.15
        var slope40 = 1.40

        // mod 8a: now do the real maths based on
        // http://www.carl-engler-schule.de/culm/culm/culm2/th_messdaten/mdv2/auszug_ausgleichsgerade.pdf
        sumBG = 0.0 // y
        var sumt = 0L // x
        var sumBG2 = 0.0 // y^2
        var sumt2 = 0L // x^2
        var sumxy = 0.0 // x*y
        //double a;
        var b: Double // y = a + b * x
        var level = 7.5
        var minutesL: Long
        // here, longer deltas include all values from 0 up the related limit
        for (i in 0 until sizeRecords) {
            val then = data[i]
            val thenDate = then.timestamp
            minutesL = (nowDate - thenDate) / (1000L * 60)
            // watch out: the scan goes backwards in time, so delta has wrong sign
            if(i * sumt2 == sumt * sumt) {
                b = 0.0
            }
            else {
                b = (i * sumxy - sumt * sumBG) / (i * sumt2 - sumt * sumt)
            }
            if (minutesL > level && level == 7.5) {
                slope05 = -b * 5
                level = 17.5
            }
            if (minutesL > level && level == 17.5) {
                slope15 = -b * 5
                level = 42.5
            }
            if (minutesL > level && level == 42.5) {
                slope40 = -b * 5
                break
            }
            sumt += minutesL
            sumt2 += minutesL * minutesL
            sumBG += then.value
            sumBG2 += then.value * then.value
            sumxy += then.value * minutesL
        }

        // mod 14f: calculate best parabola and determine delta by extending it 5 minutes into the future
        // nach https://www.codeproject.com/Articles/63170/Least-Squares-Regression-for-Quadratic-Curve-Fitti
        //
        //  y = a2*x^2 + a1*x + a0      or
        //  y = a*x^2  + b*x  + c       respectively

        // initially just test the handling of arguments
        var ppDebug = ""
        var bestA = 0.0
        var bestB = 0.0
        var bestC = 0.0
        var duraP = 0.0
        var deltaPl = 0.0
        var deltaPn = 0.0
        var bgAcceleration = 0.0
        var corrMax = 0.0
        var a0 = 0.0
        var a1 = 0.0
        var a2 = 0.0

        //if (sizeRecords <= 3) {                      // last 3 points make a trivial parabola
        //    duraP = 0.0
        //    deltaPl = 0.0
        //    deltaPn = 0.0
        //    bgAcceleration = 0.0
        //    corrMax = 0.0
        //    a0 = 0.0
        //    a1 = 0.0
        //    a2 = 0.0
        //} else {
        if (sizeRecords > 3) {
            //double corrMin = 0.90;                  // go backwards until the correlation coefficient goes below
            var sy   = 0.0 // y
            var sx   = 0.0 // x
            var sx2  = 0.0 // x^2
            var sx3  = 0.0 // x^3
            var sx4  = 0.0 // x^4
            var sxy  = 0.0 // x*y
            var sx2y = 0.0 // x^2*y
            //  corrMax = 0.0
            val iframe = data[0]
            val time0: Long = iframe.timestamp
            var tiLast = 0.0
            //# for best numerical accurarcy time and bg must be of same order of magnitude
            val scaleTime = 300.0 //# in 5m; values are  0, -1, -2, -3, -4, ...
            val scaleBg   =  50.0 //# TIR range is now 1.4 - 3.6

            for (i in 0 until sizeRecords) {
                val then = data[i]
                val thenDate = then.timestamp
                val ti = (thenDate - time0) / 1000.0 / scaleTime
                if (-ti * scaleTime > 47 * 60 ) {                       // skip records older than 47.5 minutes
                    break
                } else if (ti < tiLast - 7.5 * 60 / scaleTime)  {       // stop scan if a CGM gap > 7.5 minutes is detected
                    if (i < 3) {                                        // history too short for fit
                        duraP = -tiLast / 60.0
                        deltaPl = 0.0
                        deltaPn = 0.0
                        bgAcceleration = 0.0
                        corrMax = 0.0
                        a0 = 0.0
                        a1 = 0.0
                        a2 = 0.0
                    }
                    break
                }
                tiLast = ti
                val bg = then.value / scaleBg
                sx += ti
                sx2 += Math.pow(ti, 2.0)
                sx3 += Math.pow(ti, 3.0)
                sx4 += Math.pow(ti, 4.0)
                sy += bg
                sxy += ti * bg
                sx2y += Math.pow(ti, 2.0) * bg
                val n = i + 1
                var D  = 0.0
                var Da = 0.0
                var Db = 0.0
                var Dc = 0.0
                if (n > 3) {
                    D = sx4 * (sx2 * n - sx * sx) - sx3 * (sx3 * n - sx * sx2) + sx2 * (sx3 * sx - sx2 * sx2)
                    Da = sx2y * (sx2 * n - sx * sx) - sxy * (sx3 * n - sx * sx2) + sy * (sx3 * sx - sx2 * sx2)
                    Db = sx4 * (sxy * n - sy * sx) - sx3 * (sx2y * n - sy * sx2) + sx2 * (sx2y * sx - sxy * sx2)
                    Dc = sx4 * (sx2 * sy - sx * sxy) - sx3 * (sx3 * sy - sx * sx2y) + sx2 * (sx3 * sxy - sx2 * sx2y)
                }
                if (D != 0.0) {
                    val a: Double = Da / D
                    b = Db / D // b defined in linear fit !?
                    val c: Double = Dc / D
                    val yMean = sy / n
                    var sSquares  = 0.0
                    var sResidualSquares = 0.0
                    for (j in 0..i) {
                        val before = data[j]
                        sSquares += Math.pow(before.value / scaleBg - yMean, 2.0)
                        val deltaT: Double = (before.timestamp - time0) / 1000.0 / scaleTime
                        val bgj: Double = a * Math.pow(deltaT, 2.0) + b * deltaT + c
                        sResidualSquares += Math.pow(before.value / scaleBg - bgj, 2.0)
                    }
                    var rSqu = 0.64
                    if (sSquares != 0.0) {
                        rSqu = 1 - sResidualSquares / sSquares
                    }
                    if (n > 3) {
                        if (rSqu >= corrMax) {
                            corrMax = rSqu
                            // double delta_t = (then_date - time_0) / 1000;
                            duraP = -ti * scaleTime / 60.0 // remember we are going backwards in time
                            val delta5Min = 5 * 60 / scaleTime
                            deltaPl = -scaleBg * (a * Math.pow(-delta5Min, 2.0) - b * delta5Min) // 5 minute slope from last fitted bg starting from last bg, i.e. t=0
                            deltaPn =  scaleBg * (a * Math.pow( delta5Min, 2.0) + b * delta5Min) // 5 minute slope to next fitted bg starting from last bg, i.e. t=0
                            bgAcceleration = 2 * a * scaleBg
                            a0 = c * scaleBg
                            a1 = b * scaleBg
                            a2 = a * scaleBg
                            bestA = a * scaleBg
                            bestB = b * scaleBg
                            bestC = c * scaleBg
                        }
                    }
                }
            }
            ppDebug = ppDebug + " coeffs=(" + bestA + " / " + bestB + " / " + bestC + "); bg date=" + time0
        }
        // Ende

        return GlucoseStatus(
            glucose = now.value,
            date = nowDate,
            noise = 0.0, //for now set to nothing as not all CGMs report noise
            shortAvgDelta = shortAverageDelta,
            delta = delta,
            longAvgDelta = average(longDeltas),
            dura_ISF_average = oldavg,
            dura_ISF_minutes = minutesdur.toDouble(),
            slope05 = slope05,
            slope15 = slope15,
            slope40 = slope40,
            dura_p = duraP,
            delta_pl = deltaPl,
            delta_pn = deltaPn,
            r_squ = corrMax,
            bg_acceleration = bgAcceleration,
            a_0 = a0,
            a_1 = a1,
            a_2 = a2,
            pp_debug = ppDebug
        ).also { aapsLogger.debug(LTag.GLUCOSE, it.log()) }.asRounded()     // +pp_debug
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