package app.aaps.core.interfaces.userEntry

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.data.model.UE
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit

interface UserEntryPresentationHelper {

    @Deprecated("use fun icon(source: Sources): ImageVector?")
    @DrawableRes fun iconId(source: Sources): Int
    fun icon(source: Sources): ImageVector?
    fun listToPresentationString(list: List<ValueWithUnit>): String
    fun userEntriesToCsv(userEntries: List<UE>): String
}
