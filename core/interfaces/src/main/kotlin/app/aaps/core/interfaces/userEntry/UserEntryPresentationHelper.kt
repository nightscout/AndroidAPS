package app.aaps.core.interfaces.userEntry

import android.text.Spanned
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.UserEntry.Action
import app.aaps.database.entities.UserEntry.ColorGroup
import app.aaps.database.entities.UserEntry.Sources
import app.aaps.database.entities.ValueWithUnit

interface UserEntryPresentationHelper {

    @ColorRes fun colorId(colorGroup: ColorGroup): Int
    @DrawableRes fun iconId(source: Sources): Int
    fun actionToColoredString(action: Action): Spanned
    fun listToPresentationString(list: List<ValueWithUnit?>): String
    fun userEntriesToCsv(userEntries: List<UserEntry>): String
}
