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

    fun belowPct() = if (count > 0) (below.toDouble() / count * 100.0).roundToInt() else 0
    fun inRangePct() = if (count > 0) (inRange.toDouble() / count * 100.0).roundToInt() else 0
    fun abovePct() = if (count > 0) (above.toDouble() / count * 100.0).roundToInt() else 0

    fun toText(resourceHelper: ResourceHelper, dateUtil: DateUtil): String = resourceHelper.gs(R.string.tirformat, dateUtil.dateStringShort(date), belowPct(), inRangePct(), abovePct())

    fun toText(resourceHelper: ResourceHelper, days: Int): String = resourceHelper.gs(R.string.tirformat, "%02d".format(days) + " " + resourceHelper.gs(R.string.days), belowPct(), inRangePct(), abovePct())
}
