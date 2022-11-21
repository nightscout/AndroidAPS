package info.nightscout.androidaps.extensions

import info.nightscout.core.main.R
import info.nightscout.database.entities.UserEntry.ColorGroup

fun ColorGroup.colorId(): Int {
    return when (this) {
        ColorGroup.InsulinTreatment -> R.color.basal
        ColorGroup.CarbTreatment    -> R.color.carbs
        ColorGroup.TT               -> R.color.tempTargetConfirmation
        ColorGroup.Profile          -> R.color.white
        ColorGroup.Loop             -> R.color.loopClosed
        ColorGroup.Careportal       -> R.color.high
        ColorGroup.Pump             -> R.color.iob
        ColorGroup.Aaps             -> R.color.defaultText
        else                        -> R.color.defaultText
    }
}

