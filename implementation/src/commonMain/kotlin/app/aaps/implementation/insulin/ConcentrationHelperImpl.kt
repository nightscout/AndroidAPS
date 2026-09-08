package app.aaps.implementation.insulin

import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.format.NumberRounding
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.defs.determineCorrectBolusStepSize
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.ui.CoreUiStrings
import app.aaps.implementation.ImplementationStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Clock

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class ConcentrationHelperImpl @Inject constructor(
    val aapsLogger: AAPSLogger,
    private val activePlugin: ActivePlugin,
    private val profileFunction: ProfileFunction,
    private val rh: TextResolver,
    private val decimalFormatter: DecimalFormatter,
    private val dateUtil: DateUtil
) : ConcentrationHelper {

    override fun isU100(): Boolean = concentration == 1.0

    override fun fromPump(amount: PumpInsulin, isPriming: Boolean): Double = if (isPriming) amount.cU else amount.iU(concentration)

    override fun fromPump(rate: PumpRate): Double = rate.iU(concentration, true)

    override fun toPump(amount: Double): PumpInsulin = PumpInsulin(amount / concentration)

    override fun toPumpRate(rate: Double): PumpRate = PumpRate(rate / concentration)

    // Amount-aware (Insight specialBolusSize) + concentration-adapted deliverable IU step.
    override fun bolusStep(amount: Double): Double =
        activePlugin.activePump.pumpDescription.pumpType.determineCorrectBolusStepSize(amount / concentration) * concentration

    override fun basalRateString(rate: PumpRate, isAbsolute: Boolean, decimals: Int): String {
        if (isAbsolute.not())
            return rh.gs(CoreUiStrings.formatPercent, rate.iU(concentration, isAbsolute))
        // Was "%.${decimals}f". String.format is JVM only; NumberFormat is the multiplatform
        // equivalent, and HALF_UP is stated explicitly because that is what String.format did - the
        // NumberFormat default is HALF_EVEN, which would round a tie the other way.
        val fmt = NumberFormat(minFractionDigits = decimals, rounding = NumberRounding.HALF_UP)
        if (isU100())
            return rh.gs(CoreUiStrings.pump_base_basal_rate_dynamic, fmt.format(rate.cU))
        else {
            val iUString = rh.gs(CoreUiStrings.pump_base_basal_rate_dynamic, fmt.format(rate.iU(concentration, isAbsolute)))
            val cUString = rh.gs(ImplementationStrings.pump_base_basal_rate_cu_dynamic, fmt.format(rate.cU))
            return rh.gs(ImplementationStrings.concentration_format, iUString, cUString)
        }
    }

override fun basalTbrString(rate: PumpRate, startTime: Long, durationInMin: Int, isAbsolute: Boolean, isExtended: Boolean, decimals: Int): String {
    val startTimeString = dateUtil.timeString(startTime)
    val passedMinutes = min(T.msecs(max(0, dateUtil.now() - startTime)).mins().toInt(), durationInMin)
    return rh.gs(
        if (isExtended) ImplementationStrings.concentration_etbr_format else ImplementationStrings.concentration_tbr_format,
        basalRateString(rate, isAbsolute), startTimeString, passedMinutes, durationInMin
    )
}

    override fun insulinAmountString(amount: PumpInsulin): String {
        val bolusStep = activePlugin.activePump.pumpDescription.bolusStep
        if (isU100())
            return decimalFormatter.toPumpSupportedBolusWithUnits(amount.cU, bolusStep)
        else { // InterfacesStrings.format_insulin_units
            val iUString = decimalFormatter.toPumpSupportedBolusWithUnits(amount.iU(concentration), bolusStep)
            val cUString = decimalFormatter.toPumpSupportedBolusWithUnits(amount, bolusStep / concentration)
            return rh.gs(ImplementationStrings.concentration_format, iUString, cUString)
        }
    }

    override fun insulinAmountAgoString(amount: PumpInsulin, lastBolusTime: Long): String? {
        val agoHours = (Clock.System.now().toEpochMilliseconds() - lastBolusTime).toDouble() / 3_600_000.0
        return if (agoHours < 6.0) {
            "${insulinAmountString(amount)} ${dateUtil.sinceString(lastBolusTime, rh)}"
        } else null
    }

    override fun insulinDeliveryAgoString(amount: PumpInsulin, totalAmount: PumpInsulin, startTime: Long, durationInMin: Int?): String {
        val startTimeString = dateUtil.timeString(startTime)
        val passedMinutes = durationInMin?.let {
            min(T.msecs(max(0, dateUtil.now() - startTime)).mins().toInt(), it)
        } ?: T.msecs(dateUtil.now() - startTime).mins().toInt()
        val format = durationInMin?.let {ImplementationStrings.concentration_tbr_format } ?: ImplementationStrings.concentration_ago_format
        val bolusStep = activePlugin.activePump.pumpDescription.bolusStep
        if (isU100()) {
            val amountString = decimalFormatter.toPumpSupportedBolusWithUnits(amount.cU, bolusStep)
            val totalAmountString = decimalFormatter.toPumpSupportedBolusWithUnits(totalAmount.cU, bolusStep)
            val deliveredString = rh.gs(ImplementationStrings.concentration_delivered_format, amountString, totalAmountString)
            return rh.gs(format, deliveredString, startTimeString, passedMinutes, durationInMin)
        }
        val amountIuString = decimalFormatter.toPumpSupportedBolusWithUnits(amount.iU(concentration), bolusStep)
        val totalAmountIuString = decimalFormatter.toPumpSupportedBolusWithUnits(totalAmount.iU(concentration), bolusStep)
        val deliveredIuString = rh.gs(ImplementationStrings.concentration_delivered_format, amountIuString, totalAmountIuString)
        val amountCuString = decimalFormatter.toPumpSupportedBolusWithUnits(amount, bolusStep / concentration)
        val totalAmountCuString = decimalFormatter.toPumpSupportedBolusWithUnits(totalAmount, bolusStep / concentration)
        val deliveredCuString = rh.gs(ImplementationStrings.concentration_delivered_format, amountCuString, totalAmountCuString)
        val deliveredString = rh.gs(ImplementationStrings.concentration_format, deliveredIuString, deliveredCuString) + "\n"
        return rh.gs(format, deliveredString, startTimeString, passedMinutes, durationInMin)
    }

    override fun insulinConcentrationString(): String = rh.gs(ImplementationStrings.insulin_concentration, (concentration * 100).toInt())

    override fun bolusWithVolume(amount: Double): String = rh.gs(
        ImplementationStrings.bolus_with_volume,
        decimalFormatter.toPumpSupportedBolus(amount, activePlugin.activePump.pumpDescription.bolusStep),
        amount * 10
    )

    override fun bolusWithConvertedVolume(amount: Double): String = rh.gs(
        ImplementationStrings.bolus_with_volume,
        decimalFormatter.toPumpSupportedBolus(amount, activePlugin.activePump.pumpDescription.bolusStep),
        amount / concentration * 10
    )

    override fun bolusProgressString(delivered: PumpInsulin, isPriming: Boolean): String = rh.gs(InterfacesStrings.bolus_delivering, fromPump(delivered, isPriming))

    override fun bolusProgressString(delivered: PumpInsulin, total: Double, isPriming: Boolean): String = rh.gs(InterfacesStrings.bolus_delivered_so_far, fromPump(delivered, isPriming), total)

    /**
     * Concentration of the insulin the running profile is using. Every conversion in this class multiplies or
     * divides by it, and none of them can suspend, so it reads the synchronous mirror.
     *
     * Falls back to 1.0 (U100) when nothing is in force. Unlike the sites that record an insulin onto a
     * treatment, guessing here is safe: 1.0 is the identity for every conversion below, so the pump's own
     * units pass through untouched rather than being scaled by an insulin the user never chose. It is only
     * reachable before the first profile switch exists, when there is nothing to dose with anyway.
     */
    override val concentration: Double
        get() = profileFunction.runningICfg.value?.concentration ?: 1.0

}