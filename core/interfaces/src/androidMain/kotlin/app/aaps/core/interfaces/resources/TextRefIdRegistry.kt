package app.aaps.core.interfaces.resources

import app.aaps.core.keys.interfaces.TextRef

/**
 * Where a module tells the rest of the app how to resolve the names it owns.
 *
 * `ResourceHelper` can only see the id maps of the modules it depends on - `keys` and `interfaces`.
 * A name owned by a module further up, such as `ui`, is invisible to it, and the fallback is to show
 * the raw name. That is fine while such names are only read from Composables, because
 * `app.aaps.core.ui.compose.stringResource` lives in `:core:ui` and can see all of them. It stops
 * being fine the moment a non-Compose caller wants one - which is what happened when the shared
 * builders started naming their strings instead of numbering them, and `format_carbs` appeared on
 * the overview instead of "12 g".
 *
 * Registration happens once, from `ResourceHelperImpl`, which is downstream of every module that
 * owns strings and is constructed before anything can ask it for text.
 */
object TextRefIdRegistry {

    private val lookups = mutableMapOf<String, (String) -> Int?>()

    /** Teaches the resolver how to turn a name owned by [owner] into a resource id. */
    fun register(owner: String, idOf: (String) -> Int?) {
        lookups[owner] = idOf
    }

    /** The id for [ref], or null when no module has claimed that owner. */
    fun idOf(ref: TextRef.Named): Int? = lookups[ref.owner]?.invoke(ref.name)
}
