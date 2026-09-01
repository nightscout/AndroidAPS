package app.aaps.implementation.resources

import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.resources.TextRefValueRegistry
import app.aaps.core.keys.interfaces.TextRef

/**
 * Reads text from the generated string maps, for the platforms that have no Android resource table.
 *
 * iOS and the desktop JVM each had a placeholder resolver that answered with the **name** of a string
 * rather than its text, so a settings screen read `configbuilder_general` instead of "General". Two
 * copies of the same placeholder was the signal that this was not an iOS problem: the English
 * `strings.xml` is already the single source both would read, and `GenerateKeyStringsTask` now emits
 * it as a `name -> text` map beside the `TextRef` names it always generated.
 *
 * Each shell registers the modules it depends on with [TextRefValueRegistry] before any text is
 * asked for, the same way `MainApp.registerStringOwners()` registers id maps on Android.
 *
 * ## What it does not do
 *
 * **Only English.** The generated map holds the base locale. Everything needed for a translated
 * non-Android build - choosing a locale at runtime, and generating the other `values-*` directories -
 * is a separate job, and this one does not pretend to have done it.
 *
 * **A name with no entry falls back to the name itself**, which is what the placeholders did. That
 * happens when a shell has not registered the owning module, or when a `TextRef.Named` was built by
 * hand instead of taken from the generated object. Showing the name puts the fault on the screen
 * instead of showing empty space or taking the app down.
 *
 * [TextRef.AndroidRes] cannot be resolved here at all - it holds a number that only means something
 * to AAPT - so it renders as `res:<id>`. Shared code should not be producing one; if this shows up on
 * screen, that is the bug it is pointing at.
 */
class GeneratedTextResolver : TextResolver {

    override fun gs(ref: TextRef): String = when (ref) {
        is TextRef.Literal    -> ref.text
        is TextRef.Named      -> TextRefValueRegistry.textOf(ref) ?: ref.name
        is TextRef.AndroidRes -> "res:${ref.id}"
    }

    override fun gs(ref: TextRef, vararg args: Any?): String = formatTemplate(gs(ref), args.toList())

    /** The map is English already, so this is the same lookup. */
    override fun gsNotLocalised(ref: TextRef): String = gs(ref)

    /** A desktop window and an iPad are both wide. Nothing here runs on a watch. */
    override fun shortTextMode(): Boolean = false
}
