package app.aaps.core.ui.compose.preference

import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.keys.interfaces.PreferenceItem
import app.aaps.core.keys.interfaces.PreferenceKey
import app.aaps.core.keys.interfaces.TextRef

/**
 * Lightweight preference subscreen definition.
 * Can contain both PreferenceKeys and nested PreferenceSubScreenDefs for hierarchical structure.
 * Content is auto-generated from items using AdaptivePreferenceList.
 *
 * Titles are [TextRef], so the rendering code deals with one form only, the same as it does for
 * [PreferenceKey]. A second constructor takes plain resource ids, because ~174 call sites still build
 * these with `titleResId = R.string.x`; it wraps them in [TextRef.AndroidRes].
 *
 * Which one you get is decided by the argument NAME, and picking the wrong one is a type error rather
 * than anything readable: pass a [TextRef] (`title = UiStrings.x`) to `title`, and a resource id
 * (`titleResId = R.string.x`) to `titleResId`. Multiplatform call sites want the first, since a
 * resource id means nothing off Android.
 *
 * @param key Unique key for this subscreen
 * @param title Screen title
 * @param items List of preference items (keys and/or nested subscreens)
 * @param summary Optional summary shown in the parent list
 * @param icon Optional Compose ImageVector icon shown next to the title
 */
data class PreferenceSubScreenDef(
    val key: String,
    /** Screen title, in the same form as [PreferenceKey.title]. */
    val title: TextRef,
    val items: List<PreferenceItem> = emptyList(),
    /** Optional summary, in the same form as [PreferenceKey.summary]. */
    val summary: TextRef? = null,
    val icon: ImageVector? = null
) : PreferenceItem {

    /**
     * Resource id form, for the many call sites that still name their strings with R.string.
     *
     * @param titleResId String resource id for the screen title
     * @param summaryResId Optional string resource id for the summary shown in the parent list
     */
    constructor(
        key: String,
        titleResId: Int,
        items: List<PreferenceItem> = emptyList(),
        summaryResId: Int? = null,
        icon: ImageVector? = null
    ) : this(key, TextRef.AndroidRes(titleResId), items, summaryResId?.let { TextRef.AndroidRes(it) }, icon)

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
