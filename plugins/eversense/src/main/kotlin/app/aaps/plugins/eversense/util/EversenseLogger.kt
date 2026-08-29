package app.aaps.plugins.eversense.util

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.core.joran.spi.JoranException
import java.io.ByteArrayInputStream
import java.io.InputStream

class EversenseLogger private constructor(logDir: String) {
    private val lc = LoggerContext()
    private var isEnabled: Boolean = true

    init {
        val config = JoranConfigurator()
        config.setContext(lc)
        // Set before doConfigure() so ${EXT_FILES_DIR} in LOGBACK_XML resolves to this directory -
        // Joran checks context properties during variable substitution even though LOGBACK_XML
        // itself declares no <property> element for it.
        lc.putProperty("EXT_FILES_DIR", logDir)

        val stream: InputStream = ByteArrayInputStream(LOGBACK_XML.toByteArray())
        try {
            config.doConfigure(stream)
        } catch (e: JoranException) {
            e.printStackTrace()
        }
    }

    private fun debug(tag: String, message: String) {
        if (!isEnabled) { return }

        lc.getLogger(tag).debug(logLocationPrefix() + message)
    }

    private fun info(tag: String, message: String) {
        if (!isEnabled) { return }
        lc.getLogger(tag).info(logLocationPrefix() + message)
    }

    private fun warning(tag: String, message: String) {
        if (!isEnabled) { return }
        lc.getLogger(tag).warn(logLocationPrefix() + message)
    }

    private fun error(tag: String, message: String) {
        if (!isEnabled) { return }
        lc.getLogger(tag).error(logLocationPrefix() + message)
    }

    private fun enableLogging(value: Boolean) {
        this.isEnabled = value
    }

    private fun logLocationPrefix(): String {
        val stackInfo = Throwable().stackTrace[4]
        val className = stackInfo.className.substringAfterLast(".")
        val methodName = stackInfo.methodName
        val lineNumber = stackInfo.lineNumber

        return "$className.$methodName():$lineNumber]: "
    }

    companion object {
        // Fallback if configure() is never called before the first log call (e.g. a unit test, or
        // some log call racing plugin construction) - matches this class's original, always-hardcoded
        // behavior, so nothing regresses in that case; it just won't be reachable by the app's log
        // export on modern Android (see configure()'s doc below).
        private const val DEFAULT_LOG_DIR = "/sdcard/AndroidAPS/eversense"

        @Volatile private var configuredInstance: EversenseLogger? = null

        private val instance: EversenseLogger
            get() = configuredInstance ?: synchronized(this) {
                configuredInstance ?: EversenseLogger(DEFAULT_LOG_DIR).also { configuredInstance = it }
            }

        /**
         * Points Eversense's log file at [logDir] instead of [DEFAULT_LOG_DIR]. Call once, as early
         * as possible - Logback is configured on first use of [instance], and later calls are a
         * no-op once that has happened. See EversenseCGMPlugin, the sole caller: it passes the
         * app's own scoped external-files directory - the same one AndroidAPS.log itself uses -
         * because [DEFAULT_LOG_DIR] is a raw top-level /sdcard path, and writing there is silently
         * blocked by scoped storage on modern Android without the MANAGE_EXTERNAL_STORAGE
         * permission AAPS doesn't otherwise need. That silently produced zero Eversense.log entries
         * in every log export a user ever sent while diagnosing this.
         */
        fun configure(logDir: String) {
            synchronized(this) {
                if (configuredInstance == null) configuredInstance = EversenseLogger(logDir)
            }
        }

        fun debug(tag: String, message: String) {
            instance.debug(tag, message)
        }

        fun info(tag: String, message: String) {
            instance.info(tag, message)
        }

        fun warning(tag: String, message: String) {
            instance.warning(tag, message)
        }

        fun error(tag: String, message: String) {
            instance.error(tag, message)
        }

        fun enableLogging(value: Boolean) {
            instance.enableLogging(value)
        }

        private const val LOGBACK_XML: String = "<configuration>\n" +
            "    <!-- EXT_FILES_DIR is supplied at runtime via the context property set in init{} above,\n" +
            "         not hardcoded here - see EversenseLogger.configure(). -->\n" +
            "    <appender name=\"file\" class=\"ch.qos.logback.core.rolling.RollingFileAppender\">\n" +
            "        <file>\${EXT_FILES_DIR}/Eversense.log</file>\n" +
            "        <rollingPolicy class=\"ch.qos.logback.core.rolling.TimeBasedRollingPolicy\">\n" +
            "            <!-- daily rollover. Make sure the path matches the one in the file element or else\n" +
            "             the rollover logs are placed in the working directory. -->\n" +
            "            <fileNamePattern>\${EXT_FILES_DIR}/Eversense._%d{yyyy-MM-dd}_%d{HH-mm-ss, aux}_.%i.zip\n" +
            "            </fileNamePattern>\n" +
            "\n" +
            "            <timeBasedFileNamingAndTriggeringPolicy\n" +
            "                class=\"ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP\">\n" +
            "                <maxFileSize>5MB</maxFileSize>\n" +
            "            </timeBasedFileNamingAndTriggeringPolicy>\n" +
            "            <!-- keep 30 days' worth of history -->\n" +
            "            <maxHistory>120</maxHistory>\n" +
            "        </rollingPolicy>\n" +
            "        <encoder>\n" +
            "            <pattern>[%d{HH:mm:ss.SSS} %.-1level/%logger %msg%n</pattern>\n" +
            "        </encoder>\n" +
            "    </appender>\n" +
            "\n" +
            "    <appender name=\"logcat\" class=\"ch.qos.logback.classic.android.LogcatAppender\">\n" +
            "        <tagEncoder>\n" +
            "            <pattern>%logger{0}</pattern>\n" +
            "        </tagEncoder>\n" +
            "        <encoder>\n" +
            "            <pattern>[%d{HH:mm:ss.SSS} %msg%n</pattern>\n" +
            "        </encoder>\n" +
            "    </appender>\n" +
            "\n" +
            "    <!-- Write INFO (and higher-level) messages to the log file -->\n" +
            "    <root level=\"DEBUG\">\n" +
            "        <appender-ref ref=\"file\" />\n" +
            "        <appender-ref ref=\"logcat\" />\n" +
            "    </root>\n" +
            "</configuration>"

    }
}