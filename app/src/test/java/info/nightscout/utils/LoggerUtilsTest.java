package info.nightscout.utils;

import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LoggerUtilsTest {

    @Test
    public void getLogfilesTest() {
        String logDirectory = "src/test/res/logger";

        List<File> logs = LoggerUtils.getLogfiles(logDirectory, 2);
        assertEquals(2, logs.size());
        assertEquals("AndroidAPS.log", logs.get(0).getName());
        assertEquals("AndroidAPS.2018-01-03_01-01-00.1.zip", logs.get(1).getName());

        logs = LoggerUtils.getLogfiles(logDirectory, 10);
        assertEquals(4, logs.size());
    }


    @Test
    public void zipLogsTest() {
        String logDirectory = "src/test/res/logger";
        List<File> logs = LoggerUtils.getLogfiles(logDirectory, 2);

        String name = "AndroidAPS.log.zip";

        File zipFile = LoggerUtils.zipLogs(name, "build", logs);

        assertTrue(zipFile.exists());
        assertTrue(zipFile.isFile());
    }

}
