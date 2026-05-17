package app.aaps.core.interfaces.maintenance

import android.os.Parcelable
import androidx.compose.ui.graphics.vector.ImageVector

interface PrefsStatus : Parcelable {

    val icon: ImageVector
    val isOk: Boolean get() = false
    val isWarning: Boolean get() = false
    val isError: Boolean get() = false
}