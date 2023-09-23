package info.nightscout.androidaps.plugins.pump.medtronic.data.dto

//import info.nightscout.androidaps.db.TDD
import com.google.gson.annotations.Expose
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntryType
import info.nightscout.pump.common.utils.ByteUtil
import info.nightscout.pump.common.utils.StringUtil
import org.apache.commons.lang3.builder.ToStringBuilder
import java.util.Locale

/**
 * Created by andy on 11/3/18.
 */
/**
 * NOTE: Decoding is only done for insulin part, everything else is pretty must left undecoded.
 */
class DailyTotalsDTO(var entry: PumpHistoryEntry) {

    // bg avg, bg low hi, number Bgs,
    // Sen Avg, Sen Lo/Hi, Sens Cal/Data = 0/0,
    // Insulin=19.8[8,9], Basal[10,11], Bolus[13,14], Carbs,
    // Bolus=1.7, Fodd, Corr, Manual=1.7,
    // Num bOlus=1, food/corr, Food+corr, manual bolus=1
    private val bgAvg: Double? = null
    private val bgLow: Double? = null
    private val bgHigh: Double? = null
    private val bgCount: Int? = null
    private val sensorAvg: Double? = null
    private val sensorMin: Double? = null
    private val sensorMax: Double? = null
    private val sensorCalcCount: Int? = null
    private val sensorDataCount: Int? = null

    @Expose
    var insulinTotal = 0.0

    @Expose
    var insulinBasal: Double = 0.0

    @Expose
    var insulinBolus = 0.0
    private var insulinCarbs: Double? = null
    private var bolusTotal: Double? = null
    private var bolusFood: Double? = null
    private var bolusFoodAndCorr: Double? = null
    private var bolusCorrection: Double? = null
    private var bolusManual: Double? = null
    private var bolusCount: Int? = null
    private var bolusCountFoodOrCorr: Int? = null

    // Integer bolusCountCorr;
    var bolusCountFoodAndCorr: Int? = null
    var bolusCountManual: Int? = null
    private var bolusCountFood: Int? = null
    private var bolusCountCorr: Int? = null
    private fun setDisplayable() {
        if (insulinBasal == 0.0) {
            entry.displayableValue = "Total Insulin: " + StringUtil.getFormattedValueUS(insulinTotal, 2)
        } else {
            entry.displayableValue = ("Basal Insulin: " + StringUtil.getFormattedValueUS(insulinBasal, 2)
                + ", Total Insulin: " + StringUtil.getFormattedValueUS(insulinTotal, 2))
        }
    }

    private fun decodeEndResultsTotals(entry: PumpHistoryEntry) {
        val totals = ByteUtil.toInt(
            entry.head[0].toInt(), entry.head[1].toInt(), entry.head[2].toInt(),
            entry.head[3].toInt(), ByteUtil.BitConversion.BIG_ENDIAN
        ) * 0.025
        insulinTotal = totals
        entry.addDecodedData("Totals", totals)
    }

    private fun testDecode(data: ByteArray) {

        // Daily
        //System.out.println("Totals 522");
        for (i in 0 until data.size - 2) {
            val j = ByteUtil.toInt(data[i], data[i + 1])
            val k: Int = ByteUtil.toInt(data[i], data[i + 1], data[i + 2])
            val j1 = ByteUtil.toInt(data[i + 1], data[i])
            val k1: Int = ByteUtil.toInt(data[i + 2], data[i + 1], data[i])
            println(
                String.format(
                    Locale.ENGLISH,
                    "index: %d, number=%d, del/40=%.3f, del/10=%.3f, singular=%d, sing_hex=%s", i, j, j / 40.0, j / 10.0,
                    data[i], ByteUtil.shortHexString(data[i])
                )
            )
            println(
                String.format(
                    Locale.ENGLISH, "     number[k,j1,k1]=%d / %d /%d, del/40=%.3f, del/40=%.3f, del/40=%.3f",
                    k, j1, k1, k / 40.0, j1 / 40.0, k1 / 40.0
                )
            )
        }
    }

    private fun decodeDailyTotals515(data: ByteArray) {
        // LOG.debug("Can't decode DailyTotals515: Body={}", ByteUtil.getHex(data));
        insulinTotal = ByteUtil.toInt(data[8], data[9]) / 40.0
        insulinBasal = ByteUtil.toInt(data[10], data[11]) / 40.0
        insulinBolus = ByteUtil.toInt(data[13], data[14]) / 40.0

        // Delivery Stats: BG AVG: Bg Low/Hi=none,Number BGs=0
        // Delivery Stats: INSULIN: Basal 22.30, Bolus=4.20, Catbs = 0g (26.5)
        // Delivery Stats: BOLUS: Food=0.00, Corr=0.00, Manual=4.20
        // Delivery Stats: NUM BOLUS: Food/Corr=0,Food+Corr=0, Manual=3

        //LOG.debug("515: {}", toString());
    }

