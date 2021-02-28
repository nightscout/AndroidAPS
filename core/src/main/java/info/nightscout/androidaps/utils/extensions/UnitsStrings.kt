package info.nightscout.androidaps.utils.extensions

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.utils.resources.ResourceHelper

fun UserEntry.Units.stringId(): Int {
    return when {
        this == UserEntry.Units.Mg_Dl -> R.string.mgdl
        this == UserEntry.Units.Mmol_L -> R.string.mmol
        this == UserEntry.Units.U -> R.string.insulin_unit_shortname
        this == UserEntry.Units.U_H -> R.string.profile_ins_units_per_hour
        this == UserEntry.Units.G -> R.string.shortgram
        this == UserEntry.Units.M -> R.string.shortminute
        this == UserEntry.Units.H -> R.string.shorthour
        this == UserEntry.Units.Percent -> R.string.shortpercent
        else                            -> 0
    }
}

fun UserEntry.Units.stringkey(): String {
    return when {
        this == UserEntry.Units.Mg_Dl -> Constants.MGDL
        this == UserEntry.Units.Mmol_L -> Constants.MMOL
        this == UserEntry.Units.U -> UserEntry.Units.U.name
        this == UserEntry.Units.U_H -> UserEntry.Units.U_H.name
        this == UserEntry.Units.G -> UserEntry.Units.G.name
        this == UserEntry.Units.M -> UserEntry.Units.M.name
        this == UserEntry.Units.H -> UserEntry.Units.H.name
        this == UserEntry.Units.Percent -> "%"
        else                            -> ""
    }
}

fun UserEntry.Units.Companion(source: String?) = UserEntry.Units.values().firstOrNull { it.stringkey() == source } ?: UserEntry.Units.None