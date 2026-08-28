package app.aaps.implementation.logging

import app.aaps.core.interfaces.logging.LoggerUtils
import ch.qos.logback.classic.LoggerContext
import dev.zacsweers.metro.Inject
import org.slf4j.LoggerFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn

/**
 * This class provides several methods for log-handling (eg. sending logs as emails).
 */
// Metro builds this; Dagger consumers (MaintenanceImpl) get it via the @Provides delegate in `:app`.
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
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