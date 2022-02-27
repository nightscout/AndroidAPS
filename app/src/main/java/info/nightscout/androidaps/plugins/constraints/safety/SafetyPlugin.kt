package info.nightscout.androidaps.plugins.constraints.safety

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.R
import info.nightscout.androidaps.extensions.*
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.interfaces.*
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.androidaps.plugins.aps.openAPSAMA.OpenAPSAMAPlugin
import info.nightscout.androidaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import info.nightscout.androidaps.plugins.aps.openAPSSMBDynamicISF.OpenAPSSMBDynamicISFPlugin
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref1Plugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.HardLimits
import info.nightscout.androidaps.utils.Round
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor

@Singleton
class SafetyPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val sp: SP,
    private val rxBus: RxBus,
    private val constraintChecker: ConstraintChecker,
    private val openAPSAMAPlugin: OpenAPSAMAPlugin,
    private val openAPSSMBPlugin: OpenAPSSMBPlugin,
    private val OpenAPSSMBDynamicISFPlugin: OpenAPSSMBDynamicISFPlugin,
    private val sensitivityOref1Plugin: SensitivityOref1Plugin,
    private val activePlugin: ActivePlugin,
    private val hardLimits: HardLimits,
    private val buildHelper: BuildHelper,
    private val iobCobCalculator: IobCobCalculator,
    private val config: Config,
    private val dateUtil: DateUtil
) : PluginBase(PluginDescription()
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
        if (!buildHelper.isEngineeringModeOrRelease()) {
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

    override fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val enabled = sp.getBoolean(R.string.key_openapsama_useautosens, false)
        if (!enabled) value.set(aapsLogger, false, rh.gs(R.string.autosensdisabledinpreferences), this)
        return value
    }

    override fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val enabled = sp.getBoolean(R.string.key_use_smb, false)
        if (!enabled) value.set(aapsLogger, false, rh.gs(R.string.smbdisabledinpreferences), this)
        val closedLoop = constraintChecker.isClosedLoopAllowed()
        if (!closedLoop.value()) value.set(aapsLogger, false, rh.gs(R.string.smbnotallowedinopenloopmode), this)
        return value
    }

    override fun isUAMEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val enabled = sp.getBoolean(R.string.key_use_uam, false)
        if (!enabled) value.set(aapsLogger, false, rh.gs(R.string.uamdisabledinpreferences), this)
        val oref1Enabled = sensitivityOref1Plugin.isEnabled()
        if (!oref1Enabled) value.set(aapsLogger, false, rh.gs(R.string.uamdisabledoref1notselected), this)
        return value
    }

    override fun isAdvancedFilteringEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val bgSource = activePlugin.activeBgSource
        if (!bgSource.advancedFilteringSupported()) value.set(aapsLogger, false, rh.gs(R.string.smbalwaysdisabled), this)
        return value
    }

    override fun applyBasalConstraints(absoluteRate: Constraint<Double>, profile: Profile): Constraint<Double> {
        absoluteRate.setIfGreater(aapsLogger, 0.0, String.format(rh.gs(R.string.limitingbasalratio), 0.0, rh.gs(R.string.itmustbepositivevalue)), this)
        if (config.APS) {
            var maxBasal = sp.getDouble(R.string.key_openapsma_max_basal, 1.0)
            if (maxBasal < profile.getMaxDailyBasal()) {
                maxBasal = profile.getMaxDailyBasal()
                absoluteRate.addReason(rh.gs(R.string.increasingmaxbasal), this)
            }
            absoluteRate.setIfSmaller(aapsLogger, maxBasal, String.format(rh.gs(R.string.limitingbasalratio), maxBasal, rh.gs(R.string.maxvalueinpreferences)), this)

            // Check percentRate but absolute rate too, because we know real current basal in pump
            val maxBasalMultiplier = sp.getDouble(R.string.key_openapsama_current_basal_safety_multiplier, 4.0)
            val maxFromBasalMultiplier = floor(maxBasalMultiplier * profile.getBasal() * 100) / 100
            absoluteRate.setIfSmaller(aapsLogger, maxFromBasalMultiplier, String.format(rh.gs(R.string.limitingbasalratio), maxFromBasalMultiplier, rh.gs(R.string.maxbasalmultiplier)), this)
            val maxBasalFromDaily = sp.getDouble(R.string.key_openapsama_max_daily_safety_multiplier, 3.0)
            val maxFromDaily = floor(profile.getMaxDailyBasal() * maxBasalFromDaily * 100) / 100
            absoluteRate.setIfSmaller(aapsLogger, maxFromDaily, String.format(rh.gs(R.string.limitingbasalratio), maxFromDaily, rh.gs(R.string.maxdailybasalmultiplier)), this)
        }
        absoluteRate.setIfSmaller(aapsLogger, hardLimits.maxBasal(), String.format(rh.gs(R.string.limitingbasalratio), hardLimits.maxBasal(), rh.gs(R.string.hardlimit)), this)
        val pump = activePlugin.activePump
        // check for pump max
        if (pump.pumpDescription.tempBasalStyle == PumpDescription.ABSOLUTE) {
            val pumpLimit = pump.pumpDescription.pumpType.tbrSettings?.maxDose ?: 0.0
            absoluteRate.setIfSmaller(aapsLogger, pumpLimit, String.format(rh.gs(R.string.limitingbasalratio), pumpLimit, rh.gs(R.string.pumplimit)), this)
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
        percentRate.set(aapsLogger, percentRateAfterConst, String.format(rh.gs(R.string.limitingpercentrate), percentRateAfterConst, rh.gs(R.string.pumplimit)), this)
        if (pump.pumpDescription.tempBasalStyle == PumpDescription.PERCENT) {
            val pumpLimit = pump.pumpDescription.pumpType.tbrSettings?.maxDose ?: 0.0
            percentRate.setIfSmaller(aapsLogger, pumpLimit.toInt(), String.format(rh.gs(R.string.limitingbasalratio), pumpLimit, rh.gs(R.string.pumplimit)), this)
        }
        return percentRate
    }

    override fun applyBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        insulin.setIfGreater(aapsLogger, 0.0, String.format(rh.gs(R.string.limitingbolus), 0.0, rh.gs(R.string.itmustbepositivevalue)), this)
        val maxBolus = sp.getDouble(R.string.key_treatmentssafety_maxbolus, 3.0)
        insulin.setIfSmaller(aapsLogger, maxBolus, String.format(rh.gs(R.string.limitingbolus), maxBolus, rh.gs(R.string.maxvalueinpreferences)), this)
        insulin.setIfSmaller(aapsLogger, hardLimits.maxBolus(), String.format(rh.gs(R.string.limitingbolus), hardLimits.maxBolus(), rh.gs(R.string.hardlimit)), this)
        val pump = activePlugin.activePump
        val rounded = pump.pumpDescription.pumpType.determineCorrectBolusSize(insulin.value())
        insulin.setIfDifferent(aapsLogger, rounded, rh.gs(R.string.pumplimit), this)
        return insulin
    }

    override fun applyExtendedBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        insulin.setIfGreater(aapsLogger, 0.0, String.format(rh.gs(R.string.limitingextendedbolus), 0.0, rh.gs(R.string.itmustbepositivevalue)), this)
        val maxBolus = sp.getDouble(R.string.key_treatmentssafety_maxbolus, 3.0)
        insulin.setIfSmaller(aapsLogger, maxBolus, String.format(rh.gs(R.string.limitingextendedbolus), maxBolus, rh.gs(R.string.maxvalueinpreferences)), this)
        insulin.setIfSmaller(aapsLogger, hardLimits.maxBolus(), String.format(rh.gs(R.string.limitingextendedbolus), hardLimits.maxBolus(), rh.gs(R.string.hardlimit)), this)
        val pump = activePlugin.activePump
        val rounded = pump.pumpDescription.pumpType.determineCorrectExtendedBolusSize(insulin.value())
        insulin.setIfDifferent(aapsLogger, rounded, rh.gs(R.string.pumplimit), this)
        return insulin
    }

    override fun applyCarbsConstraints(carbs: Constraint<Int>): Constraint<Int> {
        carbs.setIfGreater(aapsLogger, 0, String.format(rh.gs(R.string.limitingcarbs), 0, rh.gs(R.string.itmustbepositivevalue)), this)
        val maxCarbs = sp.getInt(R.string.key_treatmentssafety_maxcarbs, 48)
        carbs.setIfSmaller(aapsLogger, maxCarbs, String.format(rh.gs(R.string.limitingcarbs), maxCarbs, rh.gs(R.string.maxvalueinpreferences)), this)
        return carbs
    }

    override fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> {
        val apsMode = sp.getString(R.string.key_aps_mode, "open")
        val maxIobPref: Double = if (openAPSSMBPlugin.isEnabled() || OpenAPSSMBDynamicISFPlugin.isEnabled()) sp.getDouble(R.string.key_openapssmb_max_iob, 3.0) else sp.getDouble(R.string
        .key_openapsma_max_iob, 1.5)
        maxIob.setIfSmaller(aapsLogger, maxIobPref, String.format(rh.gs(R.string.limitingiob), maxIobPref, rh.gs(R.string.maxvalueinpreferences)), this)
        if (openAPSAMAPlugin.isEnabled()) maxIob.setIfSmaller(aapsLogger, hardLimits.maxIobAMA(), String.format(rh.gs(R.string.limitingiob), hardLimits.maxIobAMA(), rh.gs(R.string.hardlimit)), this)
        if (openAPSSMBPlugin.isEnabled()) maxIob.setIfSmaller(aapsLogger, hardLimits.maxIobSMB(), String.format(rh.gs(R.string.limitingiob), hardLimits.maxIobSMB(), rh.gs(R.string.hardlimit)), this)
        if (OpenAPSSMBDynamicISFPlugin.isEnabled()) maxIob.setIfSmaller(aapsLogger, hardLimits.maxIobSMB(), String.format(rh.gs(R.string.limitingiob), hardLimits.maxIobSMB(), rh.gs(R.string.hardlimit)), this)
        if (apsMode == "lgs") maxIob.setIfSmaller(aapsLogger, HardLimits.MAX_IOB_LGS, String.format(rh.gs(R.string.limitingiob), HardLimits.MAX_IOB_LGS, rh.gs(R.string.lowglucosesuspend)), this)
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
