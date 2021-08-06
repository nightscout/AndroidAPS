package info.nightscout.androidaps.plugins.general.maintenance

import ch.qos.logback.classic.LoggerContext
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This class provides several methods for log-handling (eg. sending logs as emails).
 */
@Singleton
class LoggerUtils @Inject constructor() {

    var suffix = ".log.zip"

    /**
     * Returns the directory, in which the logs are stored on the system. This is configured in the
     * logback.xml file.
     *
     * @return
     */
    val logDirectory: String
        get() {
            val lc = LoggerFactory.getILoggerFactory() as LoggerContext
            return lc.getProperty("EXT_FILES_DIR")
        }
}