package app.aaps.implementation.logging

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.logging.LTag
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSISO8601DateFormatter
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataUsingEncoding
import platform.Foundation.NSLog
import platform.Foundation.create
import platform.Foundation.closeFile
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.seekToEndOfFile
import platform.Foundation.writeData

/**
 * Logging on iOS: the console for looking at now, a file for looking at later.
 *
 * The Android logger goes through logback, which is a JVM library, so iOS needs its own. Two sinks,
 * because the two questions are different. `NSLog` answers "what is happening" - it reaches the
 * unified log, so it shows up under `xcrun simctl launch --console`, in Console.app, and in a device
 * log. The file answers "what happened", which matters most for a diabetes app: by the time a user
 * reports something odd, the console is long gone.
 *
 * `os_log` would be the better console sink, but it is a C macro rather than a function, so
 * Kotlin/Native exposes `os_log_create` and `OS_LOG_DEFAULT` with nothing to call them with.
 * `NSLog` ends up in the same unified log.
 *
 * ## What this does not do yet
 *
 * Rotation is by size and keeps one previous file, so a run cannot fill the device. It does not zip
 * and it does not keep a dated history, which the Android side does - that belongs with whatever
 * ends up sharing logs off the phone, and there is no such path on iOS yet.
 */
@OptIn(ExperimentalForeignApi::class)
class AAPSLoggerIos(
    private val fileName: String = "aaps.log",
    private val maxBytes: Long = 5L * 1024 * 1024,
    /** Reads the stored per-tag switches. Null before the graph can supply one - see [enabled]. */
    private val logConfig: (() -> L)? = null
) : AAPSLogger {

    private val timestamps = NSISO8601DateFormatter()

    // ---------------------------------------------------------------------------------------------
    // AAPSLogger
    // ---------------------------------------------------------------------------------------------

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

    // ---------------------------------------------------------------------------------------------
    // Sinks
    // ---------------------------------------------------------------------------------------------

    /**
     * Whether [tag] is switched on, from the stored preference when there is one.
     *
     * This used to read `tag.defaultValue`, which is the compile-time default and not a switch at
     * all: eight tags ship `false`, so they could never be turned on however the log-settings sheet
     * was set, and "enable NSCLIENT_SYNC and send me the log" was impossible off Android. The
     * preference-backed answer is `L`, which lives in `shared/impl` commonMain and is available on
     * every platform.
     *
     * Deferred and nullable because this logger is one of the first things the graph builds - the
     * probe shell and the tests construct it with no graph at all - and `L` needs `Preferences`.
     * Before it can be read, the compile-time default is the only answer there is.
     */
    internal fun enabled(tag: LTag): Boolean =
        logConfig?.let { runCatching { it().findByName(tag.tag).enabled }.getOrNull() } ?: tag.defaultValue

    private fun write(level: String, tag: LTag?, message: String, gated: Boolean = true) {
        // Errors are never gated, matching Android: a tag switched off must quieten the running
        // commentary, not hide the thing that went wrong. This gated everything, so an error on one
        // of the tags that ship disabled was dropped and nothing recorded that it had been.
        if (gated && tag != null && !enabled(tag)) return

        val line = "${timestamps.stringFromDate(NSDate())} $level ${tag?.tag ?: "CORE"}: $message"
        // NSLog is a C varargs function, so `%@` needs a real Obj-C object. Passing the Kotlin
        // String straight through segfaults - it is not boxed into an NSString on the way.
        NSLog("%@", NSString.create(string = line))
        appendToFile(line)
    }

    /**
     * `{}` placeholders, as logback spells them.
     *
     * Callers write the slf4j style the Android logger accepts, so the same call has to mean the
     * same thing here. Extra arguments are dropped and missing ones are left as `{}`, which is what
     * logback does rather than throwing - a broken log line must never take the app down.
     */
    internal fun String.fill(arguments: Array<out Any?>): String {
        val out = StringBuilder()
        // Scans forward rather than searching the result again each time. Re-searching meant an
        // argument that itself contained "{}" was treated as the next placeholder, so it swallowed
        // the argument after it.
        var from = 0
        for (argument in arguments) {
            val at = indexOf("{}", from)
            if (at < 0) break
            out.append(this, from, at).append(argument.toString())
            from = at + 2
        }
        out.append(this, from, length)
        return out.toString()
    }

    private fun appendToFile(line: String) {
        val path = logPath() ?: return
        val manager = NSFileManager.defaultManager
        rotateIfNeeded(manager, path)
        if (!manager.fileExistsAtPath(path)) manager.createFileAtPath(path, null, null)

        val data = (line + "\n").let { NSString.create(string = it) }.dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val handle = NSFileHandle.fileHandleForWritingAtPath(path) ?: return
        handle.seekToEndOfFile()
        handle.writeData(data)
        handle.closeFile()
    }

    /** Keeps one previous file, so the worst case on disk is twice [maxBytes]. */
    private fun rotateIfNeeded(manager: NSFileManager, path: String) {
        val size = (manager.attributesOfItemAtPath(path, null)?.get("NSFileSize") as? Number)?.toLong() ?: return
        if (size < maxBytes) return
        val previous = "$path.1"
        manager.removeItemAtPath(previous, null)
        manager.moveItemAtPath(path, previous, null)
    }

    private fun logPath(): String? {
        val documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String ?: return null
        return "$documents/$fileName"
    }

    /** Where the log file is, so the app can offer it for sharing once there is a way to. */
    fun logFileUrl(): NSURL? = logPath()?.let { NSURL.fileURLWithPath(it) }
}
