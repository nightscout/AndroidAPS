package app.aaps.pump.medtronic.comm.history.pump

import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.utils.pump.ByteUtil
import app.aaps.pump.medtronic.MedtronicTestBase
import app.aaps.pump.medtronic.comm.history.RawHistoryPage
import app.aaps.pump.medtronic.defs.MedtronicDeviceType
import app.aaps.pump.medtronic.driver.MedtronicPumpStatus
import app.aaps.pump.medtronic.util.MedtronicUtil
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

/**
 * Created by andy on 11/1/18.
 */
@Suppress("SpellCheckingInspection")
class MedtronicPumpHistoryDecoderUTest : MedtronicTestBase() {

    @Mock lateinit var uiInteraction: UiInteraction

    private lateinit var medtronicPumpStatus: MedtronicPumpStatus

    //private var medtronicUtil: MedtronicUtil? = null
    //private var decoder: MedtronicPumpHistoryDecoder? = null

    @BeforeEach fun setup() {
        medtronicPumpStatus = MedtronicPumpStatus(preferences, rxBus, rileyLinkUtil)
        medtronicUtil = MedtronicUtil(aapsLogger, rxBus, rileyLinkUtil, medtronicPumpStatus, uiInteraction)
        decoder = MedtronicPumpHistoryDecoder(aapsLogger, medtronicUtil)
    }

    /*

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
*/
    @Test @Throws(Exception::class) fun historyProblem_148_amunhateb() {
        val pumpHistoryEntries = readAndParseHistoryPage(
            "5A 0F 20 F4 0C 03 15 19 11 00 17 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 1E 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 32 50 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 1A 11 00 0F 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 1E 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 32 50 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 33 01 12 12 00 25 DE 2D 43 15 01 50 50 00 26 EA 2D 43 15 01 4B 4B 00 2C C9 34 43 15 62 00 2F CB 17 03 15 01 33 33 00 16 DE 37 43 15 07 00 00 07 FE 23 95 6D 23 95 0A 08 00 2B 00 00 00 00 07 FE 03 8E 2C 04 70 38 00 00 04 70 38 00 00 00 00 00 00 04 70 64 06 00 00 00 06 08 00 2B 00 00 00 2C A0 2F E3 01 04 15 33 00 2F E7 04 44 15 00 16 03 2F E7 04 44 15 33 28 3B C2 06 44 15 00 16 01 3B C2 06 44 15 08 08 17 DB 0B 44 15 00 26 00 06 26 00 0C 26 00 12 28 00 18 26 00 1E 26 00 24 24 00 2A 26 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 09 18 17 DB 0B 44 15 00 26 00 02 26 00 04 26 00 06 24 00 08 24 00 0A 24 00 0C 26 00 0E 26 00 10 26 00 12 28 00 14 28 00 16 28 00 18 26 00 1A 26 00 1C 26 00 1E 26 00 20 26 00 22 26 00 24 24 00 26 24 00 28 24 00 2A 26 00 2C 26 00 2E 26 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 45 45 00 28 E9 2B 44 15 19 00 00 C1 0D 04 15 1A 00 15 C3 0D 04 15 1A 01 33 C3 0D 04 15 01 28 28 00 07 CC 2E 44 15 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 14 2D",
            MedtronicDeviceType.Medtronic_522_722
        )
        assertThat(pumpHistoryEntries).hasSize(20)
    }

