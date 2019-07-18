package info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump;

import org.junit.Before;
import org.junit.Ignore;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;

/**
 * Created by andy on 11/1/18.
 */
@Ignore
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
    public void decodeLowAmount() {
        byte[] data = new byte[] { 52, -12, 22, -81, 46, 3, 19 };

        PumpHistoryEntryGroup.doNotTranslate = true;
        PumpHistoryEntryType entryType = PumpHistoryEntryType.getByCode(52);

        PumpHistoryEntry phe = new PumpHistoryEntry();
        phe.setEntryType(entryType);
        phe.setData(ByteUtil.getListFromByteArray(data), false);

        decoder.decodeRecord(phe);

        System.out.println("Record: " + phe);
        System.out.println("Record: " + phe.getDisplayableValue());

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


    // @Test
    public void decodeDailyTotals515() {

        byte[] data = ByteUtil
            .createByteArrayFromHexString("0x6C 0x17 0x93 0x06 0x08 0x00 0x2B 0x00 0x00 0x00 0x00 0x04 0x24 0x03 0x7C 0x54 0x00 0xA8 0x10 0x00 0x00 0x00 0xA8 0x10"
                + " 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0xA8 0x64 0x03 0x00 0x00");

        // 0x6C 0x17 0x93 0x06 0x08 0x00 0x2B 0x00 0x00 0x00 0x00 0x04 0x24 0x03 0x7C 0x54 0x00 0xA8 0x10 0x00 0x00 0x00
        // 0xA8 0x10
        // 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0xA8 0x64 0x03 0x00 0x00

        // Delivery Stats: BG AVG: Bg Low/Hi=none,Number BGs=0
        // Delivery Stats: INSULIN: Basal 22.30, Bolus=4.20, Catbs = 0g
        // Delivery Stats: BOLUS: Food=0.00, Corr=0.00, Manual=4.20
        // Delivery Stats: NUM BOLUS: Food/Corr=0,Food+Corr=0, Manual=3

        testRecord(data);

    }


    // @Test
    public void decodeDailyTotals523() {

        byte[] data = new byte[] {
            0x6E, (byte)0xB1, (byte)0x92, 0x05, 0x00, (byte)0x80, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, (byte)0x9A, 0x00,
            0x50, 0x34, 0x00, 0x4A, 0x30, 0x00, 0x0B, 0x00, 0x24, 0x00, 0x00, 0x00, 0x00, 0x00, 0x26, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte)0x80, (byte)0x80, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00 };

        // Carbs=11, total=3.850,basal=2.000, bolus=1.850, basal 52%, blus=48%, Manual=0.95, #manual=5,
        // Food only=0.9, #Food Only=1,Corr Only =0, #Corr only=0,Food+Corr=0

        // Delivery Stats: Carbs=11, Total Insulin=3.850, Basal=2.000
        // Delivery Stats: Basal 52,Bolus 1.850, Bolus=48%o
        // Delivery Stats: Food only=0.9, Food only#=1, Corr only = 0.0
        // Delivery Stats: #Corr_only=0,Food+Corr=0.000, #Food+Corr=0
        // Delivery Stats: Manual = 0.95, #Manual=5

        testRecord(data);

    }


    private void testRecord(byte[] data) {
        // byte[] data = new byte[] { 0x07, 0x00, 0x00, 0x05, (byte)0xFA, (byte)0xBF, 0x12 };

        PumpHistoryEntryType entryType = PumpHistoryEntryType.getByCode(data[0]);

        PumpHistoryEntry phe = new PumpHistoryEntry();
        phe.setEntryType(entryType);
        phe.setData(ByteUtil.getListFromByteArray(data), false);

        System.out.println("EntryType: " + entryType);

        decoder.decodeRecord(phe);

        System.out.println("Record: " + phe);

    }

}
