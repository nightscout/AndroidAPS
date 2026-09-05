package app.aaps.implementation.resources

import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.resources.formatTemplate
import app.aaps.core.interfaces.resources.TextRefValueRegistry
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.TextRef.Companion.withArgs

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
class GeneratedTextResolver(
    /**
     * Whether to shorten text for a narrow screen. See [isCompactScreen], which is what the shells
     * pass; the default keeps a test deterministic rather than answering from the machine running it.
     */
    private val compactScreen: Boolean = false
) : TextResolver {

    /**
     * Mirrors `ResourceHelper.gs(ref)` on Android, arguments included.
     *
     * A [TextRef] can carry its own [TextRef.Named.args], and this used to ignore them: only the
     * `vararg` overload formatted anything. So a ref built with `withArgs(...)` and passed to a
     * single-argument reader - which is most of them, including the notification and constraint
     * paths - rendered the raw format string. Users saw "Limiting max basal rate to %1$.2f U/h" with
     * the number missing, and the same text went to Nightscout that way.
     *
     * An unresolved name falls back to the name itself, unformatted, exactly as Android does: there
     * is no template to substitute into, and showing the name is the signal that a string owner was
     * not registered.
     */
    override fun gs(ref: TextRef): String = when (ref) {
        is TextRef.Literal    -> ref.text
        is TextRef.Named      -> {
            val template = TextRefValueRegistry.textOf(ref)
            when {
                template == null   -> ref.name
                ref.args.isEmpty() -> template
                else               -> formatTemplate(template, ref.args)
            }
        }

        is TextRef.AndroidRes -> "res:${ref.id}"
    }

    /**
     * Same, with format arguments.
     *
     * Rebuilds the ref rather than formatting the resolved text, so there is exactly one place that
     * substitutes and [TextRef.Named.args] is always the thing it reads - the shape Android uses. The
     * previous form formatted the *output* of `gs(ref)`, which meant a ref carrying its own arguments
     * was formatted twice or not at all depending on which overload the caller reached for.
     */
    override fun gs(ref: TextRef, vararg args: Any?): String = gs(ref.withArgs(*args))

    /** The map is English already, so this is the same lookup. */
    override fun gsNotLocalised(ref: TextRef): String = gs(ref)

    /** Answered by the shell through [isCompactScreen]: true on an iPhone, false on an iPad or a desktop. */
    override fun shortTextMode(): Boolean = compactScreen
}
