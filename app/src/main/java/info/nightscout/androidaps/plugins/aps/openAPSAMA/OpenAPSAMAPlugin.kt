package info.nightscout.androidaps.plugins.aps.openAPSAMA

import android.content.Context
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.aps.events.EventOpenAPSUpdateGui
import info.nightscout.androidaps.plugins.aps.events.EventOpenAPSUpdateResultGui
import info.nightscout.androidaps.plugins.aps.loop.ScriptReader
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.HardLimits
import info.nightscout.androidaps.utils.Profiler
import info.nightscout.androidaps.utils.Round
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.json.JSONException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class OpenAPSAMAPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    private val rxBus: RxBusWrapper,
    private val constraintChecker: ConstraintChecker,
    resourceHelper: ResourceHelper,
    private val profileFunction: ProfileFunction,
    private val context: Context,
    private val activePlugin: ActivePluginProvider,
    private val treatmentsPlugin: TreatmentsPlugin,
    private val iobCobCalculatorPlugin: IobCobCalculatorPlugin,
    private val hardLimits: HardLimits,
    private val profiler: Profiler,
    private val fabricPrivacy: FabricPrivacy
) : PluginBase(PluginDescription()
    .mainType(PluginType.APS)
    .fragmentClass(OpenAPSAMAFragment::class.java.name)
    .pluginIcon(R.drawable.ic_generic_icon)
    .pluginName(R.string.openapsama)
    .shortName(R.string.oaps_shortname)
    .preferencesId(R.xml.pref_openapsama)
    .description(R.string.description_ama),
    aapsLogger, resourceHelper, injector
), APSInterface {

    // last values
    override var lastAPSRun: Long = 0
    override var lastAPSResult: DetermineBasalResultAMA? = null
    var lastDetermineBasalAdapterAMAJS: DetermineBasalAdapterAMAJS? = null
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
        val determineBasalAdapterAMAJS = DetermineBasalAdapterAMAJS(ScriptReader(context), injector)
        val glucoseStatus = GlucoseStatus(injector).glucoseStatusData
        val profile = profileFunction.getProfile()
        val pump = activePlugin.activePump
        if (profile == null) {
            rxBus.send(EventOpenAPSUpdateResultGui(resourceHelper.gs(R.string.noprofileselected)))
            aapsLogger.debug(LTag.APS, resourceHelper.gs(R.string.noprofileselected))
            return
        }
        if (!isEnabled(PluginType.APS)) {
            rxBus.send(EventOpenAPSUpdateResultGui(resourceHelper.gs(R.string.openapsma_disabled)))
            aapsLogger.debug(LTag.APS, resourceHelper.gs(R.string.openapsma_disabled))
            return
        }
        if (glucoseStatus == null) {
            rxBus.send(EventOpenAPSUpdateResultGui(resourceHelper.gs(R.string.openapsma_noglucosedata)))
            aapsLogger.debug(LTag.APS, resourceHelper.gs(R.string.openapsma_noglucosedata))
            return
        }
        val inputConstraints = Constraint(0.0) // fake. only for collecting all results
        val maxBasal = constraintChecker.getMaxBasalAllowed(profile).also {
            inputConstraints.copyReasons(it)
        }.value()
        var start = System.currentTimeMillis()
        var startPart = System.currentTimeMillis()
        val iobArray = iobCobCalculatorPlugin.calculateIobArrayInDia(profile)
        profiler.log(LTag.APS, "calculateIobArrayInDia()", startPart)
        startPart = System.currentTimeMillis()
        val mealData = iobCobCalculatorPlugin.mealData
        profiler.log(LTag.APS, "getMealData()", startPart)
        val maxIob = constraintChecker.getMaxIOBAllowed().also { maxIOBAllowedConstraint ->
            inputConstraints.copyReasons(maxIOBAllowedConstraint)
        }.value()
        var minBg = hardLimits.verifyHardLimits(Round.roundTo(profile.targetLowMgdl, 0.1), "minBg", hardLimits.VERY_HARD_LIMIT_MIN_BG[0].toDouble(), hardLimits.VERY_HARD_LIMIT_MIN_BG[1].toDouble())
        var maxBg = hardLimits.verifyHardLimits(Round.roundTo(profile.targetHighMgdl, 0.1), "maxBg", hardLimits.VERY_HARD_LIMIT_MAX_BG[0].toDouble(), hardLimits.VERY_HARD_LIMIT_MAX_BG[1].toDouble())
        var targetBg = hardLimits.verifyHardLimits(profile.targetMgdl, "targetBg", hardLimits.VERY_HARD_LIMIT_TARGET_BG[0].toDouble(), hardLimits.VERY_HARD_LIMIT_TARGET_BG[1].toDouble())
        var isTempTarget = false
        treatmentsPlugin.getTempTargetFromHistory(System.currentTimeMillis())?.let { tempTarget ->
            isTempTarget = true
            minBg = hardLimits.verifyHardLimits(tempTarget.low, "minBg", hardLimits.VERY_HARD_LIMIT_TEMP_MIN_BG[0].toDouble(), hardLimits.VERY_HARD_LIMIT_TEMP_MIN_BG[1].toDouble())
            maxBg = hardLimits.verifyHardLimits(tempTarget.high, "maxBg", hardLimits.VERY_HARD_LIMIT_TEMP_MAX_BG[0].toDouble(), hardLimits.VERY_HARD_LIMIT_TEMP_MAX_BG[1].toDouble())
            targetBg = hardLimits.verifyHardLimits(tempTarget.target(), "targetBg", hardLimits.VERY_HARD_LIMIT_TEMP_TARGET_BG[0].toDouble(), hardLimits.VERY_HARD_LIMIT_TEMP_TARGET_BG[1].toDouble())
        }
        if (!hardLimits.checkOnlyHardLimits(profile.dia, "dia", hardLimits.minDia(), hardLimits.maxDia())) return
        if (!hardLimits.checkOnlyHardLimits(profile.getIcTimeFromMidnight(Profile.secondsFromMidnight()), "carbratio", hardLimits.minIC(), hardLimits.maxIC())) return
        if (!hardLimits.checkOnlyHardLimits(profile.isfMgdl, "sens", hardLimits.MINISF, hardLimits.MAXISF)) return
        if (!hardLimits.checkOnlyHardLimits(profile.maxDailyBasal, "max_daily_basal", 0.02, hardLimits.maxBasal())) return
        if (!hardLimits.checkOnlyHardLimits(pump.baseBasalRate, "current_basal", 0.01, hardLimits.maxBasal())) return
        startPart = System.currentTimeMillis()
        if (constraintChecker.isAutosensModeEnabled().value()) {
            val autosensData = iobCobCalculatorPlugin.getLastAutosensDataSynchronized("OpenAPSPlugin")
            if (autosensData == null) {
                rxBus.send(EventOpenAPSUpdateResultGui(resourceHelper.gs(R.string.openaps_noasdata)))
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
            determineBasalAdapterAMAJS.setData(profile, maxIob, maxBasal, minBg, maxBg, targetBg, activePlugin.activePump.baseBasalRate, iobArray, glucoseStatus, mealData,
                lastAutosensResult.ratio,
                isTempTarget
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
            lastDetermineBasalAdapterAMAJS = null
            lastAPSResult = null
            lastAPSRun = 0
        } else {
            if (determineBasalResultAMA.rate == 0.0 && determineBasalResultAMA.duration == 0 && !treatmentsPlugin.isTempBasalInProgress) determineBasalResultAMA.tempBasalRequested = false
            determineBasalResultAMA.iob = iobArray[0]
            val now = System.currentTimeMillis()
            determineBasalResultAMA.json?.put("timestamp", DateUtil.toISOString(now))
            determineBasalResultAMA.inputConstraints = inputConstraints
            lastDetermineBasalAdapterAMAJS = determineBasalAdapterAMAJS
            lastAPSResult = determineBasalResultAMA
            lastAPSRun = now
        }
        rxBus.send(EventOpenAPSUpdateGui())

        //deviceStatus.suggested = determineBasalResultAMA.json;
    }
}