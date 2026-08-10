package app.aaps.core.ui.compose.preference

import app.aaps.core.ui.UiStrings
import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.keys.interfaces.PreferenceItem
import app.aaps.core.keys.interfaces.PreferenceKey
import app.aaps.core.keys.interfaces.TextRef

/**
 * Lightweight preference subscreen definition.
 * Can contain both PreferenceKeys and nested PreferenceSubScreenDefs for hierarchical structure.
 * Content is auto-generated from items using AdaptivePreferenceList.
 *
 * The constructor still takes plain resource ids, because roughly fifty plugin call sites build
 * these with `titleResId = UiStrings.x`. The [title] and [summary] properties wrap them, so the
 * rendering code only ever deals with [TextRef], the same as it does for [PreferenceKey].
 *
 * @param key Unique key for this subscreen
 * @param titleResId String resource ID for the screen title
 * @param items List of preference items (keys and/or nested subscreens)
 * @param summaryResId Optional string resource ID for summary shown in parent list
 * @param icon Optional Compose ImageVector icon shown next to the title
 */
data class PreferenceSubScreenDef(
    val key: String,
    val titleResId: Int,
    val items: List<PreferenceItem> = emptyList(),
    val summaryResId: Int? = null,
    val icon: ImageVector? = null
) : PreferenceItem {

    /** Screen title, in the same form as [PreferenceKey.title]. */
    val title: TextRef = TextRef.AndroidRes(titleResId)

    /** Optional summary, in the same form as [PreferenceKey.summary]. */
    val summary: TextRef? = summaryResId?.let { TextRef.AndroidRes(it) }

    /** Titles of the contained items, used to build the summary line in the parent list. */
    fun effectiveSummaryItems(): List<TextRef> =
        items.mapNotNull { item ->
            when (item) {
                is PreferenceKey          -> item.title
                is PreferenceSubScreenDef -> item.title
                else                      -> null
            }
        }
}
