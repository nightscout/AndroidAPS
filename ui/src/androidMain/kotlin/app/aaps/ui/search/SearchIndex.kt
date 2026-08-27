package app.aaps.ui.search

import app.aaps.core.ui.search.SearchableItem

/**
 * An entry in the search index containing both the searchable item
 * and pre-computed localized strings for efficient searching.
 *
 * @param item The searchable item
 * @param localizedTitle Title in current locale (for display and search)
 * @param englishTitle Title in English (for fallback search)
 * @param localizedSummary Summary in current locale (for search, nullable)
 * @param englishSummary Summary in English (for fallback search, nullable)
 * @param category Display category for grouping results
 */
data class SearchIndexEntry(
    val item: SearchableItem,
    val localizedTitle: String,
    val englishTitle: String,
    val localizedSummary: String?,
    val englishSummary: String?,
    val category: SearchCategory
)

/**
 * Categories for grouping search results in the UI.
 * Order determines display order in search results.
 */
enum class SearchCategory {
    PLUGIN,
    DIALOG,
    CATEGORY,
    PREFERENCE,
    WIKI
}
