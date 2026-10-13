package app.aaps.plugins.aps.openAPSAMA

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.GlucoseStatus
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
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profiling.Profiler
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAPSCalculationFinished
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
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
import app.aaps.plugins.aps.openAPSSMB.GlucoseStatusCalculatorSMB
import app.aaps.plugins.aps.utils.ScriptReader
import dagger.android.HasAndroidInjector
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor

@Singleton
class TestOpenAPSAMAPlugin @Inject constructor(
    private val injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val constraintChecker: ConstraintsChecker,
    rh: ResourceHelper,
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePlugin,
    private val iobCobCalculator: IobCobCalculator,
    private val processedTbrEbData: ProcessedTbrEbData,
    private val hardLimits: HardLimits,
    private val profiler: Profiler,
    private val fabricPrivacy: FabricPrivacy,
    private val dateUtil: DateUtil,
    private val persistenceLayer: PersistenceLayer,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val preferences: Preferences,
    private val importExportPrefs: ImportExportPrefs,
    private val config: Config,
    private val glucoseStatusCalculatorSMB: GlucoseStatusCalculatorSMB
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.APS)
        .fragmentClass(OpenAPSFragment::class.java.name)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_generic_icon)
        .pluginName(R.string.openapsama)
        .shortName(R.string.oaps_shortname)
        .preferencesVisibleInSimpleMode(false)
        .description(R.string.description_ama),
    aapsLogger, rh
), APS, PluginConstraints {

    // last values
    override var lastAPSRun: Long = 0
    override val algorithm = APSResult.Algorithm.AMA
    override var lastAPSResult: DetermineBasalResultAMAFromJS? = null

    //override var lastDetermineBasalAdapter: DetermineBasalAdapter? = null
    var lastAutosensResult: AutosensResult = AutosensResult()

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
        val determineBasalAdapterAMAJS = DetermineBasalAdapterAMAJS(ScriptReader(), injector)
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
            //lastDetermineBasalAdapter = null
            lastAPSResult = null
            lastAPSRun = 0
        } else {
            if (determineBasalResultAMA.rate == 0.0 && determineBasalResultAMA.duration == 0 && processedTbrEbData.getTempBasalIncludingConvertedExtended(dateUtil.now()) == null) determineBasalResultAMA
                .isTempBasalRequested =
                false
            //determineBasalResultAMA.iob = iobArray[0]
            val now = System.currentTimeMillis()
            determineBasalResultAMA.json()?.put("timestamp", dateUtil.toISOString(now))
            determineBasalResultAMA.inputConstraints = inputConstraints
            //lastDetermineBasalAdapter = determineBasalAdapterAMAJS
            lastAPSResult = determineBasalResultAMA as DetermineBasalResultAMAFromJS
            lastAPSRun = now
            if (config.isUnfinishedMode())
                importExportPrefs.exportApsResult(this::class.simpleName, determineBasalAdapterAMAJS.json(), determineBasalResultAMA.json())
            rxBus.send(EventAPSCalculationFinished())
        }
        rxBus.send(EventOpenAPSUpdateGui())

        //deviceStatus.suggested = determineBasalResultAMA.json;
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

    override fun configuration(): JSONObject = JSONObject()
    override fun applyConfiguration(configuration: JSONObject) {
    }
}