package app.aaps.implementation.logging

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.logging.LTag
import java.io.File
import java.time.Instant

/**
 * The desktop logger: the console for now, and a rotating file for afterwards.
 *
 * The counterpart of `AAPSLoggerIos`, and deliberately the same shape. It is a first pass: a single
 * file that is truncated once it grows past [maxBytes], and no dated history, which the Android side
 * does keep - that belongs with whatever ends up sharing logs off the machine, and there is no such
 * path on desktop yet.
 *
 * The file sits beside the database and the preferences, so everything the app writes is in one
 * folder a user can find.
 */
class AAPSLoggerDesktop(
    private val file: File = File(File(System.getProperty("user.home"), ".aaps"), "aaps.log"),
    private val maxBytes: Long = 5L * 1024 * 1024,
    /** Reads the stored per-tag switches. Null before the graph can supply one - see [enabled]. */
    private val logConfig: (() -> L)? = null
) : AAPSLogger {

    override fun debug(message: String) = write("DEBUG", null, message)
    override fun debug(enable: Boolean, tag: LTag, message: String) {
        if (enable) write("DEBUG", tag, message)
    }

    override fun debug(tag: LTag, message: String) = write("DEBUG", tag, message)
    override fun debug(tag: LTag, accessor: () -> String) = write("DEBUG", tag, accessor())
    override fun debug(tag: LTag, format: String, vararg arguments: Any?) = write("DEBUG", tag, format.fill(arguments))

    override fun info(tag: LTag, message: String) = write("INFO", tag, message)
    override fun info(tag: LTag, format: String, vararg arguments: Any?) = write("INFO", tag, format.fill(arguments))

    override fun warn(tag: LTag, message: String) = write("WARN", tag, message)
    override fun warn(tag: LTag, format: String, vararg arguments: Any?) = write("WARN", tag, format.fill(arguments))

    override fun error(tag: LTag, message: String) = write("ERROR", tag, message, gated = false)
    override fun error(tag: LTag, message: String, throwable: Throwable) =
        write("ERROR", tag, "$message\n${throwable.stackTraceToString()}", gated = false)

    override fun error(tag: LTag, format: String, vararg arguments: Any?) = write("ERROR", tag, format.fill(arguments), gated = false)
    override fun error(message: String) = write("ERROR", null, message, gated = false)
    override fun error(message: String, throwable: Throwable) =
        write("ERROR", null, "$message\n${throwable.stackTraceToString()}", gated = false)

    override fun error(format: String, vararg arguments: Any?) = write("ERROR", null, format.fill(arguments), gated = false)

    override fun debug(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) =
        write("DEBUG", tag, "$className.$methodName($lineNumber): $message")

    override fun info(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) =
        write("INFO", tag, "$className.$methodName($lineNumber): $message")

    override fun warn(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) =
        write("WARN", tag, "$className.$methodName($lineNumber): $message")

    override fun error(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) =
        write("ERROR", tag, "$className.$methodName($lineNumber): $message", gated = false)

    /**
     * Whether [tag] is switched on, from the stored preference when there is one.
     *
     * This used to read `tag.defaultValue`, the compile-time default, which is not a switch at
     * all: eight tags ship `false` and could never be turned on however the log-settings sheet was
     * set. `L` is the preference-backed answer and lives in `shared/impl` commonMain, so every
     * platform has it.
     *
     * Deferred and nullable because the logger is built before `Preferences` exists.
     */
    private fun enabled(tag: LTag): Boolean =
        logConfig?.let { runCatching { it().findByName(tag.tag).enabled }.getOrNull() } ?: tag.defaultValue

    private fun write(level: String, tag: LTag?, message: String, gated: Boolean = true) {
        // Errors are never gated, matching Android: a tag switched off must quieten the running
        // commentary, not hide the thing that went wrong.
        if (gated && tag != null && !enabled(tag)) return

        val line = "${Instant.now()} $level ${tag?.tag ?: "CORE"}: $message"
        println(line)
        appendToFile(line)
    }

    /** `{}` placeholders, as logback spells them - the format the callers already use. */
    private fun String.fill(arguments: Array<out Any?>): String {
        var result = this
        arguments.forEach { result = result.replaceFirst("{}", it.toString()) }
        return result
    }

    private fun appendToFile(line: String) {
        runCatching {
            file.parentFile?.mkdirs()
            // Truncate rather than rotate: keeping the newest lines matters, keeping the oldest does
            // not, and a real rotation belongs with a log-sharing path that does not exist yet.
            if (file.length() > maxBytes) file.writeText("")
            file.appendText(line + "\n")
        }
        // A logger that throws would take down whatever it was reporting on, which is never the right
        // trade. The console line above has already been written either way.
    }
}
