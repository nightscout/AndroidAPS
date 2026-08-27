package app.aaps.core.interfaces.resources

import app.aaps.core.keys.interfaces.TextRef

/**
 * Turns a [TextRef] into a String outside Compose. Inside Compose use
 * `app.aaps.core.ui.compose.stringResource` instead.
 *
 * This is the part of string resolution that every platform can implement, so it is what shared code
 * depends on. On Android `ResourceHelper` extends it and adds the resource id overloads, which only
 * mean something where Android resources exist.
 *
 * The split exists because most files in this module never call a resolver - they only name the type
 * in a signature, for example `fun minAgo(rh: ResourceHelper, time: Long?)`. Naming [TextResolver]
 * instead lets those files move to commonMain without any change to their callers, since every
 * `ResourceHelper` is a [TextResolver].
 */
interface TextResolver {

    /** Resolves [ref] in the current language. */
    fun gs(ref: TextRef): String

    /** Same, with format arguments. */
    fun gs(ref: TextRef, vararg args: Any?): String

    /** Same, but always in English - used for data that is stored or uploaded, not displayed. */
    fun gsNotLocalised(ref: TextRef): String

    /** True on a small screen, where the UI uses shorter texts. */
    fun shortTextMode(): Boolean
}
