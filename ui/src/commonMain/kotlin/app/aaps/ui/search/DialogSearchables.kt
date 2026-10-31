package app.aaps.ui.search

import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.ui.search.SearchableItem
import app.aaps.core.ui.search.SearchableProvider
import app.aaps.ui.compose.navigation.ElementAvailability
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
 * discoverable on the wrong build, and by [ElementAvailability] so actions the current
 * setup cannot do (e.g. Prime/Fill on a patch pump) are not found either.
 */
@ContributesIntoSet(AppScope::class, binding = binding<SearchableProvider>())
@SingleIn(AppScope::class)
@Inject
class DialogSearchables(
    private val visibilityContext: VisibilityContext,
    private val elementAvailability: ElementAvailability
) : SearchableProvider {

    override fun getSearchableItems(): List<SearchableItem> =
        ElementType.searchableEntries
            .filter { it.isAvailable(visibilityContext) }
            .filter { elementAvailability.isAvailable(it) }
            .map { SearchableItem.Dialog(it) }
}
