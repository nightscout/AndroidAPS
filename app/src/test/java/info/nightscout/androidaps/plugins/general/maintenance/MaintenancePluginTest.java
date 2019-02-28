package info.nightscout.androidaps.plugins.general.maintenance;

import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MaintenancePluginTest {

    private MaintenancePlugin sut = new MaintenancePlugin();

    @Test
    public void getLogfilesTest() {
        String logDirectory = "src/test/res/logger";

        List<File> logs = sut.getLogfiles(logDirectory, 2);
        assertEquals(2, logs.size());
        assertEquals("AndroidAPS.log", logs.get(0).getName());
        assertEquals("AndroidAPS.2018-01-03_01-01-00.1.zip", logs.get(1).getName());

        logs = sut.getLogfiles(logDirectory, 10);
        assertEquals(4, logs.size());
    }


    @Test
    public void zipLogsTest() {
        String logDirectory = "src/test/res/logger";
        List<File> logs = sut.getLogfiles(logDirectory, 2);

        String name = "AndroidAPS.log.zip";

        File zipFile = new File("build/" + name);
        zipFile = sut.zipLogs(zipFile, logs);

        assertTrue(zipFile.exists());
        assertTrue(zipFile.isFile());
    }

}
