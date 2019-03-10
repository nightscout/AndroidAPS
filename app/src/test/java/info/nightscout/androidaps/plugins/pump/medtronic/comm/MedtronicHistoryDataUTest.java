package info.nightscout.androidaps.plugins.pump.medtronic.comm;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import uk.org.lidalia.slf4jtest.TestLogger;
import uk.org.lidalia.slf4jtest.TestLoggerFactory;

import android.util.Log;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.RawHistoryPage;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.MedtronicPumpHistoryDecoder;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry;

/**
 * Created by andy on 3/10/19.
 */

public class MedtronicHistoryDataUTest {

    TestLogger LOGGER = TestLoggerFactory.getTestLogger(MedtronicHistoryDataUTest.class);

    byte[] historyPageData = ByteUtil
        .createByteArrayFromString("16 00 12 EC 14 47 13 33 00 14 F2 14 47 13 00 16 01 14 F2 14 47 13 33 00 1C C9 15 47 13 00 16 00 1C C9 15 47 13 33 4E 31 D3 15 47 13 00 16 01 31 D3 15 47 13 33 00 1A F1 15 47 13 00 16 00 1A F1 15 47 13 33 50 1D F1 15 47 13 00 16 01 1D F1 15 47 13 33 50 11 D8 16 47 13 00 16 01 11 D8 16 47 13 33 50 31 FB 16 47 13 00 16 01 31 FB 16 47 13 33 50 12 E3 17 47 13 00 16 01 12 E3 17 47 13 33 00 1E FB 17 47 13 00 16 00 1E FB 17 47 13 33 D8 21 FB 17 47 13 00 16 01 21 FB 17 47 13 07 00 00 05 CC 27 93 6D 27 93 05 0C 00 E8 00 00 00 00 05 CC 05 CC 64 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 0C 00 E8 00 00 00 33 00 36 C4 00 48 13 00 16 00 36 C4 00 48 13 33 D8 29 C9 00 48 13 00 16 01 29 C9 00 48 13 33 00 12 E7 00 48 13 00 16 00 12 E7 00 48 13 33 BC 19 C9 01 48 13 00 16 01 19 C9 01 48 13 33 00 26 CE 01 48 13 00 16 00 26 CE 01 48 13 33 44 29 CE 01 48 13 00 16 01 29 CE 01 48 13 33 00 13 D3 01 48 13 00 16 00 13 D3 01 48 13 33 64 31 F1 01 48 13 00 16 01 31 F1 01 48 13 33 00 0B F7 01 48 13 00 16 00 0B F7 01 48 13 33 00 12 D8 02 48 13 00 16 01 12 D8 02 48 13 33 00 10 F1 02 48 13 00 16 00 10 F1 02 48 13 33 00 30 C4 03 48 13 00 16 01 30 C4 03 48 13 33 00 04 CA 03 48 13 00 16 00 04 CA 03 48 13 33 00 2F D3 03 48 13 00 16 01 2F D3 03 48 13 33 00 30 D8 03 48 13 00 16 00 30 D8 03 48 13 33 00 13 E7 03 48 13 00 16 01 13 E7 03 48 13 33 00 2E FB 03 48 13 00 16 00 2E FB 03 48 13 19 00 00 C1 04 08 13 07 00 00 04 0C 28 93 6D 28 93 05 0C 00 E8 00 00 00 00 04 0C 04 0C 64 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 0C 00 E8 00 00 00 06 3E 03 7A 19 DC 48 49 13 0C 3E 0C E6 08 09 13 07 00 00 01 E4 29 93 6D 29 93 05 0C 00 E8 00 00 00 00 01 E4 01 E4 64 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 0C 00 E8 00 00 00 1A 00 13 D2 0D 0A 13 1A 01 28 D2 0D 0A 13 21 00 2A D8 0D 0A 13 03 00 00 00 0E 2D D9 2D 0A 13 33 98 26 DE 0D 4A 13 00 16 01 26 DE 0D 4A 13 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 5D 70");

    MedtronicPumpHistoryDecoder decoder = new MedtronicPumpHistoryDecoder();


    // Logger LOGGER = LoggerFactory.getLogger(MedtronicHistoryDataUTest.class);

    @Before
    public void setup() {

        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace");

        // final TestAppender appender = new TestAppender();
        // final Logger logger = Logger.getRootLogger();
        // logger.addAppender(appender);
        // try {
        // Logger.getLogger(MyTest.class).info("Test");
        // } finally {
        // logger.removeAppender(appender);
        // }
    }


    @Test
    public void testTBR() throws Exception {

        RawHistoryPage historyPage = new RawHistoryPage();
        historyPage.appendData(historyPageData);

        List<PumpHistoryEntry> pumpHistoryEntries = decoder.processPageAndCreateRecords(historyPage,
            PumpHistoryEntry.class);

        System.out.println("PumpHistoryEntries: " + pumpHistoryEntries.size());

        Log.d("Test", "Log.d");
        LOGGER.debug("Logger.debug");

        for (PumpHistoryEntry pumpHistoryEntry : pumpHistoryEntries) {
            Log.d("MedtronicHistoryDataUTest", pumpHistoryEntry.toString());
        }

    }


    public void testBolus() throws Exception {

    }

}
