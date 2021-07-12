package info.nightscout.androidaps.extensions

import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.UserEntry.*

fun ColorGroup.colorId(): Int {
    return when (this) {
        ColorGroup.InsulinTreatment -> R.color.basal
        ColorGroup.CarbTreatment    -> R.color.carbs
        ColorGroup.TT               -> R.color.tempTargetConfirmation
        ColorGroup.Profile          -> R.color.white
        ColorGroup.Loop             -> R.color.loopClosed
        ColorGroup.Careportal       -> R.color.high
        ColorGroup.Pump             -> R.color.iob
        ColorGroup.Aaps             -> R.color.defaulttext
        else                        -> R.color.defaulttext
    }
}

