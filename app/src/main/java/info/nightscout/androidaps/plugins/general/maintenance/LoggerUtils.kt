package info.nightscout.androidaps.plugins.general.maintenance

import info.nightscout.androidaps.annotations.OpenForTesting
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This class provides several methods for log-handling (eg. sending logs as emails).
 */
@OpenForTesting
@Singleton
class LoggerUtils @Inject constructor(
    val prefFileListProvider: PrefFileListProvider
) {

    var suffix = ".log.zip"

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
    val logDirectory get() = prefFileListProvider.logsPath
}