    private fun decodeDailyTotals522(data: ByteArray) {
        insulinTotal = ByteUtil.toInt(data[8], data[9]) / 40.0
        insulinBasal = ByteUtil.toInt(data[10], data[11]) / 40.0
        insulinBolus = ByteUtil.toInt(data[13], data[14]) / 40.0
        bolusTotal = ByteUtil.toInt(data[17], data[18], data[19]) / 40.0
        bolusFood = ByteUtil.toInt(data[21], data[22]) / 40.0
        bolusCorrection = ByteUtil.toInt(data[23], data[24], data[25]) / 40.0
        bolusManual = ByteUtil.toInt(data[26], data[27], data[28]) / 40.0
        bolusCount = ByteUtil.asUINT8(data[30])
        bolusCountFoodOrCorr = ByteUtil.asUINT8(data[31])
        bolusCountFoodAndCorr = ByteUtil.asUINT8(data[32])
        bolusCountManual = ByteUtil.asUINT8(data[33])

        // bg avg, bg low hi, number Bgs,
        // Sen Avg, Sen Lo/Hi, Sens Cal/Data = 0/0,
        // Insulin=19.8[8,9], Basal[10,11], Bolus[13,14], Carbs,
        // Bolus=1.7[18,19], Fodd, Corr, Manual=1.7[27,28],
        // Num bOlus=1, food/corr, Food+corr, manual bolus=1

        //LOG.debug("522: {}", toString());
    }

    private fun decodeDailyTotals523(data: ByteArray) {
        insulinTotal = ByteUtil.toInt(data[8], data[9]) / 40.0
        insulinBasal = ByteUtil.toInt(data[10], data[11]) / 40.0
        insulinBolus = ByteUtil.toInt(data[13], data[14]) / 40.0
        insulinCarbs = ByteUtil.toInt(data[16], data[17]) * 1.0
        bolusFood = ByteUtil.toInt(data[18], data[19]) / 40.0
        bolusCorrection = ByteUtil.toInt(data[20], data[21]) / 40.0
        bolusFoodAndCorr = ByteUtil.toInt(data[22], data[23]) / 40.0
        bolusManual = ByteUtil.toInt(data[24], data[25]) / 40.0
        bolusCountFood = ByteUtil.asUINT8(data[26])
        bolusCountCorr = ByteUtil.asUINT8(data[27])
        bolusCountFoodAndCorr = ByteUtil.asUINT8(data[28])
        bolusCountManual = ByteUtil.asUINT8(data[29]) // +

        // Delivery Stats: Carbs=11, Total Insulin=3.850, Basal=2.000
        // Delivery Stats: Basal 52,Bolus 1.850, Bolus=48%o
        // Delivery Stats: Food only=0.9, Food only#=1, Corr only = 0.0
        // Delivery Stats: #Corr_only=0,Food+Corr=0.000, #Food+Corr=0
        // Delivery Stats: Manual = 0.95, #Manual=5

        //LOG.debug("523: {}", toString());
    }

    override fun toString(): String {
        return ToStringBuilder(this)
            .append("bgAvg", bgAvg)
            .append("bgLow", bgLow)
            .append("bgHigh", bgHigh)
            .append("bgCount", bgCount)
            .append("sensorAvg", sensorAvg)
            .append("sensorMin", sensorMin)
            .append("sensorMax", sensorMax)
            .append("sensorCalcCount", sensorCalcCount)
            .append("sensorDataCount", sensorDataCount)
            .append("insulinTotal", insulinTotal)
            .append("insulinBasal", insulinBasal)
            .append("insulinBolus", insulinBolus)
            .append("insulinCarbs", insulinCarbs)
            .append("bolusTotal", bolusTotal)
            .append("bolusFood", bolusFood)
            .append("bolusFoodAndCorr", bolusFoodAndCorr)
            .append("bolusCorrection", bolusCorrection)
            .append("bolusManual", bolusManual)
            .append("bolusCount", bolusCount)
            .append("bolusCountFoodOrCorr", bolusCountFoodOrCorr)
            .append("bolusCountFoodAndCorr", bolusCountFoodAndCorr)
            .append("bolusCountManual", bolusCountManual)
            .append("bolusCountFood", bolusCountFood)
            .append("bolusCountCorr", bolusCountCorr)
            .append("entry", entry)
            .toString()
    }

    init {
        when (entry.entryType) {
            PumpHistoryEntryType.EndResultTotals -> decodeEndResultsTotals(entry)
            PumpHistoryEntryType.DailyTotals515  -> decodeDailyTotals515(entry.body)
            PumpHistoryEntryType.DailyTotals522  -> decodeDailyTotals522(entry.body)
            PumpHistoryEntryType.DailyTotals523  -> decodeDailyTotals523(entry.body)

            else                                 -> {
            }
        }
        setDisplayable()
    }
}