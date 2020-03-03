package info.nightscout.androidaps.utils

import android.text.Spanned
import android.util.LongSparseArray
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile

object TirCalculator {
    fun calculate(days: Long, lowMgdl: Double, highMgdl: Double): LongSparseArray<TIR> {
        if (lowMgdl < 39) throw RuntimeException("Low below 39")
        if (lowMgdl > highMgdl) throw RuntimeException("Low > High")
        val startTime = MidnightTime.calc(DateUtil.now()) - T.days(days).msecs()
        val endTime = MidnightTime.calc(DateUtil.now())

        val bgReadings = MainApp.getDbHelper().getBgreadingsDataFromTime(startTime, endTime, true)
        val result = LongSparseArray<TIR>()
        for (bg in bgReadings) {
            val midnight = MidnightTime.calc(bg.date)
            var tir = result[midnight]
            if (tir == null) {
                tir = TIR(midnight, lowMgdl, highMgdl)
                result.append(midnight, tir)
            }
            if (bg.value < 39) tir.error()
            if (bg.value >= 39 && bg.value < lowMgdl) tir.below()
            if (bg.value in lowMgdl..highMgdl) tir.inRange()
            if (bg.value > highMgdl) tir.above()
        }
        return result
    }

    fun averageTIR(tirs: LongSparseArray<TIR>): TIR {
        val totalTir = if (tirs.size() > 0) {
            TIR(tirs.valueAt(0).date, tirs.valueAt(0).lowThreshold, tirs.valueAt(0).highThreshold)
        } else {
            TIR(7, 70.0, 180.0)
        }
        for (i in 0 until tirs.size()) {
            val tir = tirs.valueAt(i)
            totalTir.below += tir.below
            totalTir.inRange += tir.inRange
            totalTir.above += tir.above
            totalTir.error += tir.error
            totalTir.count += tir.count
        }
        return totalTir
    }

    fun stats(): Spanned {
        val lowTirMgdl = Constants.STATS_RANGE_LOW_MMOL * Constants.MMOLL_TO_MGDL
        val highTirMgdl = Constants.STATS_RANGE_HIGH_MMOL * Constants.MMOLL_TO_MGDL
        val lowTitMgdl = Constants.STATS_TARGET_LOW_MMOL * Constants.MMOLL_TO_MGDL
        val highTitMgdl = Constants.STATS_TARGET_HIGH_MMOL * Constants.MMOLL_TO_MGDL

        val tir7 = calculate(7, lowTirMgdl, highTirMgdl)
        val averageTir7 = averageTIR(tir7)
        val tir30 = calculate(30, lowTirMgdl, highTirMgdl)
        val averageTir30 = averageTIR(tir30)
        val tit7 = calculate(7, lowTitMgdl, highTitMgdl)
        val averageTit7 = averageTIR(tit7)
        val tit30 = calculate(30, lowTitMgdl, highTitMgdl)
        val averageTit30 = averageTIR(tit30)
        return HtmlHelper.fromHtml(
            "<br><b>" + MainApp.gs(R.string.tir) + ":</b><br>" +
                toText(tir7) +
                "<br><b>" + MainApp.gs(R.string.average) + " (" + Profile.toCurrentUnitsString(lowTirMgdl) + "-" + Profile.toCurrentUnitsString(highTirMgdl) + "):</b><br>" +
                averageTir7.toText(tir7.size()) + "<br>" +
                averageTir30.toText(tir30.size()) +
                "<br><b>" + MainApp.gs(R.string.average) + " (" + Profile.toCurrentUnitsString(lowTitMgdl) + "-" + Profile.toCurrentUnitsString(highTitMgdl) + "):</b><br>" +
                averageTit7.toText(tit7.size()) + "<br>" +
                averageTit30.toText(tit30.size())
        )
    }

    fun toText(tirs: LongSparseArray<TIR>): String {
        var t = ""
        for (i in 0 until tirs.size()) {
            t += "${tirs.valueAt(i).toText()}<br>"
        }
        return t
    }

}