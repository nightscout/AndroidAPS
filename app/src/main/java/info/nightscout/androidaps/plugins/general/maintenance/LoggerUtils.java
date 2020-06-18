package info.nightscout.androidaps.plugins.general.maintenance;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;

/**
 * This class provides serveral methods for log-handling (eg. sending logs as emails).
 */
public class LoggerUtils {

    public static String SUFFIX = ".log.zip";

    /**
     * Returns the directory, in which the logs are stored on the system. This is configured in the
     * logback.xml file.
     *
     * @return
     */
    public static String getLogDirectory() {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        return lc.getProperty("EXT_FILES_DIR");
    }

}
