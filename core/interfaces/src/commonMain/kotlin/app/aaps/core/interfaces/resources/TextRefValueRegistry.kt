package app.aaps.core.interfaces.resources

import app.aaps.core.keys.interfaces.TextRef

/**
 * Where a module tells the platforms without an Android resource table how to read the names it owns.
 *
 * The counterpart of [TextRefIdRegistry]. That one hands out `R.string` ids, which only exist on
 * Android; this one hands out the English text that `GenerateKeyStringsTask` generated from the same
 * `strings.xml`. iOS and the desktop JVM have no `Resources` to look a name up in, so without this
 * every label renders as its own name and a settings screen reads `configbuilder_general`.
 *
 * Registration happens once from the platform shell, in the same spirit as `registerStringOwners()`
 * on Android: each shell registers the modules it actually depends on, because a name is only unique
 * inside one module.
 *
 * Android does not register here and must not. Text there comes through AAPT, which is what keeps
 * locale matching and the translations working.
 */
object TextRefValueRegistry {

    private val lookups = mutableMapOf<String, (String) -> String?>()

    /** Teaches the resolver how to turn a name owned by [owner] into its English text. */
    fun register(owner: String, textOf: (String) -> String?) {
        lookups[owner] = textOf
    }

    /** The text for [ref], or null when no module has claimed that owner or that name. */
    fun textOf(ref: TextRef.Named): String? = lookups[ref.owner]?.invoke(ref.name)

    /** Drops every registration. For tests, so one test cannot see what another registered. */
    fun clear() {
        lookups.clear()
    }
}
