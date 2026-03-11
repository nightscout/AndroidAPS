package app.aaps.plugins.constraints.safety

import android.content.Context
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.constraints.Safety
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.defs.determineCorrectBolusSize
import app.aaps.core.interfaces.pump.defs.determineCorrectExtendedBolusSize
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.extensions.put
import app.aaps.core.objects.extensions.store
import app.aaps.core.validators.preferences.AdaptiveDoublePreference
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveListPreference
import app.aaps.plugins.constraints.R
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafetyPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val preferences: Preferences,
    private val constraintChecker: ConstraintsChecker,
    private val activePlugin: ActivePlugin,
    private val hardLimits: HardLimits,
    private val config: Config,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil,
    private val uiInteraction: UiInteraction,
    private val decimalFormatter: DecimalFormatter
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.CONSTRAINTS)
        .neverVisible(true)
        .alwaysEnabled(true)
        .showInList { false }
        .pluginName(R.string.safety)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN),
    aapsLogger, rh
), PluginConstraints, Safety {

    /**
     * Constraints interface
     */
    override fun isLoopInvocationAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        if (!activePlugin.activePump.pumpDescription.isTempBasalCapable) value.set(false, rh.gs(R.string.pumpisnottempbasalcapable), this)
        return value
    }

    override fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        if (!config.isEngineeringModeOrRelease()) {
            if (value.value()) {
                uiInteraction.addNotification(Notification.TOAST_ALARM, rh.gs(R.string.closed_loop_disabled_on_dev_branch), Notification.NORMAL)
            }
            value.set(false, rh.gs(R.string.closed_loop_disabled_on_dev_branch), this)
        }
        val pump = activePlugin.activePump
        if (!pump.isFakingTempsByExtendedBoluses && persistenceLayer.getExtendedBolusActiveAt(dateUtil.now()) != null) {
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
            val pumpLimit = pump.pumpDescription.pumpType.tbrSettings()?.maxDose ?: 0.0
            absoluteRate.setIfSmaller(pumpLimit, rh.gs(app.aaps.core.ui.R.string.limitingbasalratio, pumpLimit, rh.gs(app.aaps.core.ui.R.string.pumplimit)), this)

            // Not all pumps have dynamic TBR constraint
            val dynamicPumpLimit = pump.pumpDescription.maxTempAbsolute
            if (dynamicPumpLimit > 0.0) absoluteRate.setIfSmaller(dynamicPumpLimit, rh.gs(app.aaps.core.ui.R.string.limitingbasalratio, dynamicPumpLimit, rh.gs(app.aaps.core.ui.R.string.pumplimit)), this)
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
            val pumpLimit = pump.pumpDescription.pumpType.tbrSettings()?.maxDose ?: 0.0
            percentRate.setIfSmaller(pumpLimit.toInt(), rh.gs(app.aaps.core.ui.R.string.limitingbasalratio, pumpLimit, rh.gs(app.aaps.core.ui.R.string.pumplimit)), this)
        }
        return percentRate
    }

    override fun applyBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        insulin.setIfGreater(0.0, rh.gs(app.aaps.core.ui.R.string.limitingbolus, 0.0, rh.gs(app.aaps.core.ui.R.string.itmustbepositivevalue)), this)
        val maxBolus = preferences.get(DoubleKey.SafetyMaxBolus)
        insulin.setIfSmaller(maxBolus, rh.gs(app.aaps.core.ui.R.string.limitingbolus, maxBolus, rh.gs(R.string.maxvalueinpreferences)), this)
        insulin.setIfSmaller(hardLimits.maxBolus(), rh.gs(app.aaps.core.ui.R.string.limitingbolus, hardLimits.maxBolus(), rh.gs(R.string.hardlimit)), this)
        val pump = activePlugin.activePump
        val dynamicPumpLimit = pump.pumpDescription.maxBolusSize
        if (dynamicPumpLimit > 0.0) {
            // Not all pumps have dynamic bolus size constraint
            insulin.setIfSmaller(dynamicPumpLimit, rh.gs(app.aaps.core.ui.R.string.limitingbolus, dynamicPumpLimit, rh.gs(app.aaps.core.ui.R.string.pumplimit)), this)
        }

        val rounded = pump.pumpDescription.pumpType.determineCorrectBolusSize(insulin.value())
        insulin.setIfDifferent(rounded, rh.gs(app.aaps.core.ui.R.string.pumplimit), this)
        return insulin
    }

    override fun applyExtendedBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        insulin.setIfGreater(0.0, rh.gs(R.string.limitingextendedbolus, 0.0, rh.gs(app.aaps.core.ui.R.string.itmustbepositivevalue)), this)
        val maxBolus = preferences.get(DoubleKey.SafetyMaxBolus)
        insulin.setIfSmaller(maxBolus, rh.gs(R.string.limitingextendedbolus, maxBolus, rh.gs(R.string.maxvalueinpreferences)), this)
        insulin.setIfSmaller(hardLimits.maxBolus(), rh.gs(R.string.limitingextendedbolus, hardLimits.maxBolus(), rh.gs(R.string.hardlimit)), this)
        val pump = activePlugin.activePump
        val rounded = pump.pumpDescription.pumpType.determineCorrectExtendedBolusSize(insulin.value())
        insulin.setIfDifferent(rounded, rh.gs(app.aaps.core.ui.R.string.pumplimit), this)
        return insulin
    }

    override fun applyCarbsConstraints(carbs: Constraint<Int>): Constraint<Int> {
        val maxCarbs = preferences.get(IntKey.SafetyMaxCarbs)
        carbs.setIfSmaller(maxCarbs, rh.gs(R.string.limitingcarbs, maxCarbs, rh.gs(R.string.maxvalueinpreferences)), this)
        return carbs
    }

    override fun configuration(): JSONObject =
        JSONObject()
            .put(StringKey.SafetyAge, preferences)
            .put(DoubleKey.SafetyMaxBolus, preferences)
            .put(IntKey.SafetyMaxCarbs, preferences)

    override fun applyConfiguration(configuration: JSONObject) {
        configuration
            .store(StringKey.SafetyAge, preferences)
            .store(DoubleKey.SafetyMaxBolus, preferences)
            .store(IntKey.SafetyMaxCarbs, preferences)
    }

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null) return
        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "safety_settings"
            title = rh.gs(R.string.treatmentssafety_title)
            initialExpandedChildrenCount = 0
            addPreference(
                AdaptiveListPreference(
                    ctx = context,
                    stringKey = StringKey.SafetyAge,
                    summary = app.aaps.core.ui.R.string.patient_age_summary,
                    title = app.aaps.core.ui.R.string.patient_type,
                    entries = hardLimits.ageEntries(),
                    entryValues = hardLimits.ageEntryValues()
                )
            )
            addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.SafetyMaxBolus, title = app.aaps.core.ui.R.string.max_bolus_title))
            addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.SafetyMaxCarbs, title = app.aaps.core.ui.R.string.max_carbs_title))
        }
    }
}
