package app.aaps.core.interfaces.userEntry

import android.text.Spanned
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import app.aaps.core.data.model.UE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit

interface UserEntryPresentationHelper {

    @ColorRes fun colorId(colorGroup: Action.ColorGroup): Int
    @DrawableRes fun iconId(source: Sources): Int
    fun actionToColoredString(action: Action): Spanned
    fun listToPresentationString(list: List<ValueWithUnit>): String
    fun userEntriesToCsv(userEntries: List<UE>): String
}
