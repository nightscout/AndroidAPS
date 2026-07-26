package app.aaps.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.masterEditingEnabled
import app.aaps.core.ui.search.SearchableItem

/**
 * Displays search results in a categorized list.
 *
 * @param results List of local search result entries
 * @param wikiResults List of wiki search result entries
 * @param isSearching Whether local search is in progress
 * @param isSearchingWiki Whether wiki search is in progress
 * @param revision Monotonic counter bumped on every plugin toggle. It exists purely to break Compose skipping:
 *   a toggle changes a plugin's isEnabled() (external state) but not the result entries, so the rebuilt results
 *   list is equals() to the old one and both this composable and its list items would otherwise be skipped,
 *   leaving switches/greying stale. Feeding it into the item keys forces the rows to reflect the new state.
 * @param onResultClick Called when a result item is clicked (only for enabled items)
 * @param modifier Modifier for the component
 */
@Composable
fun SearchResults(
    results: List<SearchIndexEntry>,
    wikiResults: List<SearchIndexEntry>,
    isSearching: Boolean,
    isSearchingWiki: Boolean,
    wikiOffline: Boolean,
    revision: Int,
    onResultClick: (SearchIndexEntry) -> Unit,
    onPluginToggle: (PluginBase) -> Unit,
    modifier: Modifier = Modifier
) {
    val allResults = results + wikiResults

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isSearching                              -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                allResults.isEmpty() && !isSearchingWiki -> {
                    Text(
                        text = stringResource(R.string.no_search_results),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    )
                }

                else                                     -> {
                    // Group results by category
                    val groupedResults = allResults.groupBy { it.category }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        // Show each category with its results
                        SearchCategory.entries.forEach { category ->
                            val categoryResults = groupedResults[category]
                            if (!categoryResults.isNullOrEmpty()) {
                                item(key = "header_${category.name}") {
                                    CategoryHeader(category = category)
                                }

                                items(
                                    items = categoryResults,
                                    // Stable key (no revision): a toggle re-reads the live enabled state via the `revision`
                                    // input passed INTO the row, not by re-keying — this keeps the row's composition alive
                                    // so its Switch animates instead of being torn down and rebuilt.
                                    key = { "${category.name}_${it.item.key}" }
                                ) { entry ->
                                    SearchResultItem(
                                        entry = entry,
                                        revision = revision,
                                        onResultClick = onResultClick,
                                        onPluginToggle = onPluginToggle
                                    )
                                }
                            }
                        }

                        // Show wiki loading indicator or offline notice
                        if (isSearchingWiki) {
                            item(key = "wiki_loading") {
                                WikiLoadingIndicator()
                            }
                        } else if (wikiOffline) {
                            item(key = "wiki_offline") {
                                WikiOfflineIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Header for a search result category.
 */
@Composable
private fun CategoryHeader(
    category: SearchCategory,
    modifier: Modifier = Modifier
) {
    val titleResId = when (category) {
        SearchCategory.PLUGIN     -> R.string.search_category_plugins
        SearchCategory.CATEGORY   -> R.string.search_category_categories
        SearchCategory.PREFERENCE -> R.string.search_category_preferences
        SearchCategory.DIALOG     -> R.string.search_category_dialogs
        SearchCategory.WIKI       -> R.string.search_category_wiki
    }

    Column(modifier = modifier) {
        Text(
            text = stringResource(titleResId),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

/**
 * A single search result item.
 *
 * @param entry The search result entry
 * @param revision Bumped on every plugin toggle (see [SearchResults]). Used as a [remember] key so this row
 *   re-reads the live, non-Compose `plugin.isEnabled()` after a toggle while keeping a stable LazyColumn key.
 * @param onResultClick Called when an enabled item is clicked
 * @param onPluginToggle Called to flip a plugin's enabled state
 */
@Composable
private fun SearchResultItem(
    entry: SearchIndexEntry,
    revision: Int,
    onResultClick: (SearchIndexEntry) -> Unit,
    onPluginToggle: (PluginBase) -> Unit,
    modifier: Modifier = Modifier
) {
    // A disabled row is greyed to signal it's off — but only the icon + text are dimmed, never the trailing
    // switch (a dimmed switch reads as non-interactive, when it's actually the control to enable the plugin).
    val pluginItem = entry.item as? SearchableItem.Plugin
    val plugin = pluginItem?.pluginRef
    // plugin.isEnabled() is external (non-Compose) state; keying the read on `revision` (bumped per toggle)
    // re-reads it after a switch without changing the LazyColumn item key.
    val isEnabled = remember(revision, entry) { entry.item.plugin?.isEnabled() ?: true }
    // Synced single-select selections (APS/Sensitivity/Smoothing/Calibration) drive the master's active plugin,
    // so on a client with the master offline they must not be changed from here — same gate as the Config Builder
    // (see PluginCategoryScreen). Non-synced categories (SYNC/GENERAL/pump/…) stay editable offline.
    val editableWhenSynced = masterEditingEnabled()
    // Whether this plugin can be flipped from here: alwaysEnabled plugins are locked on, an already-active
    // single-select plugin can't be turned off (only replaced by enabling another), and a synced selection is
    // gated while the master is offline.
    val canToggle = plugin != null && !plugin.pluginDescription.alwaysEnabled &&
        (!plugin.getType().singleSelect || !isEnabled) &&
        (!plugin.getType().selectionSyncs || editableWhenSynced)
    val dimmed = !isEnabled
    val contentAlpha = if (dimmed) 0.5f else 1f
    val contentColor = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface

    // Whole-row tap: open an enabled plugin/preference; for a disabled but toggleable plugin, enable it — so the
    // entire row is actionable, not just the small switch.
    val rowClick: (() -> Unit)? = when {
        isEnabled                   -> { { onResultClick(entry) } }
        canToggle && plugin != null -> { { onPluginToggle(plugin) } }
        else                        -> null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (rowClick != null) Modifier.clickable(onClick = rowClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon: wiki items get book icon, others prefer ImageVector over drawable resource
        val isWiki = entry.item is SearchableItem.Wiki
        val icon = if (isWiki) Icons.AutoMirrored.Filled.MenuBook else entry.item.icon
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .alpha(contentAlpha)
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        // Title and summary — dimmed together with the icon when the row is disabled.
        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(contentAlpha),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = entry.localizedTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Non-plugin item whose owning plugin is disabled → "disabled" hint; a plugin row conveys its state
            // via the switch, so it shows its normal summary instead.
            if (dimmed && pluginItem == null) {
                Spacer(modifier = Modifier.height(2.dp))
                val pluginName = entry.item.plugin?.name ?: ""
                Text(
                    text = stringResource(R.string.search_plugin_disabled, pluginName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    maxLines = 1
                )
            } else {
                entry.localizedSummary?.let { summary ->
                    if (summary.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Trailing enable/disable switch. Single-select plugins can only be switched ON (the active one can't be
        // turned off — it's replaced by enabling another); alwaysEnabled plugins are locked on.
        if (plugin != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isEnabled,
                onCheckedChange = if (canToggle) { { onPluginToggle(plugin) } } else null,
                enabled = canToggle
            )
        }
    }
}

/**
 * Loading indicator shown while wiki search is in progress.
 */
@Composable
private fun WikiLoadingIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.search_category_wiki) + "\u2026",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Indicator shown when wiki search is unavailable due to no internet connection.
 */
@Composable
private fun WikiOfflineIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.WifiOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.wiki_search_offline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
