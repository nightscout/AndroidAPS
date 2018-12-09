package info.nightscout.androidaps.plugins.PumpMedtronic.data.dto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;

import info.nightscout.androidaps.plugins.PumpCommon.utils.ByteUtil;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.pump.PumpHistoryEntryType;

/**
 * Created by andy on 11/3/18.
 */

public class DailyTotalsDTO {

    private static final Logger LOG = LoggerFactory.getLogger(DailyTotalsDTO.class);

    // bg avg, bg low hi, number Bgs,
    // Sen Avg, Sen Lo/Hi, Sens Cal/Data = 0/0,
    // Insulin=19.8[8,9], Basal[10,11], Bolus[13,14], Carbs,
    // Bolus=1.7, Fodd, Corr, Manual=1.7,
    // Num bOlus=1, food/corr, Food+corr, manual bolus=1
    Double bgAvg;
    Double bgLow;
    Double bgHigh;
    Integer bgCount;

    Double sensorAvg;
    Double sensorMin;
    Double sensorMax;
    Integer sensorCalcCount;
    Integer sensorDataCount;

    Double insulinTotal;
    Double insulinBasal;
    Double insulinBolus;
    Double insulinCarbs;

    Double bolusTotal;
    Double bolusFood;
    Double bolusCorrection;
    Double bolusManual;

    Integer bolusCount;
    Integer bolusCountFoodOrCorr;
    // Integer bolusCountCorr;
    Integer bolusCountFoodAndCorr;
    Integer bolusCountManual;


    public DailyTotalsDTO(PumpHistoryEntryType entryType, byte[] data) {
        switch (entryType) {

            case DailyTotals515:
                decodeData512(data);
                break;

            case DailyTotals522:
                decodeData522(data);
                break;
            case DailyTotals523:
                decodeData523(data);
                break;

            default:
                break;
        }
    }


    private void decodeData512(byte[] data) {
        LOG.debug("Can't decode DailyTotals512: Body={}", ByteUtil.getHex(data));
    }


    // bg avg, bg low hi, number Bgs,
    // Sen Avg, Sen Lo/Hi, Sens Cal/Data = 0/0,
    // Insulin=19.8[8,9], Basal[10,11], Bolus[13,14], Carbs,
    // Bolus=1.7[18,19], Fodd, Corr, Manual=1.7[27,28],
    // Num bOlus=1, food/corr, Food+corr, manual bolus=1
    private void decodeData522(byte[] data) {

        // Double bgAvg;
        // Double bgLow;
        // Double bgHigh;
        // Integer bgCount;
        //
        // Double sensorAvg;
        // Double sensorMin;
        // Double sensorMax;
        // Integer sensorCalcCount;
        // Integer sensorDataCount;

        this.insulinTotal = ByteUtil.toInt(data[8], data[9]) / 40.0d;
        this.insulinBasal = ByteUtil.toInt(data[10], data[11]) / 40.0d;
        this.insulinBolus = ByteUtil.toInt(data[13], data[14]) / 40.0d;
        // Double insulinCarbs;

        this.bolusTotal = ByteUtil.toInt(data[17], data[18], data[19]) / 40.0d;
        this.bolusFood = ByteUtil.toInt(data[21], data[22]) / 40.0d;
        this.bolusCorrection = ByteUtil.toInt(data[23], data[24], data[25]) / 40.0d;
        this.bolusManual = ByteUtil.toInt(data[26], data[27], data[28]) / 40.0d;

        bolusCount = ByteUtil.asUINT8(data[30]);
        bolusCountFoodOrCorr = ByteUtil.asUINT8(data[31]);
        // Integer bolusCountCorr;
        bolusCountFoodAndCorr = ByteUtil.asUINT8(data[32]);
        bolusCountManual = ByteUtil.asUINT8(data[33]);

        LOG.debug("{}", toString());

    }


