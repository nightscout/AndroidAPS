package info.nightscout.plugins.aps.openAPSAMA

import android.content.Context
import dagger.android.HasAndroidInjector
import info.nightscout.annotations.OpenForTesting
import info.nightscout.core.constraints.ConstraintObject
import info.nightscout.core.extensions.target
import info.nightscout.core.utils.MidnightUtils
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.database.ValueWrapper
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.aps.APS
import info.nightscout.interfaces.aps.AutosensResult
import info.nightscout.interfaces.aps.DetermineBasalAdapter
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.ConstraintsChecker
import info.nightscout.interfaces.constraints.PluginConstraints
import info.nightscout.interfaces.iob.GlucoseStatusProvider
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.profiling.Profiler
import info.nightscout.interfaces.utils.HardLimits
import info.nightscout.interfaces.utils.Round
import info.nightscout.plugins.aps.OpenAPSFragment
import info.nightscout.plugins.aps.R
import info.nightscout.plugins.aps.events.EventOpenAPSUpdateGui
import info.nightscout.plugins.aps.events.EventResetOpenAPSGui
import info.nightscout.plugins.aps.utils.ScriptReader
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import org.json.JSONException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor

@OpenForTesting
@Singleton
class OpenAPSAMAPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val constraintChecker: ConstraintsChecker,
    rh: ResourceHelper,
    private val profileFunction: ProfileFunction,
    private val context: Context,
    private val activePlugin: ActivePlugin,
    private val iobCobCalculator: IobCobCalculator,
    private val hardLimits: HardLimits,
    private val profiler: Profiler,
    private val fabricPrivacy: FabricPrivacy,
    private val dateUtil: DateUtil,
    private val repository: AppRepository,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val sp: SP
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.APS)
        .fragmentClass(OpenAPSFragment::class.java.name)
        .pluginIcon(info.nightscout.core.ui.R.drawable.ic_generic_icon)
        .pluginName(R.string.openapsama)
        .shortName(R.string.oaps_shortname)
        .preferencesId(R.xml.pref_openapsama)
        .description(R.string.description_ama),
    aapsLogger, rh, injector
), APS, PluginConstraints {

    // last values
    override var lastAPSRun: Long = 0
    override var lastAPSResult: DetermineBasalResultAMA? = null
    override var lastDetermineBasalAdapter: DetermineBasalAdapter? = null
    override var lastAutosensResult: AutosensResult = AutosensResult()

    override fun specialEnableCondition(): Boolean {
        return try {
            val pump = activePlugin.activePump
            pump.pumpDescription.isTempBasalCapable
        } catch (ignored: Exception) {
            // may fail during initialization
            true
        }
    }

    override fun specialShowInListCondition(): Boolean {
        val pump = activePlugin.activePump
        return pump.pumpDescription.isTempBasalCapable
    }

    override fun invoke(initiator: String, tempBasalFallback: Boolean) {
        aapsLogger.debug(LTag.APS, "invoke from $initiator tempBasalFallback: $tempBasalFallback")
        lastAPSResult = null
        val determineBasalAdapterAMAJS = DetermineBasalAdapterAMAJS(ScriptReader(context), injector)
        val glucoseStatus = glucoseStatusProvider.glucoseStatusData
        val profile = profileFunction.getProfile()
        val pump = activePlugin.activePump
        if (profile == null) {
            rxBus.send(EventResetOpenAPSGui(rh.gs(info.nightscout.core.ui.R.string.no_profile_set)))
            aapsLogger.debug(LTag.APS, rh.gs(info.nightscout.core.ui.R.string.no_profile_set))
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
        val iobArray = iobCobCalculator.calculateIobArrayInDia(profile)
        profiler.log(LTag.APS, "calculateIobArrayInDia()", startPart)
        startPart = System.currentTimeMillis()
        val mealData = iobCobCalculator.getMealDataWithWaitingForCalculationFinish()
        profiler.log(LTag.APS, "getMealData()", startPart)
        val maxIob = constraintChecker.getMaxIOBAllowed().also { maxIOBAllowedConstraint ->
            inputConstraints.copyReasons(maxIOBAllowedConstraint)
        }.value()
        var minBg =
            hardLimits.verifyHardLimits(
                Round.roundTo(profile.getTargetLowMgdl(), 0.1),
                info.nightscout.core.ui.R.string.profile_low_target,
                HardLimits.VERY_HARD_LIMIT_MIN_BG[0],
                HardLimits.VERY_HARD_LIMIT_MIN_BG[1]
            )
        var maxBg =
            hardLimits.verifyHardLimits(
                Round.roundTo(profile.getTargetHighMgdl(), 0.1),
                info.nightscout.core.ui.R.string.profile_high_target,
                HardLimits.VERY_HARD_LIMIT_MAX_BG[0],
                HardLimits.VERY_HARD_LIMIT_MAX_BG[1]
            )
        var targetBg =
            hardLimits.verifyHardLimits(profile.getTargetMgdl(), info.nightscout.core.ui.R.string.temp_target_value, HardLimits.VERY_HARD_LIMIT_TARGET_BG[0], HardLimits.VERY_HARD_LIMIT_TARGET_BG[1])
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
        if (!hardLimits.checkHardLimits(
                profile.getIcTimeFromMidnight(MidnightUtils.secondsFromMidnight()),
                info.nightscout.core.ui.R.string.profile_carbs_ratio_value,
                hardLimits.minIC(),
                hardLimits.maxIC()
            )
        ) return
        if (!hardLimits.checkHardLimits(profile.getIsfMgdl(), info.nightscout.core.ui.R.string.profile_sensitivity_value, HardLimits.MIN_ISF, HardLimits.MAX_ISF)) return
        if (!hardLimits.checkHardLimits(profile.getMaxDailyBasal(), info.nightscout.core.ui.R.string.profile_max_daily_basal_value, 0.02, hardLimits.maxBasal())) return
        if (!hardLimits.checkHardLimits(pump.baseBasalRate, info.nightscout.core.ui.R.string.current_basal_value, 0.01, hardLimits.maxBasal())) return
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
        profiler.log(LTag.APS, "detectSensitivityAndCarbAbsorption()", startPart)
        profiler.log(LTag.APS, "AMA data gathering", start)
        start = System.currentTimeMillis()
        try {
            determineBasalAdapterAMAJS.setData(
                profile, maxIob, maxBasal, minBg, maxBg, targetBg, activePlugin.activePump.baseBasalRate, iobArray, glucoseStatus, mealData,
                lastAutosensResult.ratio,
                isTempTarget,
                tdd1D = null,
                tdd7D = null,
                tddLast4H = null,
                tddLast8to4H = null,
                tddLast24H = null,
            )
        } catch (e: JSONException) {
            fabricPrivacy.logException(e)
            return
        }
        val determineBasalResultAMA = determineBasalAdapterAMAJS.invoke()
        profiler.log(LTag.APS, "AMA calculation", start)
        // Fix bug determine basal
        if (determineBasalResultAMA == null) {
            aapsLogger.error(LTag.APS, "SMB calculation returned null")
            lastDetermineBasalAdapter = null
            lastAPSResult = null
            lastAPSRun = 0
        } else {
            if (determineBasalResultAMA.rate == 0.0 && determineBasalResultAMA.duration == 0 && iobCobCalculator.getTempBasalIncludingConvertedExtended(dateUtil.now()) == null) determineBasalResultAMA.isTempBasalRequested =
                false
            determineBasalResultAMA.iob = iobArray[0]
            val now = System.currentTimeMillis()
            determineBasalResultAMA.json?.put("timestamp", dateUtil.toISOString(now))
            determineBasalResultAMA.inputConstraints = inputConstraints
            lastDetermineBasalAdapter = determineBasalAdapterAMAJS
            lastAPSResult = determineBasalResultAMA as DetermineBasalResultAMA
            lastAPSRun = now
        }
        rxBus.send(EventOpenAPSUpdateGui())

        //deviceStatus.suggested = determineBasalResultAMA.json;
    }

    override fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> {
        if (isEnabled()) {
            val maxIobPref: Double = sp.getDouble(R.string.key_openapsma_max_iob, 1.5)
            maxIob.setIfSmaller(maxIobPref, rh.gs(R.string.limiting_iob, maxIobPref, rh.gs(R.string.maxvalueinpreferences)), this)
            maxIob.setIfSmaller(hardLimits.maxIobAMA(), rh.gs(R.string.limiting_iob, hardLimits.maxIobAMA(), rh.gs(R.string.hardlimit)), this)
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
            absoluteRate.setIfSmaller(maxBasal, rh.gs(info.nightscout.core.ui.R.string.limitingbasalratio, maxBasal, rh.gs(R.string.maxvalueinpreferences)), this)

            // Check percentRate but absolute rate too, because we know real current basal in pump
            val maxBasalMultiplier = sp.getDouble(R.string.key_openapsama_current_basal_safety_multiplier, 4.0)
            val maxFromBasalMultiplier = floor(maxBasalMultiplier * profile.getBasal() * 100) / 100
            absoluteRate.setIfSmaller(
                maxFromBasalMultiplier,
                rh.gs(info.nightscout.core.ui.R.string.limitingbasalratio, maxFromBasalMultiplier, rh.gs(R.string.max_basal_multiplier)),
                this
            )
            val maxBasalFromDaily = sp.getDouble(R.string.key_openapsama_max_daily_safety_multiplier, 3.0)
            val maxFromDaily = floor(profile.getMaxDailyBasal() * maxBasalFromDaily * 100) / 100
            absoluteRate.setIfSmaller(maxFromDaily, rh.gs(info.nightscout.core.ui.R.string.limitingbasalratio, maxFromDaily, rh.gs(R.string.max_daily_basal_multiplier)), this)
        }
        return absoluteRate
    }

    override fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val enabled = sp.getBoolean(R.string.key_openapsama_use_autosens, false)
        if (!enabled) value.set(false, rh.gs(R.string.autosens_disabled_in_preferences), this)
        return value
    }
}