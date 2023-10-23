package app.aaps.implementation.logging

import app.aaps.annotations.OpenForTesting
import app.aaps.core.interfaces.logging.LoggerUtils
import app.aaps.core.interfaces.maintenance.PrefFileListProvider
import dagger.Reusable
import javax.inject.Inject

/**
 * This class provides several methods for log-handling (eg. sending logs as emails).
 */
@OpenForTesting
@Reusable
class LoggerUtilsImpl @Inject constructor(
    val prefFileListProvider: PrefFileListProvider
) : LoggerUtils {

    override var suffix = ".log.zip"

    /**
     * Returns the directory, in which the logs are stored on the system. This is configured in the
     * logback.xml file.
     *
     * @return
     */
    /*
        This is failing after slf4j update to 2.0.0
        It would be better to find a way to read the value from xml
        So far replaced by static value
        val logDirectory: String
            get() {
                val lc = LoggerFactory.getILoggerFactory() as LoggerContext
                return lc.getProperty("EXT_FILES_DIR")
            }
    */
    override val logDirectory get() = prefFileListProvider.logsPath
}