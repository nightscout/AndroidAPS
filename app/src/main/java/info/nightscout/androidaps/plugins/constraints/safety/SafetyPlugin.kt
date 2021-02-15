package info.nightscout.androidaps.plugins.constraints.safety

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.aps.openAPSAMA.OpenAPSAMAPlugin
import info.nightscout.androidaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref1Plugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.HardLimits
import info.nightscout.androidaps.utils.Round
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor

@Singleton
class SafetyPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    resourceHelper: ResourceHelper,
    private val sp: SP,
    private val rxBus: RxBusWrapper,
    private val constraintChecker: ConstraintChecker,
    private val openAPSAMAPlugin: OpenAPSAMAPlugin,
    private val openAPSSMBPlugin: OpenAPSSMBPlugin,
    private val sensitivityOref1Plugin: SensitivityOref1Plugin,
    private val activePlugin: ActivePluginProvider,
    private val hardLimits: HardLimits,
    private val buildHelper: BuildHelper,
    private val treatmentsPlugin: TreatmentsPlugin,
    private val config: Config
) : PluginBase(PluginDescription()
    .mainType(PluginType.CONSTRAINTS)
    .neverVisible(true)
    .alwaysEnabled(true)
    .showInList(false)
    .pluginName(R.string.safety)
    .preferencesId(R.xml.pref_safety),
    aapsLogger, resourceHelper, injector
), ConstraintsInterface {

    /**
     * Constraints interface
     */
    override fun isLoopInvocationAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        if (!activePlugin.activePump.pumpDescription.isTempBasalCapable) value[aapsLogger, false, resourceHelper.gs(R.string.pumpisnottempbasalcapable)] = this
        return value
    }

    override fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        val mode = sp.getString(R.string.key_aps_mode, "open")
        if (mode == "open") value[aapsLogger, false, resourceHelper.gs(R.string.closedmodedisabledinpreferences)] = this
        if (!buildHelper.isEngineeringModeOrRelease()) {
            if (value.value()) {
                val n = Notification(Notification.TOAST_ALARM, resourceHelper.gs(R.string.closed_loop_disabled_on_dev_branch), Notification.NORMAL)
                rxBus.send(EventNewNotification(n))
            }
            value[aapsLogger, false, resourceHelper.gs(R.string.closed_loop_disabled_on_dev_branch)] = this
        }
        val pump = activePlugin.activePump
        if (!pump.isFakingTempsByExtendedBoluses && treatmentsPlugin.isInHistoryExtendedBoluslInProgress) {
            value[aapsLogger, false, resourceHelper.gs(R.string.closed_loop_disabled_with_eb)] = this
        }
        return value
    }

    override fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val enabled = sp.getBoolean(R.string.key_openapsama_useautosens, false)
        if (!enabled) value[aapsLogger, false, resourceHelper.gs(R.string.autosensdisabledinpreferences)] = this
        return value
    }

    override fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val enabled = sp.getBoolean(R.string.key_use_smb, false)
        if (!enabled) value[aapsLogger, false, resourceHelper.gs(R.string.smbdisabledinpreferences)] = this
        val closedLoop = constraintChecker.isClosedLoopAllowed()
        if (!closedLoop.value()) value[aapsLogger, false, resourceHelper.gs(R.string.smbnotallowedinopenloopmode)] = this
        return value
    }

    override fun isUAMEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val enabled = sp.getBoolean(R.string.key_use_uam, false)
        if (!enabled) value[aapsLogger, false, resourceHelper.gs(R.string.uamdisabledinpreferences)] = this
        val oref1Enabled = sensitivityOref1Plugin.isEnabled(PluginType.SENSITIVITY)
        if (!oref1Enabled) value[aapsLogger, false, resourceHelper.gs(R.string.uamdisabledoref1notselected)] = this
        return value
    }

    override fun isAdvancedFilteringEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val bgSource = activePlugin.activeBgSource
        if (!bgSource.advancedFilteringSupported()) value[aapsLogger, false, resourceHelper.gs(R.string.smbalwaysdisabled)] = this
        return value
    }

    override fun applyBasalConstraints(absoluteRate: Constraint<Double>, profile: Profile): Constraint<Double> {
        absoluteRate.setIfGreater(aapsLogger, 0.0, String.format(resourceHelper.gs(R.string.limitingbasalratio), 0.0, resourceHelper.gs(R.string.itmustbepositivevalue)), this)
        if (config.APS) {
            var maxBasal = sp.getDouble(R.string.key_openapsma_max_basal, 1.0)
            if (maxBasal < profile.maxDailyBasal) {
                maxBasal = profile.maxDailyBasal
                absoluteRate.addReason(resourceHelper.gs(R.string.increasingmaxbasal), this)
            }
            absoluteRate.setIfSmaller(aapsLogger, maxBasal, String.format(resourceHelper.gs(R.string.limitingbasalratio), maxBasal, resourceHelper.gs(R.string.maxvalueinpreferences)), this)

            // Check percentRate but absolute rate too, because we know real current basal in pump
            val maxBasalMultiplier = sp.getDouble(R.string.key_openapsama_current_basal_safety_multiplier, 4.0)
            val maxFromBasalMultiplier = floor(maxBasalMultiplier * profile.basal * 100) / 100
            absoluteRate.setIfSmaller(aapsLogger, maxFromBasalMultiplier, String.format(resourceHelper.gs(R.string.limitingbasalratio), maxFromBasalMultiplier, resourceHelper.gs(R.string.maxbasalmultiplier)), this)
            val maxBasalFromDaily = sp.getDouble(R.string.key_openapsama_max_daily_safety_multiplier, 3.0)
            val maxFromDaily = floor(profile.maxDailyBasal * maxBasalFromDaily * 100) / 100
            absoluteRate.setIfSmaller(aapsLogger, maxFromDaily, String.format(resourceHelper.gs(R.string.limitingbasalratio), maxFromDaily, resourceHelper.gs(R.string.maxdailybasalmultiplier)), this)
        }
        absoluteRate.setIfSmaller(aapsLogger, hardLimits.maxBasal(), String.format(resourceHelper.gs(R.string.limitingbasalratio), hardLimits.maxBasal(), resourceHelper.gs(R.string.hardlimit)), this)
        val pump = activePlugin.activePump
        // check for pump max
        if (pump.pumpDescription.tempBasalStyle == PumpDescription.ABSOLUTE) {
            val pumpLimit = pump.pumpDescription.pumpType.tbrSettings.maxDose
            absoluteRate.setIfSmaller(aapsLogger, pumpLimit, String.format(resourceHelper.gs(R.string.limitingbasalratio), pumpLimit, resourceHelper.gs(R.string.pumplimit)), this)
        }

        // do rounding
        if (pump.pumpDescription.tempBasalStyle == PumpDescription.ABSOLUTE) {
            absoluteRate[aapsLogger] = Round.roundTo(absoluteRate.value(), pump.pumpDescription.tempAbsoluteStep)
        }
        return absoluteRate
    }

    override fun applyBasalPercentConstraints(percentRate: Constraint<Int>, profile: Profile): Constraint<Int> {
        val currentBasal = profile.basal
        val absoluteRate = currentBasal * (percentRate.originalValue().toDouble() / 100)
        percentRate.addReason("Percent rate " + percentRate.originalValue() + "% recalculated to " + DecimalFormatter.to2Decimal(absoluteRate) + " U/h with current basal " + DecimalFormatter.to2Decimal(currentBasal) + " U/h", this)
        val absoluteConstraint = Constraint(absoluteRate)
        applyBasalConstraints(absoluteConstraint, profile)
        percentRate.copyReasons(absoluteConstraint)
        val pump = activePlugin.activePump
        var percentRateAfterConst = java.lang.Double.valueOf(absoluteConstraint.value() / currentBasal * 100).toInt()
        percentRateAfterConst = if (percentRateAfterConst < 100) Round.ceilTo(percentRateAfterConst.toDouble(), pump.pumpDescription.tempPercentStep.toDouble()).toInt() else Round.floorTo(percentRateAfterConst.toDouble(), pump.pumpDescription.tempPercentStep.toDouble()).toInt()
        percentRate[aapsLogger, percentRateAfterConst, String.format(resourceHelper.gs(R.string.limitingpercentrate), percentRateAfterConst, resourceHelper.gs(R.string.pumplimit))] = this
        if (pump.pumpDescription.tempBasalStyle == PumpDescription.PERCENT) {
            val pumpLimit = pump.pumpDescription.pumpType.tbrSettings.maxDose
            percentRate.setIfSmaller(aapsLogger, pumpLimit.toInt(), String.format(resourceHelper.gs(R.string.limitingbasalratio), pumpLimit, resourceHelper.gs(R.string.pumplimit)), this)
        }
        return percentRate
    }

    override fun applyBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        insulin.setIfGreater(aapsLogger, 0.0, String.format(resourceHelper.gs(R.string.limitingbolus), 0.0, resourceHelper.gs(R.string.itmustbepositivevalue)), this)
        val maxBolus = sp.getDouble(R.string.key_treatmentssafety_maxbolus, 3.0)
        insulin.setIfSmaller(aapsLogger, maxBolus, String.format(resourceHelper.gs(R.string.limitingbolus), maxBolus, resourceHelper.gs(R.string.maxvalueinpreferences)), this)
        insulin.setIfSmaller(aapsLogger, hardLimits.maxBolus(), String.format(resourceHelper.gs(R.string.limitingbolus), hardLimits.maxBolus(), resourceHelper.gs(R.string.hardlimit)), this)
        val pump = activePlugin.activePump
        val rounded = pump.pumpDescription.pumpType.determineCorrectBolusSize(insulin.value())
        insulin.setIfDifferent(aapsLogger, rounded, resourceHelper.gs(R.string.pumplimit), this)
        return insulin
    }

    override fun applyExtendedBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        insulin.setIfGreater(aapsLogger, 0.0, String.format(resourceHelper.gs(R.string.limitingextendedbolus), 0.0, resourceHelper.gs(R.string.itmustbepositivevalue)), this)
        val maxBolus = sp.getDouble(R.string.key_treatmentssafety_maxbolus, 3.0)
        insulin.setIfSmaller(aapsLogger, maxBolus, String.format(resourceHelper.gs(R.string.limitingextendedbolus), maxBolus, resourceHelper.gs(R.string.maxvalueinpreferences)), this)
        insulin.setIfSmaller(aapsLogger, hardLimits.maxBolus(), String.format(resourceHelper.gs(R.string.limitingextendedbolus), hardLimits.maxBolus(), resourceHelper.gs(R.string.hardlimit)), this)
        val pump = activePlugin.activePump
        val rounded = pump.pumpDescription.pumpType.determineCorrectExtendedBolusSize(insulin.value())
        insulin.setIfDifferent(aapsLogger, rounded, resourceHelper.gs(R.string.pumplimit), this)
        return insulin
    }

    override fun applyCarbsConstraints(carbs: Constraint<Int>): Constraint<Int> {
        carbs.setIfGreater(aapsLogger, 0, String.format(resourceHelper.gs(R.string.limitingcarbs), 0, resourceHelper.gs(R.string.itmustbepositivevalue)), this)
        val maxCarbs = sp.getInt(R.string.key_treatmentssafety_maxcarbs, 48)
        carbs.setIfSmaller(aapsLogger, maxCarbs, String.format(resourceHelper.gs(R.string.limitingcarbs), maxCarbs, resourceHelper.gs(R.string.maxvalueinpreferences)), this)
        return carbs
    }

    override fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> {
        val apsMode = sp.getString(R.string.key_aps_mode, "open")
        val maxIobPref: Double = if (openAPSSMBPlugin.isEnabled(PluginType.APS)) sp.getDouble(R.string.key_openapssmb_max_iob, 3.0) else sp.getDouble(R.string.key_openapsma_max_iob, 1.5)
        maxIob.setIfSmaller(aapsLogger, maxIobPref, String.format(resourceHelper.gs(R.string.limitingiob), maxIobPref, resourceHelper.gs(R.string.maxvalueinpreferences)), this)
        if (openAPSAMAPlugin.isEnabled(PluginType.APS)) maxIob.setIfSmaller(aapsLogger, hardLimits.maxIobAMA(), String.format(resourceHelper.gs(R.string.limitingiob), hardLimits.maxIobAMA(), resourceHelper.gs(R.string.hardlimit)), this)
        if (openAPSSMBPlugin.isEnabled(PluginType.APS)) maxIob.setIfSmaller(aapsLogger, hardLimits.maxIobSMB(), String.format(resourceHelper.gs(R.string.limitingiob), hardLimits.maxIobSMB(), resourceHelper.gs(R.string.hardlimit)), this)
        if (apsMode == "lgs") maxIob.setIfSmaller(aapsLogger, hardLimits.MAXIOB_LGS, String.format(resourceHelper.gs(R.string.limitingiob), hardLimits.MAXIOB_LGS, resourceHelper.gs(R.string.lowglucosesuspend)), this)
        return maxIob
    }
}