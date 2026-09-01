package app.aaps.ios.shell.di

import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef

/**
 * Strings, until iOS can read the app's resource files.
 *
 * Android keeps its text in `strings.xml` and looks it up by resource id. iOS has neither, so this
 * answers with the **name** of the string rather than its text: a button whose label is
 * `exported_ago` renders as `exported_ago`.
 *
 * That is deliberate rather than lazy. The alternative placeholders are both worse: an empty string
 * makes a screen look broken and hides which text was wanted, and invented English would read as
 * finished work and quietly become the wording nobody ever replaced. A visible key is obviously
 * unfinished, and it says exactly which string to look up.
 *
 * Arguments are appended rather than substituted, because the format string itself is what is
 * missing - there is no `%1$s` to put them into. A count or a dose is usually the useful half of
 * such a line, so showing it beats dropping it.
 */
internal object IosTextResolver : TextResolver {

    override fun gs(ref: TextRef): String = when (ref) {
        is TextRef.Literal    -> ref.text
        is TextRef.Named      -> ref.name
        is TextRef.AndroidRes -> "res:${ref.id}"
    }

    override fun gs(ref: TextRef, vararg args: Any?): String =
        if (args.isEmpty()) gs(ref) else "${gs(ref)} ${args.joinToString(" ")}"

    override fun gsNotLocalised(ref: TextRef): String = gs(ref)

    override fun shortTextMode(): Boolean = false
}
