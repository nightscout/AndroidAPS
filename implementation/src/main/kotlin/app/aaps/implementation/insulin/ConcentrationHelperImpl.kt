package app.aaps.implementation.insulin

import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.implementation.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConcentrationHelperImpl @Inject constructor(
    val aapsLogger: AAPSLogger,
    private val activePlugin: ActivePlugin,
    private val insulin: Insulin,
    private val rh: ResourceHelper,
    private val decimalFormatter: DecimalFormatter
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

    override fun insulinAmountString(amount: PumpInsulin): String {
        if (isU100())
            return decimalFormatter.toPumpSupportedBolusWithUnits(amount.cU, activePlugin.activePump.pumpDescription.bolusStep)
        else { // app.aaps.core.ui.R.string.format_insulin_units
            val iUString = decimalFormatter.toPumpSupportedBolusWithUnits(amount.iU(concentration), activePlugin.activePump.pumpDescription.bolusStep)
            val cUString = decimalFormatter.toPumpSupportedBolusWithUnits(amount, activePlugin.activePump.pumpDescription.bolusStep / concentration)
            return rh.gs(R.string.concentration_format, iUString, cUString)
        }
    }

    override fun insulinAmountAgoString(amount: PumpInsulin, ago: String): String = "${insulinAmountString(amount)} $ago"

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