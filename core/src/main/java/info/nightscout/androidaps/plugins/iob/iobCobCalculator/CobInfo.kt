package info.nightscout.androidaps.plugins.iob.iobCobCalculator

import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.resources.ResourceHelper

/** All COB up to now, including carbs not yet processed by IobCob calculation.  */
class CobInfo(val timestamp: Long, val displayCob: Double?, val futureCarbs: Double) {

    fun generateCOBString(): String {
        var cobStringResult = "--g"
        if (displayCob != null) {
            cobStringResult = DecimalFormatter.to0Decimal(displayCob)
            if (futureCarbs > 0)
                cobStringResult += "(${DecimalFormatter.to0Decimal(futureCarbs)})"
            cobStringResult += "g"
        }
        return cobStringResult
    }

    fun displayText(rh: ResourceHelper, dateUtil: DateUtil, isDev: Boolean): String? =
        if (displayCob != null) {
            var cobText = rh.gs(R.string.format_carbs, displayCob.toInt())
            if (futureCarbs > 0) cobText += "(" + DecimalFormatter.to0Decimal(futureCarbs) + ")"
            // This is only temporary for debugging
            if (isDev) cobText += "\n" + dateUtil.timeString(timestamp)
            cobText
        } else null
}
