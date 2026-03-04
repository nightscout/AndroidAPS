package app.aaps.ui.search

import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.search.SearchableItem
import app.aaps.core.ui.search.SearchableProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides searchable items for dialogs and action screens.
 * Auto-derived from [ElementType.searchableEntries] — no manual registration needed.
 */
@Singleton
class DialogSearchables @Inject constructor() : SearchableProvider {

    override fun getSearchableItems(): List<SearchableItem> =
        ElementType.searchableEntries.map { SearchableItem.Dialog(it) }
}
