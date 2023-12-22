package app.aaps.plugins.aps.extensions

import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DecimalFormatter

fun PumpEnactResult.toHtml(rh: ResourceHelper, decimalFormatter: DecimalFormatter): String {
    var ret = "<b>" + rh.gs(app.aaps.core.ui.R.string.success) + "</b>: " + success
    if (queued) {
        ret = rh.gs(app.aaps.core.ui.R.string.waitingforpumpresult)
    } else if (enacted) {
        when {
            bolusDelivered > 0         -> {
                ret += "<br><b>" + rh.gs(app.aaps.core.ui.R.string.enacted) + "</b>: " + enacted
                if (comment.isNotEmpty()) ret += "<br><b>" + rh.gs(app.aaps.core.ui.R.string.comment) + "</b>: " + comment
                ret += "<br><b>" + rh.gs(app.aaps.core.ui.R.string.smb_shortname) + "</b>: " + bolusDelivered + " " + rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname)
            }

            isTempCancel               -> {
                ret += "<br><b>" + rh.gs(app.aaps.core.ui.R.string.enacted) + "</b>: " + enacted
                ret += "<br><b>" + rh.gs(app.aaps.core.ui.R.string.comment) + "</b>: " + comment +
                    "<br>" + rh.gs(app.aaps.core.ui.R.string.cancel_temp)
            }

            isPercent && percent != -1 -> {
                ret += "<br><b>" + rh.gs(app.aaps.core.ui.R.string.enacted) + "</b>: " + enacted
                if (comment.isNotEmpty()) ret += "<br><b>" + rh.gs(app.aaps.core.ui.R.string.comment) + "</b>: " + comment
                ret += "<br><b>" + rh.gs(app.aaps.core.ui.R.string.duration) + "</b>: " + duration + " min"
                ret += "<br><b>" + rh.gs(app.aaps.core.ui.R.string.percent) + "</b>: " + percent + "%"
            }

            absolute != -1.0           -> {
                ret += "<br><b>" + rh.gs(app.aaps.core.ui.R.string.enacted) + "</b>: " + enacted
                if (comment.isNotEmpty()) ret += "<br><b>" + rh.gs(app.aaps.core.ui.R.string.comment) + "</b>: " + comment
                ret += "<br><b>" + rh.gs(app.aaps.core.ui.R.string.duration) + "</b>: " + duration + " min"
                ret += "<br><b>" + rh.gs(app.aaps.core.ui.R.string.absolute) + "</b>: " + decimalFormatter.to2Decimal(absolute) + " U/h"
            }
        }
    } else {
        if (comment.isNotEmpty()) ret += "<br><b>" + rh.gs(app.aaps.core.ui.R.string.comment) + "</b>: " + comment
    }
    return ret
}