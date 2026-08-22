package app.aaps.pump.virtual.extensions

import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.ui.UiStrings

fun PumpEnactResult.toText(rh: TextResolver): String {
    var ret = rh.gs(UiStrings.success) + ": " + success
    if (enacted) {
        when {
            bolusDelivered > 0 -> {
                ret += "\n${rh.gs(UiStrings.enacted)}: $enacted"
                ret += "\n${rh.gs(UiStrings.comment)}: $comment"
                ret += "\n${rh.gs(UiStrings.configbuilder_insulin)}: $bolusDelivered ${rh.gs(UiStrings.insulin_unit_shortname)}"
            }

            isTempCancel       -> {
                ret += "\n${rh.gs(UiStrings.enacted)}: $enacted"
                if (comment.isNotEmpty()) ret += "\n${rh.gs(UiStrings.comment)}: $comment"
                ret += "\n${rh.gs(UiStrings.cancel_temp)}"
            }

            isPercent          -> {
                ret += "\n${rh.gs(UiStrings.enacted)}: $enacted"
                if (comment.isNotEmpty()) ret += "\n${rh.gs(UiStrings.comment)}: $comment"
                ret += "\n${rh.gs(UiStrings.duration)}: $duration min"
                ret += "\n${rh.gs(UiStrings.percent)}: $percent%"
            }

            else               -> {
                ret += "\n${rh.gs(UiStrings.enacted)}: $enacted"
                if (comment.isNotEmpty()) ret += "\n${rh.gs(UiStrings.comment)}: $comment"
                ret += "\n${rh.gs(UiStrings.duration)}: $duration min"
                ret += "\n${rh.gs(UiStrings.absolute)}: $absolute U/h"
            }
        }
    } else {
        ret += "\n${rh.gs(UiStrings.comment)}: $comment"
    }
    return ret
}
