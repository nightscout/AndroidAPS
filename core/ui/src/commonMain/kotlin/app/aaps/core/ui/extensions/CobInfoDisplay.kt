package app.aaps.core.ui.extensions

import app.aaps.core.data.iob.CobInfo
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.ui.UiStrings

/**
 * Text for a [CobInfo] on screen.
 *
 * These build user visible text out of this module's strings, so they belong here. They used to sit
 * in `:core:objects`, which made a domain module depend on this one only to name a string.
 */
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

fun CobInfo.displayText(rh: TextResolver, decimalFormatter: DecimalFormatter): String? =
    displayCob?.let { displayCob ->
        var cobText = rh.gs(UiStrings.format_carbs, displayCob.toInt())
        if (futureCarbs > 0) cobText += "(" + decimalFormatter.to0Decimal(futureCarbs) + ")"
        cobText
    }
