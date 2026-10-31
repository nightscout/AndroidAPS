package app.aaps.pump.virtual.extensions

import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.ui.CoreUiStrings

fun PumpEnactResult.toText(rh: TextResolver): String {
    var ret = rh.gs(CoreUiStrings.success) + ": " + success
    if (enacted) {
        when {
            bolusDelivered > 0 -> {
                ret += "\n${rh.gs(CoreUiStrings.enacted)}: $enacted"
                ret += "\n${rh.gs(CoreUiStrings.comment)}: $comment"
                ret += "\n${rh.gs(CoreUiStrings.configbuilder_insulin)}: $bolusDelivered ${rh.gs(CoreUiStrings.insulin_unit_shortname)}"
            }

            isTempCancel       -> {
                ret += "\n${rh.gs(CoreUiStrings.enacted)}: $enacted"
                if (comment.isNotEmpty()) ret += "\n${rh.gs(CoreUiStrings.comment)}: $comment"
                ret += "\n${rh.gs(CoreUiStrings.cancel_temp)}"
            }

            isPercent          -> {
                ret += "\n${rh.gs(CoreUiStrings.enacted)}: $enacted"
                if (comment.isNotEmpty()) ret += "\n${rh.gs(CoreUiStrings.comment)}: $comment"
                ret += "\n${rh.gs(CoreUiStrings.duration)}: $duration min"
                ret += "\n${rh.gs(CoreUiStrings.percent)}: $percent%"
            }

            else               -> {
                ret += "\n${rh.gs(CoreUiStrings.enacted)}: $enacted"
                if (comment.isNotEmpty()) ret += "\n${rh.gs(CoreUiStrings.comment)}: $comment"
                ret += "\n${rh.gs(CoreUiStrings.duration)}: $duration min"
                ret += "\n${rh.gs(CoreUiStrings.absolute)}: $absolute U/h"
            }
        }
    } else {
        ret += "\n${rh.gs(CoreUiStrings.comment)}: $comment"
    }
    return ret
}
