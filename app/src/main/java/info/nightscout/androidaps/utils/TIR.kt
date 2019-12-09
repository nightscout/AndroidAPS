package info.nightscout.androidaps.utils

import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import kotlin.math.roundToInt

class TIR(val date: Long, val lowThreshold: Double, val highThreshold: Double) {
    internal var below = 0
    internal var inRange = 0
    internal var above = 0
    internal var error = 0
    internal var count = 0

    fun error() = run { error++ }
    fun below() = run { below++; count++ }
    fun inRange() = run { inRange++; count++ }
    fun above() = run { above++; count++ }

    fun belowPct() = if (count > 0) (below.toDouble() / count * 100.0).roundToInt() else 0
    fun inRangePct() = if (count > 0) (inRange.toDouble() / count * 100.0).roundToInt() else 0
    fun abovePct() = if (count > 0) (above.toDouble() / count * 100.0).roundToInt() else 0

    fun toText(): String = MainApp.gs(R.string.tirformat, DateUtil.dateStringShort(date), belowPct(), inRangePct(), abovePct())

    fun toText(days: Int): String = MainApp.gs(R.string.tirformat, "%02d".format(days) + " " + MainApp.gs(R.string.days), belowPct(), inRangePct(), abovePct())
}
