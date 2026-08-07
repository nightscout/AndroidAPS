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
 * A [TextRef] is meaningful **only inside one running process**. [Res.id] must never be written to
 * preferences, to the database, to a Nightscout document, to a wear message or into a crash report
 * as a number, because the same number means different things on different platforms and will change
 * again when a module moves its strings to `commonMain`. Persist the preference `key` instead, which
 * is a stable string.
 *
 * ### Why an Int and not a string name
 *
 * Resolving by name needs `Resources.getIdentifier()`, which is a reflective lookup that R8 cannot
 * see - it would keep every string alive and silently return 0 for a typo. Keeping the Android
 * resource id means the existing `R.string.x` references stay compile checked exactly as they are
 * today.
 */
sealed interface TextRef {

    /**
     * Text that lives in a resource table.
     *
     * [id] is deliberately opaque:
     * - **positive** - an Android resource id from `R.string.*`, used directly. This is the only
     *   form that exists today.
     * - **negative** - a token generated when a module moves its strings to
     *   `commonMain/composeResources`; the resolver turns it into an index into that module's
     *   platform table. Android resource ids are always positive (`0x7f……`), so the two forms can
     *   coexist and modules can migrate one at a time rather than all at once.
     *
     * [args] are format arguments, in the order the format string expects. They are not checked at
     * compile time - no worse than `stringResource(id, a, b)` today, but the arguments now travel
     * further from the format string, so a mismatch shows up when the text is built.
     */
    data class Res(val id: Int, val args: List<Any> = emptyList()) : TextRef

    /**
     * Text that is only known at run time - a scanned pump name, a wiki page title, a user label.
     *
     * This replaces the `titleResId = 0` and `titleResId = -1` sentinels that used to mean "there is
     * no resource here", which callers had to remember to test for.
     */
    data class Literal(val text: String) : TextRef
}
