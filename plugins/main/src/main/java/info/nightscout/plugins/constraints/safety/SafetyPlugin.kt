package info.nightscout.plugins.constraints.safety

import dagger.android.HasAndroidInjector
import info.nightscout.core.extensions.putDouble
import info.nightscout.core.extensions.putInt
import info.nightscout.core.extensions.putString
import info.nightscout.core.extensions.storeDouble
import info.nightscout.core.extensions.storeInt
import info.nightscout.core.extensions.storeString
import info.nightscout.core.events.EventNewNotification
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.constraints.Safety
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.notifications.Notification
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.pump.defs.PumpDescription
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.interfaces.utils.HardLimits
import info.nightscout.interfaces.utils.Round
import info.nightscout.plugins.R
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafetyPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val sp: SP,
    private val rxBus: RxBus,
    private val constraintChecker: Constraints,
    private val activePlugin: ActivePlugin,
    private val hardLimits: HardLimits,
    private val config: Config,
    private val iobCobCalculator: IobCobCalculator,
    private val dateUtil: DateUtil
) : PluginBase(
    PluginDescription()
    .mainType(PluginType.CONSTRAINTS)
    .neverVisible(true)
    .alwaysEnabled(true)
    .showInList(false)
    .pluginName(R.string.safety)
    .preferencesId(R.xml.pref_safety),
    aapsLogger, rh, injector
), Constraints, Safety {

    /**
     * Constraints interface
     */
    override fun isLoopInvocationAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        if (!activePlugin.activePump.pumpDescription.isTempBasalCapable) value.set(aapsLogger, false, rh.gs(R.string.pumpisnottempbasalcapable), this)
        return value
    }

    override fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        val mode = sp.getString(R.string.key_aps_mode, "open")
        if (mode == "open") value.set(aapsLogger, false, rh.gs(R.string.closedmodedisabledinpreferences), this)
        if (!config.isEngineeringModeOrRelease()) {
            if (value.value()) {
                val n = Notification(Notification.TOAST_ALARM, rh.gs(R.string.closed_loop_disabled_on_dev_branch), Notification.NORMAL)
                rxBus.send(EventNewNotification(n))
            }
            value.set(aapsLogger, false, rh.gs(R.string.closed_loop_disabled_on_dev_branch), this)
        }
        val pump = activePlugin.activePump
        if (!pump.isFakingTempsByExtendedBoluses && iobCobCalculator.getExtendedBolus(dateUtil.now()) != null) {
            value.set(aapsLogger, false, rh.gs(R.string.closed_loop_disabled_with_eb), this)
        }
        return value
    }

    override fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val closedLoop = constraintChecker.isClosedLoopAllowed()
        if (!closedLoop.value()) value.set(aapsLogger, false, rh.gs(R.string.smbnotallowedinopenloopmode), this)
        return value
    }

    override fun isAdvancedFilteringEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val bgSource = activePlugin.activeBgSource
        if (!bgSource.advancedFilteringSupported()) value.set(aapsLogger, false, rh.gs(R.string.smbalwaysdisabled), this)
        return value
    }

    override fun applyBasalConstraints(absoluteRate: Constraint<Double>, profile: Profile): Constraint<Double> {
        absoluteRate.setIfGreater(aapsLogger, 0.0, rh.gs(R.string.limitingbasalratio, 0.0, rh.gs(R.string.itmustbepositivevalue)), this)
        absoluteRate.setIfSmaller(aapsLogger, hardLimits.maxBasal(),rh.gs(R.string.limitingbasalratio, hardLimits.maxBasal(), rh.gs(R.string.hardlimit)), this)
        val pump = activePlugin.activePump
        // check for pump max
        if (pump.pumpDescription.tempBasalStyle == PumpDescription.ABSOLUTE) {
            val pumpLimit = pump.pumpDescription.pumpType.tbrSettings?.maxDose ?: 0.0
            absoluteRate.setIfSmaller(aapsLogger, pumpLimit, rh.gs(R.string.limitingbasalratio, pumpLimit, rh.gs(R.string.pumplimit)), this)
        }

        // do rounding
        if (pump.pumpDescription.tempBasalStyle == PumpDescription.ABSOLUTE) {
            absoluteRate.set(aapsLogger, Round.roundTo(absoluteRate.value(), pump.pumpDescription.tempAbsoluteStep))
        }
        return absoluteRate
    }

    override fun applyBasalPercentConstraints(percentRate: Constraint<Int>, profile: Profile): Constraint<Int> {
        val currentBasal = profile.getBasal()
        val absoluteRate = currentBasal * (percentRate.originalValue().toDouble() / 100)
        percentRate.addReason("Percent rate " + percentRate.originalValue() + "% recalculated to " + DecimalFormatter.to2Decimal(absoluteRate) + " U/h with current basal " + DecimalFormatter.to2Decimal(currentBasal) + " U/h", this)
        val absoluteConstraint = Constraint(absoluteRate)
        applyBasalConstraints(absoluteConstraint, profile)
        percentRate.copyReasons(absoluteConstraint)
        val pump = activePlugin.activePump
        var percentRateAfterConst = java.lang.Double.valueOf(absoluteConstraint.value() / currentBasal * 100).toInt()
        percentRateAfterConst = if (percentRateAfterConst < 100) Round.ceilTo(percentRateAfterConst.toDouble(), pump.pumpDescription.tempPercentStep.toDouble()).toInt() else Round.floorTo(percentRateAfterConst.toDouble(), pump.pumpDescription.tempPercentStep.toDouble()).toInt()
        percentRate.set(aapsLogger, percentRateAfterConst, rh.gs(R.string.limitingpercentrate, percentRateAfterConst, rh.gs(R.string.pumplimit)), this)
        if (pump.pumpDescription.tempBasalStyle == PumpDescription.PERCENT) {
            val pumpLimit = pump.pumpDescription.pumpType.tbrSettings?.maxDose ?: 0.0
            percentRate.setIfSmaller(aapsLogger, pumpLimit.toInt(), rh.gs(R.string.limitingbasalratio, pumpLimit, rh.gs(R.string.pumplimit)), this)
        }
        return percentRate
    }

    override fun applyBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        insulin.setIfGreater(aapsLogger, 0.0, rh.gs(R.string.limitingbolus, 0.0, rh.gs(R.string.itmustbepositivevalue)), this)
        val maxBolus = sp.getDouble(R.string.key_treatmentssafety_maxbolus, 3.0)
        insulin.setIfSmaller(aapsLogger, maxBolus, rh.gs(R.string.limitingbolus, maxBolus, rh.gs(R.string.maxvalueinpreferences)), this)
        insulin.setIfSmaller(aapsLogger, hardLimits.maxBolus(), rh.gs(R.string.limitingbolus, hardLimits.maxBolus(), rh.gs(R.string.hardlimit)), this)
        val pump = activePlugin.activePump
        val rounded = pump.pumpDescription.pumpType.determineCorrectBolusSize(insulin.value())
        insulin.setIfDifferent(aapsLogger, rounded, rh.gs(R.string.pumplimit), this)
        return insulin
    }

    override fun applyExtendedBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        insulin.setIfGreater(aapsLogger, 0.0, rh.gs(R.string.limitingextendedbolus, 0.0, rh.gs(R.string.itmustbepositivevalue)), this)
        val maxBolus = sp.getDouble(R.string.key_treatmentssafety_maxbolus, 3.0)
        insulin.setIfSmaller(aapsLogger, maxBolus, rh.gs(R.string.limitingextendedbolus, maxBolus, rh.gs(R.string.maxvalueinpreferences)), this)
        insulin.setIfSmaller(aapsLogger, hardLimits.maxBolus(), rh.gs(R.string.limitingextendedbolus, hardLimits.maxBolus(), rh.gs(R.string.hardlimit)), this)
        val pump = activePlugin.activePump
        val rounded = pump.pumpDescription.pumpType.determineCorrectExtendedBolusSize(insulin.value())
        insulin.setIfDifferent(aapsLogger, rounded, rh.gs(R.string.pumplimit), this)
        return insulin
    }

    override fun applyCarbsConstraints(carbs: Constraint<Int>): Constraint<Int> {
        carbs.setIfGreater(aapsLogger, 0, rh.gs(R.string.limitingcarbs, 0, rh.gs(R.string.itmustbepositivevalue)), this)
        val maxCarbs = sp.getInt(R.string.key_treatmentssafety_maxcarbs, 48)
        carbs.setIfSmaller(aapsLogger, maxCarbs, rh.gs(R.string.limitingcarbs, maxCarbs, rh.gs(R.string.maxvalueinpreferences)), this)
        return carbs
    }

    override fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> {
        val apsMode = sp.getString(R.string.key_aps_mode, "open")
        if (apsMode == "lgs") maxIob.setIfSmaller(aapsLogger, HardLimits.MAX_IOB_LGS, rh.gs(R.string.limiting_iob, HardLimits.MAX_IOB_LGS, rh.gs(R.string.lowglucosesuspend)), this)
        return maxIob
    }

    override fun configuration(): JSONObject =
        JSONObject()
            .putString(R.string.key_age, sp, rh)
            .putDouble(R.string.key_treatmentssafety_maxbolus, sp, rh)
            .putInt(R.string.key_treatmentssafety_maxcarbs, sp, rh)

    override fun applyConfiguration(configuration: JSONObject) {
        configuration.storeString(R.string.key_age, sp, rh)
        configuration.storeDouble(R.string.key_treatmentssafety_maxbolus, sp, rh)
        configuration.storeInt(R.string.key_treatmentssafety_maxcarbs, sp, rh)
    }
}
