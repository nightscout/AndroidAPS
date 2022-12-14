package info.nightscout.pump.virtual.extensions

import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.shared.interfaces.ResourceHelper

fun PumpEnactResult.toText(rh: ResourceHelper): String {
    var ret = rh.gs(info.nightscout.core.ui.R.string.success) + ": " + success
    if (enacted) {
        when {
            bolusDelivered > 0 -> {
                ret += "\n${rh.gs(info.nightscout.core.ui.R.string.enacted)}: $enacted"
                ret += "\n${rh.gs(info.nightscout.core.ui.R.string.comment)}: $comment"
                ret += "\n${rh.gs(info.nightscout.core.ui.R.string.configbuilder_insulin)}: $bolusDelivered ${rh.gs(info.nightscout.core.ui.R.string.insulin_unit_shortname)}"
            }

            isTempCancel       -> {
                ret += "\n${rh.gs(info.nightscout.core.ui.R.string.enacted)}: $enacted"
                if (comment.isNotEmpty()) ret += "\n${rh.gs(info.nightscout.core.ui.R.string.comment)}: $comment"
                ret += "\n${rh.gs(info.nightscout.core.ui.R.string.cancel_temp)}"
            }

            isPercent          -> {
                ret += "\n${rh.gs(info.nightscout.core.ui.R.string.enacted)}: $enacted"
                if (comment.isNotEmpty()) ret += "\n${rh.gs(info.nightscout.core.ui.R.string.comment)}: $comment"
                ret += "\n${rh.gs(info.nightscout.core.ui.R.string.duration)}: $duration min"
                ret += "\n${rh.gs(info.nightscout.core.ui.R.string.percent)}: $percent%"
            }

            else               -> {
                ret += "\n${rh.gs(info.nightscout.core.ui.R.string.enacted)}: $enacted"
                if (comment.isNotEmpty()) ret += "\n${rh.gs(info.nightscout.core.ui.R.string.comment)}: $comment"
                ret += "\n${rh.gs(info.nightscout.core.ui.R.string.duration)}: $duration min"
                ret += "\n${rh.gs(info.nightscout.core.ui.R.string.absolute)}: $absolute U/h"
            }
        }
    } else {
        ret += "\n${rh.gs(info.nightscout.core.ui.R.string.comment)}: $comment"
    }
    return ret
}
