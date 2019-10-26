package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import com.google.common.base.MoreObjects;
import com.google.gson.annotations.Expose;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.db.TDD;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.StringUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry;

/**
 * Created by andy on 11/3/18.
 */

/**
 * NOTE: Decoding is only done for insulin part, everything else is pretty must left undecoded.
 */

public class DailyTotalsDTO {

    private static final Logger LOG = LoggerFactory.getLogger(L.PUMPCOMM);

    // bg avg, bg low hi, number Bgs,
    // Sen Avg, Sen Lo/Hi, Sens Cal/Data = 0/0,
    // Insulin=19.8[8,9], Basal[10,11], Bolus[13,14], Carbs,
    // Bolus=1.7, Fodd, Corr, Manual=1.7,
    // Num bOlus=1, food/corr, Food+corr, manual bolus=1
    private Double bgAvg;
    private Double bgLow;
    private Double bgHigh;
    private Integer bgCount;

    private Double sensorAvg;
    private Double sensorMin;
    private Double sensorMax;
    private Integer sensorCalcCount;
    private Integer sensorDataCount;

    @Expose
    private Double insulinTotal = 0.0d;
    @Expose
    private Double insulinBasal = 0.0d;
    @Expose
    private Double insulinBolus = 0.0d;
    private Double insulinCarbs;

    private Double bolusTotal;
    private Double bolusFood;
    private Double bolusFoodAndCorr;
    private Double bolusCorrection;
    private Double bolusManual;

    private Integer bolusCount;
    private Integer bolusCountFoodOrCorr;
    // Integer bolusCountCorr;
    Integer bolusCountFoodAndCorr;
    Integer bolusCountManual;
    private Integer bolusCountFood;
    private Integer bolusCountCorr;

    PumpHistoryEntry entry;


    public DailyTotalsDTO(PumpHistoryEntry entry) {
        this.entry = entry;

        switch (entry.getEntryType()) {
            case EndResultTotals:
                decodeEndResultsTotals(entry);
                break;

            case DailyTotals515:
                decodeDailyTotals515(entry.getBody());
                break;

            case DailyTotals522:
                decodeDailyTotals522(entry.getBody());
                break;

            case DailyTotals523:
                decodeDailyTotals523(entry.getBody());
                break;

            default:
                break;
        }

        setDisplayable();
    }


    private void setDisplayable() {

        if (this.insulinBasal == null) {
            this.entry.setDisplayableValue("Total Insulin: " + StringUtil.getFormatedValueUS(this.insulinTotal, 2));
        } else {
            this.entry.setDisplayableValue("Basal Insulin: " + StringUtil.getFormatedValueUS(this.insulinBasal, 2)
                    + ", Total Insulin: " + StringUtil.getFormatedValueUS(this.insulinTotal, 2));
        }

    }


    private void decodeEndResultsTotals(PumpHistoryEntry entry) {
        double totals = ByteUtil.toInt((int) entry.getHead()[0], (int) entry.getHead()[1], (int) entry.getHead()[2],
                (int) entry.getHead()[3], ByteUtil.BitConversion.BIG_ENDIAN) * 0.025d;

        this.insulinTotal = totals;

        entry.addDecodedData("Totals", totals);
    }


    private void testDecode(byte[] data) {

        // Daily

        byte[] body = data; // entry.getBody();
        //System.out.println("Totals 522");

        for (int i = 0; i < body.length - 2; i++) {

            int j = ByteUtil.toInt(body[i], body[i + 1]);
            int k = ByteUtil.toInt(body[i], body[i + 1], body[i + 2]);

            int j1 = ByteUtil.toInt(body[i + 1], body[i]);
            int k1 = ByteUtil.toInt(body[i + 2], body[i + 1], body[i]);

            System.out.println(String.format(
                    "index: %d, number=%d, del/40=%.3f, del/10=%.3f, singular=%d, sing_hex=%s", i, j, j / 40.0d, j / 10.0d,
                    body[i], ByteUtil.shortHexString(body[i])));

            System.out.println(String.format("     number[k,j1,k1]=%d / %d /%d, del/40=%.3f, del/40=%.3f, del/40=%.3f",
                    k, j1, k1, k / 40.0d, j1 / 40.0d, k1 / 40.0d));

        }
    }


