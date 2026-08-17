package app.aaps.core.interfaces.maintenance

import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.keys.interfaces.TextRef

interface PrefsMetadataKey {

    val key: String
    val icon: ImageVector
    val label: Int
    /**
     * The value as it should be shown, as a reference rather than resolved text - the layer that
     * draws decides the language.
     */
    fun formatForDisplay(value: String): TextRef
}