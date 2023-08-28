package info.nightscout.plugins.aps.openAPSSMB

import android.content.Context
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.core.extensions.target
import info.nightscout.core.utils.MidnightUtils
import info.nightscout.database.ValueWrapper
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.aps.APS
import info.nightscout.interfaces.aps.AutosensResult
import info.nightscout.interfaces.aps.DetermineBasalAdapter
import info.nightscout.interfaces.aps.SMBDefaults
import info.nightscout.interfaces.bgQualityCheck.BgQualityCheck
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.iob.GlucoseStatusProvider
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.profiling.Profiler
import info.nightscout.interfaces.stats.TddCalculator
import info.nightscout.interfaces.utils.HardLimits
import info.nightscout.interfaces.utils.Round
import info.nightscout.plugins.aps.R
import info.nightscout.plugins.aps.utils.ScriptReader
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor

@OpenForTesting
@Singleton
class OpenAPSSMBPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val constraintChecker: Constraints,
    rh: ResourceHelper,
    private val profileFunction: ProfileFunction,
    val context: Context,
    private val activePlugin: ActivePlugin,
    private val iobCobCalculator: IobCobCalculator,
    private val hardLimits: HardLimits,
    private val profiler: Profiler,
    private val sp: SP,
    private val dateUtil: DateUtil,
    private val repository: AppRepository,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val bgQualityCheck: BgQualityCheck,
    private val tddCalculator: TddCalculator
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.APS)
        .fragmentClass(info.nightscout.plugins.aps.OpenAPSFragment::class.java.name)
        .pluginIcon(info.nightscout.core.ui.R.drawable.ic_generic_icon)
        .pluginName(R.string.openapssmb)
        .shortName(info.nightscout.core.ui.R.string.smb_shortname)
        .preferencesId(R.xml.pref_openapssmb)
        .description(R.string.description_smb)
        .setDefault(),
    aapsLogger, rh, injector
), APS, Constraints {

    // DynamicISF specific
    var tdd1D : Double? = null
    var tdd7D : Double? = null
    var tddLast24H : Double? = null
    var tddLast4H : Double? = null
    var tddLast8to4H : Double? = null
    var dynIsfEnabled : Constraint<Boolean> = Constraint(false)

    // last values
    override var lastAPSRun: Long = 0
    override var lastAPSResult: DetermineBasalResultSMB? = null
    override var lastDetermineBasalAdapter: DetermineBasalAdapter? = null
    override var lastAutosensResult = AutosensResult()

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
        val smbAlwaysEnabled = sp.getBoolean(R.string.key_enableSMB_always, false)
        preferenceFragment.findPreference<SwitchPreference>(rh.gs(R.string.key_enableSMB_with_COB))?.isVisible = !smbAlwaysEnabled
        preferenceFragment.findPreference<SwitchPreference>(rh.gs(R.string.key_enableSMB_with_temptarget))?.isVisible = !smbAlwaysEnabled
        preferenceFragment.findPreference<SwitchPreference>(rh.gs(R.string.key_enableSMB_after_carbs))?.isVisible = !smbAlwaysEnabled
    }

    override fun invoke(initiator: String, tempBasalFallback: Boolean) {
        aapsLogger.debug(LTag.APS, "invoke from $initiator tempBasalFallback: $tempBasalFallback")
        lastAPSResult = null
        val glucoseStatus = glucoseStatusProvider.glucoseStatusData
        val profile = profileFunction.getProfile()
        val pump = activePlugin.activePump
        if (profile == null) {
            rxBus.send(info.nightscout.plugins.aps.events.EventResetOpenAPSGui(rh.gs(info.nightscout.core.ui.R.string.no_profile_set)))
            aapsLogger.debug(LTag.APS, rh.gs(info.nightscout.core.ui.R.string.no_profile_set))
            return
        }
        if (!isEnabled()) {
            rxBus.send(info.nightscout.plugins.aps.events.EventResetOpenAPSGui(rh.gs(R.string.openapsma_disabled)))
            aapsLogger.debug(LTag.APS, rh.gs(R.string.openapsma_disabled))
            return
        }
        if (glucoseStatus == null) {
            rxBus.send(info.nightscout.plugins.aps.events.EventResetOpenAPSGui(rh.gs(R.string.openapsma_no_glucose_data)))
            aapsLogger.debug(LTag.APS, rh.gs(R.string.openapsma_no_glucose_data))
            return
        }

        val inputConstraints = Constraint(0.0) // fake. only for collecting all results
        val maxBasal = constraintChecker.getMaxBasalAllowed(profile).also {
            inputConstraints.copyReasons(it)
        }.value()
        var start = System.currentTimeMillis()
        var startPart = System.currentTimeMillis()
        profiler.log(LTag.APS, "getMealData()", startPart)
        val maxIob = constraintChecker.getMaxIOBAllowed().also { maxIOBAllowedConstraint ->
            inputConstraints.copyReasons(maxIOBAllowedConstraint)
        }.value()

        var minBg = hardLimits.verifyHardLimits(Round.roundTo(profile.getTargetLowMgdl(), 0.1), info.nightscout.core.ui.R.string.profile_low_target, HardLimits.VERY_HARD_LIMIT_MIN_BG[0], HardLimits.VERY_HARD_LIMIT_MIN_BG[1])
        var maxBg =
            hardLimits.verifyHardLimits(Round.roundTo(profile.getTargetHighMgdl(), 0.1), info.nightscout.core.ui.R.string.profile_high_target, HardLimits.VERY_HARD_LIMIT_MAX_BG[0], HardLimits.VERY_HARD_LIMIT_MAX_BG[1])
        var targetBg = hardLimits.verifyHardLimits(profile.getTargetMgdl(), info.nightscout.core.ui.R.string.temp_target_value, HardLimits.VERY_HARD_LIMIT_TARGET_BG[0], HardLimits.VERY_HARD_LIMIT_TARGET_BG[1])
        var isTempTarget = false
        val tempTarget = repository.getTemporaryTargetActiveAt(dateUtil.now()).blockingGet()
        if (tempTarget is ValueWrapper.Existing) {
            isTempTarget = true
            minBg =
                hardLimits.verifyHardLimits(
                    tempTarget.value.lowTarget,
                    info.nightscout.core.ui.R.string.temp_target_low_target,
                    HardLimits.VERY_HARD_LIMIT_TEMP_MIN_BG[0].toDouble(),
                    HardLimits.VERY_HARD_LIMIT_TEMP_MIN_BG[1].toDouble()
                )
            maxBg =
                hardLimits.verifyHardLimits(
                    tempTarget.value.highTarget,
                    info.nightscout.core.ui.R.string.temp_target_high_target,
                    HardLimits.VERY_HARD_LIMIT_TEMP_MAX_BG[0].toDouble(),
                    HardLimits.VERY_HARD_LIMIT_TEMP_MAX_BG[1].toDouble()
                )
            targetBg =
                hardLimits.verifyHardLimits(
                    tempTarget.value.target(),
                    info.nightscout.core.ui.R.string.temp_target_value,
                    HardLimits.VERY_HARD_LIMIT_TEMP_TARGET_BG[0].toDouble(),
                    HardLimits.VERY_HARD_LIMIT_TEMP_TARGET_BG[1].toDouble()
                )
        }
        if (!hardLimits.checkHardLimits(profile.dia, info.nightscout.core.ui.R.string.profile_dia, hardLimits.minDia(), hardLimits.maxDia())) return
        if (!hardLimits.checkHardLimits(profile.getIcTimeFromMidnight(MidnightUtils.secondsFromMidnight()), info.nightscout.core.ui.R.string.profile_carbs_ratio_value, hardLimits.minIC(), hardLimits.maxIC())) return
        if (!hardLimits.checkHardLimits(profile.getIsfMgdl(), info.nightscout.core.ui.R.string.profile_sensitivity_value, HardLimits.MIN_ISF, HardLimits.MAX_ISF)) return
        if (!hardLimits.checkHardLimits(profile.getMaxDailyBasal(), info.nightscout.core.ui.R.string.profile_max_daily_basal_value, 0.02, hardLimits.maxBasal())) return
        if (!hardLimits.checkHardLimits(pump.baseBasalRate, info.nightscout.core.ui.R.string.current_basal_value, 0.01, hardLimits.maxBasal())) return
        startPart = System.currentTimeMillis()
        if (constraintChecker.isAutosensModeEnabled().value()) {
            val autosensData = iobCobCalculator.getLastAutosensDataWithWaitForCalculationFinish("OpenAPSPlugin")
            if (autosensData == null) {
                rxBus.send(info.nightscout.plugins.aps.events.EventResetOpenAPSGui(rh.gs(R.string.openaps_no_as_data)))
                return
            }
            lastAutosensResult = autosensData.autosensResult
        } else {
            lastAutosensResult.sensResult = "autosens disabled"
        }
        val iobArray = iobCobCalculator.calculateIobArrayForSMB(lastAutosensResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, isTempTarget)
        profiler.log(LTag.APS, "calculateIobArrayInDia()", startPart)
        startPart = System.currentTimeMillis()
        val smbAllowed = Constraint(!tempBasalFallback).also {
            constraintChecker.isSMBModeEnabled(it)
            inputConstraints.copyReasons(it)
        }
        val advancedFiltering = Constraint(!tempBasalFallback).also {
            constraintChecker.isAdvancedFilteringEnabled(it)
            inputConstraints.copyReasons(it)
        }
        val uam = Constraint(true).also {
            constraintChecker.isUAMEnabled(it)
            inputConstraints.copyReasons(it)
        }
        dynIsfEnabled = Constraint(true).also {
            constraintChecker.isDynIsfModeEnabled(it)
            inputConstraints.copyReasons(it)
        }
        val flatBGsDetected = bgQualityCheck.state == BgQualityCheck.State.FLAT
        profiler.log(LTag.APS, "detectSensitivityAndCarbAbsorption()", startPart)
        profiler.log(LTag.APS, "SMB data gathering", start)
        start = System.currentTimeMillis()

        // DynamicISF specific
        // without these values DynISF doesn't work properly
        // Current implementation is fallback to SMB if TDD history is not available. Thus calculated here
        tdd1D = tddCalculator.averageTDD(tddCalculator.calculate(1, allowMissingDays = false))?.totalAmount
        tdd7D = tddCalculator.averageTDD(tddCalculator.calculate(7, allowMissingDays = false))?.totalAmount
        tddLast24H = tddCalculator.calculateDaily(-24, 0)?.totalAmount
        tddLast4H = tddCalculator.calculateDaily(-4, 0)?.totalAmount
        tddLast8to4H = tddCalculator.calculateDaily(-8, -4)?.totalAmount

        provideDetermineBasalAdapter().also { determineBasalAdapterSMBJS ->
            determineBasalAdapterSMBJS.setData(
                profile, maxIob, maxBasal, minBg, maxBg, targetBg,
                activePlugin.activePump.baseBasalRate,
                iobArray,
                glucoseStatus,
                iobCobCalculator.getMealDataWithWaitingForCalculationFinish(),
                lastAutosensResult.ratio,
                isTempTarget,
                smbAllowed.value(),
                uam.value(),
                advancedFiltering.value(),
                flatBGsDetected,
                tdd1D = tdd1D,
                tdd7D = tdd7D,
                tddLast24H = tddLast24H,
                tddLast4H = tddLast4H,
                tddLast8to4H = tddLast8to4H
            )
            val now = System.currentTimeMillis()
            val determineBasalResultSMB = determineBasalAdapterSMBJS.invoke()
            profiler.log(LTag.APS, "SMB calculation", start)
            if (determineBasalResultSMB == null) {
                aapsLogger.error(LTag.APS, "SMB calculation returned null")
                lastDetermineBasalAdapter = null
                lastAPSResult = null
                lastAPSRun = 0
            } else {
                // TODO still needed with oref1?
                // Fix bug determine basal
                if (determineBasalResultSMB.rate == 0.0 && determineBasalResultSMB.duration == 0 && iobCobCalculator.getTempBasalIncludingConvertedExtended(dateUtil.now()) == null) determineBasalResultSMB.isTempBasalRequested =
                    false
                determineBasalResultSMB.iob = iobArray[0]
                determineBasalResultSMB.json?.put("timestamp", dateUtil.toISOString(now))
                determineBasalResultSMB.inputConstraints = inputConstraints
                lastDetermineBasalAdapter = determineBasalAdapterSMBJS
                lastAPSResult = determineBasalResultSMB as DetermineBasalResultSMB
                lastAPSRun = now
            }
        }
        rxBus.send(info.nightscout.plugins.aps.events.EventOpenAPSUpdateGui())
    }

    override fun isSuperBolusEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        value.set(aapsLogger, false)
        return value
    }

    override fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> {
        if (isEnabled()) {
            val maxIobPref: Double = sp.getDouble(R.string.key_openapssmb_max_iob, 3.0)
            maxIob.setIfSmaller(aapsLogger, maxIobPref, rh.gs(R.string.limiting_iob, maxIobPref, rh.gs(R.string.maxvalueinpreferences)), this)
            maxIob.setIfSmaller(aapsLogger, hardLimits.maxIobSMB(), rh.gs(R.string.limiting_iob, hardLimits.maxIobSMB(), rh.gs(R.string.hardlimit)), this)
        }
        return maxIob
    }

    override fun applyBasalConstraints(absoluteRate: Constraint<Double>, profile: Profile): Constraint<Double> {
        if (isEnabled()) {
            var maxBasal = sp.getDouble(R.string.key_openapsma_max_basal, 1.0)
            if (maxBasal < profile.getMaxDailyBasal()) {
                maxBasal = profile.getMaxDailyBasal()
                absoluteRate.addReason(rh.gs(R.string.increasing_max_basal), this)
            }
            absoluteRate.setIfSmaller(aapsLogger, maxBasal, rh.gs(info.nightscout.core.ui.R.string.limitingbasalratio, maxBasal, rh.gs(R.string.maxvalueinpreferences)), this)

            // Check percentRate but absolute rate too, because we know real current basal in pump
            val maxBasalMultiplier = sp.getDouble(R.string.key_openapsama_current_basal_safety_multiplier, 4.0)
            val maxFromBasalMultiplier = floor(maxBasalMultiplier * profile.getBasal() * 100) / 100
            absoluteRate.setIfSmaller(aapsLogger, maxFromBasalMultiplier, rh.gs(info.nightscout.core.ui.R.string.limitingbasalratio, maxFromBasalMultiplier, rh.gs(R.string.max_basal_multiplier)), this)
            val maxBasalFromDaily = sp.getDouble(R.string.key_openapsama_max_daily_safety_multiplier, 3.0)
            val maxFromDaily = floor(profile.getMaxDailyBasal() * maxBasalFromDaily * 100) / 100
            absoluteRate.setIfSmaller(aapsLogger, maxFromDaily, rh.gs(info.nightscout.core.ui.R.string.limitingbasalratio, maxFromDaily, rh.gs(R.string.max_daily_basal_multiplier)), this)
        }
        return absoluteRate
    }

    override fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val enabled = sp.getBoolean(R.string.key_use_smb, false)
        if (!enabled) value.set(aapsLogger, false, rh.gs(R.string.smb_disabled_in_preferences), this)
        return value
    }

    override fun isUAMEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val enabled = sp.getBoolean(R.string.key_use_uam, false)
        if (!enabled) value.set(aapsLogger, false, rh.gs(R.string.uam_disabled_in_preferences), this)
        return value
    }

    override fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val enabled = sp.getBoolean(R.string.key_openapsama_use_autosens, false)
        if (!enabled) value.set(aapsLogger, false, rh.gs(R.string.autosens_disabled_in_preferences), this)
        return value
    }

    fun provideDetermineBasalAdapter(): DetermineBasalAdapter = DetermineBasalAdapterSMBJS(ScriptReader(context), injector)
}