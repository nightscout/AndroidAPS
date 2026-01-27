package app.aaps.implementation.insulin

import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.implementation.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConcentrationHelperImpl @Inject constructor(
    val aapsLogger: AAPSLogger,
    private val activePlugin: ActivePlugin,
    private val rh: ResourceHelper,
    private val preferences: Preferences,
    private val decimalFormatter: DecimalFormatter
) : ConcentrationHelper {

    override fun isU100(): Boolean = concentration == 1.0

    override fun fromPump(amount: PumpInsulin): Double = amount.iU(concentration)

    override fun fromPump(rate: PumpRate): Double = rate.iU(concentration, true)

    override fun basalRateString(rate: PumpRate, isAbsolute: Boolean): String {
        if (isAbsolute.not())
            return rh.gs(app.aaps.core.ui.R.string.formatPercent, rate.iU(concentration, isAbsolute))
        if (isU100())
            return rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, rate)
        else {
            val iUString = rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, rate.iU(concentration, isAbsolute))
            val cUString = rh.gs(R.string.pump_base_basal_rate_cu, rate.cU)
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

    override fun bolusProgressString(delivered: PumpInsulin): String = rh.gs(app.aaps.core.interfaces.R.string.bolus_delivering, fromPump(delivered))

    override fun bolusProgressString(delivered: PumpInsulin, total: Double): String = rh.gs(app.aaps.core.interfaces.R.string.bolus_delivered_so_far, fromPump(delivered), total)

    /**
     * Provide current running iCfg concentration
     */
    override val concentration: Double
        get() = activePlugin.activeInsulin.iCfg.concentration

}