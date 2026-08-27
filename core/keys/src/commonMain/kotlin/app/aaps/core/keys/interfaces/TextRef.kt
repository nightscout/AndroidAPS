package app.aaps.core.keys.interfaces

/**
 * A reference to user visible text, without saying where that text comes from.
 *
 * Preference keys used to carry a bare `Int` resource id. That works only on Android, so it blocks
 * this module from becoming multiplatform. A [TextRef] says "there is some text here" and leaves the
 * question of how to find it to whoever draws the screen - `ResourceHelper.gs(ref)` outside Compose,
 * `stringResource(ref)` inside it.
 *
 * ### Do not persist it
 *
 * A [TextRef] is meaningful **only inside one running process**. [AndroidRes.id] must never be
 * written to preferences, to the database, to a Nightscout document, to a wear message or into a
 * crash report as a number, because the same number means different things on different builds.
 * Persist the preference `key` instead, which is a stable string.
 *
 * ### Names and ids
 *
 * [Named] is for a module that has stopped naming Android resource ids in its own code. [AndroidRes]
 * is for everything else, and there is a lot of it, so both stay.
 *
 * An earlier version of this note argued that a name could not work, because resolving one needs
 * `Resources.getIdentifier()` - a reflective lookup R8 cannot see, which would keep every string
 * alive and silently return 0 for a typo. That is true of a hand written name and false of a
 * generated one: `GenerateKeyStringsTask` emits the names and their id map together from the same
 * `strings.xml`, so R8 sees ordinary `R.string.x` references and a typo does not compile.
 */
sealed interface TextRef {

    /**
     * A string from an Android `R.string.*` table, in a module that still owns AAPT resources.
     *
     * Most of the app is still this form, and that is fine - a module only needs to change when it
     * itself becomes multiplatform. The two resource forms coexist so modules can migrate one at a
     * time rather than all at once.
     *
     * [args] are format arguments, in the order the format string expects. Nullable, because the id form
     * `gs(id, vararg args: Any?)` has always accepted null and formats it as "null" - the named form has
     * to behave the same or a caller cannot move across. They are not checked at
     * compile time - no worse than `stringResource(id, a, b)` today, but the arguments now travel
     * further from the format string, so a mismatch shows up when the text is built.
     */
    data class AndroidRes(val id: Int, val args: List<Any?> = emptyList()) : TextRef

    /**
     * A string named rather than numbered, so the declaring code holds nothing Android specific.
     *
     * [name] is the `name` attribute from `strings.xml`. Take these from the generated object for
     * the owning module - `KeysStrings` for `:core:keys`, `UiStrings` for `:core:ui` - never by
     * writing the string out by hand, because only the generated form is checked against the XML at
     * build time.
     *
     * [owner] says which module's `strings.xml` [name] came from, because a name is only unique
     * within one module. `ns_wifi_ssids` really does exist in both `:core:keys` and `:core:ui` with
     * different translations in Bulgarian, Norwegian and Chinese, so a resolver that guessed by
     * lookup order would silently pick one of them. Tagging the owner makes it unambiguous and costs
     * nothing at the call sites, because only the generator ever constructs this.
     *
     * Each platform resolves the pair its own way. Android looks it up in that module's generated id
     * map and then reads it through `Resources`, so locale matching and the always English lookup
     * work exactly as they did when keys carried ids.
     *
     * [args] behave as in [AndroidRes].
     */
    data class Named(val owner: String, val name: String, val args: List<Any?> = emptyList()) : TextRef

    /**
     * Text that is only known at run time - a scanned pump name, a wiki page title, a user label.
     *
     * This replaces the `titleResId = 0` and `titleResId = -1` sentinels that used to mean "there is
     * no resource here", which callers had to remember to test for.
     */
    data class Literal(val text: String) : TextRef

    companion object {

        /**
         * The same reference with format arguments attached.
         *
         * The generated objects hand out argument-free references, because a generator cannot know
         * what a call site wants to substitute. This is how a call site supplies them:
         * `UiStrings.some_format.withArgs(count, unit)`.
         */
        fun TextRef.withArgs(vararg args: Any?): TextRef = when (this) {
            is Named      -> copy(args = args.toList())
            is AndroidRes -> copy(args = args.toList())
            is Literal    -> this
        }
    }
}
