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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.R
import app.aaps.core.ui.search.SearchableItem

/**
 * Displays search results in a categorized list.
 *
 * @param results List of local search result entries
 * @param wikiResults List of wiki search result entries
 * @param isSearching Whether local search is in progress
 * @param isSearchingWiki Whether wiki search is in progress
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
    onResultClick: (SearchIndexEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val allResults = results + wikiResults

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isSearching                                  -> {
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

                else                                         -> {
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
                                    key = { "${category.name}_${it.item.key}" }
                                ) { entry ->
                                    val isEnabled = entry.item.plugin?.isEnabled() ?: true
                                    SearchResultItem(
                                        entry = entry,
                                        isEnabled = isEnabled,
                                        onClick = { if (isEnabled) onResultClick(entry) }
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
 * @param isEnabled Whether the item is from an enabled plugin (or built-in)
 * @param onClick Called when item is clicked (only if enabled)
 */
@Composable
private fun SearchResultItem(
    entry: SearchIndexEntry,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentAlpha = if (isEnabled) 1f else 0.5f
    val contentColor = if (isEnabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isEnabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(contentAlpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon: wiki items get book icon, others prefer ImageVector over drawable resource
        val isWiki = entry.item is SearchableItem.Wiki
        val icon = if (isWiki) Icons.AutoMirrored.Filled.MenuBook else entry.item.icon
        val iconResId = entry.item.iconResId
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
        } else if (iconResId != null) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        // Title and summary
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = entry.localizedTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Show "Plugin X disabled" for disabled items, otherwise show summary
            if (!isEnabled) {
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
