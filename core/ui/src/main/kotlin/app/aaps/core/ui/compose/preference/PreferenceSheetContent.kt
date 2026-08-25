package app.aaps.core.ui.compose.preference

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

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
 */
@Composable
fun PreferenceSheetContent(
    settingsDef: PreferenceSubScreenDef,
    modifier: Modifier = Modifier
) {
    ProvidePreferenceTheme {
        Column(modifier) {
            val groups = settingsDef.items.filterIsInstance<PreferenceSubScreenDef>()
            if (groups.isEmpty()) {
                // Single flat list → one always-expanded card titled by the def itself.
                CollapsibleCardSectionContent(
                    titleResId = settingsDef.titleResId,
                    expanded = true,
                    onToggle = {},
                    icon = settingsDef.icon,
                    collapsible = false
                ) {
                    AdaptivePreferenceList(items = settingsDef.items)
                }
            } else {
                // Mixed defs: render any loose keys first (no card), then a collapsible card per group.
                val looseKeys = settingsDef.items.filterNot { it is PreferenceSubScreenDef }
                if (looseKeys.isNotEmpty()) AdaptivePreferenceList(items = looseKeys)
                groups.forEach { group ->
                    var expanded by remember(group.key) { mutableStateOf(false) }
                    CollapsibleCardSectionContent(
                        titleResId = group.titleResId,
                        expanded = expanded,
                        onToggle = { expanded = !expanded },
                        icon = group.icon
                    ) {
                        AdaptivePreferenceList(items = group.items)
                    }
                }
            }
        }
    }
}
