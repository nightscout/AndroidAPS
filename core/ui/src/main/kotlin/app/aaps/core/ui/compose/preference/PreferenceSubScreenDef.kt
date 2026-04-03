package app.aaps.core.ui.compose.preference

import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.keys.interfaces.PreferenceItem
import app.aaps.core.keys.interfaces.PreferenceKey

/**
 * Lightweight preference subscreen definition.
 * Can contain both PreferenceKeys and nested PreferenceSubScreenDefs for hierarchical structure.
 * Content is auto-generated from items using AdaptivePreferenceList.
 *
 * @param key Unique key for this subscreen
 * @param titleResId String resource ID for the screen title
 * @param items List of preference items (keys and/or nested subscreens)
 * @param summaryResId Optional string resource ID for summary shown in parent list
 * @param iconResId Optional drawable resource ID for the icon shown next to the title
 * @param icon Optional Compose ImageVector icon (preferred over iconResId)
 */
data class PreferenceSubScreenDef(
    val key: String,
    val titleResId: Int,
    val items: List<PreferenceItem> = emptyList(),
    val summaryResId: Int? = null,
    val iconResId: Int? = null,
    val icon: ImageVector? = null
) : PreferenceItem {

    /** Effective summary items - from items' titleResId */
    fun effectiveSummaryItems(): List<Int> =
        items.mapNotNull { item ->
            when (item) {
                is PreferenceKey          -> item.titleResId.takeIf { it != 0 }
                is PreferenceSubScreenDef -> item.titleResId.takeIf { it != 0 }
                else                      -> null
            }
        }
}
