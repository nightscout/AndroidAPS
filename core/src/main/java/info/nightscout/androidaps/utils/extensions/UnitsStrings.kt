package info.nightscout.androidaps.utils.extensions

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.UserEntry

fun UserEntry.Units.stringId(): Int {
    return when {
        this == UserEntry.Units.Mg_Dl  -> R.string.mgdl
        this == UserEntry.Units.Mmol_L -> R.string.mmol
        this == UserEntry.Units.U      -> R.string.insulin_unit_shortname
        this == UserEntry.Units.U_H -> R.string.profile_ins_units_per_hour
        this == UserEntry.Units.G -> R.string.shortgram
        this == UserEntry.Units.M -> R.string.shortminute
        this == UserEntry.Units.H -> R.string.shorthour
        this == UserEntry.Units.Percent -> R.string.shortpercent
        else                            -> 0
    }
}