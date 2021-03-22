package info.nightscout.androidaps.utils.extensions

import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.UserEntry.*

fun ColorGroup.attributId(): Int {
    return when (this) {
        ColorGroup.InsulinTreatment -> R.attr.basal
        ColorGroup.CarbTreatment    -> R.attr.carbsColor
        ColorGroup.TT               -> R.attr.tempTargetConfirmation
        ColorGroup.Profile          -> R.attr.defaultTextColor
        ColorGroup.Loop             -> R.attr.loopClosed
        ColorGroup.Careportal       -> R.attr.bgHigh
        ColorGroup.Pump             -> R.attr.iobColor
        ColorGroup.Aaps             -> R.attr.defaultTextColor
        else                        -> R.attr.defaultTextColor
    }
}

