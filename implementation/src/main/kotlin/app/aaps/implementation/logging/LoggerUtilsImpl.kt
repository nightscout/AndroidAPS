package app.aaps.implementation.logging

import app.aaps.core.interfaces.logging.LoggerUtils
import ch.qos.logback.classic.LoggerContext
import dagger.Reusable
import org.slf4j.LoggerFactory
import javax.inject.Inject

/**
 * This class provides several methods for log-handling (eg. sending logs as emails).
 */
@Reusable
class LoggerUtilsImpl @Inject constructor() : LoggerUtils {

    override var suffix = ".log"

    /**
     * Returns the directory, in which the logs are stored on the system. This is configured in the
     * logback.xml file.
     *
     * @return path
     */
    override val logDirectory: String
        get() = (LoggerFactory.getILoggerFactory() as LoggerContext).getProperty("EXT_FILES_DIR")
}