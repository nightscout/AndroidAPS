package app.aaps.ios.shell.di

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef

/**
 * The two leaves the probe graph has to supply itself.
 *
 * Everything else the probe builds is real AAPS code. These two are interfaces whose only
 * implementations live in Android source sets, so on iOS there is nothing to bind them to yet.
 * They are kept deliberately dumb: the point is to watch Metro build real objects, not to
 * reimplement logging or resources.
 */
internal object ProbeLogger : AAPSLogger {

    /** Counts calls, so the probe can show that the injected logger is the one being used. */
    var calls: Int = 0
        private set

    private fun note() { calls++ }

    override fun debug(message: String) = note()
    override fun debug(enable: Boolean, tag: LTag, message: String) = note()
    override fun debug(tag: LTag, message: String) = note()
    override fun debug(tag: LTag, accessor: () -> String) = note()
    override fun debug(tag: LTag, format: String, vararg arguments: Any?) = note()
    override fun warn(tag: LTag, message: String) = note()
    override fun warn(tag: LTag, format: String, vararg arguments: Any?) = note()
    override fun info(tag: LTag, message: String) = note()
    override fun info(tag: LTag, format: String, vararg arguments: Any?) = note()
    override fun error(tag: LTag, message: String) = note()
    override fun error(tag: LTag, message: String, throwable: Throwable) = note()
    override fun error(tag: LTag, format: String, vararg arguments: Any?) = note()
    override fun error(message: String) = note()
    override fun error(message: String, throwable: Throwable) = note()
    override fun error(format: String, vararg arguments: Any?) = note()
    override fun debug(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) = note()
    override fun info(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) = note()
    override fun warn(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) = note()
    override fun error(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) = note()
}

/** Resolves a [TextRef] to something printable, with no translations behind it. */
internal object ProbeTextResolver : TextResolver {

    override fun gs(ref: TextRef): String = when (ref) {
        is TextRef.Literal    -> ref.text
        is TextRef.Named      -> ref.name
        is TextRef.AndroidRes -> "res:${ref.id}"
    }

    override fun gs(ref: TextRef, vararg args: Any): String = gs(ref)
    override fun gsNotLocalised(ref: TextRef): String = gs(ref)
    override fun shortTextMode(): Boolean = false
}
