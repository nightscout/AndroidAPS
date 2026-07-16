package app.aaps.ui.search

import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.ui.search.SearchableItem
import app.aaps.core.ui.search.SearchableProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides searchable items for dialogs and action screens.
 * Auto-derived from [ElementType.searchableEntries] — no manual registration needed.
 *
 * Entries are filtered by their [ElementType.visibility] so mode-exclusive screens
 * (e.g. client-only "pair with master", master-only "authorized clients") are not
 * discoverable on the wrong build.
 */
@Singleton
class DialogSearchables @Inject constructor(
    private val visibilityContext: VisibilityContext
) : SearchableProvider {

    override fun getSearchableItems(): List<SearchableItem> =
        ElementType.searchableEntries
            .filter { it.visibility.isVisible(visibilityContext) }
            .map { SearchableItem.Dialog(it) }
}
