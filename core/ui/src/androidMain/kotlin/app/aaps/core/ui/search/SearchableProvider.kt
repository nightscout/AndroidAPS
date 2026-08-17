package app.aaps.core.ui.search

/**
 * Interface for providing searchable items to the global search index.
 * Implementations should be registered via Dagger @IntoSet binding.
 *
 * Built-in screens, dialogs, hints, and other non-plugin searchables
 * should implement this interface.
 *
 * Plugin preferences are collected separately via ActivePlugin.
 */
interface SearchableProvider {

    /**
     * Returns the list of searchable items provided by this source.
     * Called when building or refreshing the search index.
     */
    fun getSearchableItems(): List<SearchableItem>
}
