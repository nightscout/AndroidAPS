package app.aaps.core.ui.compose.preference

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.aaps.core.ui.compose.consumeOverscroll

/**
 * Unified renderer for a preference subscreen shown in a bottom sheet / dialog.
 *
 * Every settings sheet looks the same: an [ElevatedCard][CollapsibleCardSectionContent] with the
 * group's icon + title. The flat-vs-cards choice is content-driven and decided here, in one place:
 *
 * - [settingsDef] contains nested [PreferenceSubScreenDef] groups → render each as a **collapsible**
 *   card (e.g. status-light thresholds: cannula / insulin / sensor / pump).
 * - [settingsDef] is a single flat list of keys → render **one always-expanded** card (no chevron,
 *   not clickable) using the def's own title + icon (e.g. wizard / fill / carbs button settings).
 *
 * Wraps [ProvidePreferenceTheme] so callers only supply the def and (optionally) surrounding chrome.
 *
 * The content scrolls here, so a caller must not put it in a scrolling column of its own: a vertical
 * scroll inside another one is measured with an infinite height and crashes. Anything a caller shows
 * above the rows, a title or a back button, goes in [header] and scrolls with them. Before, the sheets
 * that put this straight into a bottom sheet had no scroll at all, and in a short window (large font,
 * small screen, landscape) their last rows could not be reached (#5564).
 */
@Composable
fun PreferenceSheetContent(
    settingsDef: PreferenceSubScreenDef,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {}
) {
    // Without it a key's own visibility rule is skipped, and the sheet showed rows the settings screen
    // hides (insulin age and battery age thresholds on a patch pump).
    val visibilityContext = LocalVisibilityContext.current
    // consumeOverscroll: at the end of the rows the left-over scroll would drag the bottom sheet instead.
    Column(
        modifier
            .consumeOverscroll()
            .verticalScroll(rememberScrollState())
    ) {
        header()
        ProvidePreferenceTheme {
            val groups = settingsDef.items.filterIsInstance<PreferenceSubScreenDef>()
            if (groups.isEmpty()) {
                // Single flat list → one always-expanded card titled by the def itself.
                CollapsibleCardSectionContent(
                    title = settingsDef.title,
                    expanded = true,
                    onToggle = {},
                    icon = settingsDef.icon,
                    collapsible = false
                ) {
                    AdaptivePreferenceList(items = settingsDef.items, visibilityContext = visibilityContext)
                }
            } else {
                // Mixed defs: render any loose keys first (no card), then a collapsible card per group.
                val looseKeys = settingsDef.items.filterNot { it is PreferenceSubScreenDef }
                if (looseKeys.isNotEmpty()) AdaptivePreferenceList(items = looseKeys, visibilityContext = visibilityContext)
                groups.forEach { group ->
                    var expanded by remember(group.key) { mutableStateOf(false) }
                    CollapsibleCardSectionContent(
                        title = group.title,
                        expanded = expanded,
                        onToggle = { expanded = !expanded },
                        icon = group.icon
                    ) {
                        AdaptivePreferenceList(items = group.items, visibilityContext = visibilityContext)
                    }
                }
            }
        }
    }
}
