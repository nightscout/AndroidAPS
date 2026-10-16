package app.aaps.core.keys

import app.aaps.core.keys.interfaces.ComposedKey
import app.aaps.core.keys.interfaces.NonPreferenceKey

/**
 * What an import should DO with a key, which is not the same question as what the key is.
 *
 * The split is Miloš's (plan 4.1.6). Writing a value and changing a plugin's enabled state are
 * different acts with different consequences, and "keep the pump working" is a filter over the
 * first, so the four cases are:
 *
 * - [General] - a core setting owned by no plugin. Written, always.
 * - [PumpInternal] - owned by a pump driver. Written on a full import, SKIPPED when the user asked
 *   to keep the pump settings, which is the whole point of the category.
 * - [OtherPluginInternal] - owned by some other plugin. Written, always. Separate from [General]
 *   not because writing differs, but because REMOVING differs: registered-but-unowned and owned are
 *   different cases for a sweep that deletes orphans.
 * - [ConfigBuilderEnabled] - not a value at all. It decides whether a plugin runs, so it goes
 *   through `setPluginEnabled`, which starts or stops the plugin only when the state actually
 *   changes. Writing it as a plain value would leave the store and the running plugins disagreeing.
 */
enum class KeyCategory {
    General,
    PumpInternal,
    OtherPluginInternal,
    ConfigBuilderEnabled
}

/**
 * What a stored preference key turned out to be.
 *
 * A [Composed] result carries the argument back out, because the caller needs it to write the value:
 * `preferences.put(key, argument, value = ...)`. Its [key] is always also a [ComposedKey] - the enums
 * implement both interfaces - so a caller that needs the composed side can cast.
 */
sealed interface ResolvedKey {

    val key: NonPreferenceKey

    /** What an import should do with it - see [KeyCategory]. */
    val category: KeyCategory

    /** A key stored under its own name. */
    data class Plain(override val key: NonPreferenceKey, override val category: KeyCategory) : ResolvedKey

    /** One instance of a [ComposedKey], with the argument that was composed into the stored name. */
    data class Composed(override val key: NonPreferenceKey, val argument: String, override val category: KeyCategory) : ResolvedKey
}

/**
 * Turns a stored preference name back into the key that owns it.
 *
 * ## Why this has to exist
 *
 * An export is `Map<String, String>` - names and text, no types. To write an imported value through
 * `Preferences` (so that the typed store, the observable flows and the sync stamps all agree) the
 * import first has to know WHICH key a stored name is. `Preferences.get(key: String)` cannot answer
 * that: it is `prefsList.find { it.key == key }`, an exact match, so it resolves plain keys and fails
 * on every [ComposedKey] instance - and 26 composed templates are exportable, each expanding to as
 * many stored names as the user has widgets, objectives, profiles or paired pumps.
 *
 * ## Why the prefix match is safe
 *
 * A composed name is `key + format` with the argument substituted, so the only way back is to match
 * the prefix - which is sound only while no prefix swallows another. That is not a property anyone
 * can hold in their head, so it is a test: `ComposedKeyPrefixTest` fails if any composed prefix is a
 * prefix of another, or if a plain key starts with one. It found a real collision when it was written
 * (`appwidget_use_black_` sat inside `appwidget_`), which is why that key was moved.
 *
 * The index here is still built longest-prefix-first. That is not a second line of defence against a
 * collision - it is determinism: without it the answer would depend on iteration order, and a bug
 * that depends on iteration order is the kind that reproduces on one machine and not another. The
 * test is what keeps the question from arising.
 *
 * ## What it refuses
 *
 * The argument is checked against the format, so a name that merely starts with a prefix does not
 * resolve: `%d` needs a whole number, `%s` needs a non-empty value. Only single-placeholder formats
 * (`%d`, `%s` - the only two in use) are indexed; anything else is left out rather than guessed at,
 * because inverting a multi-placeholder format is ambiguous and a wrong split would write a value
 * under a key the user never set.
 *
 * Unknown names return null. That is normal, not an error: a file from a newer AAPS carries keys this
 * build has never had, and a client build never constructs the pump and APS plugins at all, so their
 * keys are registered nowhere. The caller decides what to do with an unresolved name.
 *
 * ## Why ownership is passed in rather than looked up
 *
 * [KeyCategory] needs to know which plugin owns a key, and this class cannot work that out:
 *
 * 1. `Preferences.registerPreferences(keys)` is `prefsList.addAll(keys)` - it **throws the caller
 *    away**. There is no plugin-to-keys map anywhere in the app to consult.
 * 2. Even if there were, `core:interfaces` depends on `core:keys`, so this module cannot see
 *    `PluginBase` or `PluginType` without a dependency cycle.
 *
 * So the owner sets are built where the plugins are - walk the plugin list, take each
 * `PluginBaseWithPreferences.ownPreferences`, and split on whether the plugin is a pump driver - and
 * handed in here. Both default to empty, which classifies everything as [KeyCategory.General] or
 * [KeyCategory.ConfigBuilderEnabled]: correct for a caller that only needs to resolve names, and
 * deliberately NOT a silent half-answer for one that needs the pump filter, because an empty pump set
 * makes "skip pump keys" skip nothing. A caller that asks for that mode must pass the sets.
 */