    private void decodeDailyTotals515(byte[] data) {
        // LOG.debug("Can't decode DailyTotals515: Body={}", ByteUtil.getHex(data));

        this.insulinTotal = ByteUtil.toInt(data[8], data[9]) / 40.0d;
        this.insulinBasal = ByteUtil.toInt(data[10], data[11]) / 40.0d;
        this.insulinBolus = ByteUtil.toInt(data[13], data[14]) / 40.0d;

        // Delivery Stats: BG AVG: Bg Low/Hi=none,Number BGs=0
        // Delivery Stats: INSULIN: Basal 22.30, Bolus=4.20, Catbs = 0g (26.5)
        // Delivery Stats: BOLUS: Food=0.00, Corr=0.00, Manual=4.20
        // Delivery Stats: NUM BOLUS: Food/Corr=0,Food+Corr=0, Manual=3

        //LOG.debug("515: {}", toString());
    }


    private void decodeDailyTotals522(byte[] data) {

        this.insulinTotal = ByteUtil.toInt(data[8], data[9]) / 40.0d;
        this.insulinBasal = ByteUtil.toInt(data[10], data[11]) / 40.0d;
        this.insulinBolus = ByteUtil.toInt(data[13], data[14]) / 40.0d;

        this.bolusTotal = ByteUtil.toInt(data[17], data[18], data[19]) / 40.0d;
        this.bolusFood = ByteUtil.toInt(data[21], data[22]) / 40.0d;
        this.bolusCorrection = ByteUtil.toInt(data[23], data[24], data[25]) / 40.0d;
        this.bolusManual = ByteUtil.toInt(data[26], data[27], data[28]) / 40.0d;

        bolusCount = ByteUtil.asUINT8(data[30]);
        bolusCountFoodOrCorr = ByteUtil.asUINT8(data[31]);
        bolusCountFoodAndCorr = ByteUtil.asUINT8(data[32]);
        bolusCountManual = ByteUtil.asUINT8(data[33]);

        // bg avg, bg low hi, number Bgs,
        // Sen Avg, Sen Lo/Hi, Sens Cal/Data = 0/0,
        // Insulin=19.8[8,9], Basal[10,11], Bolus[13,14], Carbs,
        // Bolus=1.7[18,19], Fodd, Corr, Manual=1.7[27,28],
        // Num bOlus=1, food/corr, Food+corr, manual bolus=1

        //LOG.debug("522: {}", toString());
    }


    private void decodeDailyTotals523(byte[] data) {

        this.insulinTotal = ByteUtil.toInt(data[8], data[9]) / 40.0d;
        this.insulinBasal = ByteUtil.toInt(data[10], data[11]) / 40.0d;
        this.insulinBolus = ByteUtil.toInt(data[13], data[14]) / 40.0d;
        this.insulinCarbs = ByteUtil.toInt(data[16], data[17]) * 1.0d;

        this.bolusFood = ByteUtil.toInt(data[18], data[19]) / 40.0d;
        this.bolusCorrection = ByteUtil.toInt(data[20], data[21]) / 40.0d;
        this.bolusFoodAndCorr = ByteUtil.toInt(data[22], data[23]) / 40.0d;
        this.bolusManual = ByteUtil.toInt(data[24], data[25]) / 40.0d;

        this.bolusCountFood = ByteUtil.asUINT8(data[26]);
        this.bolusCountCorr = ByteUtil.asUINT8(data[27]);
        this.bolusCountFoodAndCorr = ByteUtil.asUINT8(data[28]);
        this.bolusCountManual = ByteUtil.asUINT8(data[29]); // +

        // Delivery Stats: Carbs=11, Total Insulin=3.850, Basal=2.000
        // Delivery Stats: Basal 52,Bolus 1.850, Bolus=48%o
        // Delivery Stats: Food only=0.9, Food only#=1, Corr only = 0.0
        // Delivery Stats: #Corr_only=0,Food+Corr=0.000, #Food+Corr=0
        // Delivery Stats: Manual = 0.95, #Manual=5

        //LOG.debug("523: {}", toString());
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
                .add("bolusCountFood", bolusCountFood) //
                .add("bolusCountCorr", bolusCountCorr) //
                .add("bolusCountManual", bolusCountManual) //
                .omitNullValues() //
                .toString();
    }


    public void setTDD(TDD tdd) {
        tdd.date = DateTimeUtil.toMillisFromATD(this.entry.atechDateTime);
        tdd.basal = insulinBasal;
        tdd.bolus = insulinBolus;
        tdd.total = insulinTotal;
    }


    public boolean doesEqual(TDD tdd) {
        return tdd.total == this.insulinTotal && tdd.bolus == this.insulinBolus && tdd.basal == this.insulinBasal;
    }

}
