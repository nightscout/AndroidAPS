package info.nightscout.comboctl.base

/**
 * Valid log levels.
 *
 * VERBOSE is recommended for log lines that produce large amounts of log information
 * since they are called constantly. One example would be a packet data dump.
 *
 * DEBUG is recommended for detailed information about the internals that does _not_
 * show up all the time (unlike VERBOSE).
 *
 * INFO is to be used for important information.
 *
 * WARN is to be used for situations that could lead to errors or are otherwise
 * of potential concern.
 *
 * ERROR is to be used for when an error occurs.
 */
enum class LogLevel(val str: String, val numericLevel: Int) {
    VERBOSE("VERBOSE", 4),
    DEBUG("DEBUG", 3),
    INFO("INFO", 2),
    WARN("WARN", 1),
    ERROR("ERROR", 0)
}

/**
 * Interface for backends that actually logs the given message.
 */
interface LoggerBackend {
    /**
     * Instructs the backend to log the given message.
     *
     * The tag and log level are provided so the backend can highlight
     * these in its output in whatever way it wishes.
     *
     * In addition, a throwable can be logged in case the log line
     * was caused by one. The [SingleTagLogger.invoke] call may have provided
     * only a message string, or a throwable, or both, which is why both of these
     * arguments are nullable.
     *
     * @param tag Tag for this message. Typically, this is the name of the
     *        class the log operation was performed in.
     * @param level Log level of the given message.
     * @param message Optional string containing the message to log.
     * @param throwable Optional throwable.
     */
    fun log(tag: String, level: LogLevel, throwable: Throwable?, message: String?)
}

/**
 * Backend that does not actually log anything. Logical equivalent of Unix' /dev/null.
 */
class NullLoggerBackend : LoggerBackend {
    override fun log(tag: String, level: LogLevel, throwable: Throwable?, message: String?) = Unit
}

/**
 * Backend that prints log lines to stderr.
 */
class StderrLoggerBackend : LoggerBackend {
    override fun log(tag: String, level: LogLevel, throwable: Throwable?, message: String?) {
        val timestamp = getElapsedTimeInMs()

        val stackInfo = Throwable().stackTrace[1]
        val className = stackInfo.className.substringAfterLast(".")
        val methodName = stackInfo.methodName
        val lineNumber = stackInfo.lineNumber

        val fullMessage = "[${timestamp.toStringWithDecimal(3).padStart(10, ' ')}] " +
            "[${level.str}] [$tag] [$className.$methodName():$lineNumber]" +
            (if (throwable != null) "  (${throwable::class.qualifiedName}: \"${throwable.message}\")" else "") +
            (if (message != null) "  $message" else "")

        System.err.println(fullMessage)
    }
}

class SingleTagLogger(val tag: String) {
    inline operator fun invoke(logLevel: LogLevel, logLambda: () -> String) {
        if (logLevel.numericLevel <= Logger.threshold.numericLevel)
            Logger.backend.log(tag, logLevel, null, logLambda.invoke())
    }

    operator fun invoke(logLevel: LogLevel, throwable: Throwable) {
        if (logLevel.numericLevel <= Logger.threshold.numericLevel)
            Logger.backend.log(tag, logLevel, throwable, null)
    }

    inline operator fun invoke(logLevel: LogLevel, throwable: Throwable, logLambda: () -> String) {
        if (logLevel.numericLevel <= Logger.threshold.numericLevel)
            Logger.backend.log(tag, logLevel, throwable, logLambda.invoke())
    }
}

/**
 * Main logging interface.
 *
 * Applications can set a custom logger backend simply by setting
 * the [Logger.backend] variable to a new value. By default, the
 * [StderrLoggerBackend] is used.
 *
 * The logger is used by adding a line like this at the top of:
 * a source file:
 *
 *     private val logger = Logger.get("TagName")
 *
 * Then, in the source, logging can be done like this:
 *
 *     logger(LogLevel.DEBUG) { "Logging example" }
 *
 * This logs the "Logging example line" with the DEBUG log level
 * and the "TagName" tag (see [LoggerBackend.log] for details).
 *
 * Additionally, the threshold value provides a way to prefilter
 * logging based on the log level. Only log calls with the log
 * level of the threshold value (or below) are logged. This helps
 * with reducing log spam, and improves performance, since the
 * log line lambdas (shown above) are then only invoked if the
 * log level is <= the threshold. In practice, this is most useful
 * for enabling/disabling verbose logging. Verbose log lines are
 * only really useful for development and more advanced debugging;
 * in most cases, [LogLevel.DEBUG] as threshold should suffice.
 */
object Logger {
    var threshold = LogLevel.DEBUG
    var backend: LoggerBackend = StderrLoggerBackend()
    fun get(tag: String) = SingleTagLogger(tag)
}
