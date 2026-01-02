package app.aaps.plugins.aps.openAPSSMB

import android.content.Context
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import app.aaps.core.data.aps.SMBDefaults
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.DetermineBasalAdapter
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
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
import app.aaps.core.interfaces.profiling.Profiler
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAPSCalculationFinished
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.extensions.target
import app.aaps.core.utils.MidnightUtils
import app.aaps.plugins.aps.OpenAPSFragment
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.events.EventOpenAPSUpdateGui
import app.aaps.plugins.aps.events.EventResetOpenAPSGui
import app.aaps.plugins.aps.utils.ScriptReader
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor

@Singleton
open class TestOpenAPSSMBPlugin @Inject constructor(
    private val injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val constraintChecker: ConstraintsChecker,
    rh: ResourceHelper,
    private val profileFunction: ProfileFunction,
    val context: Context,
    private val activePlugin: ActivePlugin,
    private val iobCobCalculator: IobCobCalculator,
    private val processedTbrEbData: ProcessedTbrEbData,
    private val hardLimits: HardLimits,
    private val profiler: Profiler,
    private val preferences: Preferences,
    protected val dateUtil: DateUtil,
    private val persistenceLayer: PersistenceLayer,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val bgQualityCheck: BgQualityCheck,
    private val tddCalculator: TddCalculator,
    private val glucoseStatusCalculatorSMB: GlucoseStatusCalculatorSMB
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.APS)
        .fragmentClass(OpenAPSFragment::class.java.name)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_generic_icon)
        .pluginName(R.string.openapssmb)
        .shortName(app.aaps.core.ui.R.string.smb_shortname)
        .preferencesVisibleInSimpleMode(false)
        .description(R.string.description_smb)
        .setDefault(),
    aapsLogger, rh
), APS, PluginConstraints {

    // DynamicISF specific
    var tdd1D: Double? = null
    var tdd7D: Double? = null
    var tddLast24H: Double? = null
    var tddLast4H: Double? = null
    var tddLast8to4H: Double? = null
    var dynIsfEnabled: Constraint<Boolean> = ConstraintObject(false, aapsLogger)

    // last values
    override var lastAPSRun: Long = 0
    override val algorithm = APSResult.Algorithm.SMB
    override fun configuration(): JSONObject = JSONObject()
    override fun applyConfiguration(configuration: JSONObject) {
    }

    override var lastAPSResult: DetermineBasalResultSMBFromJS? = null

    //override var lastDetermineBasalAdapter: DetermineBasalAdapter? = null
    private var lastAutosensResult = AutosensResult()

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
        preferenceFragment.findPreference<SwitchPreference>(BooleanKey.ApsUseSmbWithCob.key)?.isVisible = !smbAlwaysEnabled || !advancedFiltering
        preferenceFragment.findPreference<SwitchPreference>(BooleanKey.ApsUseSmbWithLowTt.key)?.isVisible = !smbAlwaysEnabled || !advancedFiltering
        preferenceFragment.findPreference<SwitchPreference>(BooleanKey.ApsUseSmbAfterCarbs.key)?.isVisible = !smbAlwaysEnabled || !advancedFiltering
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
        val maxBasal = constraintChecker.getMaxBasalAllowed(profile).also {
            inputConstraints.copyReasons(it)
        }.value()
        var start = System.currentTimeMillis()
        var startPart = System.currentTimeMillis()
        profiler.log(LTag.APS, "getMealData()", startPart)
        val maxIob = constraintChecker.getMaxIOBAllowed().also { maxIOBAllowedConstraint ->
            inputConstraints.copyReasons(maxIOBAllowedConstraint)
        }.value()

        var minBg =
            hardLimits.verifyHardLimits(
                Round.roundTo(profile.getTargetLowMgdl(), 0.1),
                app.aaps.core.ui.R.string.profile_low_target,
                HardLimits.LIMIT_MIN_BG[0],
                HardLimits.LIMIT_MIN_BG[1]
            )
        var maxBg =
            hardLimits.verifyHardLimits(
                Round.roundTo(profile.getTargetHighMgdl(), 0.1),
                app.aaps.core.ui.R.string.profile_high_target,
                HardLimits.LIMIT_MAX_BG[0],
                HardLimits.LIMIT_MAX_BG[1]
            )
        var targetBg =
            hardLimits.verifyHardLimits(profile.getTargetMgdl(), app.aaps.core.ui.R.string.temp_target_value, HardLimits.LIMIT_TARGET_BG[0], HardLimits.LIMIT_TARGET_BG[1])
        var isTempTarget = false
        val tempTarget = persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())
        if (tempTarget != null) {
            isTempTarget = true
            minBg =
                hardLimits.verifyHardLimits(
                    tempTarget.lowTarget,
                    app.aaps.core.ui.R.string.temp_target_low_target,
                    HardLimits.LIMIT_TEMP_MIN_BG[0],
                    HardLimits.LIMIT_TEMP_MIN_BG[1]
                )
            maxBg =
                hardLimits.verifyHardLimits(
                    tempTarget.highTarget,
                    app.aaps.core.ui.R.string.temp_target_high_target,
                    HardLimits.LIMIT_TEMP_MAX_BG[0],
                    HardLimits.LIMIT_TEMP_MAX_BG[1]
                )
            targetBg =
                hardLimits.verifyHardLimits(
                    tempTarget.target(),
                    app.aaps.core.ui.R.string.temp_target_value,
                    HardLimits.LIMIT_TEMP_TARGET_BG[0],
                    HardLimits.LIMIT_TEMP_TARGET_BG[1]
                )
        }
        if (!hardLimits.checkHardLimits(profile.dia, app.aaps.core.ui.R.string.profile_dia, hardLimits.minDia(), hardLimits.maxDia())) return
        if (!hardLimits.checkHardLimits(
                profile.getIcTimeFromMidnight(MidnightUtils.secondsFromMidnight()),
                app.aaps.core.ui.R.string.profile_carbs_ratio_value,
                hardLimits.minIC(),
                hardLimits.maxIC()
            )
        ) return
        if (!hardLimits.checkHardLimits(profile.getIsfMgdl("test"), app.aaps.core.ui.R.string.profile_sensitivity_value, HardLimits.MIN_ISF, HardLimits.MAX_ISF)) return
        if (!hardLimits.checkHardLimits(profile.getMaxDailyBasal(), app.aaps.core.ui.R.string.profile_max_daily_basal_value, 0.02, hardLimits.maxBasal())) return
        if (!hardLimits.checkHardLimits(pump.baseBasalRate, app.aaps.core.ui.R.string.current_basal_value, 0.01, hardLimits.maxBasal())) return
        startPart = System.currentTimeMillis()
        if (constraintChecker.isAutosensModeEnabled().value()) {
            val autosensData = iobCobCalculator.getLastAutosensDataWithWaitForCalculationFinish("OpenAPSPlugin")
            if (autosensData == null) {
                rxBus.send(EventResetOpenAPSGui(rh.gs(R.string.openaps_no_as_data)))
                return
            }
            lastAutosensResult = autosensData.autosensResult
        } else {
            lastAutosensResult.sensResult = "autosens disabled"
        }
        val iobArray = iobCobCalculator.calculateIobArrayForSMB(lastAutosensResult, SMBDefaults.exercise_mode, SMBDefaults.half_basal_exercise_target, isTempTarget)
        profiler.log(LTag.APS, "calculateIobArrayInDia()", startPart)
        startPart = System.currentTimeMillis()
        val smbAllowed = ConstraintObject(!tempBasalFallback, aapsLogger).also {
            constraintChecker.isSMBModeEnabled(it)
            inputConstraints.copyReasons(it)
        }
        val advancedFiltering = ConstraintObject(!tempBasalFallback, aapsLogger).also {
            constraintChecker.isAdvancedFilteringEnabled(it)
            inputConstraints.copyReasons(it)
        }
        val uam = ConstraintObject(true, aapsLogger).also {
            constraintChecker.isUAMEnabled(it)
            inputConstraints.copyReasons(it)
        }
        dynIsfEnabled = ConstraintObject(true, aapsLogger).also {
            //constraintChecker.isDynIsfModeEnabled(it)
            inputConstraints.copyReasons(it)
        }
        val flatBGsDetected = bgQualityCheck.state == BgQualityCheck.State.FLAT
        profiler.log(LTag.APS, "detectSensitivityAndCarbAbsorption()", startPart)
        profiler.log(LTag.APS, "SMB data gathering", start)
        start = System.currentTimeMillis()

        // DynamicISF specific
        // without these values DynISF doesn't work properly
        // Current implementation is fallback to SMB if TDD history is not available. Thus calculated here
        tdd1D = tddCalculator.averageTDD(tddCalculator.calculate(1, allowMissingDays = false))?.data?.totalAmount
        tdd7D = tddCalculator.averageTDD(tddCalculator.calculate(7, allowMissingDays = false))?.data?.totalAmount
        tddLast24H = tddCalculator.calculateDaily(-24, 0)?.totalAmount
        tddLast4H = tddCalculator.calculateDaily(-4, 0)?.totalAmount
        tddLast8to4H = tddCalculator.calculateDaily(-8, -4)?.totalAmount

        if (tdd1D == null || tdd7D == null || tddLast4H == null || tddLast8to4H == null || tddLast24H == null) {
            inputConstraints.copyReasons(
                ConstraintObject(false, aapsLogger).also {
                    it.set(false, rh.gs(R.string.fallback_smb_no_tdd), this)
                }
            )
            inputConstraints.copyReasons(
                ConstraintObject(false, aapsLogger)
                    .apply { set(true, "tdd1D=$tdd1D tdd7D=$tdd7D tddLast4H=$tddLast4H tddLast8to4H=$tddLast8to4H tddLast24H=$tddLast24H", this) }
            )
        }


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
                //lastDetermineBasalAdapter = null
                lastAPSResult = null
                lastAPSRun = 0
            } else {
                // TODO still needed with oref1?
                // Fix bug determine basal
                if (determineBasalResultSMB.rate == 0.0 && determineBasalResultSMB.duration == 0 && processedTbrEbData.getTempBasalIncludingConvertedExtended(dateUtil.now()) == null) determineBasalResultSMB
                    .isTempBasalRequested =
                    false
                //determineBasalResultSMB.iob = iobArray[0]
                determineBasalResultSMB.json()?.put("timestamp", dateUtil.toISOString(now))
                determineBasalResultSMB.inputConstraints = inputConstraints
                //lastDetermineBasalAdapter = determineBasalAdapterSMBJS
                lastAPSResult = determineBasalResultSMB as DetermineBasalResultSMBFromJS
                lastAPSRun = now
                // if (config.isUnfinishedMode())
                // importExportPrefs.exportApsResult(
                //     when (determineBasalAdapterSMBJS) {
                //         is DetermineBasalAdapterSMBJS -> OpenAPSSMBPlugin::class.simpleName
                //         is DetermineBasalAdapterSMBDynamicISFJS -> OpenAPSSMBDynamicISFPlugin::class.simpleName
                //         else -> "Error"
                //     }, determineBasalAdapterSMBJS.json(), determineBasalResultSMB.json()
                // )
                rxBus.send(EventAPSCalculationFinished())
            }
        }
        rxBus.send(EventOpenAPSUpdateGui())
    }

    override fun getGlucoseStatusData(allowOldData: Boolean): GlucoseStatus? = glucoseStatusCalculatorSMB.getGlucoseStatusData(allowOldData)

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

    open fun provideDetermineBasalAdapter(): DetermineBasalAdapter = DetermineBasalAdapterSMBJS(ScriptReader(), injector)
}