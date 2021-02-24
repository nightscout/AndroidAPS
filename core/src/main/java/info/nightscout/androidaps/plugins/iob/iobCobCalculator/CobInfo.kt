package info.nightscout.androidaps.plugins.iob.iobCobCalculator

import info.nightscout.androidaps.utils.DecimalFormatter

/** All COB up to now, including carbs not yet processed by IobCob calculation.  */
class CobInfo(val displayCob: Double?, val futureCarbs: Double) {

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
}