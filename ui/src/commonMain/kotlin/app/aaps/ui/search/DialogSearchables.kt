package app.aaps.ui.search

import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.ui.search.SearchableItem
import app.aaps.core.ui.search.SearchableProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Provides searchable items for dialogs and action screens.
 * Auto-derived from [ElementType.searchableEntries] — no manual registration needed.
 *
 * Entries are filtered by their [ElementType.visibility] so mode-exclusive screens
 * (e.g. client-only "pair with master", master-only "authorized clients") are not
 * discoverable on the wrong build.
 */
@ContributesIntoSet(AppScope::class, binding = binding<SearchableProvider>())
@SingleIn(AppScope::class)
class DialogSearchables @Inject constructor(
    private val visibilityContext: VisibilityContext
) : SearchableProvider {

    override fun getSearchableItems(): List<SearchableItem> =
        ElementType.searchableEntries
            .filter { it.visibility.isVisible(visibilityContext) }
            .map { SearchableItem.Dialog(it) }
}
