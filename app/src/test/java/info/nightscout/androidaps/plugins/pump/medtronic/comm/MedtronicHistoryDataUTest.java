package info.nightscout.androidaps.plugins.pump.medtronic.comm;

import android.util.Log;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.RawHistoryPage;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.MedtronicPumpHistoryDecoder;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry;
//import uk.org.lidalia.slf4jtest.TestLogger;
//import uk.org.lidalia.slf4jtest.TestLoggerFactory;

/**
 * Created by andy on 3/10/19.
 */
@Ignore
public class MedtronicHistoryDataUTest {

    //TestLogger LOGGER = TestLoggerFactory.getTestLogger(MedtronicHistoryDataUTest.class);

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

        List<PumpHistoryEntry> pumpHistoryEntries = decoder.processPageAndCreateRecords(historyPage);

        System.out.println("PumpHistoryEntries: " + pumpHistoryEntries.size());

        Log.d("Test", "Log.d");
        //LOGGER.debug("Logger.debug");

        for (PumpHistoryEntry pumpHistoryEntry : pumpHistoryEntries) {
            Log.d("MedtronicHistoryDataUTest", pumpHistoryEntry.toString());
        }

    }


    public void testRussel() throws Exception {
        byte[] historyPageData = ByteUtil
                .createByteArrayFromString("06 15 04 F6 00 40 60 01 05 06 36 04 FE 00 40 60 01 05 06 2F 18 1A 00 40 20 C1 05 06 2F 0C 45 00 40 20 C1 05 06 2F 0C 56 00 40 20 C1 05 06 2F 0C 78 00 40 20 C1 05 06 2F 0C AD 00 40 20 C1 05 06 15 04 BA 00 40 40 A1 05 0C 15 0E 40 00 01 05 64 00 0D 44 00 01 05 17 00 14 44 00 01 05 18 00 00 44 00 01 05 21 00 07 44 00 01 05 21 00 0C 4E 00 01 05 07 00 00 00 00 01 85 6D 01 85 06 08 00 2B 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 08 00 2B 00 00 00 03 00 00 00 15 00 67 35 02 05 03 00 03 00 03 1C 67 15 02 05 07 00 00 00 40 02 85 6D 02 85 06 08 00 2B 00 00 00 00 00 40 00 40 64 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 08 00 2B 00 00 00 2C 78 39 5F 17 03 05 07 00 00 02 6C 03 85 6D 03 85 06 08 00 2B 00 00 00 00 02 6C 02 6C 64 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 08 00 2B 00 00 00 26 01 33 44 01 04 05 27 03 74 41 01 B2 07 28 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 29 74");

        RawHistoryPage historyPage = new RawHistoryPage();
        historyPage.appendData(historyPageData);

        List<PumpHistoryEntry> pumpHistoryEntries = decoder.processPageAndCreateRecords(historyPage);

        System.out.println("PumpHistoryEntries: " + pumpHistoryEntries.size());

        Log.d("Test", "Log.d");
        //LOGGER.debug("Logger.debug");

        for (PumpHistoryEntry pumpHistoryEntry : pumpHistoryEntries) {
            Log.d("MedtronicHistoryDataUTest", pumpHistoryEntry.toString());
        }

    }

}
