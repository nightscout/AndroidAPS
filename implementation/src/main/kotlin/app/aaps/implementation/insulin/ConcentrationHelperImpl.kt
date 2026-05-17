package app.aaps.implementation.insulin

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.implementation.R
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

@Singleton
class ConcentrationHelperImpl @Inject constructor(
    val aapsLogger: AAPSLogger,
    private val activePlugin: ActivePlugin,
    private val insulin: Insulin,
    private val rh: ResourceHelper,
    private val decimalFormatter: DecimalFormatter,
    private val dateUtil: DateUtil
) : ConcentrationHelper {

    override fun isU100(): Boolean = concentration == 1.0

    override fun fromPump(amount: PumpInsulin, isPriming: Boolean): Double = if (isPriming) amount.cU else amount.iU(concentration)

    override fun fromPump(rate: PumpRate): Double = rate.iU(concentration, true)

    override fun basalRateString(rate: PumpRate, isAbsolute: Boolean, decimals: Int): String {
        if (isAbsolute.not())
            return rh.gs(app.aaps.core.ui.R.string.formatPercent, rate.iU(concentration, isAbsolute))
        val fmt = "%.${decimals}f"
        if (isU100())
            return rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate_dynamic, fmt.format(rate.cU))
        else {
            val iUString = rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate_dynamic, fmt.format(rate.iU(concentration, isAbsolute)))
            val cUString = rh.gs(R.string.pump_base_basal_rate_cu_dynamic, fmt.format(rate.cU))
            return rh.gs(R.string.concentration_format, iUString, cUString)
        }
    }

override fun basalTbrString(rate: PumpRate, startTime: Long, durationInMin: Int, isAbsolute: Boolean, isExtended: Boolean, decimals: Int): String {
    val startTimeString = dateUtil.timeString(startTime)
    val passedMinutes = min(T.msecs(max(0, dateUtil.now() - startTime)).mins().toInt(), durationInMin)
    return rh.gs(
        if (isExtended) R.string.concentration_etbr_format else R.string.concentration_tbr_format,
        basalRateString(rate, isAbsolute), startTimeString, passedMinutes, durationInMin
    )
}

    override fun insulinAmountString(amount: PumpInsulin): String {
        val bolusStep = activePlugin.activePump.pumpDescription.bolusStep
        if (isU100())
            return decimalFormatter.toPumpSupportedBolusWithUnits(amount.cU, bolusStep)
        else { // app.aaps.core.ui.R.string.format_insulin_units
            val iUString = decimalFormatter.toPumpSupportedBolusWithUnits(amount.iU(concentration), bolusStep)
            val cUString = decimalFormatter.toPumpSupportedBolusWithUnits(amount, bolusStep / concentration)
            return rh.gs(R.string.concentration_format, iUString, cUString)
        }
    }

    override fun insulinAmountAgoString(amount: PumpInsulin, lastBolusTime: Long): String? {
        val agoHours = (System.currentTimeMillis() - lastBolusTime).toDouble() / 3_600_000.0
        return if (agoHours < 6.0) {
            "${insulinAmountString(amount)} ${dateUtil.sinceString(lastBolusTime, rh)}"
        } else null
    }

    override fun insulinDeliveryAgoString(amount: PumpInsulin, totalAmount: PumpInsulin, startTime: Long, durationInMin: Int?): String {
        val startTimeString = dateUtil.timeString(startTime)
        val passedMinutes = durationInMin?.let {
            min(T.msecs(max(0, dateUtil.now() - startTime)).mins().toInt(), it)
        } ?: T.msecs(dateUtil.now() - startTime).mins().toInt()
        val format = durationInMin?.let {R.string.concentration_tbr_format } ?: R.string.concentration_ago_format
        val bolusStep = activePlugin.activePump.pumpDescription.bolusStep
        if (isU100()) {
            val amountString = decimalFormatter.toPumpSupportedBolusWithUnits(amount.cU, bolusStep)
            val totalAmountString = decimalFormatter.toPumpSupportedBolusWithUnits(totalAmount.cU, bolusStep)
            val deliveredString = rh.gs(R.string.concentration_delivered_format, amountString, totalAmountString)
            return rh.gs(format, deliveredString, startTimeString, passedMinutes, durationInMin)
        }
        val amountIuString = decimalFormatter.toPumpSupportedBolusWithUnits(amount.iU(concentration), bolusStep)
        val totalAmountIuString = decimalFormatter.toPumpSupportedBolusWithUnits(totalAmount.iU(concentration), bolusStep)
        val deliveredIuString = rh.gs(R.string.concentration_delivered_format, amountIuString, totalAmountIuString)
        val amountCuString = decimalFormatter.toPumpSupportedBolusWithUnits(amount, bolusStep / concentration)
        val totalAmountCuString = decimalFormatter.toPumpSupportedBolusWithUnits(totalAmount, bolusStep / concentration)
        val deliveredCuString = rh.gs(R.string.concentration_delivered_format, amountCuString, totalAmountCuString)
        val deliveredString = rh.gs(R.string.concentration_format, deliveredIuString, deliveredCuString) + "\n"
        return rh.gs(format, deliveredString, startTimeString, passedMinutes, durationInMin)
    }

    override fun insulinConcentrationString(): String = rh.gs(R.string.insulin_concentration, (concentration * 100).toInt())

    override fun bolusWithVolume(amount: Double): String = rh.gs(
        R.string.bolus_with_volume,
        decimalFormatter.toPumpSupportedBolus(amount, activePlugin.activePump.pumpDescription.bolusStep),
        amount * 10
    )

    override fun bolusWithConvertedVolume(amount: Double): String = rh.gs(
        R.string.bolus_with_volume,
        decimalFormatter.toPumpSupportedBolus(amount, activePlugin.activePump.pumpDescription.bolusStep),
        amount / concentration * 10
    )

    override fun bolusProgressString(delivered: PumpInsulin, isPriming: Boolean): String = rh.gs(app.aaps.core.interfaces.R.string.bolus_delivering, fromPump(delivered, isPriming))

    override fun bolusProgressString(delivered: PumpInsulin, total: Double, isPriming: Boolean): String = rh.gs(app.aaps.core.interfaces.R.string.bolus_delivered_so_far, fromPump(delivered, isPriming), total)

    override val concentration: Double
        get() = insulin.iCfg.concentration

}