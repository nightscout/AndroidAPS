package info.nightscout.androidaps.plugins.general.maintenance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import info.nightscout.androidaps.logging.L;

/**
 * This class provides serveral methods for log-handling (eg. sending logs as emails).
 */
public class LoggerUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(L.CORE);

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
