package app.aaps.core.interfaces.maintenance

import android.content.Context
import androidx.compose.ui.graphics.vector.ImageVector

interface PrefsMetadataKey {

    val key: String
    val icon: ImageVector
    val label: Int
    fun formatForDisplay(context: Context, value: String): String
}