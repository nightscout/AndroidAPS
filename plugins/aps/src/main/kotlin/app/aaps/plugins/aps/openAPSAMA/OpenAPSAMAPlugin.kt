package app.aaps.plugins.aps.openAPSAMA

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.aps.SMBDefaults
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.CurrentTemp
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.aps.OapsProfile
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAPSCalculationFinished
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntentKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.extensions.convertedToAbsolute
import app.aaps.core.objects.extensions.getPassedDurationToTimeInMinutes
import app.aaps.core.objects.extensions.plannedRemainingMinutes
import app.aaps.core.objects.extensions.target
import app.aaps.core.utils.MidnightUtils
import app.aaps.core.validators.preferences.AdaptiveDoublePreference
import app.aaps.core.validators.preferences.AdaptiveIntentPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.plugins.aps.OpenAPSFragment
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.events.EventOpenAPSUpdateGui
import app.aaps.plugins.aps.events.EventResetOpenAPSGui
import app.aaps.plugins.aps.openAPSSMB.GlucoseStatusCalculatorSMB
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.math.floor
import kotlin.math.min

@Singleton
class OpenAPSAMAPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val constraintsChecker: ConstraintsChecker,
    rh: ResourceHelper,
    config: Config,
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePlugin,
    private val iobCobCalculator: IobCobCalculator,
    private val processedTbrEbData: ProcessedTbrEbData,
    private val hardLimits: HardLimits,
    private val dateUtil: DateUtil,
    private val persistenceLayer: PersistenceLayer,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val preferences: Preferences,
    private val determineBasalAMA: DetermineBasalAMA,
    private val glucoseStatusCalculatorSMB: GlucoseStatusCalculatorSMB,
    private val apsResultProvider: Provider<APSResult>
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.APS)
        .fragmentClass(OpenAPSFragment::class.java.name)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_generic_icon)
        .pluginName(R.string.openapsama)
        .shortName(R.string.oaps_shortname)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .preferencesVisibleInSimpleMode(false)
        .showInList { config.APS }
        .description(R.string.description_ama),
    aapsLogger, rh
), APS, PluginConstraints {

    // last values
    override var lastAPSRun: Long = 0
    override val algorithm = APSResult.Algorithm.AMA
    override var lastAPSResult: APSResult? = null

    override fun specialEnableCondition(): Boolean {
        return try {
            val pump = activePlugin.activePump
            pump.pumpDescription.isTempBasalCapable
        } catch (_: Exception) {
            // may fail during initialization
            true
        }
    }

    override fun specialShowInListCondition(): Boolean {
        try {
            val pump = activePlugin.activePump
            return pump.pumpDescription.isTempBasalCapable
        } catch (_: Exception) {
            return true
        }
    }

    override fun invoke(initiator: String, tempBasalFallback: Boolean) {

        aapsLogger.debug(LTag.APS, "invoke from $initiator tempBasalFallback: $tempBasalFallback")
        lastAPSResult = null
        val glucoseStatus = glucoseStatusProvider.glucoseStatusData
        val profile = profileFunction.getProfile()
        val pump = activePlugin.activePump
        if (profile == null) {
            rxBus.send(EventResetOpenAPSGui(rh.gs(app.aaps.core.ui.R.string.no_profile_set)))
            aapsLogger.debug(LTag.APS, rh.gs(app.aaps.core.ui.R.string.no_profile_set))
            return
        }
        if (!isEnabled()) {
            rxBus.send(EventResetOpenAPSGui(rh.gs(R.string.openapsma_disabled)))
            aapsLogger.debug(LTag.APS, rh.gs(R.string.openapsma_disabled))
            return
        }
        if (glucoseStatus == null) {
            rxBus.send(EventResetOpenAPSGui(rh.gs(R.string.openapsma_no_glucose_data)))
            aapsLogger.debug(LTag.APS, rh.gs(R.string.openapsma_no_glucose_data))
            return
        }
        val inputConstraints = ConstraintObject(0.0, aapsLogger) // fake. only for collecting all results

        if (!hardLimits.checkHardLimits(profile.dia, app.aaps.core.ui.R.string.profile_dia, hardLimits.minDia(), hardLimits.maxDia())) return
        if (!hardLimits.checkHardLimits(
                profile.getIcTimeFromMidnight(MidnightUtils.secondsFromMidnight()),
                app.aaps.core.ui.R.string.profile_carbs_ratio_value,
                hardLimits.minIC(),
                hardLimits.maxIC()
            )
        ) return
        if (!hardLimits.checkHardLimits(profile.getIsfMgdl("OpenAPSAMAPlugin"), app.aaps.core.ui.R.string.profile_sensitivity_value, HardLimits.MIN_ISF, HardLimits.MAX_ISF)) return
        if (!hardLimits.checkHardLimits(profile.getMaxDailyBasal(), app.aaps.core.ui.R.string.profile_max_daily_basal_value, 0.02, hardLimits.maxBasal())) return
        if (!hardLimits.checkHardLimits(pump.baseBasalRate, app.aaps.core.ui.R.string.current_basal_value, 0.01, hardLimits.maxBasal())) return

        val now = dateUtil.now()
        val tb = processedTbrEbData.getTempBasalIncludingConvertedExtended(now)
        val currentTemp = CurrentTemp(
            duration = tb?.plannedRemainingMinutes ?: 0,
            rate = tb?.convertedToAbsolute(now, profile) ?: 0.0,
            minutesrunning = tb?.getPassedDurationToTimeInMinutes(now)
        )
        var minBg = hardLimits.verifyHardLimits(Round.roundTo(profile.getTargetLowMgdl(), 0.1), app.aaps.core.ui.R.string.profile_low_target, HardLimits.LIMIT_MIN_BG[0], HardLimits.LIMIT_MIN_BG[1])
        var maxBg = hardLimits.verifyHardLimits(Round.roundTo(profile.getTargetHighMgdl(), 0.1), app.aaps.core.ui.R.string.profile_high_target, HardLimits.LIMIT_MAX_BG[0], HardLimits.LIMIT_MAX_BG[1])
        var targetBg = hardLimits.verifyHardLimits(profile.getTargetMgdl(), app.aaps.core.ui.R.string.temp_target_value, HardLimits.LIMIT_TARGET_BG[0], HardLimits.LIMIT_TARGET_BG[1])

        var isTempTarget = false
        persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())?.let { tempTarget ->
            isTempTarget = true
            minBg = hardLimits.verifyHardLimits(tempTarget.lowTarget, app.aaps.core.ui.R.string.temp_target_low_target, HardLimits.LIMIT_TEMP_MIN_BG[0], HardLimits.LIMIT_TEMP_MIN_BG[1])
            maxBg = hardLimits.verifyHardLimits(tempTarget.highTarget, app.aaps.core.ui.R.string.temp_target_high_target, HardLimits.LIMIT_TEMP_MAX_BG[0], HardLimits.LIMIT_TEMP_MAX_BG[1])
            targetBg = hardLimits.verifyHardLimits(tempTarget.target(), app.aaps.core.ui.R.string.temp_target_value, HardLimits.LIMIT_TEMP_TARGET_BG[0], HardLimits.LIMIT_TEMP_TARGET_BG[1])
        }

        val autosensResult =
            if (constraintsChecker.isAutosensModeEnabled().value()) {
                val autosensData = iobCobCalculator.getLastAutosensDataWithWaitForCalculationFinish("OpenAPSPlugin")
                if (autosensData == null) {
                    rxBus.send(EventResetOpenAPSGui(rh.gs(R.string.openaps_no_as_data)))
                    return
                }
                autosensData.autosensResult
            } else AutosensResult(sensResult = "autosens disabled")

        val iobArray = iobCobCalculator.calculateIobArrayInDia(profile)
        val mealData = iobCobCalculator.getMealDataWithWaitingForCalculationFinish()

        val oapsProfile = OapsProfile(
            dia = min(profile.dia, 3.0),
            min_5m_carbimpact = if (mealData.usedMinCarbsImpact > 0) mealData.usedMinCarbsImpact else preferences.get(DoubleKey.ApsAmaMin5MinCarbsImpact),
            max_iob = constraintsChecker.getMaxIOBAllowed().also { inputConstraints.copyReasons(it) }.value(),
            max_daily_basal = profile.getMaxDailyBasal(),
            max_basal = constraintsChecker.getMaxBasalAllowed(profile).also { inputConstraints.copyReasons(it) }.value(),
            min_bg = minBg,
            max_bg = maxBg,
            target_bg = targetBg,
            carb_ratio = profile.getIc(),
            sens = profile.getIsfMgdl("OpenAPSAMAPlugin"),
            autosens_adjust_targets = preferences.get(BooleanKey.ApsAmaAutosensAdjustTargets),
            max_daily_safety_multiplier = preferences.get(DoubleKey.ApsMaxDailyMultiplier),
            current_basal_safety_multiplier = preferences.get(DoubleKey.ApsMaxCurrentBasalMultiplier),
            lgsThreshold = 0, // not used
            high_temptarget_raises_sensitivity = false, // not used
            low_temptarget_lowers_sensitivity = false, // not used
            sensitivity_raises_target = false, // not used
            resistance_lowers_target = false, // not used
            adv_target_adjustments = false, // not used
            exercise_mode = false, // not used
            half_basal_exercise_target = 0, // not used
            maxCOB = 0, // not used
            skip_neutral_temps = pump.setNeutralTempAtFullHour(),
            remainingCarbsCap = 0, // not used
            enableUAM = false, // not used
            A52_risk_enable = SMBDefaults.A52_risk_enable,
            SMBInterval = 0, // not used
            enableSMB_with_COB = false, // not used
            enableSMB_with_temptarget = false, // not used
            allowSMB_with_high_temptarget = false, // not used
            enableSMB_always = false, // not used
            enableSMB_after_carbs = false, // not used
            maxSMBBasalMinutes = 0, // not used
            maxUAMSMBBasalMinutes = 0, // not used
            bolus_increment = pump.pumpDescription.bolusStep, // not used
            carbsReqThreshold = 0, // not used
            current_basal = activePlugin.activePump.baseBasalRate,
            temptargetSet = isTempTarget,
            autosens_max = preferences.get(DoubleKey.AutosensMax), // not used
            out_units = if (profileFunction.getUnits() == GlucoseUnit.MMOL) "mmol/L" else "mg/dl",
            variable_sens = 0.0, // not used
            insulinDivisor = 0, // not used
            TDD = 0.0 // not used
        )

        aapsLogger.debug(LTag.APS, ">>> Invoking determine_basal AMA <<<")
        aapsLogger.debug(LTag.APS, "Glucose status:     $glucoseStatus")
        aapsLogger.debug(LTag.APS, "Current temp:       $currentTemp")
        aapsLogger.debug(LTag.APS, "IOB data:           ${iobArray.joinToString()}")
        aapsLogger.debug(LTag.APS, "Profile:            $oapsProfile")
        aapsLogger.debug(LTag.APS, "Autosens data:      $autosensResult")
        aapsLogger.debug(LTag.APS, "Meal data:          $mealData")

        determineBasalAMA.determine_basal(
            glucose_status = glucoseStatus,
            currenttemp = currentTemp,
            iob_data_array = iobArray,
            profile = oapsProfile,
            autosens_data = autosensResult,
            meal_data = mealData,
            currentTime = now
        ).also {
            val determineBasalResult = apsResultProvider.get().with(it)
            // Preserve input data
            determineBasalResult.inputConstraints = inputConstraints
            determineBasalResult.autosensResult = autosensResult
            determineBasalResult.iobData = iobArray
            determineBasalResult.glucoseStatus = glucoseStatus
            determineBasalResult.currentTemp = currentTemp
            determineBasalResult.oapsProfile = oapsProfile
            determineBasalResult.mealData = mealData
            lastAPSResult = determineBasalResult
            lastAPSRun = now
            aapsLogger.debug(LTag.APS, "Result: $it")
            rxBus.send(EventAPSCalculationFinished())
        }
        rxBus.send(EventOpenAPSUpdateGui())
    }

    override fun getGlucoseStatusData(allowOldData: Boolean): GlucoseStatus? = glucoseStatusCalculatorSMB.getGlucoseStatusData(allowOldData)

    override fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> {
        if (isEnabled()) {
            val maxIobPref: Double = preferences.get(DoubleKey.ApsAmaMaxIob)
            maxIob.setIfSmaller(maxIobPref, rh.gs(R.string.limiting_iob, maxIobPref, rh.gs(R.string.maxvalueinpreferences)), this)
            maxIob.setIfSmaller(hardLimits.maxIobAMA(), rh.gs(R.string.limiting_iob, hardLimits.maxIobAMA(), rh.gs(R.string.hardlimit)), this)
        }
        return maxIob
    }

    override fun applyBasalConstraints(absoluteRate: Constraint<Double>, profile: Profile): Constraint<Double> {
        if (isEnabled()) {
            var maxBasal = preferences.get(DoubleKey.ApsMaxBasal)
            if (maxBasal < profile.getMaxDailyBasal()) {
                maxBasal = profile.getMaxDailyBasal()
                absoluteRate.addReason(rh.gs(R.string.increasing_max_basal), this)
            }
            absoluteRate.setIfSmaller(maxBasal, rh.gs(app.aaps.core.ui.R.string.limitingbasalratio, maxBasal, rh.gs(R.string.maxvalueinpreferences)), this)

            // Check percentRate but absolute rate too, because we know real current basal in pump
            val maxBasalMultiplier = preferences.get(DoubleKey.ApsMaxCurrentBasalMultiplier)
            val maxFromBasalMultiplier = floor(maxBasalMultiplier * profile.getBasal() * 100) / 100
            absoluteRate.setIfSmaller(
                maxFromBasalMultiplier,
                rh.gs(app.aaps.core.ui.R.string.limitingbasalratio, maxFromBasalMultiplier, rh.gs(R.string.max_basal_multiplier)),
                this
            )
            val maxBasalFromDaily = preferences.get(DoubleKey.ApsMaxDailyMultiplier)
            val maxFromDaily = floor(profile.getMaxDailyBasal() * maxBasalFromDaily * 100) / 100
            absoluteRate.setIfSmaller(maxFromDaily, rh.gs(app.aaps.core.ui.R.string.limitingbasalratio, maxFromDaily, rh.gs(R.string.max_daily_basal_multiplier)), this)
        }
        return absoluteRate
    }

    override fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val enabled = preferences.get(BooleanKey.ApsUseAutosens)
        if (!enabled) value.set(false, rh.gs(R.string.autosens_disabled_in_preferences), this)
        return value
    }

    // Needed only for dynamic ISF so far
    override fun configuration(): JSONObject = JSONObject()
    override fun applyConfiguration(configuration: JSONObject) {}

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null && requiredKey != "absorption_ama_advanced") return
        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "openapsma_settings"
            title = rh.gs(R.string.openapsama)
            initialExpandedChildrenCount = 0
            addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ApsMaxBasal, dialogMessage = R.string.openapsma_max_basal_summary, title = R.string.openapsma_max_basal_title))
            addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ApsAmaMaxIob, dialogMessage = R.string.openapsma_max_iob_summary, title = R.string.openapsma_max_iob_title))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsUseAutosens, title = R.string.openapsama_use_autosens))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsAmaAutosensAdjustTargets, summary = R.string.openapsama_autosens_adjust_targets_summary, title = R.string.openapsama_autosens_adjust_targets))
            addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ApsAmaMin5MinCarbsImpact, dialogMessage = R.string.openapsama_min_5m_carb_impact_summary, title = R.string.openapsama_min_5m_carb_impact))
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "absorption_ama_advanced"
                title = rh.gs(app.aaps.core.ui.R.string.advanced_settings_title)
                addPreference(
                    AdaptiveIntentPreference(
                        ctx = context,
                        intentKey = IntentKey.ApsLinkToDocs,
                        intent = Intent().apply { action = Intent.ACTION_VIEW; data = rh.gs(R.string.openapsama_link_to_preference_json_doc).toUri() },
                        summary = R.string.openapsama_link_to_preference_json_doc_txt
                    )
                )
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsAlwaysUseShortDeltas, summary = R.string.always_use_short_avg_summary, title = R.string.always_use_short_avg))
                addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ApsMaxDailyMultiplier, dialogMessage = R.string.openapsama_max_daily_safety_multiplier_summary, title = R.string.openapsama_max_daily_safety_multiplier))
                addPreference(
                    AdaptiveDoublePreference(
                        ctx = context,
                        doubleKey = DoubleKey.ApsMaxCurrentBasalMultiplier,
                        dialogMessage = R.string.openapsama_current_basal_safety_multiplier_summary,
                        title = R.string.openapsama_current_basal_safety_multiplier
                    )
                )
                addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ApsAmaBolusSnoozeDivisor, dialogMessage = R.string.openapsama_bolus_snooze_dia_divisor_summary, title = R.string.openapsama_bolus_snooze_dia_divisor))
            })
        }
    }
}