class PreferenceKeyResolver(
    keys: Collection<NonPreferenceKey>,
    pumpOwned: Collection<NonPreferenceKey> = emptyList(),
    otherPluginOwned: Collection<NonPreferenceKey> = emptyList()
) {

    private val plain: Map<String, NonPreferenceKey> =
        keys.filter { it !is ComposedKey }.associateBy { it.key }

    /** Longest prefix first - see the note on determinism above. */
    private val composed: List<NonPreferenceKey> =
        keys.filter { it is ComposedKey && it.format.isSinglePlaceholder() }
            .sortedByDescending { (it as ComposedKey).key.length }

    // Identity sets, not name sets: two keys may never share a stored name (PreferenceKeySnapshotTest
    // enforces that), but comparing the objects keeps this honest if that ever slips.
    private val pump: Set<NonPreferenceKey> = pumpOwned.toSet()
    private val otherPlugin: Set<NonPreferenceKey> = otherPluginOwned.toSet()

    /**
     * Order matters. `ConfigBuilderEnabled` lives in a core enum, so by ownership it is "General" -
     * but it is not a value, and treating it as one leaves the store and the running plugins
     * disagreeing. It wins over ownership deliberately.
     */
    fun categoryOf(key: NonPreferenceKey): KeyCategory = when {
        key === BooleanComposedKey.ConfigBuilderEnabled -> KeyCategory.ConfigBuilderEnabled
        key in pump                                     -> KeyCategory.PumpInternal
        key in otherPlugin                              -> KeyCategory.OtherPluginInternal
        else                                            -> KeyCategory.General
    }

    fun resolve(storedKey: String): ResolvedKey? {
        plain[storedKey]?.let { return ResolvedKey.Plain(it, categoryOf(it)) }

        for (candidate in composed) {
            val template = candidate as ComposedKey
            if (!storedKey.startsWith(template.key)) continue
            val argument = storedKey.removePrefix(template.key)
            if (!template.format.accepts(argument)) continue
            return ResolvedKey.Composed(candidate, argument, categoryOf(candidate))
        }
        return null
    }

    /** True when this name belongs to a key this build knows. */
    fun canResolve(storedKey: String): Boolean = resolve(storedKey) != null

    private companion object {

        fun String.isSinglePlaceholder(): Boolean = this == "%d" || this == "%s"

        fun String.accepts(argument: String): Boolean = when (this) {
            // The same rule composeKey enforces on the way in: %d takes a whole number. Checked with
            // toLongOrNull rather than a digit test so a negative argument round-trips.
            "%d" -> argument.toLongOrNull() != null
            "%s" -> argument.isNotEmpty()
            else -> false
        }
    }
}
