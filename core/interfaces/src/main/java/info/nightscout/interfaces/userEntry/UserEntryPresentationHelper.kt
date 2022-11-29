package info.nightscout.interfaces.userEntry

import android.text.Spanned
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import info.nightscout.database.entities.UserEntry
import info.nightscout.database.entities.UserEntry.Action
import info.nightscout.database.entities.UserEntry.ColorGroup
import info.nightscout.database.entities.UserEntry.Sources
import info.nightscout.database.entities.ValueWithUnit

interface UserEntryPresentationHelper {

    @ColorRes fun colorId(colorGroup: ColorGroup): Int
    @DrawableRes fun iconId(source: Sources): Int
    fun actionToColoredString(action: Action): Spanned
    fun listToPresentationString(list: List<ValueWithUnit?>): String
    fun userEntriesToCsv(userEntries: List<UserEntry>): String
}
