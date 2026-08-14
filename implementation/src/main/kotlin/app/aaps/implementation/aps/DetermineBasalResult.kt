package app.aaps.implementation.aps

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.CurrentTemp
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.MealData
import app.aaps.core.interfaces.aps.OapsProfile
import app.aaps.core.interfaces.aps.OapsProfileAutoIsf
import app.aaps.core.interfaces.aps.Predictions
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.convertedToAbsolute
import app.aaps.core.objects.extensions.convertedToPercent
import app.aaps.core.ui.R
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.abs
import kotlin.math.max

class DetermineBasalResult @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val fabricPrivacy: FabricPrivacy,
    private val constraintChecker: ConstraintsChecker,
    private val preferences: Preferences,
    private val activePlugin: ActivePlugin,
    private val processedTbrEbData: ProcessedTbrEbData,
    private val profileFunction: ProfileFunction,
    private val rh: ResourceHelper,
    private val decimalFormatter: DecimalFormatter,
    private val dateUtil: DateUtil,
    private val apsResultProvider: Provider<APSResult>,
    private val ch: ConcentrationHelper
) : APSResult {

    override var date: Long = 0
    override var reason: String = ""
    override var rate = -1.0
    override var percent = 0
    override var usePercent = false
    override var duration = -1
    override var isTempBasalRequested = false

    override var hasPredictions = false
    override var smb = 0.0 // super micro bolus in units
    override var deliverAt: Long = 0
    override var targetBG = 0.0
    override var carbsReq = 0
    override var carbsReqWithin = 0
    override var variableSens: Double? = null
    override var isfMgdlForCarbs: Double? = null // used only to pass to AAPS client
    override var scriptDebug: List<String>? = null
    var eventualBG = 0.0
    var snoozeBG = 0.0

    override var inputConstraints: Constraint<Double>? = null
    override var rateConstraint: Constraint<Double>? = null
    override var percentConstraint: Constraint<Int>? = null
    override var smbConstraint: Constraint<Double>? = null

    // Inputs
    override var algorithm: APSResult.Algorithm = APSResult.Algorithm.UNKNOWN
    override var autosensResult: AutosensResult? = null
    override var iobData: Array<IobTotal>? = null
    override var glucoseStatus: GlucoseStatus? = null
    override var currentTemp: CurrentTemp? = null
    override var oapsProfile: OapsProfile? = null
    override var oapsProfileAutoIsf: OapsProfileAutoIsf? = null
    override var mealData: MealData? = null

    lateinit var result: RT

    override fun with(result: RT): APSResult = this.also {
        this.algorithm = result.algorithm
        this.result = result
        hasPredictions = true
        date = result.timestamp ?: dateUtil.now()

        reason = result.reason.toString()
        eventualBG = result.eventualBG ?: 0.0
        snoozeBG = result.snoozeBG ?: 0.0
        carbsReq = result.carbsReq ?: 0
        carbsReqWithin = result.carbsReqWithin ?: 0
        if (result.rate != null && result.duration != null) {
            isTempBasalRequested = true
            rate = max(0.0, result.rate ?: error("!"))
            duration = result.duration ?: error("!")
        }
        smb = result.units ?: 0.0
        targetBG = result.targetBG ?: 0.0
        deliverAt = result.deliverAt ?: 0L
        variableSens = result.variable_sens
        isfMgdlForCarbs = result.isfMgdlForCarbs
        scriptDebug = result.consoleError
    }

    override val carbsRequiredText: String
        get() = rh.gs(R.string.carbsreq, carbsReq, carbsReqWithin)

    override suspend fun resultAsString(): String {
        val pump = activePlugin.activePump
        if (isChangeRequested()) {
            // rate
            var ret: String = if (rate == 0.0 && duration == 0) "${rh.gs(R.string.cancel_temp)} "
            else if (rate == -1.0) "${rh.gs(R.string.let_temp_basal_run)}\n"
            else if (usePercent) "${rh.gs(R.string.percent_rate_duration, percent.toDouble(), percent * ch.fromPump(pump.baseBasalRate) / 100.0, duration)} "
            else "${rh.gs(R.string.rate_percent_duration, rate, rate / ch.fromPump(pump.baseBasalRate) * 100.0, duration)} "

            // smb
            if (smb != 0.0) ret += "SMB: ${decimalFormatter.toPumpSupportedBolus(smb, activePlugin.activePump.pumpDescription.bolusStep)} "
            if (isCarbsRequired) {
                ret += "$carbsRequiredText "
            }

            // reason
            ret += rh.gs(R.string.reason) + ": " + reason
            return ret
        }
        return if (isCarbsRequired) {
            carbsRequiredText
        } else rh.gs(R.string.nochangerequested)
    }

    override fun newAndClone(): APSResult = apsResultProvider.get().with(result)
    override fun json(): JsonObject {
        reportNonFiniteResultFields()
        // Straight to a tree. This used to serialise to text and parse it back with org.json, once per
        // loop cycle. Same default Json either way, so a non-finite Double still throws here - see
        // `reportNonFiniteResultFields`, that crash is the signal and must not be swallowed.
        return Json.encodeToJsonElement(RT.serializer(), result).jsonObject
    }

    /**
     * Diagnostic for the recurring device-status crash (Crashlytics
     * `RT.write$Self … Unexpected special floating-point value NaN`): kotlinx.serialization's default
     * `Json` rejects non-finite [Double]s, so a single NaN/±Infinity in the APS result crashes
     * `buildAndStoreDeviceStatus` every loop cycle. The framework stack names none of the fields, so
     * we cannot tell WHICH value went bad or from WHICH algorithm.
     *
     * We deliberately do NOT sanitize/swallow here — the crash is the signal driving the ongoing
     * DetermineBasal NaN hunt (see `DetermineBasalSMB` minPredBG pin, `OpenAPSSMBPlugin` invalidInputs
     * guard). Instead, right before the (still-crashing) encode, we report exactly which field is
     * non-finite plus the ISF inputs that feed it, so the next occurrence pinpoints the field and
     * algorithm instead of an opaque framework trace. The encode runs unchanged afterwards.
     */
    private fun reportNonFiniteResultFields() {
        val offenders = buildList {
            fun check(name: String, value: Double?) { if (value != null && !value.isFinite()) add("$name=$value") }
            check("bg", result.bg)
            check("eventualBG", result.eventualBG)
            check("targetBG", result.targetBG)
            check("snoozeBG", result.snoozeBG)
            check("insulinReq", result.insulinReq)
            check("units", result.units)
            check("sensitivityRatio", result.sensitivityRatio)
            check("rate", result.rate)
            check("COB", result.COB)
            check("IOB", result.IOB)
            check("variable_sens", result.variable_sens)
            check("isfMgdlForCarbs", result.isfMgdlForCarbs)
        }
        if (offenders.isEmpty()) return

        val p = oapsProfile
        val msg = "APS result has non-finite field(s) [${offenders.joinToString()}] " +
            "algorithm=${result.algorithm} runningDynamicIsf=${result.runningDynamicIsf} " +
            "glucose=${glucoseStatus?.glucose} autosensRatio=${autosensResult?.ratio} " +
            "sens=${p?.sens} carb_ratio=${p?.carb_ratio} variable_sens=${p?.variable_sens} " +
            "TDD=${p?.TDD} insulinDivisor=${p?.insulinDivisor}"
        aapsLogger.error(LTag.APS, msg)
        fabricPrivacy.logException(IllegalStateException(msg))
    }

    override fun predictions(): Predictions? = result.predBGs
    override fun rawData(): RT = result

    override fun toString(): String = result.toString()

    override val predictionsAsGv: MutableList<GV>
        get() {
            val array: MutableList<GV> = ArrayList()
            val startTime = date

            val predictions = predictions()
            predictions?.IOB?.let { iob ->
                for (i in 1 until iob.size) {
                    val gv = GV(
                        raw = 0.0,
                        noise = 0.0,
                        value = iob[i].toDouble(),
                        timestamp = startTime + i * 5 * 60 * 1000L,
                        sourceSensor = SourceSensor.IOB_PREDICTION,
                        trendArrow = TrendArrow.NONE
                    )
                    array.add(gv)
                }
            }
            predictions?.aCOB?.let { iob ->
                for (i in 1 until iob.size) {
                    val gv = GV(
                        raw = 0.0,
                        noise = 0.0,
                        value = iob[i].toDouble(),
                        timestamp = startTime + i * 5 * 60 * 1000L,
                        sourceSensor = SourceSensor.A_COB_PREDICTION,
                        trendArrow = TrendArrow.NONE
                    )
                    array.add(gv)
                }
            }
            predictions?.COB?.let { iob ->
                for (i in 1 until iob.size) {
                    val gv = GV(
                        raw = 0.0,
                        noise = 0.0,
                        value = iob[i].toDouble(),
                        timestamp = startTime + i * 5 * 60 * 1000L,
                        sourceSensor = SourceSensor.COB_PREDICTION,
                        trendArrow = TrendArrow.NONE
                    )
                    array.add(gv)
                }
            }
            predictions?.UAM?.let { iob ->
                for (i in 1 until iob.size) {
                    val gv = GV(
                        raw = 0.0,
                        noise = 0.0,
                        value = iob[i].toDouble(),
                        timestamp = startTime + i * 5 * 60 * 1000L,
                        sourceSensor = SourceSensor.UAM_PREDICTION,
                        trendArrow = TrendArrow.NONE
                    )
                    array.add(gv)
                }
            }
            predictions?.ZT?.let { iob ->
                for (i in 1 until iob.size) {
                    val gv = GV(
                        raw = 0.0,
                        noise = 0.0,
                        value = iob[i].toDouble(),
                        timestamp = startTime + i * 5 * 60 * 1000L,
                        sourceSensor = SourceSensor.ZT_PREDICTION,
                        trendArrow = TrendArrow.NONE
                    )
                    array.add(gv)
                }
            }
            return array
        }
    override val latestPredictionsTime: Long
        get() {
            var latest: Long = 0
            val startTime = date
            val predictions = predictions()
            predictions?.IOB?.let { if (it.isNotEmpty()) latest = max(latest, startTime + (it.size - 1) * 5 * 60 * 1000L) }
            predictions?.aCOB?.let { if (it.isNotEmpty()) latest = max(latest, startTime + (it.size - 1) * 5 * 60 * 1000L) }
            predictions?.COB?.let { if (it.isNotEmpty()) latest = max(latest, startTime + (it.size - 1) * 5 * 60 * 1000L) }
            predictions?.UAM?.let { if (it.isNotEmpty()) latest = max(latest, startTime + (it.size - 1) * 5 * 60 * 1000L) }
            predictions?.ZT?.let { if (it.isNotEmpty()) latest = max(latest, startTime + (it.size - 1) * 5 * 60 * 1000L) }
            return latest
        }

    override suspend fun isChangeRequested(): Boolean {
        val closedLoopEnabled = constraintChecker.isClosedLoopAllowed()
        // closed loop mode: handle change at driver level
        if (closedLoopEnabled.value()) {
            aapsLogger.debug(LTag.APS, "DEFAULT: Closed mode")
            return isTempBasalRequested || isBolusRequested
        }

        // open loop mode: try to limit request
        if (!isTempBasalRequested && !isBolusRequested) {
            aapsLogger.debug(LTag.APS, "FALSE: No request")
            return false
        }
        val now = System.currentTimeMillis()
        val activeTemp = processedTbrEbData.getTempBasalIncludingConvertedExtended(now)
        val pump = activePlugin.activePump
        val profile = profileFunction.getProfile()
        if (profile == null) {
            aapsLogger.error("FALSE: No Profile")
            return false
        }
        return if (usePercent) {
            if (activeTemp == null && percent == 100) {
                aapsLogger.debug(LTag.APS, "FALSE: No temp running, asking cancel temp")
                return false
            }
            if (activeTemp != null && abs(percent - activeTemp.convertedToPercent(now, profile)) < pump.pumpDescription.basalStep) {
                aapsLogger.debug(LTag.APS, "FALSE: Temp equal")
                return false
            }
            // always report zero temp
            if (percent == 0) {
                aapsLogger.debug(LTag.APS, "TRUE: Zero temp")
                return true
            }
            // always report high temp
            if (pump.pumpDescription.tempBasalStyle == PumpDescription.PERCENT) {
                val pumpLimit = pump.pumpDescription.pumpType.tbrSettings()?.maxDose ?: 0.0
                if (percent.toDouble() == pumpLimit) {
                    aapsLogger.debug(LTag.APS, "TRUE: Pump limit")
                    return true
                }
            }
            // report change bigger than 30%
            var percentMinChangeChange = preferences.get(IntKey.LoopOpenModeMinChange).toDouble()
            percentMinChangeChange /= 100.0
            val lowThreshold = 1 - percentMinChangeChange
            val highThreshold = 1 + percentMinChangeChange
            var change = percent / 100.0
            if (activeTemp != null) change = percent / activeTemp.convertedToPercent(now, profile).toDouble()
            if (change !in lowThreshold..highThreshold) {
                aapsLogger.debug(LTag.APS, "TRUE: Outside allowed range " + change * 100.0 + "%")
                true
            } else {
                aapsLogger.debug(LTag.APS, "TRUE: Inside allowed range " + change * 100.0 + "%")
                false
            }
        } else {
            if (activeTemp == null && rate == ch.fromPump(pump.baseBasalRate)) {
                aapsLogger.debug(LTag.APS, "FALSE: No temp running, asking cancel temp")
                return false
            }
            if (activeTemp != null && abs(rate - activeTemp.convertedToAbsolute(now, profile)) < pump.pumpDescription.basalStep) {
                aapsLogger.debug(LTag.APS, "FALSE: Temp equal")
                return false
            }
            // always report zero temp
            if (rate == 0.0) {
                aapsLogger.debug(LTag.APS, "TRUE: Zero temp")
                return true
            }
            // always report high temp
            if (pump.pumpDescription.tempBasalStyle == PumpDescription.ABSOLUTE) {
                val pumpLimit = pump.pumpDescription.pumpType.tbrSettings()?.maxDose ?: 0.0
                if (rate == pumpLimit) {
                    aapsLogger.debug(LTag.APS, "TRUE: Pump limit")
                    return true
                }
            }
            // report change bigger than 30%
            var percentMinChangeChange = preferences.get(IntKey.LoopOpenModeMinChange).toDouble()
            percentMinChangeChange /= 100.0
            val lowThreshold = 1 - percentMinChangeChange
            val highThreshold = 1 + percentMinChangeChange
            var change = rate / profile.getBasal()
            if (activeTemp != null) change = rate / activeTemp.convertedToAbsolute(now, profile)
            if (change !in lowThreshold..highThreshold) {
                aapsLogger.debug(LTag.APS, "TRUE: Outside allowed range " + change * 100.0 + "%")
                true
            } else {
                aapsLogger.debug(LTag.APS, "TRUE: Inside allowed range " + change * 100.0 + "%")
                false
            }
        }
    }
}