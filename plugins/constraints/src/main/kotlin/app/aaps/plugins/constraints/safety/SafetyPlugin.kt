package app.aaps.plugins.constraints.safety

import app.aaps.core.main.constraints.ConstraintObject
import app.aaps.core.main.utils.extensions.putDouble
import app.aaps.core.main.utils.extensions.putInt
import app.aaps.core.main.utils.extensions.putString
import app.aaps.core.main.utils.extensions.storeDouble
import app.aaps.core.main.utils.extensions.storeInt
import app.aaps.core.main.utils.extensions.storeString
import app.aaps.core.interfaces.aps.ApsMode
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.constraints.Safety
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.defs.PumpDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.Round
import dagger.android.HasAndroidInjector
import info.nightscout.plugins.constraints.R
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafetyPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val sp: SP,
    private val constraintChecker: ConstraintsChecker,
    private val activePlugin: ActivePlugin,
    private val hardLimits: HardLimits,
    private val config: Config,
    private val iobCobCalculator: IobCobCalculator,
    private val dateUtil: DateUtil,
    private val uiInteraction: UiInteraction,
    private val decimalFormatter: DecimalFormatter
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.CONSTRAINTS)
        .neverVisible(true)
        .alwaysEnabled(true)
        .showInList(false)
        .pluginName(R.string.safety)
        .preferencesId(R.xml.pref_safety),
    aapsLogger, rh, injector
), PluginConstraints, Safety {

    /**
     * Constraints interface
     */
    override fun isLoopInvocationAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        if (!activePlugin.activePump.pumpDescription.isTempBasalCapable) value.set(false, rh.gs(R.string.pumpisnottempbasalcapable), this)
        return value
    }

    override fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        val mode = ApsMode.fromString(sp.getString(info.nightscout.core.utils.R.string.key_aps_mode, ApsMode.OPEN.name))
        if (mode == ApsMode.OPEN) value.set(false, rh.gs(R.string.closedmodedisabledinpreferences), this)
        if (!config.isEngineeringModeOrRelease()) {
            if (value.value()) {
                uiInteraction.addNotification(Notification.TOAST_ALARM, rh.gs(R.string.closed_loop_disabled_on_dev_branch), Notification.NORMAL)
            }
            value.set(false, rh.gs(R.string.closed_loop_disabled_on_dev_branch), this)
        }
        val pump = activePlugin.activePump
        if (!pump.isFakingTempsByExtendedBoluses && iobCobCalculator.getExtendedBolus(dateUtil.now()) != null) {
            value.set(false, rh.gs(R.string.closed_loop_disabled_with_eb), this)
        }
        return value
    }

    override fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val closedLoop = constraintChecker.isClosedLoopAllowed()
        if (!closedLoop.value()) value.set(false, rh.gs(R.string.smbnotallowedinopenloopmode), this)
        return value
    }

    override fun isAdvancedFilteringEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val bgSource = activePlugin.activeBgSource
        if (!bgSource.advancedFilteringSupported()) value.set(false, rh.gs(R.string.smbalwaysdisabled), this)
        return value
    }

    override fun applyBasalConstraints(absoluteRate: Constraint<Double>, profile: Profile): Constraint<Double> {
        absoluteRate.setIfGreater(0.0, rh.gs(app.aaps.core.ui.R.string.limitingbasalratio, 0.0, rh.gs(app.aaps.core.ui.R.string.itmustbepositivevalue)), this)
        absoluteRate.setIfSmaller(hardLimits.maxBasal(), rh.gs(app.aaps.core.ui.R.string.limitingbasalratio, hardLimits.maxBasal(), rh.gs(R.string.hardlimit)), this)
        val pump = activePlugin.activePump
        // check for pump max
        if (pump.pumpDescription.tempBasalStyle == PumpDescription.ABSOLUTE) {
            val pumpLimit = pump.pumpDescription.pumpType.tbrSettings?.maxDose ?: 0.0
            absoluteRate.setIfSmaller(pumpLimit, rh.gs(app.aaps.core.ui.R.string.limitingbasalratio, pumpLimit, rh.gs(app.aaps.core.ui.R.string.pumplimit)), this)
        }

        // do rounding
        if (pump.pumpDescription.tempBasalStyle == PumpDescription.ABSOLUTE) {
            absoluteRate.set(Round.roundTo(absoluteRate.value(), pump.pumpDescription.tempAbsoluteStep))
        }
        return absoluteRate
    }

    override fun applyBasalPercentConstraints(percentRate: Constraint<Int>, profile: Profile): Constraint<Int> {
        val currentBasal = profile.getBasal()
        val absoluteRate = currentBasal * (percentRate.originalValue().toDouble() / 100)
        percentRate.addReason(
            "Percent rate " + percentRate.originalValue() + "% recalculated to " + decimalFormatter.to2Decimal(absoluteRate) + " U/h with current basal " + decimalFormatter.to2Decimal(
                currentBasal
            ) + " U/h", this
        )
        val absoluteConstraint = ConstraintObject(absoluteRate, aapsLogger)
        applyBasalConstraints(absoluteConstraint, profile)
        percentRate.copyReasons(absoluteConstraint)
        val pump = activePlugin.activePump
        var percentRateAfterConst = java.lang.Double.valueOf(absoluteConstraint.value() / currentBasal * 100).toInt()
        percentRateAfterConst =
            if (percentRateAfterConst < 100) Round.ceilTo(percentRateAfterConst.toDouble(), pump.pumpDescription.tempPercentStep.toDouble())
                .toInt() else Round.floorTo(percentRateAfterConst.toDouble(), pump.pumpDescription.tempPercentStep.toDouble()).toInt()
        percentRate.set(percentRateAfterConst, rh.gs(app.aaps.core.ui.R.string.limitingpercentrate, percentRateAfterConst, rh.gs(app.aaps.core.ui.R.string.pumplimit)), this)
        if (pump.pumpDescription.tempBasalStyle == PumpDescription.PERCENT) {
            val pumpLimit = pump.pumpDescription.pumpType.tbrSettings?.maxDose ?: 0.0
            percentRate.setIfSmaller(pumpLimit.toInt(), rh.gs(app.aaps.core.ui.R.string.limitingbasalratio, pumpLimit, rh.gs(app.aaps.core.ui.R.string.pumplimit)), this)
        }
        return percentRate
    }

    override fun applyBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        insulin.setIfGreater(0.0, rh.gs(app.aaps.core.ui.R.string.limitingbolus, 0.0, rh.gs(app.aaps.core.ui.R.string.itmustbepositivevalue)), this)
        val maxBolus = sp.getDouble(info.nightscout.core.utils.R.string.key_treatmentssafety_maxbolus, 3.0)
        insulin.setIfSmaller(maxBolus, rh.gs(app.aaps.core.ui.R.string.limitingbolus, maxBolus, rh.gs(R.string.maxvalueinpreferences)), this)
        insulin.setIfSmaller(hardLimits.maxBolus(), rh.gs(app.aaps.core.ui.R.string.limitingbolus, hardLimits.maxBolus(), rh.gs(R.string.hardlimit)), this)
        val pump = activePlugin.activePump
        val rounded = pump.pumpDescription.pumpType.determineCorrectBolusSize(insulin.value())
        insulin.setIfDifferent(rounded, rh.gs(app.aaps.core.ui.R.string.pumplimit), this)
        return insulin
    }

    override fun applyExtendedBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        insulin.setIfGreater(0.0, rh.gs(R.string.limitingextendedbolus, 0.0, rh.gs(app.aaps.core.ui.R.string.itmustbepositivevalue)), this)
        val maxBolus = sp.getDouble(info.nightscout.core.utils.R.string.key_treatmentssafety_maxbolus, 3.0)
        insulin.setIfSmaller(maxBolus, rh.gs(R.string.limitingextendedbolus, maxBolus, rh.gs(R.string.maxvalueinpreferences)), this)
        insulin.setIfSmaller(hardLimits.maxBolus(), rh.gs(R.string.limitingextendedbolus, hardLimits.maxBolus(), rh.gs(R.string.hardlimit)), this)
        val pump = activePlugin.activePump
        val rounded = pump.pumpDescription.pumpType.determineCorrectExtendedBolusSize(insulin.value())
        insulin.setIfDifferent(rounded, rh.gs(app.aaps.core.ui.R.string.pumplimit), this)
        return insulin
    }

    override fun applyCarbsConstraints(carbs: Constraint<Int>): Constraint<Int> {
        carbs.setIfGreater(0, rh.gs(R.string.limitingcarbs, 0, rh.gs(app.aaps.core.ui.R.string.itmustbepositivevalue)), this)
        val maxCarbs = sp.getInt(info.nightscout.core.utils.R.string.key_treatmentssafety_maxcarbs, 48)
        carbs.setIfSmaller(maxCarbs, rh.gs(R.string.limitingcarbs, maxCarbs, rh.gs(R.string.maxvalueinpreferences)), this)
        return carbs
    }

    override fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> {
        val apsMode = ApsMode.fromString(sp.getString(info.nightscout.core.utils.R.string.key_aps_mode, ApsMode.OPEN.name))
        if (apsMode == ApsMode.LGS) maxIob.setIfSmaller(
            HardLimits.MAX_IOB_LGS,
            rh.gs(app.aaps.core.ui.R.string.limiting_iob, HardLimits.MAX_IOB_LGS, rh.gs(app.aaps.core.ui.R.string.lowglucosesuspend)),
            this
        )
        return maxIob
    }

    override fun configuration(): JSONObject =
        JSONObject()
            .putString(info.nightscout.core.utils.R.string.key_age, sp, rh)
            .putDouble(info.nightscout.core.utils.R.string.key_treatmentssafety_maxbolus, sp, rh)
            .putInt(info.nightscout.core.utils.R.string.key_treatmentssafety_maxcarbs, sp, rh)

    override fun applyConfiguration(configuration: JSONObject) {
        configuration.storeString(info.nightscout.core.utils.R.string.key_age, sp, rh)
        configuration.storeDouble(info.nightscout.core.utils.R.string.key_treatmentssafety_maxbolus, sp, rh)
        configuration.storeInt(info.nightscout.core.utils.R.string.key_treatmentssafety_maxcarbs, sp, rh)
    }
}