    private void decodeData523(byte[] data) {
        LOG.debug("Can't decode DailyTotals523: Body={}", ByteUtil.getHex(data));

        // 0x6E 0xB1 0x92
        // 0x05 0x00 0x80 0x00 0x00 0x01 0x00 0x00 0x00 0x9A 0x00 0x50 0x34 0x00 0x4A 0x30 0x00 0x0B 0x00
        // 0x24 0x00 0x00 0x00 0x00 0x00 0x26 0x01 0x00 0x00 0x05 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00
        // 0x00 0x80 0x80 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00

        // 0x05 0x00 0x80 0x00 0x00 0x01 0x00 0x00 0x00 0x9A 0x00 0x50 0x34 0x00 0x4A 0x30 0x00 0x0B 0x00
        // 0x24 0x00 0x00 0x00 0x00 0x00 0x26 0x01 0x00 0x00
        // 0x05 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x80 0x80 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00

        // 19:31:40.314 [Thread-47]
        // D/info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.pump.MedtronicPumpHistoryDecoder:
        // [MedtronicPumpHistoryDecoder.decodeDailyTotals():447]: PumpHistoryRecord [type=DailyTotals523 [110, 0x6E],
        // DT: 16.11.2018 00:00:00, length=1,2,49(52), data={Raw Data=0x6E 0xB0 0x92 0x05 0x00 0x00 0x00 0x00 0x00 0x00
        // 0x00 0x00 0x55 0x00 0x55 0x64 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00
        // 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00}]
        // 19:31:40.318 [Thread-47] D/info.nightscout.androidaps.plugins.PumpMedtronic.data.dto.DailyTotalsDTO:
        // [DailyTotalsDTO.decodeData523():117]: Can't decode DailyTotals523: Body=0x05 0x00 0x00 0x00 0x00 0x00 0x00
        // 0x00 0x00 0x55 0x00 0x55 0x64 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00
        // 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00
        // 19:31:40.320 [Thread-47]
        // W/info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.pump.MedtronicPumpHistoryDecoder:
        // [MedtronicPumpHistoryDecoder.createRecords():181]: #0 WIP PumpHistoryRecord [type=DailyTotals523 [110, 0x6E],
        // DT: 16.11.2018 00:00:00, length=1,2,49(52), data={Raw Data=0x6E 0xB0 0x92 0x05 0x00 0x00 0x00 0x00 0x00 0x00
        // 0x00 0x00 0x55 0x00 0x55 0x64 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00
        // 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00}]
        // 19:31:40.341 [Thread-47]
        // D/info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.pump.MedtronicPumpHistoryDecoder:
        // [MedtronicPumpHistoryDecoder.decodeDateTime():793]: DT: 2018 11 17
        // 19:31:40.342 [Thread-47]
        // D/info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.pump.MedtronicPumpHistoryDecoder:
        // [MedtronicPumpHistoryDecoder.decodeDateTime():793]: DT: 2018 11 17
        // 19:31:40.342 [Thread-47]
        // D/info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.pump.MedtronicPumpHistoryDecoder:
        // [MedtronicPumpHistoryDecoder.decodeDailyTotals():446]: DailyTotals523 - 0x6E 0xB1 0x92 0x05 0x00 0x80 0x00
        // 0x00 0x01 0x00 0x00 0x00 0x9A 0x00 0x50 0x34 0x00 0x4A 0x30 0x00 0x0B 0x00 0x24 0x00 0x00 0x00 0x00 0x00 0x26
        // 0x01 0x00 0x00 0x05 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x80 0x80 0x00 0x00 0x00 0x00 0x00 0x00 0x00
        // 0x00
        // 19:31:40.343 [Thread-47]
        // D/info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.pump.MedtronicPumpHistoryDecoder:
        // [MedtronicPumpHistoryDecoder.decodeDailyTotals():447]: PumpHistoryRecord [type=DailyTotals523 [110, 0x6E],
        // DT: 17.11.2018 00:00:00, length=1,2,49(52), data={Raw Data=0x6E 0xB1 0x92 0x05 0x00 0x80 0x00 0x00 0x01 0x00
        // 0x00 0x00 0x9A 0x00 0x50 0x34 0x00 0x4A 0x30 0x00 0x0B 0x00 0x24 0x00 0x00 0x00 0x00 0x00 0x26 0x01 0x00 0x00
        // 0x05 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x80 0x80 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00}]
        // 19:31:40.343 [Thread-47] D/info.nightscout.androidaps.plugins.PumpMedtronic.data.dto.DailyTotalsDTO:
        // [DailyTotalsDTO.decodeData523():117]: Can't decode DailyTotals523: Body=0x05 0x00 0x80 0x00 0x00 0x01 0x00
        // 0x00 0x00 0x9A 0x00 0x50 0x34 0x00 0x4A 0x30 0x00 0x0B 0x00 0x24 0x00 0x00 0x00 0x00 0x00 0x26 0x01 0x00 0x00
        // 0x05 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x80 0x80 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00
        // 19:31:40.344 [Thread-47]
        // W/info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.pump.MedtronicPumpHistoryDecoder:
        // [MedtronicPumpHistoryDecoder.createRecords():181]: #60 WIP PumpHistoryRecord [type=DailyTotals523 [110,
        // 0x6E], DT: 17.11.2018 00:00:00, length=1,2,49(52), data={Raw Data=0x6E 0xB1 0x92 0x05 0x00 0x80 0x00 0x00
        // 0x01 0x00 0x00 0x00 0x9A 0x00 0x50 0x34 0x00 0x4A 0x30 0x00 0x0B 0x00 0x24 0x00 0x00 0x00 0x00 0x00 0x26 0x01
        // 0x00 0x00 0x05 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x80 0x80 0x00 0x00 0x00 0x00 0x00 0x00 0x00
        // 0x00}]
    }


    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this) //
            .add("bgAvg", bgAvg) //
            .add("bgLow", bgLow) //
            .add("bgHigh", bgHigh) //
            .add("bgCount", bgCount) //
            .add("sensorAvg", sensorAvg) //
            .add("sensorMin", sensorMin) //
            .add("sensorMax", sensorMax) //
            .add("sensorCalcCount", sensorCalcCount) //
            .add("sensorDataCount", sensorDataCount) //
            .add("insulinTotal", insulinTotal) //
            .add("insulinBasal", insulinBasal) //
            .add("insulinBolus", insulinBolus) //
            .add("insulinCarbs", insulinCarbs) //
            .add("bolusTotal", bolusTotal) //
            .add("bolusFood", bolusFood) //
            .add("bolusCorrection", bolusCorrection) //
            .add("bolusManual", bolusManual) //
            .add("bolusCount", bolusCount) //
            .add("bolusCountFoodOrCorr", bolusCountFoodOrCorr) //
            .add("bolusCountFoodAndCorr", bolusCountFoodAndCorr) //
            .add("bolusCountManual", bolusCountManual) //
            .toString();
    }
}
