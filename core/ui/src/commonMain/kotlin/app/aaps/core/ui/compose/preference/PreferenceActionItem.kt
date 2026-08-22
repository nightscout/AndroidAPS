package app.aaps.core.ui.compose.preference

import app.aaps.core.keys.interfaces.PreferenceItem
import app.aaps.core.keys.interfaces.TextRef

/**
 * A preference row that runs an action instead of reading or writing a stored value.
 *
 * Titles are [TextRef], the same form as [PreferenceSubScreenDef] and
 * [app.aaps.core.keys.interfaces.PreferenceKey], so the rendering code deals with one form only and
 * this file holds nothing Android specific.
 *
 * Unlike [PreferenceSubScreenDef] there is no resource id constructor here. That one exists only
 * because ~174 call sites still build subscreens with `titleResId = R.string.x`; an action item has a
 * single call site, so a module that still owns AAPT resources writes
 * `title = TextRef.AndroidRes(R.string.x)` and nothing needs a second constructor.
 *
 * @param key Unique key for this row
 * @param title Row title
 * @param summary Optional summary shown under the title
 * @param onAction Called when the row is clicked
 */
data class PreferenceActionItem(
    val key: String,
    val title: TextRef,
    val summary: TextRef? = null,
    val onAction: () -> Unit,
) : PreferenceItem
