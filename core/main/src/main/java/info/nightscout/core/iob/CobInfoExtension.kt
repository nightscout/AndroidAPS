package info.nightscout.core.iob

import info.nightscout.interfaces.iob.CobInfo
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.shared.interfaces.ResourceHelper

fun CobInfo.generateCOBString(): String {
    var cobStringResult = "--g"
    displayCob?.let { displayCob ->
        cobStringResult = DecimalFormatter.to0Decimal(displayCob)
        if (futureCarbs > 0)
            cobStringResult += "(${DecimalFormatter.to0Decimal(futureCarbs)})"
        cobStringResult += "g"
    }
    return cobStringResult
}

fun CobInfo.displayText(rh: ResourceHelper): String? =
    displayCob?.let { displayCob ->
        var cobText = rh.gs(info.nightscout.core.ui.R.string.format_carbs, displayCob.toInt())
        if (futureCarbs > 0) cobText += "(" + DecimalFormatter.to0Decimal(futureCarbs) + ")"
        cobText
    }
