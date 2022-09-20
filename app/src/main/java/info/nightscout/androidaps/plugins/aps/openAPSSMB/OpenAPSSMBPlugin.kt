package info.nightscout.androidaps.plugins.aps.openAPSSMB

import android.content.Context
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.extensions.target
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.plugins.aps.events.EventOpenAPSUpdateGui
import info.nightscout.androidaps.plugins.aps.events.EventOpenAPSUpdateResultGui
import info.nightscout.androidaps.plugins.aps.loop.ScriptReader
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatusProvider
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.HardLimits
import info.nightscout.androidaps.utils.Profiler
import info.nightscout.androidaps.utils.Round
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@OpenForTesting
@Singleton
class OpenAPSSMBPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val constraintChecker: ConstraintChecker,
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
    private val glucoseStatusProvider: GlucoseStatusProvider
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.APS)
        .fragmentClass(OpenAPSSMBFragment::class.java.name)
        .pluginIcon(R.drawable.ic_generic_icon)
        .pluginName(R.string.openapssmb)
        .shortName(R.string.smb_shortname)
        .preferencesId(R.xml.pref_openapssmb)
        .description(R.string.description_smb)
        .setDefault(),
    aapsLogger, rh, injector
), APS, Constraints {

    // last values
    override var lastAPSRun: Long = 0
    override var lastAPSResult: DetermineBasalResultSMB? = null
    override var lastDetermineBasalAdapter: DetermineBasalAdapterInterface? = null
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
            rxBus.send(EventOpenAPSUpdateResultGui(rh.gs(R.string.noprofileset)))
            aapsLogger.debug(LTag.APS, rh.gs(R.string.noprofileset))
            return
        }
        if (!isEnabled()) {
            rxBus.send(EventOpenAPSUpdateResultGui(rh.gs(R.string.openapsma_disabled)))
            aapsLogger.debug(LTag.APS, rh.gs(R.string.openapsma_disabled))
            return
        }
        if (glucoseStatus == null) {
            rxBus.send(EventOpenAPSUpdateResultGui(rh.gs(R.string.openapsma_noglucosedata)))
            aapsLogger.debug(LTag.APS, rh.gs(R.string.openapsma_noglucosedata))
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

        var minBg = hardLimits.verifyHardLimits(Round.roundTo(profile.getTargetLowMgdl(), 0.1), R.string.profile_low_target, HardLimits.VERY_HARD_LIMIT_MIN_BG[0], HardLimits.VERY_HARD_LIMIT_MIN_BG[1])
        var maxBg =
            hardLimits.verifyHardLimits(Round.roundTo(profile.getTargetHighMgdl(), 0.1), R.string.profile_high_target, HardLimits.VERY_HARD_LIMIT_MAX_BG[0], HardLimits.VERY_HARD_LIMIT_MAX_BG[1])
        var targetBg = hardLimits.verifyHardLimits(profile.getTargetMgdl(), R.string.temp_target_value, HardLimits.VERY_HARD_LIMIT_TARGET_BG[0], HardLimits.VERY_HARD_LIMIT_TARGET_BG[1])
        var isTempTarget = false
        val tempTarget = repository.getTemporaryTargetActiveAt(dateUtil.now()).blockingGet()
        if (tempTarget is ValueWrapper.Existing) {
            isTempTarget = true
            minBg =
                hardLimits.verifyHardLimits(
                    tempTarget.value.lowTarget,
                    R.string.temp_target_low_target,
                    HardLimits.VERY_HARD_LIMIT_TEMP_MIN_BG[0].toDouble(),
                    HardLimits.VERY_HARD_LIMIT_TEMP_MIN_BG[1].toDouble()
                )
            maxBg =
                hardLimits.verifyHardLimits(
                    tempTarget.value.highTarget,
                    R.string.temp_target_high_target,
                    HardLimits.VERY_HARD_LIMIT_TEMP_MAX_BG[0].toDouble(),
                    HardLimits.VERY_HARD_LIMIT_TEMP_MAX_BG[1].toDouble()
                )
            targetBg =
                hardLimits.verifyHardLimits(
                    tempTarget.value.target(),
                    R.string.temp_target_value,
                    HardLimits.VERY_HARD_LIMIT_TEMP_TARGET_BG[0].toDouble(),
                    HardLimits.VERY_HARD_LIMIT_TEMP_TARGET_BG[1].toDouble()
                )
        }
        if (!hardLimits.checkHardLimits(profile.dia, R.string.profile_dia, hardLimits.minDia(), hardLimits.maxDia())) return
        if (!hardLimits.checkHardLimits(profile.getIcTimeFromMidnight(Profile.secondsFromMidnight()), R.string.profile_carbs_ratio_value, hardLimits.minIC(), hardLimits.maxIC())) return
        if (!hardLimits.checkHardLimits(profile.getIsfMgdl(), R.string.profile_sensitivity_value, HardLimits.MIN_ISF, HardLimits.MAX_ISF)) return
        if (!hardLimits.checkHardLimits(profile.getMaxDailyBasal(), R.string.profile_max_daily_basal_value, 0.02, hardLimits.maxBasal())) return
        if (!hardLimits.checkHardLimits(pump.baseBasalRate, R.string.current_basal_value, 0.01, hardLimits.maxBasal())) return
        startPart = System.currentTimeMillis()
        if (constraintChecker.isAutosensModeEnabled().value()) {
            val autosensData = iobCobCalculator.getLastAutosensDataWithWaitForCalculationFinish("OpenAPSPlugin")
            if (autosensData == null) {
                rxBus.send(EventOpenAPSUpdateResultGui(rh.gs(R.string.openaps_noasdata)))
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
        profiler.log(LTag.APS, "detectSensitivityAndCarbAbsorption()", startPart)
        profiler.log(LTag.APS, "SMB data gathering", start)
        start = System.currentTimeMillis()

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
                activePlugin.activeBgSource.javaClass.simpleName == "DexcomPlugin"
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
                if (determineBasalResultSMB.rate == 0.0 && determineBasalResultSMB.duration == 0 && iobCobCalculator.getTempBasalIncludingConvertedExtended(dateUtil.now()) == null) determineBasalResultSMB.tempBasalRequested =
                    false
                determineBasalResultSMB.iob = iobArray[0]
                determineBasalResultSMB.json?.put("timestamp", dateUtil.toISOString(now))
                determineBasalResultSMB.inputConstraints = inputConstraints
                lastDetermineBasalAdapter = determineBasalAdapterSMBJS
                lastAPSResult = determineBasalResultSMB as DetermineBasalResultSMB
                lastAPSRun = now
            }
        }
        rxBus.send(EventOpenAPSUpdateGui())
    }

    override fun isSuperBolusEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        value.set(aapsLogger, false)
        return value
    }

    fun provideDetermineBasalAdapter(): DetermineBasalAdapterInterface = DetermineBasalAdapterSMBJS(ScriptReader(context), injector)
}