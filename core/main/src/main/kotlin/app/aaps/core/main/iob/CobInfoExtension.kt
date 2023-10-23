package app.aaps.core.main.iob

import app.aaps.core.interfaces.iob.CobInfo
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DecimalFormatter

fun CobInfo.generateCOBString(decimalFormatter: DecimalFormatter): String {
    var cobStringResult = "--g"
    displayCob?.let { displayCob ->
        cobStringResult = decimalFormatter.to0Decimal(displayCob)
        if (futureCarbs > 0)
            cobStringResult += "(${decimalFormatter.to0Decimal(futureCarbs)})"
        cobStringResult += "g"
    }
    return cobStringResult
}

fun CobInfo.displayText(rh: ResourceHelper, decimalFormatter: DecimalFormatter): String? =
    displayCob?.let { displayCob ->
        var cobText = rh.gs(app.aaps.core.ui.R.string.format_carbs, displayCob.toInt())
        if (futureCarbs > 0) cobText += "(" + decimalFormatter.to0Decimal(futureCarbs) + ")"
        cobText
    }
