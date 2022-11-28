package info.nightscout.core.pump

import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.shared.interfaces.ResourceHelper

fun PumpEnactResult.toHtml(rh: ResourceHelper): String {
    var ret = "<b>" + rh.gs(info.nightscout.core.main.R.string.success) + "</b>: " + success
    if (queued) {
        ret = rh.gs(info.nightscout.core.main.R.string.waitingforpumpresult)
    } else if (enacted) {
        when {
            bolusDelivered > 0         -> {
                ret += "<br><b>" + rh.gs(info.nightscout.core.main.R.string.enacted) + "</b>: " + enacted
                if (comment.isNotEmpty()) ret += "<br><b>" + rh.gs(info.nightscout.core.main.R.string.comment) + "</b>: " + comment
                ret += "<br><b>" + rh.gs(info.nightscout.core.main.R.string.smb_shortname) + "</b>: " + bolusDelivered + " " + rh.gs(info.nightscout.core.main.R.string.insulin_unit_shortname)
            }

            isTempCancel               -> {
                ret += "<br><b>" + rh.gs(info.nightscout.core.main.R.string.enacted) + "</b>: " + enacted
                ret += "<br><b>" + rh.gs(info.nightscout.core.main.R.string.comment) + "</b>: " + comment +
                    "<br>" + rh.gs(info.nightscout.core.main.R.string.cancel_temp)
            }

            isPercent && percent != -1 -> {
                ret += "<br><b>" + rh.gs(info.nightscout.core.main.R.string.enacted) + "</b>: " + enacted
                if (comment.isNotEmpty()) ret += "<br><b>" + rh.gs(info.nightscout.core.main.R.string.comment) + "</b>: " + comment
                ret += "<br><b>" + rh.gs(info.nightscout.core.main.R.string.duration) + "</b>: " + duration + " min"
                ret += "<br><b>" + rh.gs(info.nightscout.core.main.R.string.percent) + "</b>: " + percent + "%"
            }

            absolute != -1.0           -> {
                ret += "<br><b>" + rh.gs(info.nightscout.core.main.R.string.enacted) + "</b>: " + enacted
                if (comment.isNotEmpty()) ret += "<br><b>" + rh.gs(info.nightscout.core.main.R.string.comment) + "</b>: " + comment
                ret += "<br><b>" + rh.gs(info.nightscout.core.main.R.string.duration) + "</b>: " + duration + " min"
                ret += "<br><b>" + rh.gs(info.nightscout.core.main.R.string.absolute) + "</b>: " + DecimalFormatter.to2Decimal(absolute) + " U/h"
            }
        }
    } else {
        if (comment.isNotEmpty()) ret += "<br><b>" + rh.gs(info.nightscout.core.main.R.string.comment) + "</b>: " + comment
    }
    return ret
}