    @Test @Throws(Exception::class) fun historyProblem_423_duzy78() {
        val pumpHistoryEntries = readAndParseHistoryPage(
            "16 00 08 D0 0E 51 15 33 60 0A D0 0E 51 15 00 16 01 0A D0 0E 51 15 33 00 07 DF 0E 51 15 00 16 00 07 DF 0E 51 15 33 6C 09 DF 0E 51 15 00 16 01 09 DF 0E 51 15 33 00 25 ED 0E 51 15 00 16 00 25 ED 0E 51 15 33 2C 27 ED 0E 51 15 00 16 01 27 ED 0E 51 15 33 00 07 F4 0E 51 15 00 16 00 07 F4 0E 51 15 33 00 09 F4 0E 51 15 00 16 01 09 F4 0E 51 15 33 2C 25 D5 0F 51 15 00 16 01 25 D5 0F 51 15 01 3C 3C 00 30 D5 4F 51 15 33 2C 25 F7 0F 51 15 00 16 01 25 F7 0F 51 15 33 00 07 C6 10 51 15 00 16 00 07 C6 10 51 15 33 30 09 C6 10 51 15 00 16 01 09 C6 10 51 15 33 30 25 E8 10 51 15 00 16 01 25 E8 10 51 15 33 30 24 CF 11 51 15 00 16 01 24 CF 11 51 15 33 00 23 E4 11 51 15 00 16 00 23 E4 11 51 15 33 3C 25 E4 11 51 15 00 16 01 25 E4 11 51 15 33 00 23 E8 11 51 15 00 16 00 23 E8 11 51 15 33 4A 25 E8 11 51 15 00 16 01 25 E8 11 51 15 33 00 19 EE 11 51 15 00 16 00 19 EE 11 51 15 33 30 1B EE 11 51 15 00 16 01 1B EE 11 51 15 33 00 23 F2 11 51 15 00 16 00 23 F2 11 51 15 33 3E 25 F2 11 51 15 00 16 01 25 F2 11 51 15 33 00 24 C0 12 51 15 00 16 00 24 C0 12 51 15 33 5E 25 C0 12 51 15 00 16 01 25 C0 12 51 15 33 00 23 CF 12 51 15 00 16 00 23 CF 12 51 15 33 64 25 CF 12 51 15 00 16 01 25 CF 12 51 15 33 00 23 D9 12 51 15 00 16 00 23 D9 12 51 15 33 6A 25 D9 12 51 15 00 16 01 25 D9 12 51 15 33 00 23 E9 12 51 15 00 16 00 23 E9 12 51 15 33 30 25 E9 12 51 15 00 16 01 25 E9 12 51 15 01 16 16 00 10 C2 53 51 15 33 30 24 CF 13 51 15 00 16 01 24 CF 13 51 15 33 30 04 EE 13 51 15 00 16 01 04 EE 13 51 15 01 14 14 00 3B F0 53 51 15 33 00 22 C0 14 51 15 00 16 00 22 C0 14 51 15 33 22 24 C0 14 51 15 00 16 01 24 C0 14 51 15 33 22 03 DF 14 51 15 00 16 01 03 DF 14 51 15 1E 00 37 E1 14 11 15 1F 00 01 EE 14 11 15 33 22 03 C6 15 51 15 00 16 01 03 C6 15 51 15 33 00 20 D9 15 51 15 00 16 00 20 D9 15 51 15 33 34 22 D9 15 51 15 00 16 01 22 D9 15 51 15 39 14 0E DF 35 71 15 83 92 40 01 0B 0B 00 37 E0 55 51 15 33 00 21 E3 15 51 15 00 16 00 21 E3 15 51 15 33 22 22 E3 15 51 15 00 16 01 22 E3 15 51 15 33 00 21 E8 15 51 15 00 16 00 21 E8 15 51 15 33 30 23 E8 15 51 15 00 16 01 23 E8 15 51 15 33 00 20 ED 15 51 15 00 16 00 20 ED 15 51 15 33 22 22 ED 15 51 15 00 16 01 22 ED 15 51 15 33 00 03 F8 15 51 15 00 16 00 03 F8 15 51 15 33 32 05 F8 15 51 15 00 16 01 05 F8 15 51 15 33 00 01 CB 16 51 15 00 16 00 01 CB 16 51 15 33 20 03 CB 16 51 15 00 16 01 03 CB 16 51 15 33 2A 20 ED 16 51 15 00 16 01 20 ED 16 51 15 33 00 02 F8 16 51 15 00 16 00 02 F8 16 51 15 33 2C 04 F8 16 51 15 00 16 01 04 F8 16 51 15 33 00 1F CA 17 51 15 00 16 00 1F CA 17 51 15 33 34 21 CA 17 51 15 00 16 01 21 CA 17 51 15 33 00 1F D4 17 51 15 00 16 00 1F D4 17 51 15 33 38 21 D4 17 51 15 00 16 01 21 D4 17 51 15 33 00 15 EE 17 51 15 00 16 00 15 EE 17 51 15 33 42 17 EE 17 51 15 00 16 01 17 EE 17 51 15 07 00 00 08 0A 31 95 6C 31 95 05 00 A1 A1 A1 01 00 00 08 0A 04 8E 39 03 7C 2B 00 00 03 7C 2B 00 00 00 00 00 00 03 7C 64 07 00 00 00 07 33 00 05 C1 00 52 15 00 16 00 05 C1 00 52 15 33 50 07 C1 00 52 15 00 16 01 07 C1 00 52 15 33 00 01 CB 00 52 15 00 16 00 01 CB 00 52 15 33 26 03 CB 00 52 15 00 16 01 03 CB 00 52 15 33 00 1E DE 00 52 15 00 00 00 8F 0E",
            MedtronicDeviceType.Medtronic_515_715
        )
        assertThat(pumpHistoryEntries).hasSize(131)
    }

