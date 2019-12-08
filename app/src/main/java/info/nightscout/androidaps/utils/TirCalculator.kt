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
            val tir = result[midnight] ?: TIR(midnight, lowMgdl, highMgdl)
            if (bg.value < 39) tir.error()
            if (bg.value >= 39 && bg.value < lowMgdl) tir.below()
            if (bg.value in lowMgdl..highMgdl) tir.inRange()
            if (bg.value > highMgdl) tir.inRange()
        }
        return result
    }
}