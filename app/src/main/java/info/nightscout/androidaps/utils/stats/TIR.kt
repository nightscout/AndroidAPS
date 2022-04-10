package info.nightscout.androidaps.utils.stats

import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
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

    private fun belowPct() = if (count > 0) (below.toDouble() / count * 100.0).roundToInt() else 0
    private fun inRangePct() = if (count > 0) 100 - belowPct() - abovePct() else 0
    private fun abovePct() = if (count > 0) (above.toDouble() / count * 100.0).roundToInt() else 0

    fun toText(rh: ResourceHelper, dateUtil: DateUtil): String = rh.gs(R.string.tirformat, dateUtil.dateStringShort(date), belowPct(), inRangePct(), abovePct())

    fun toText(rh: ResourceHelper, days: Int): String = rh.gs(R.string.tirformat, "%02d".format(days) + " " + rh.gs(R.string.days), belowPct(), inRangePct(), abovePct())
}