    @Test @Throws(Exception::class) fun historyProblem_476_OpossumGit() {
        val pumpHistoryEntries = readAndParseHistoryPage(
            "08 07 50 05 0D 4D 15 00 18 00 08 14 00 0E 10 00 14 08 00 1E 12 00 26 16 00 2B 1A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 09 18 50 05 0D 4D 15 00 18 00 02 18 00 04 18 00 06 18 00 08 14 00 0A 14 00 0C 14 00 0E 10 00 10 10 00 12 10 00 14 08 00 16 08 00 18 08 00 1A 08 00 1C 08 00 1E 14 00 20 14 00 22 14 00 24 14 00 26 16 00 28 16 00 2A 16 00 2C 1C 00 2E 1C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 04 04 00 44 09 4D 4D 15 33 06 57 15 0D 4D 15 00 16 01 57 15 0D 4D 15 33 00 77 15 0D 4D 15 00 16 00 77 15 0D 4D 15 33 00 5D 16 0D 4D 15 00 16 04 5D 16 0D 4D 15 33 00 61 1A 0D 4D 15 00 16 00 61 1A 0D 4D 15 5D 00 5E 31 0D 0D 15 1A 00 6E 31 0D 0D 15 06 03 04 D2 6E 31 6D 0D 15 0C 03 11 40 00 01 05 64 01 13 40 00 01 05 17 00 0A 41 00 01 05 18 00 40 39 15 0D 15 21 00 53 04 16 0D 15 03 00 00 00 58 57 09 36 0D 15 5D 01 78 0A 16 0D 15 64 01 78 0A 16 8D 15 2C 68 78 0A 16 8D 15 24 3C 78 0A 16 8D 15 63 02 78 0A 16 8D 15 1B 12 78 0A 16 8D 15 65 61 78 0A 16 8D 15 61 00 78 0A 16 8D 15 32 0E 78 0A 16 8D 15 66 00 78 0A 16 8D 15 3C 01 78 0A 16 8D 15 3D 88 32 93 00 00 00 3E 00 00 00 00 00 00 26 01 78 0A 16 8D 15 27 01 B2 08 00 00 00 28 00 00 00 00 00 00 60 00 78 0A 16 4D 15 23 00 78 0A 16 8D 15 5E 01 78 0A 16 8D 15 2D 01 78 0A 16 8D 15 5A 0F 78 0A 16 8D 15 49 13 00 07 12 0A 1E 0B 2A 0A 00 00 00 00 00 00 00 00 00 23 08 27 2C 23 00 00 00 00 00 00 00 00 00 00 00 32 41 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 49 13 00 07 12 0A 1E 0B 2A 0A 00 00 00 00 00 00 00 00 00 23 08 27 2C 23 00 00 00 00 00 00 00 00 00 00 00 32 41 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 22 62 00 78 0A 16 8D 15 5F 51 78 0A 16 8D 15 4F 00 78 0A 16 8D 15 40 01 00 6F 1C 16 1E 00 3C 14 00 1E 3C 1F 15 70 40 01 00 6F 1C 16 1E 00 3C 14 00 1E 3C 1F 15 70 08 18 78 0A 16 8D 15 00 18 00 02 18 00 04 18 00 06 18 00 08 14 00 0A 14 00 0C 14 00 0E 10 00 10 10 00 12 10 00 14 08 00 16 08 00 18 08 00 1A 08 00 1C 08 00 1E 14 00 20 14 00 22 14 00 24 14 00 26 16 00 28 16 00 2A 16 00 2C 1C 00 2E 1C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 D6 06",
            MedtronicDeviceType.Medtronic_522_722
        )
        assertThat(pumpHistoryEntries).hasSize(41)
    }

    @Throws(Exception::class)
    private fun readAndParseHistoryPage(
        historyPageString: String,
        medtronicDeviceType: MedtronicDeviceType
    ): List<PumpHistoryEntry> {
        val historyPageData = ByteUtil.createByteArrayFromString(historyPageString)
        aapsLogger.debug("History Page Length:" + historyPageData.size)
        medtronicUtil.medtronicPumpModel = medtronicDeviceType
        medtronicUtil.isModelSet = true
        val historyPage = RawHistoryPage(aapsLogger)
        historyPage.appendData(historyPageData)
        val pumpHistoryEntries: List<PumpHistoryEntry> = decoder.processPageAndCreateRecords(historyPage)
        displayHistoryRecords(pumpHistoryEntries)
        return pumpHistoryEntries
    }

    private fun displayHistoryRecords(pumpHistoryEntries: List<PumpHistoryEntry>) {
        aapsLogger.debug("PumpHistoryEntries: " + pumpHistoryEntries.size)
        for (pumpHistoryEntry in pumpHistoryEntries) {
            aapsLogger.debug(pumpHistoryEntry.toString())
        }
    }
}
