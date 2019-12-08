package info.nightscout.androidaps.utils

import android.util.LongSparseArray
import info.nightscout.androidaps.MainApp

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
        val totalTir =
                if (tirs.size() > 0) TIR(tirs.valueAt(0).date, tirs.valueAt(0).lowThreshold, tirs.valueAt(0).highThreshold)
                else TIR(7, 70.0, 180.0)
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

    fun toText(tirs: LongSparseArray<TIR>): String {
        var t = ""
        for (i in 0 until tirs.size()) {
            t += "${tirs.valueAt(i).toText()}\n"
        }
        return t
    }

}