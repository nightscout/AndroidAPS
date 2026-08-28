package app.aaps.ios.shell.di

import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef

/**
 * The one leaf the probe graph still has to supply itself.
 *
 * Logging used to be here too, until `AAPSLoggerIos` replaced it. Resources are harder: strings
 * live in Android resource files and iOS has no reader for them, so this stays a stand-in. It is
 * deliberately dumb - the point is to watch Metro build real objects, not to reimplement resources.
 */
internal object ProbeTextResolver : TextResolver {

    override fun gs(ref: TextRef): String = when (ref) {
        is TextRef.Literal    -> ref.text
        is TextRef.Named      -> ref.name
        is TextRef.AndroidRes -> "res:${ref.id}"
    }

    override fun gs(ref: TextRef, vararg args: Any?): String = gs(ref)
    override fun gsNotLocalised(ref: TextRef): String = gs(ref)
    override fun shortTextMode(): Boolean = false
}
