package app.aaps.plugins.aps.openAPSAutoISF

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.LongSparseArray
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import app.aaps.core.data.aps.SMBDefaults
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.CurrentTemp
import app.aaps.core.interfaces.aps.OapsProfile
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
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
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.profiling.Profiler
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAPSCalculationFinished
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.keys.AdaptiveIntentPreference
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.IntentKey
import app.aaps.core.keys.Preferences
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.objects.aps.DetermineBasalResult
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.extensions.convertedToAbsolute
import app.aaps.core.objects.extensions.getPassedDurationToTimeInMinutes
import app.aaps.core.objects.extensions.plannedRemainingMinutes
import app.aaps.core.objects.extensions.put
import app.aaps.core.objects.extensions.store
import app.aaps.core.objects.extensions.target
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.utils.MidnightUtils
import app.aaps.core.validators.AdaptiveDoublePreference
import app.aaps.core.validators.AdaptiveIntPreference
import app.aaps.core.validators.AdaptiveSwitchPreference
import app.aaps.core.validators.AdaptiveUnitPreference
import app.aaps.plugins.aps.OpenAPSFragment
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.events.EventOpenAPSUpdateGui
import app.aaps.plugins.aps.events.EventResetOpenAPSGui
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@Singleton
open class OpenAPSAutoISFPlugin @Inject constructor(
    private val injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val constraintsChecker: ConstraintsChecker,
    rh: ResourceHelper,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    config: Config,
    private val activePlugin: ActivePlugin,
    private val iobCobCalculator: IobCobCalculator,
    private val hardLimits: HardLimits,
    private val preferences: Preferences,
    protected val dateUtil: DateUtil,
    private val processedTbrEbData: ProcessedTbrEbData,
    private val persistenceLayer: PersistenceLayer,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val bgQualityCheck: BgQualityCheck,
    private val uiInteraction: UiInteraction,
    private val determineBasalAutoISF: DetermineBasalAutoISF,
    private val profiler: Profiler
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.APS)
        .fragmentClass(OpenAPSFragment::class.java.name)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_generic_icon)
        .pluginName(R.string.openaps_auto_isf)
        .shortName(R.string.autoisf_shortname)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .preferencesVisibleInSimpleMode(false)
        .showInList(config.APS)
        .description(R.string.description_auto_isf),
    aapsLogger, rh
), APS, PluginConstraints {

    // last values
    override var lastAPSRun: Long = 0
    override val algorithm = APSResult.Algorithm.AUTO_ISF
    override var lastAPSResult: DetermineBasalResult? = null
    private var consoleError = mutableListOf<String>()

    private val autoISF_max = preferences.get(DoubleKey.ApsAutoIsfMax)
    private val autoISF_min = preferences.get(DoubleKey.ApsAutoIsfMin)
    private val bgAccel_ISF_weight = preferences.get(DoubleKey.ApsAutoIsfBgAccelWeight)
    private val bgBrake_ISF_weight = preferences.get(DoubleKey.ApsAutoIsfBgBrakeWeight)
    private val enable_pp_ISF_always = preferences.get(BooleanKey.ApsAutoIsfPpAlways)
    private val pp_ISF_hours = preferences.get(IntKey.ApsAutoIsfPpIsfHours)
    private val pp_ISF_weight = preferences.get(DoubleKey.ApsAutoIsfPpWeight)
    private val delta_ISFrange_weight = preferences.get(DoubleKey.ApsAutoIsfDeltaWeight)
    private val lower_ISFrange_weight = preferences.get(DoubleKey.ApsAutoIsfLowBgWeight)
    private val higher_ISFrange_weight = preferences.get(DoubleKey.ApsAutoIsfHighBgWeight)
    private val enable_dura_ISF_with_COB = preferences.get(BooleanKey.ApsAutoIsfDuraAfterCarbs)
    private val dura_ISF_weight = preferences.get(DoubleKey.ApsAutoIsfDuraWeight)
    private val smb_delivery_ratio = preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatio)
    private val smb_delivery_ratio_min = preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatioMin)
    private val smb_delivery_ratio_max = preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatioMax)
    private val smb_delivery_ratio_bg_range = preferences.get(UnitDoubleKey.ApsAutoIsfSmbDeliveryRatioBgRange)
    val smbMaxRangeExtension = preferences.get(DoubleKey.ApsAutoIsfSmbMaxRangeExtension)
    private val enableSMB_EvenOn_OddOff = preferences.get(BooleanKey.ApsAutoIsfSmbOnEvenTt) // for TT
    private val enableSMB_EvenOn_OddOff_always = preferences.get(BooleanKey.ApsAutoIsfSmbOnEvenPt) // for profile target
    val iobThresholdPercent = preferences.get(IntKey.ApsAutoIsfIobThPercent)
    private val exerciseMode = SMBDefaults.exercise_mode
    private val highTemptargetRaisesSensitivity = preferences.get(BooleanKey.ApsAutoIsfHighTtRaisesSens)
    private var enableDynAps = true
    override fun supportsDynamicIsf(): Boolean = preferences.get(BooleanKey.ApsUseAutoIsfWeights) && enableDynAps

    override fun getIsfMgdl(multiplier: Double, timeShift: Int, caller: String): Double? {
        val start = dateUtil.now()
        val sensitivity = calculateVariableIsf(start, bg = null)
        profiler.log(LTag.APS, String.format("getIsfMgdl() %s %f %s %s", sensitivity.first, sensitivity.second, dateUtil.dateAndTimeAndSecondsString(start), caller), start)
        return sensitivity.second?.let { it * multiplier }
    }

    override fun getIsfMgdl(timestamp: Long, bg: Double, multiplier: Double, timeShift: Int, caller: String): Double? {
        val start = dateUtil.now()
        val sensitivity = calculateVariableIsf(timestamp, bg)
        profiler.log(LTag.APS, String.format("getIsfMgdl() %s %f %s %s", sensitivity.first, sensitivity.second, dateUtil.dateAndTimeAndSecondsString(timestamp), caller), start)
        return sensitivity.second?.let { it * multiplier }
    }

    override fun specialEnableCondition(): Boolean {
        return try {
            activePlugin.activePump.pumpDescription.isTempBasalCapable
        } catch (ignored: Exception) {
            // may fail during initialization
            true
        }
    }

    override fun specialShowInListCondition(): Boolean {
        val pump = activePlugin.activePump
        return pump.pumpDescription.isTempBasalCapable
    }

    override fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {
        super.preprocessPreferences(preferenceFragment)
        val smbAlwaysEnabled = preferences.get(BooleanKey.ApsUseSmbAlways)
        val advancedFiltering = activePlugin.activeBgSource.advancedFilteringSupported()
        preferenceFragment.findPreference<SwitchPreference>(rh.gs(app.aaps.core.keys.R.string.key_openaps_allow_smb_with_COB))?.isVisible = !smbAlwaysEnabled || !advancedFiltering
        preferenceFragment.findPreference<SwitchPreference>(rh.gs(app.aaps.core.keys.R.string.key_openaps_allow_smb_with_low_temp_target))?.isVisible = !smbAlwaysEnabled || !advancedFiltering
        preferenceFragment.findPreference<SwitchPreference>(rh.gs(app.aaps.core.keys.R.string.key_openaps_enable_smb_after_carbs))?.isVisible = !smbAlwaysEnabled || !advancedFiltering
    }

    private val dynIsfCache = LongSparseArray<Double>()
    private fun calculateVariableIsf(timestamp: Long, bg: Double?): Pair<String, Double?> {
        // Todo : Calculate here ISF value with AutoISF algo
        val profile = profileFunction.getProfile(timestamp)
        if (profile == null) return Pair("OFF", null)
        if (!preferences.get(BooleanKey.ApsUseDynamicSensitivity)) return Pair("OFF", null)
        val result = persistenceLayer.getApsResultCloseTo(timestamp)
        if (result?.variableSens != null) {
            //aapsLogger.debug("calculateVariableIsf $caller DB  ${dateUtil.dateAndTimeAndSecondsString(timestamp)} ${result.variableSens}")
            return Pair("DB", result.variableSens)
        }
        val glucose = bg ?: glucoseStatusProvider.glucoseStatusData?.glucose ?: return Pair("GLUC", null)
        // Round down to 30 min and use it as a key for caching
        // Add BG to key as it affects calculation
        val key = timestamp - timestamp % T.mins(30).msecs() + glucose.toLong()
        val cached = dynIsfCache[key]
        if (cached != null && timestamp < dateUtil.now()) {
            //aapsLogger.debug("calculateVariableIsf $caller HIT ${dateUtil.dateAndTimeAndSecondsString(timestamp)} $cached")
            return Pair("HIT", cached)
        }
        // no cached result found, let's calculate the value
        enableDynAps = false // disable supportsDynamicIsf feature to get profile ISF value
        val sensitivity = (autoISF(timestamp) ?: 1.0) * profile.getIsfMgdlTimeFromMidnight(MidnightUtils.secondsFromMidnight(timestamp))
        aapsLogger.debug("XXXXX $sensitivity ${profile.getIsfMgdlTimeFromMidnight(MidnightUtils.secondsFromMidnight(timestamp))}")
        enableDynAps = true // enable supportsDynamicIsf feature after calculation
        dynIsfCache.put(key, sensitivity)
        if (dynIsfCache.size() > 1000) dynIsfCache.clear()
        return Pair("CALC", sensitivity)
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
        if (!hardLimits.checkHardLimits(profile.getIsfMgdl("OpenAPSAutoISFPlugin"), app.aaps.core.ui.R.string.profile_sensitivity_value, HardLimits.MIN_ISF, HardLimits.MAX_ISF)) return
        if (!hardLimits.checkHardLimits(profile.getMaxDailyBasal(), app.aaps.core.ui.R.string.profile_max_daily_basal_value, 0.02, hardLimits.maxBasal())) return
        if (!hardLimits.checkHardLimits(pump.baseBasalRate, app.aaps.core.ui.R.string.current_basal_value, 0.01, hardLimits.maxBasal())) return

        // End of check, start gathering data

        val dynIsfMode = preferences.get(BooleanKey.ApsUseAutoIsfWeights)
        val smbEnabled = preferences.get(BooleanKey.ApsUseSmb)
        val advancedFiltering = constraintsChecker.isAdvancedFilteringEnabled().also { inputConstraints.copyReasons(it) }.value()

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

        var autosensResult = AutosensResult()
        var variableSensitivity = 0.0
        val sens = profile.getIsfMgdl("OpenAPSAutoISFPlugin")

        if (constraintsChecker.isAutosensModeEnabled().value()) {
            val autosensData = iobCobCalculator.getLastAutosensDataWithWaitForCalculationFinish("OpenAPSPlugin")
            if (autosensData == null) {
                rxBus.send(EventResetOpenAPSGui(rh.gs(R.string.openaps_no_as_data)))
                return
            }
            autosensResult = autosensData.autosensResult
        } else autosensResult.sensResult = "autosens disabled"
        val iobArray = iobCobCalculator.calculateIobArrayForSMB(autosensResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, isTempTarget)
        val mealData = iobCobCalculator.getMealDataWithWaitingForCalculationFinish()
        val iobData = iobArray[0]
        val profile_percentage = if (profile is ProfileSealed.EPS) profile.value.originalPercentage else 100
        var microBolusAllowed = constraintsChecker.isSMBModeEnabled(ConstraintObject(tempBasalFallback.not(), aapsLogger)).also { inputConstraints.copyReasons(it) }.value()

        if (dynIsfMode) {
            consoleError = mutableListOf<String>()
            variableSensitivity = autoISF(now) ?: 1.0
        }
        val oapsProfile = OapsProfile(
            dia = 0.0, // not used
            min_5m_carbimpact = 0.0, // not used
            max_iob = constraintsChecker.getMaxIOBAllowed().also { inputConstraints.copyReasons(it) }.value(),
            max_daily_basal = profile.getMaxDailyBasal(),
            max_basal = constraintsChecker.getMaxBasalAllowed(profile).also { inputConstraints.copyReasons(it) }.value(),
            min_bg = minBg,
            max_bg = maxBg,
            target_bg = targetBg,
            carb_ratio = profile.getIc(),
            sens = sens,
            autosens_adjust_targets = false, // not used
            max_daily_safety_multiplier = preferences.get(DoubleKey.ApsMaxDailyMultiplier),
            current_basal_safety_multiplier = preferences.get(DoubleKey.ApsMaxCurrentBasalMultiplier),
            lgsThreshold = profileUtil.convertToMgdlDetect(preferences.get(UnitDoubleKey.ApsLgsThreshold)).toInt(),
            high_temptarget_raises_sensitivity = exerciseMode || highTemptargetRaisesSensitivity, //was false,
            low_temptarget_lowers_sensitivity = preferences.get(BooleanKey.ApsAutoIsfLowTtLowersSens), // was false,
            sensitivity_raises_target = preferences.get(BooleanKey.ApsSensitivityRaisesTarget),
            resistance_lowers_target = preferences.get(BooleanKey.ApsResistanceLowersTarget),
            adv_target_adjustments = SMBDefaults.adv_target_adjustments,
            exercise_mode = SMBDefaults.exercise_mode,
            half_basal_exercise_target = preferences.get(IntKey.ApsAutoIsfHalfBasalExerciseTarget),
            maxCOB = SMBDefaults.maxCOB,
            skip_neutral_temps = pump.setNeutralTempAtFullHour(),
            remainingCarbsCap = SMBDefaults.remainingCarbsCap,
            enableUAM = constraintsChecker.isUAMEnabled().also { inputConstraints.copyReasons(it) }.value(),
            A52_risk_enable = SMBDefaults.A52_risk_enable,
            SMBInterval = preferences.get(IntKey.ApsMaxSmbFrequency),
            enableSMB_with_COB = smbEnabled && preferences.get(BooleanKey.ApsUseSmbWithCob),
            enableSMB_with_temptarget = smbEnabled && preferences.get(BooleanKey.ApsUseSmbWithLowTt),
            allowSMB_with_high_temptarget = smbEnabled && preferences.get(BooleanKey.ApsUseSmbWithHighTt),
            enableSMB_always = smbEnabled && preferences.get(BooleanKey.ApsUseSmbAlways) && advancedFiltering,
            enableSMB_after_carbs = smbEnabled && preferences.get(BooleanKey.ApsUseSmbAfterCarbs) && advancedFiltering,
            maxSMBBasalMinutes = preferences.get(IntKey.ApsMaxMinutesOfBasalToLimitSmb),
            maxUAMSMBBasalMinutes = preferences.get(IntKey.ApsUamMaxMinutesOfBasalToLimitSmb),
            bolus_increment = pump.pumpDescription.bolusStep,
            carbsReqThreshold = preferences.get(IntKey.ApsCarbsRequestThreshold),
            current_basal = activePlugin.activePump.baseBasalRate,
            temptargetSet = isTempTarget,
            autosens_max = preferences.get(DoubleKey.AutosensMax),
            out_units = if (profileFunction.getUnits() == GlucoseUnit.MMOL) "mmol/L" else "mg/dl",
            variable_sens = variableSensitivity, //variableSensitivity,
            insulinDivisor = 0,
            TDD = 0.0
        )
        val exercise_ratio = 1.0
        //todo calculate exercice ratio
        val iobTH_reduction_ratio = profile_percentage / 100.0 * exercise_ratio;     // later: * activityRatio;
        val loopWantedSmb = loop_smb(microBolusAllowed, oapsProfile, iobData.iob, iobTH_reduction_ratio)
        if (dynIsfMode)
            microBolusAllowed = microBolusAllowed && loopWantedSmb != "AAPS" && (loopWantedSmb == "enforced" || loopWantedSmb == "fullLoop")
        val flatBGsDetected = bgQualityCheck.state == BgQualityCheck.State.FLAT
        val target_bg = (minBg + maxBg) / 2
        val smbRatio = determine_varSMBratio(oapsProfile, glucoseStatus.glucose.toInt(), target_bg, loopWantedSmb)

        aapsLogger.debug(LTag.APS, ">>> Invoking determine_basal AutoISF <<<")
        aapsLogger.debug(LTag.APS, "Glucose status:     $glucoseStatus")
        aapsLogger.debug(LTag.APS, "Current temp:       $currentTemp")
        aapsLogger.debug(LTag.APS, "IOB data:           ${iobArray.joinToString()}")
        aapsLogger.debug(LTag.APS, "Profile:            $oapsProfile")
        aapsLogger.debug(LTag.APS, "Autosens data:      $autosensResult")
        aapsLogger.debug(LTag.APS, "Meal data:          $mealData")
        aapsLogger.debug(LTag.APS, "MicroBolusAllowed:  $microBolusAllowed")
        aapsLogger.debug(LTag.APS, "flatBGsDetected:    $flatBGsDetected")
        aapsLogger.debug(LTag.APS, "AutoIsfMode:         $dynIsfMode")

        determineBasalAutoISF.determine_basal(
            glucose_status = glucoseStatus,
            currenttemp = currentTemp,
            iob_data_array = iobArray,
            profile = oapsProfile,
            autosens_data = autosensResult,
            meal_data = mealData,
            microBolusAllowed = microBolusAllowed,
            currentTime = now,
            flatBGsDetected = flatBGsDetected,
            dynIsfMode = dynIsfMode,
            loop_wanted_smb = loopWantedSmb,
            profile_percentage = profile_percentage,
            smb_ratio = smbRatio,
            smb_max_range_extension = smbMaxRangeExtension,
            iob_threshold_percent = iobThresholdPercent,
            auto_isf_console =    consoleError
        ).also {
            val determineBasalResult = DetermineBasalResult(injector, it)
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

    override fun isSuperBolusEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        value.set(false)
        return value
    }

    override fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> {
        if (isEnabled()) {
            val maxIobPref = preferences.get(DoubleKey.ApsSmbMaxIob)
            maxIob.setIfSmaller(maxIobPref, rh.gs(R.string.limiting_iob, maxIobPref, rh.gs(R.string.maxvalueinpreferences)), this)
            maxIob.setIfSmaller(hardLimits.maxIobSMB(), rh.gs(R.string.limiting_iob, hardLimits.maxIobSMB(), rh.gs(R.string.hardlimit)), this)
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

    override fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val enabled = preferences.get(BooleanKey.ApsUseSmb)
        if (!enabled) value.set(false, rh.gs(R.string.smb_disabled_in_preferences), this)
        return value
    }

    override fun isUAMEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val enabled = preferences.get(BooleanKey.ApsUseUam)
        if (!enabled) value.set(false, rh.gs(R.string.uam_disabled_in_preferences), this)
        return value
    }

    override fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val enabled = preferences.get(BooleanKey.ApsUseAutosens)
        if (!enabled) value.set(false, rh.gs(R.string.autosens_disabled_in_preferences), this)
        return value
    }

    override fun configuration(): JSONObject =
        JSONObject()
            .put(BooleanKey.ApsUseDynamicSensitivity, preferences, rh)
            .put(IntKey.ApsDynIsfAdjustmentFactor, preferences, rh)

    override fun applyConfiguration(configuration: JSONObject) {
        configuration
            .store(BooleanKey.ApsUseDynamicSensitivity, preferences, rh)
            .store(IntKey.ApsDynIsfAdjustmentFactor, preferences, rh)
    }

    // Rounds value to 'digits' decimal places
    // different for negative numbers fun round(value: Double, digits: Int): Double = BigDecimal(value).setScale(digits, RoundingMode.HALF_EVEN).toDouble()
    fun round(value: Double, digits: Int): Double {
        if (value.isNaN()) return Double.NaN
        val scale = 10.0.pow(digits.toDouble())
        return Math.round(value * scale) / scale
    }

    fun convert_bg(value: Double): String =
        profileUtil.fromMgdlToStringInUnits(value).replace("-0.0", "0.0")

    fun autoISF(currentTime: Long): Double? {
        //origin_sens: String, oapsProfile: OapsProfile, sensitivityRatio: Double, loop_wanted_smb: String): Double? {
        val profile = profileFunction.getProfile()
        val sens = profile?.getIsfMgdl("OpenAPSAutoISFPlugin")
        val glucose_status = glucoseStatusProvider.glucoseStatusData
        val dynIsfMode = preferences.get(BooleanKey.ApsUseAutoIsfWeights)
        val normalTarget = 100
        if (!dynIsfMode || sens == null || glucose_status == null) {
            consoleError.add("autoISF disabled in Preferences")
            consoleError.add("----------------------------------")
            consoleError.add("end autoISF")
            consoleError.add("----------------------------------")
            return sens
        }
        val high_temptarget_raises_sensitivity = exerciseMode || highTemptargetRaisesSensitivity
        val meal_data = iobCobCalculator.getMealDataWithWaitingForCalculationFinish()
        var target_bg = hardLimits.verifyHardLimits(profile.getTargetMgdl(), app.aaps.core.ui.R.string.temp_target_value, HardLimits.LIMIT_TARGET_BG[0], HardLimits.LIMIT_TARGET_BG[1])
        var isTempTarget = false
        persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())?.let { tempTarget ->
            isTempTarget = true
            target_bg = hardLimits.verifyHardLimits(tempTarget.target(), app.aaps.core.ui.R.string.temp_target_value, HardLimits.LIMIT_TEMP_TARGET_BG[0], HardLimits.LIMIT_TEMP_TARGET_BG[1])
        }
        var sensitivityRatio = 1.0
        var origin_sens = ""
        val low_temptarget_lowers_sensitivity = preferences.get(BooleanKey.ApsAutoIsfLowTtLowersSens)
        if (high_temptarget_raises_sensitivity && isTempTarget && target_bg > normalTarget
            || low_temptarget_lowers_sensitivity && isTempTarget && target_bg < normalTarget
        ) {
            // w/ target 100, temp target 110 = .89, 120 = 0.8, 140 = 0.67, 160 = .57, and 200 = .44
            // e.g.: Sensitivity ratio set to 0.8 based on temp target of 120; Adjusting basal from 1.65 to 1.35; ISF from 58.9 to 73.6
            //sensitivityRatio = 2/(2+(target_bg-normalTarget)/40);
            val halfBasalTarget = preferences.get(IntKey.ApsAutoIsfHalfBasalExerciseTarget)
            val c = (halfBasalTarget - normalTarget).toDouble()
            if (c * (c + target_bg-normalTarget) <= 0.0) {
                sensitivityRatio = preferences.get(DoubleKey.AutosensMax)
            } else {
                sensitivityRatio = c / (c + target_bg - normalTarget)
                // limit sensitivityRatio to profile.autosens_max (1.2x by default)
                sensitivityRatio = min(sensitivityRatio, preferences.get(DoubleKey.AutosensMax))
                sensitivityRatio = round(sensitivityRatio, 2)
                origin_sens = "from TT modifier"
                consoleError.add("Sensitivity ratio set to $sensitivityRatio based on temp target of $target_bg; ")
            }
        } else {
            var autosensResult = AutosensResult()

            if (constraintsChecker.isAutosensModeEnabled().value()) {
                iobCobCalculator.getLastAutosensDataWithWaitForCalculationFinish("OpenAPSPlugin")?.also {
                    autosensResult = it.autosensResult
                }
            } else autosensResult.sensResult = "autosens disabled"
            sensitivityRatio = autosensResult.ratio
            consoleError.add("Autosens ratio: $sensitivityRatio; ")
        }
        // Todo include here exercise_ratio calculation
        val autosensResult = AutosensResult()

        if (constraintsChecker.isAutosensModeEnabled().value()) {
            val autosensData = iobCobCalculator.getLastAutosensDataWithWaitForCalculationFinish("OpenAPSPlugin")
            if (autosensData == null) {
                rxBus.send(EventResetOpenAPSGui(rh.gs(R.string.openaps_no_as_data)))
                return null
            }
            autosensData.autosensResult
        } else autosensResult.sensResult = "autosens disabled"

        val dura05: Double = glucose_status.duraISFminutes
        val avg05: Double = glucose_status.duraISFaverage
        val maxISFReduction: Double = autoISF_max
        var sens_modified = false
        var pp_ISF = 1.0
        var delta_ISF = 1.0
        var acce_ISF = 1.0
        var acce_weight: Double = 1.0
        val bg_off = target_bg + 10.0 - glucose_status.glucose;                      // move from central BG=100 to target+10 as virtual BG'=100

        // calculate acce_ISF from bg acceleration and adapt ISF accordingly
        val fit_corr: Double = glucose_status.corrSqu
        val bg_acce: Double = glucose_status.bgAcceleration
        if (glucose_status.a2 != 0.0 && fit_corr >= 0.9) {
            var minmax_delta: Double = -glucose_status.a1 / 2 / glucose_status.a2 * 5      // back from 5min block to 1 min
            var minmax_value: Double = round(glucose_status.a0 - minmax_delta * minmax_delta / 25 * glucose_status.a2, 1)
            minmax_delta = round(minmax_delta, 1)
            if (minmax_delta > 0 && bg_acce < 0) {
                consoleError.add("Parabolic fit extrapolates a maximum of ${convert_bg(minmax_value)} in about $minmax_delta minutes")
            } else if (minmax_delta > 0 && bg_acce > 0.0) {

                consoleError.add("Parabolic fit extrapolates a minimum of ${convert_bg(minmax_value)} in about $minmax_delta minutes")
                if (minmax_delta <= 30 && minmax_value < target_bg) {   // start braking
                    acce_weight = -bgBrake_ISF_weight
                    consoleError.add("extrapolation below target soon: use bgBrake_ISF_weight of ${-acce_weight}")
                }
            }
        }
        if (fit_corr < 0.9) {
            consoleError.add("acce_ISF adaptation by-passed as correlation ${round(fit_corr, 3)} is too low")
        } else {
            val fit_share = 10 * (fit_corr - 0.9)                                // 0 at correlation 0.9, 1 at 1.00
            var cap_weight = 1.0                                             // full contribution above target
            if (acce_weight == 1.0 && glucose_status.glucose < target_bg) { // below target acce goes towards target
                if (bg_acce > 0) {
                    if (bg_acce > 1) {
                        cap_weight = 0.5
                    }            // halve the effect below target
                    acce_weight = bgBrake_ISF_weight
                } else if (bg_acce < 0) {
                    acce_weight = bgAccel_ISF_weight
                }
            } else if (acce_weight == 1.0) {                                       // above target acce goes away from target
                if (bg_acce < 0.0) {
                    acce_weight = bgBrake_ISF_weight
                } else if (bg_acce > 0.0) {
                    acce_weight = bgAccel_ISF_weight
                }
            }
            acce_ISF = 1.0 + bg_acce * cap_weight * acce_weight * fit_share
            consoleError.add("acce_ISF adaptation is ${round(acce_ISF, 2)}")
            if (acce_ISF != 1.0) {
                sens_modified = true
            }
        }

        val bg_ISF = 1 + interpolate(100 - bg_off, "bg")
        consoleError.add("bg_ISF adaptation is ${round(bg_ISF, 2)}")
        var liftISF = 1.0
        var final_ISF = 1.0
        if (bg_ISF < 1.0) {
            liftISF = min(bg_ISF, acce_ISF)
            if (acce_ISF > 1.0) {
                liftISF = bg_ISF * acce_ISF                                 // bg_ISF could become > 1 now
                consoleError.add("bg_ISF adaptation lifted to ${round(liftISF, 2)} as bg accelerates already")
            }
            final_ISF = withinISFlimits(liftISF, autoISF_min, maxISFReduction, sensitivityRatio, origin_sens, isTempTarget, high_temptarget_raises_sensitivity, target_bg, normalTarget)
            return min(720.0, round(sens / final_ISF, 1))         // observe ISF maximum of 720(?)
        } else if (bg_ISF > 1.0) {
            sens_modified = true
        }

        val bg_delta = glucose_status.delta
        val deltaType: String
        deltaType = if (enable_pp_ISF_always || pp_ISF_hours >= (currentTime - meal_data.lastCarbTime) / 1000 / 3600) {
            "pp"
        } else {
            "delta"
        }
        when {
            bg_off > 0.0                     -> {
                consoleError.add(deltaType + "_ISF adaptation by-passed as average glucose < $target_bg+10")
            }
            glucose_status.shortAvgDelta < 0 -> {
                consoleError.add(deltaType + "_ISF adaptation by-passed as no rise or too short lived")
            }
            deltaType == "pp"                -> {
                pp_ISF = 1.0 + max(0.0, bg_delta * pp_ISF_weight)
                consoleError.add("pp_ISF adaptation is ${round(pp_ISF, 2)}")
                if (pp_ISF != 1.0) {
                    sens_modified = true
                }

            }
            else                             -> {
                delta_ISF = interpolate(bg_delta, "delta");
                //  mod V14d: halve the effect below target_bg+30
                if (bg_off > -20.0) {
                    delta_ISF = 0.5 * delta_ISF
                }
                delta_ISF = 1.0 + delta_ISF
                consoleError.add("delta_ISF adaptation is ${round(delta_ISF, 2)}")

                if (delta_ISF != 1.0) {
                    sens_modified = true
                }
            }
        }

        var dura_ISF = 1.0
        val weightISF: Double = dura_ISF_weight
        when {
            meal_data.mealCOB > 0 && !enable_dura_ISF_with_COB -> {
                consoleError.add("dura_ISF by-passed; preferences disabled mealCOB of ${round(meal_data.mealCOB, 1)}")
            }
            dura05 < 10.0                                    -> {
                consoleError.add("dura_ISF by-passed; bg is only $dura05 m at level $avg05");
            }
            avg05 <= target_bg                               -> {
                consoleError.add("dura_ISF by-passed; avg. glucose $avg05 below target $target_bg")
            }
            else                                               -> {
                // fight the resistance at high levels
                val dura05Weight = dura05 / 60
                val avg05Weight = weightISF / target_bg
                dura_ISF += dura05Weight * avg05Weight * (avg05 - target_bg)
                sens_modified = true
                consoleError.add("dura_ISF adaptation is ${round(dura_ISF, 2)} because ISF ${round(sens, 1)} did not do it for ${round(dura05, 1)}m")
            }
        }
        if (sens_modified) {
            liftISF = max(dura_ISF, max(bg_ISF, max(delta_ISF, max(acce_ISF, pp_ISF))))
            if (acce_ISF < 1.0) {                                                                           // 13.JAN.2022 brakes on for otherwise stronger or stable ISF
                consoleError.add("strongest autoISF factor ${round(liftISF, 2)} weakened to ${round(liftISF * acce_ISF, 2)} as bg decelerates already")
                liftISF = liftISF * acce_ISF                                                               // brakes on for otherwise stronger or stable ISF
            }                                                                                               // brakes on for otherwise stronger or stable ISF
            final_ISF = withinISFlimits(liftISF, autoISF_min, maxISFReduction, sensitivityRatio, origin_sens, isTempTarget, high_temptarget_raises_sensitivity, target_bg, normalTarget)
            return round(sens / final_ISF, 1)
        }
        consoleError.add("----------------------------------")
        consoleError.add("end autoISF")
        consoleError.add("----------------------------------")
        return sens     // nothing changed
    }

    fun interpolate(xdata: Double, type: String): Double {   // interpolate ISF behaviour based on polygons defining nonlinear functions defined by value pairs for ...
        val polyX: Array<Double>
        val polyY: Array<Double>
        if (type == "bg") {
            //  ...         <----------------------  glucose  ---------------------->
            polyX = arrayOf(50.0, 60.0, 80.0, 90.0, 100.0, 110.0, 150.0, 180.0, 200.0)
            polyY = arrayOf(-0.5, -0.5, -0.3, -0.2, 0.0, 0.0, 0.5, 0.7, 0.7)
        } else {
            //  ...         <-------  delta  -------->
            polyX = arrayOf(2.0, 7.0, 12.0, 16.0, 20.0)
            polyY = arrayOf(0.0, 0.0, 0.4, 0.7, 0.7)
        }
        val polymax: Int = polyX.size - 1
        var step = polyX[0]
        var sVal = polyY[0]
        var stepT = polyX[polymax]
        var sValold = polyY[polymax]

        var newVal = 1.0
        var lowVal = 1.0
        val topVal: Double
        val lowX: Double
        val topX: Double
        val myX: Double
        var lowLabl = step

        if (step > xdata) {
            // extrapolate backwards
            stepT = polyX[1]
            sValold = polyY[1]
            lowVal = sVal
            topVal = sValold
            lowX = step
            topX = stepT
            myX = xdata
            newVal = lowVal + (topVal - lowVal) / (topX - lowX) * (myX - lowX)
        } else if (stepT < xdata) {
            // extrapolate forwards
            step = polyX[polymax - 1]
            sVal = polyY[polymax - 1]
            lowVal = sVal
            topVal = sValold
            lowX = step
            topX = stepT
            myX = xdata
            newVal = lowVal + (topVal - lowVal) / (topX - lowX) * (myX - lowX)
        } else {
            // interpolate
            for (i: Int in 0..polymax) {
                step = polyX[i]
                sVal = polyY[i]
                if (step == xdata) {
                    newVal = sVal
                    break
                } else if (step > xdata) {
                    topVal = sVal
                    lowX = lowLabl
                    myX = xdata
                    topX = step
                    newVal = lowVal + (topVal - lowVal) / (topX - lowX) * (myX - lowX)
                    break
                }
                lowVal = sVal
                lowLabl = step
            }
        }
        newVal = if (type == "delta") {
            newVal * delta_ISFrange_weight
        }      // delta range
        else if (xdata > 100) {
            newVal * higher_ISFrange_weight
        }     // higher BG range
        else {
            newVal * lower_ISFrange_weight
        }      // lower BG range
        return newVal
    }

    fun withinISFlimits(
        liftISF: Double, minISFReduction: Double, maxISFReduction: Double, sensitivityRatio: Double, origin_sens: String, temptargetSet: Boolean,
        high_temptarget_raises_sensitivity: Boolean, target_bg: Double, normalTarget: Int
    ): Double {
        var liftISFlimited: Double = liftISF
        if (liftISF < minISFReduction) {
            consoleError.add("weakest autoISF factor ${round(liftISF, 2)} limited by autoISF_min $minISFReduction")
            liftISFlimited = minISFReduction
        } else if (liftISF > maxISFReduction) {
            consoleError.add("strongest autoISF factor ${round(liftISF, 2)} limited by autoISF_max $maxISFReduction")
            liftISFlimited = maxISFReduction
        }
        val finalISF: Double
        var origin_sens_final = origin_sens
        if (high_temptarget_raises_sensitivity && temptargetSet && target_bg > normalTarget) {
            finalISF = liftISFlimited * sensitivityRatio
            origin_sens_final = " including exercise mode impact"
        } else if (liftISFlimited >= 1) {
            finalISF = max(liftISFlimited, sensitivityRatio)
        } else {
            finalISF = min(liftISFlimited, sensitivityRatio)
        }
        consoleError.add("final ISF factor is ${round(finalISF, 2)}" + origin_sens_final)
        consoleError.add("----------------------------------")
        consoleError.add("end autoISF")
        consoleError.add("----------------------------------")
        return finalISF
    }

    fun loop_smb(microBolusAllowed: Boolean, profile: OapsProfile, iob_data_iob: Double, iobTH_reduction_ratio: Double): String {
        if (!microBolusAllowed) {
            return "AAPS"                                                 // see message in enable_smb
        }
        if (profile.temptargetSet && enableSMB_EvenOn_OddOff || profile.min_bg == profile.max_bg && enableSMB_EvenOn_OddOff_always && !profile.temptargetSet) {
            val target = convert_bg(profile.target_bg)
            val msgType: String
            val evenTarget: Boolean
            val msgUnits: String
            val msgTail: String
            val msgEven: String
            val msg: String
            msgType = if (profile.temptargetSet) {
                "TempTarget"
            } else {
                "profile target"
            }
            if (profile.out_units == "mmol/L") {
                evenTarget = (target.toDouble() * 10.0).toInt() % 2 == 0
                msgUnits = "has "
                msgTail = "decimal"
            } else {
                evenTarget = target.toInt() % 2 == 0
                msgUnits = "is "
                msgTail = "number"
            }
            msgEven = if (evenTarget) {
                "even "
            } else {
                "odd "
            }
            val iobTHeffective = iobThresholdPercent
            if (!evenTarget) {
                consoleError.add("SMB disabled; $msgType $target $msgUnits $msgEven $msgTail")
                consoleError.add("Loop at minimum power")
                return "blocked"
            } else if (profile.max_iob == 0.0) {
                consoleError.add("SMB disabled because of max_iob=0")
                return "blocked"
            } else if (iobTHeffective / 100.0 < iob_data_iob / (profile.max_iob * iobTH_reduction_ratio)) {
                if (iobTH_reduction_ratio != 1.0) {
                    consoleError.add("Full Loop modified max_iob ${profile.max_iob} to effectively ${round(profile.max_iob * iobTH_reduction_ratio, 2)} due to profile % and/or exercise mode")
                    msg = "effective maxIOB ${round(profile.max_iob * iobTH_reduction_ratio, 2)}"
                } else {
                    msg = "maxIOB ${profile.max_iob}"
                }
                consoleError.add("SMB disabled by Full Loop logic: iob ${iob_data_iob} is more than $iobTHeffective% of $msg")
                consoleError.add("Full Loop capped");
                return "iobTH";
            } else {
                consoleError.add("SMB enabled; $msgType $target $msgUnits $msgEven $msgTail")
                if (profile.target_bg < 100) {     // indirect assessment; later set it in GUI
                    consoleError.add("Loop at full power")
                    return "fullLoop"                                      // even number
                } else {
                    consoleError.add("Loop at medium power")
                    return "enforced"                                      // even number
                }
            }
        }
        consoleError.add("Full Loop disabled")
        return "AAPS"                                                      // leave it to standard AAPS
    }

    fun determine_varSMBratio(profile: OapsProfile, bg: Int, target_bg: Double, loop_wanted_smb: String): Double {   // let SMB delivery ratio increase from min to max depending on how much bg exceeds target
        var smb_delivery_ratio_bg_range = smb_delivery_ratio_bg_range
        if (smb_delivery_ratio_bg_range < 10) {
            smb_delivery_ratio_bg_range = smb_delivery_ratio_bg_range * 18
        }  // was in mmol/l
        var fix_SMB: Double = smb_delivery_ratio
        var lower_SMB = min(smb_delivery_ratio_min, smb_delivery_ratio_max)
        var higher_SMB = max(smb_delivery_ratio_min, smb_delivery_ratio_max)
        var higher_bg = target_bg + smb_delivery_ratio_bg_range
        var new_SMB: Double = fix_SMB
        if (smb_delivery_ratio_bg_range > 0) {
            new_SMB = lower_SMB + (higher_SMB - lower_SMB) * (bg - target_bg) / smb_delivery_ratio_bg_range
            new_SMB = max(lower_SMB, min(higher_SMB, new_SMB))   // cap if outside target_bg--higher_bg
        }
        if (loop_wanted_smb == "fullLoop") {                                // go for max impactError.add("SMB delivery ratio set to ${max(fix_SMB, new_SMB)} as max of fixed and interpolated values")
            return max(fix_SMB, new_SMB)
        }

        if (smb_delivery_ratio_bg_range == 0.0) {                     // deactivated in SMB extended menu
            consoleError.add("SMB delivery ratio set to fixed value $fix_SMB")
            return fix_SMB
        }
        if (bg <= target_bg) {
            consoleError.add("SMB delivery ratio limited by minimum value $lower_SMB")
            return lower_SMB
        }
        if (bg >= higher_bg) {
            consoleError.add("SMB delivery ratio limited by maximum value $higher_SMB")
            return higher_SMB
        }
        consoleError.add("SMB delivery ratio set to interpolated value $new_SMB")
        return new_SMB
    }

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null &&
            requiredKey != "absorption_smb_advanced" &&
            requiredKey != "auto_isf_settings" &&
            requiredKey != "acce_ISF_settings" &&
            requiredKey != "bg_ISF_settings" &&
            requiredKey != "pp_ISF_settings" &&
            requiredKey != "delta_ISF_settings" &&
            requiredKey != "dura_ISF_settings" &&
            requiredKey != "smb_delivery_settings" &&
            requiredKey != "full_loop_settings"
            ) return
        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "openapsautoisf_settings"
            title = rh.gs(R.string.openaps_auto_isf)
            initialExpandedChildrenCount = 0
            addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ApsMaxBasal, dialogMessage = R.string.openapsma_max_basal_summary, title = R.string.openapsma_max_basal_title))
            addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ApsSmbMaxIob, dialogMessage = R.string.openapssmb_max_iob_summary, title = R.string.openapssmb_max_iob_title))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsUseAutoIsfWeights, summary = R.string.autoISF_settings_summary, title = R.string.autoISF_settings_title))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsUseAutosens, title = R.string.openapsama_use_autosens))
            //addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.ApsDynIsfAdjustmentFactor, dialogMessage = R.string.dyn_isf_adjust_summary, title = R.string.dyn_isf_adjust_title))
            addPreference(AdaptiveUnitPreference(ctx = context, unitKey = UnitDoubleKey.ApsLgsThreshold, dialogMessage = R.string.lgs_threshold_summary, title = R.string.lgs_threshold_title))
            //addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsDynIsfAdjustSensitivity, summary = R.string.dynisf_adjust_sensitivity_summary, title = R.string.dynisf_adjust_sensitivity))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsSensitivityRaisesTarget, summary = R.string.sensitivity_raises_target_summary, title = R.string.sensitivity_raises_target_title))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsResistanceLowersTarget, summary = R.string.resistance_lowers_target_summary, title = R.string.resistance_lowers_target_title))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsUseSmb, summary = R.string.enable_smb_summary, title = R.string.enable_smb))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsUseSmbWithHighTt, summary = R.string.enable_smb_with_high_temp_target_summary, title = R.string.enable_smb_with_high_temp_target))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsUseSmbAlways, summary = R.string.enable_smb_always_summary, title = R.string.enable_smb_always))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsUseSmbWithCob, summary = R.string.enable_smb_with_cob_summary, title = R.string.enable_smb_with_cob))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsUseSmbWithLowTt, summary = R.string.enable_smb_with_temp_target_summary, title = R.string.enable_smb_with_temp_target))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsUseSmbAfterCarbs, summary = R.string.enable_smb_after_carbs_summary, title = R.string.enable_smb_after_carbs))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsUseUam, summary = R.string.enable_uam_summary, title = R.string.enable_uam))
            addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.ApsMaxSmbFrequency, title = R.string.smb_interval_summary))
            addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.ApsMaxMinutesOfBasalToLimitSmb, title = R.string.smb_max_minutes_summary))
            addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.ApsUamMaxMinutesOfBasalToLimitSmb, dialogMessage = R.string.uam_smb_max_minutes, title = R.string.uam_smb_max_minutes_summary))
            addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.ApsCarbsRequestThreshold, dialogMessage = R.string.carbs_req_threshold_summary, title = R.string.carbs_req_threshold))
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "absorption_smb_advanced"
                title = rh.gs(app.aaps.core.ui.R.string.advanced_settings_title)
                addPreference(
                    AdaptiveIntentPreference(
                        ctx = context,
                        intentKey = IntentKey.ApsLinkToDocs,
                        intent = Intent().apply { action = Intent.ACTION_VIEW; data = Uri.parse(rh.gs(R.string.openapsama_link_to_preference_json_doc)) },
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
            })
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "auto_isf_settings"
                title = rh.gs(R.string.autoISF_settings_title)
                summary = rh.gs(R.string.autoISF_settings_summary)
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsUseAutoIsfWeights, summary = R.string.openapsama_enable_autoISF, title = R.string.openapsama_enable_autoISF))
                addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ApsAutoIsfMin, dialogMessage = R.string.openapsama_autoISF_min_summary, title = R.string.openapsama_autoISF_min))
                addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ApsAutoIsfMax, dialogMessage = R.string.openapsama_autoISF_max_summary, title = R.string.openapsama_autoISF_max))
                addPreference(preferenceManager.createPreferenceScreen(context).apply {
                    key = "acce_ISF_settings"
                    title = rh.gs(R.string.acce_ISF_settings_title)
                    summary = rh.gs(R.string.acce_ISF_settings_summary)
                    addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ApsAutoIsfBgAccelWeight, dialogMessage = R.string.openapsama_bgAccel_ISF_weight_summary, title = R.string.openapsama_bgAccel_ISF_weight))
                    addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ApsAutoIsfBgBrakeWeight, dialogMessage = R.string.openapsama_bgBrake_ISF_weight_summary, title = R.string.openapsama_bgBrake_ISF_weight))
                })
                addPreference(preferenceManager.createPreferenceScreen(context).apply {
                    key = "bg_ISF_settings"
                    title = rh.gs(R.string.bg_ISF_settings_title)
                    summary = rh.gs(R.string.bg_ISF_settings_summary)
                    addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ApsAutoIsfLowBgWeight, dialogMessage = R.string.openapsama_lower_ISFrange_weight_summary, title = R.string.openapsama_lower_ISFrange_weight))
                    addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ApsAutoIsfHighBgWeight, dialogMessage = R.string.openapsama_higher_ISFrange_weight_summary, title = R.string.openapsama_higher_ISFrange_weight))
                })
                addPreference(preferenceManager.createPreferenceScreen(context).apply {
                    key = "pp_ISF_settings"
                    title = rh.gs(R.string.pp_ISF_settings_title)
                    summary = rh.gs(R.string.pp_ISF_settings_summary)
                    addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsAutoIsfPpAlways, summary = R.string.enable_postprandial_ISF_always_summary, title = R.string.enable_postprandial_ISF_always))
                    addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.ApsAutoIsfPpIsfHours, dialogMessage = R.string.openapsama_pp_ISF_hours_summary, title = R.string.openapsama_pp_ISF_hours))
                    addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ApsAutoIsfPpWeight, dialogMessage = R.string.openapsama_pp_ISF_weight_summary, title = R.string.openapsama_pp_ISF_weight))
                })
                addPreference(preferenceManager.createPreferenceScreen(context).apply {
                    key = "delta_ISF_settings"
                    title = rh.gs(R.string.delta_ISF_settings_title)
                    summary = rh.gs(R.string.delta_ISF_settings_summary)
                    addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ApsAutoIsfDeltaWeight, dialogMessage = R.string.openapsama_delta_ISFrange_weight_summary, title = R.string.openapsama_delta_ISFrange_weight))
                })
                addPreference(preferenceManager.createPreferenceScreen(context).apply {
                    key = "dura_ISF_settings"
                    title = rh.gs(R.string.dura_ISF_settings_title)
                    summary = rh.gs(R.string.dura_ISF_settings_summary)
                    addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsAutoIsfDuraAfterCarbs, summary = R.string.enableautoISFwithcob_summary, title = R.string.enableautoISFwithcob))
                    addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ApsAutoIsfDuraWeight, dialogMessage = R.string.openapsama_dura_ISF_weight_summary, title = R.string.openapsama_dura_ISF_weight))
                })
                addPreference(preferenceManager.createPreferenceScreen(context).apply {
                    key = "smb_delivery_settings"
                    title = rh.gs(R.string.smb_delivery_settings_title)
                    summary = rh.gs(R.string.smb_delivery_settings_summary)
                    addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ApsAutoIsfSmbDeliveryRatio, dialogMessage = R.string.openapsama_smb_delivery_ratio_summary, title = R.string.openapsama_smb_delivery_ratio))
                    addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ApsAutoIsfSmbDeliveryRatioMin, dialogMessage = R.string.openapsama_smb_delivery_ratio_min_summary, title = R.string.openapsama_smb_delivery_ratio_min))
                    addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ApsAutoIsfSmbDeliveryRatioMax, dialogMessage = R.string.openapsama_smb_delivery_ratio_max_summary, title = R.string.openapsama_smb_delivery_ratio_max))
                    addPreference(
                        AdaptiveUnitPreference(
                            ctx = context,
                            unitKey = UnitDoubleKey.ApsAutoIsfSmbDeliveryRatioBgRange,
                            dialogMessage = R.string.openapsama_smb_delivery_ratio_bg_range_summary,
                            title = R.string.openapsama_smb_delivery_ratio_bg_range
                        )
                    )
                    addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.ApsAutoIsfSmbMaxRangeExtension, dialogMessage = R.string.openapsama_smb_max_range_extension_summary, title = R.string.openapsama_smb_max_range_extension))
                    addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsAutoIsfSmbOnEvenTt, summary = R.string.enableSMB_EvenOn_OddOff_summary, title = R.string.enableSMB_EvenOn_OddOff))
                    addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.ApsAutoIsfSmbOnEvenPt, summary = R.string.enableSMB_EvenOn_OddOff_always_summary, title = R.string.enableSMB_EvenOn_OddOff_always))
                })
                addPreference(preferenceManager.createPreferenceScreen(context).apply {
                    key = "full_loop_settings"
                    title = rh.gs(R.string.full_loop_settings_title)
                    summary = rh.gs(R.string.full_loop_settings_summary)
                    addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.ApsAutoIsfIobThPercent, dialogMessage = R.string.openapsama_iob_threshold_percent_summary, title = R.string.openapsama_iob_threshold_percent))
                })
            })
        }
    }
}