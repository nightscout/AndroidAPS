package info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.pump;

import org.junit.Before;

import info.nightscout.androidaps.plugins.PumpCommon.utils.ByteUtil;

/**
 * Created by andy on 11/1/18.
 */
public class MedtronicPumpHistoryDecoderUTest {

    MedtronicPumpHistoryDecoder decoder = new MedtronicPumpHistoryDecoder();


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


    // @Test
    public void decodeRecord() throws Exception {

        byte[] data = new byte[] { 0x07, 0x00, 0x00, 0x05, (byte)0xFA, (byte)0xBF, 0x12 };

        PumpHistoryEntryType entryType = PumpHistoryEntryType.getByCode(0x07);

        PumpHistoryEntry phe = new PumpHistoryEntry();
        phe.setEntryType(entryType);
        phe.setData(ByteUtil.getListFromByteArray(data), false);

        decoder.decodeRecord(phe);

        System.out.println("Record: " + phe);

    }


    // @Test
    public void decodeDailyTotals522() {
        // PumpHistoryRecord [type=DailyTotals522 [109, 0x6D], DT: 01.11.2018 00:00:00, length=1,2,41(44), data={Raw
        // Data=0x6D 0xA1 0x92 0x05 0x0C 0x00 0xE8 0x00 0x00 0x00 0x00 0x04 0x0A 0x04 0x0A 0x64 0x00 0x00 0x00 0x00 0x00
        // 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x0C 0x00 0xE8 0x00 0x00
        // 0x00}]
        byte[] data4443 = new byte[] {
            0x6D, (byte)0xA1, (byte)0x92, 0x05, 0x0C, 0x00, (byte)0xE8, 0x00, 0x00, 0x00, 0x00, 0x04, 0x0A, 0x04, 0x0A,
            0x64, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x0C, 0x00, (byte)0xE8, 0x00, 0x00, 0x00 };

        byte[] data = new byte[] {
            0x6D, (byte)0xA2, (byte)0x92, 0x05, 0x0C, 0x00, (byte)0xE8, 0x00, 0x00, 0x00, 0x00, 0x03, 0x18, 0x02,
            (byte)0xD4, 0x5B, 0x00, 0x44, 0x09, 0x00, 0x00, 0x00, 0x44, 0x09, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x44, 0x64, 0x01, 0x00, 0x00, 0x00, 0x01, 0x0C, 0x00, (byte)0xE8, 0x00, 0x00, 0x00 };

        // basal 18.1, bolus 1.7 manual = 1.7
        // All (bg low hi, number Bgs, Sen Lo/Hi, Sens Cal/Data, Basal, Bolus, Carbs, Fodd, Corr, Manual=1.7, food/corr,
        // Food+corr, manual bolus=1
        testRecord(data);

    }


    private void testRecord(byte[] data) {
        // byte[] data = new byte[] { 0x07, 0x00, 0x00, 0x05, (byte)0xFA, (byte)0xBF, 0x12 };

        PumpHistoryEntryType entryType = PumpHistoryEntryType.getByCode(data[0]);

        PumpHistoryEntry phe = new PumpHistoryEntry();
        phe.setEntryType(entryType);
        phe.setData(ByteUtil.getListFromByteArray(data), false);

        decoder.decodeRecord(phe);

        System.out.println("Record: " + phe);

    }

}
