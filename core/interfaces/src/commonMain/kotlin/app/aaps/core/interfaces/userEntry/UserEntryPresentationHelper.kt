package app.aaps.core.interfaces.userEntry

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.data.model.UE
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit

interface UserEntryPresentationHelper {

    fun icon(source: Sources): ImageVector
    @Composable fun iconColor(source: Sources): Color
    fun listToPresentationString(list: List<ValueWithUnit>): String
    fun userEntriesToCsv(userEntries: List<UE>): String
}
