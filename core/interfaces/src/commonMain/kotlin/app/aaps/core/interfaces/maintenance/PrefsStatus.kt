package app.aaps.core.interfaces.maintenance

import androidx.compose.ui.graphics.vector.ImageVector

interface PrefsStatus {

    val icon: ImageVector
    val isOk: Boolean get() = false
    val isWarning: Boolean get() = false
    val isError: Boolean get() = false
}
