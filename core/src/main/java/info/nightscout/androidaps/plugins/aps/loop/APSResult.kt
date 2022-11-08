package info.nightscout.androidaps.plugins.aps.loop

import android.text.Spanned
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.data.IobTotal
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.extensions.convertedToAbsolute
import info.nightscout.androidaps.extensions.convertedToPercent
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.IobCobCalculator
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.interfaces.PumpDescription
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.HtmlHelper.fromHtml
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max

/**
 * Created by mike on 09.06.2016.
 */
@Suppress("LeakingThis")
open class APSResult @Inject constructor(val injector: HasAndroidInjector) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var sp: SP
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var dateUtil: DateUtil

    var date: Long = 0
    var reason: String = ""
    var rate = 0.0
    var percent = 0
    var usePercent = false
    var duration = 0
    var tempBasalRequested = false
    var iob: IobTotal? = null
    var json: JSONObject? = JSONObject()
    var hasPredictions = false
    var smb = 0.0 // super micro bolus in units
    var deliverAt: Long = 0
    var targetBG = 0.0
    var carbsReq = 0
    var carbsReqWithin = 0
    var inputConstraints: Constraint<Double>? = null
    var rateConstraint: Constraint<Double>? = null
    var percentConstraint: Constraint<Int>? = null
    var smbConstraint: Constraint<Double>? = null

    init {
        injector.androidInjector().inject(this)
    }

    fun rate(rate: Double): APSResult {
        this.rate = rate
        return this
    }

    fun duration(duration: Int): APSResult {
        this.duration = duration
        return this
    }

    fun percent(percent: Int): APSResult {
        this.percent = percent
        return this
    }

    fun tempBasalRequested(tempBasalRequested: Boolean): APSResult {
        this.tempBasalRequested = tempBasalRequested
        return this
    }

    fun usePercent(usePercent: Boolean): APSResult {
        this.usePercent = usePercent
        return this
    }

    fun json(json: JSONObject?): APSResult {
        this.json = json
        return this
    }

    val carbsRequiredText: String
        get() = rh.gs(R.string.carbsreq, carbsReq, carbsReqWithin)

    override fun toString(): String {
        val pump = activePlugin.activePump
        if (isChangeRequested) {
            // rate
            var ret: String = if (rate == 0.0 && duration == 0) "${rh.gs(R.string.canceltemp)} "
            else if (rate == -1.0) "${rh.gs(R.string.let_temp_basal_run)}\n"
            else if (usePercent) "${rh.gs(R.string.rate)}: ${DecimalFormatter.to2Decimal(percent.toDouble())}% (${DecimalFormatter.to2Decimal(percent * pump.baseBasalRate / 100.0)} U/h) " +
                "${rh.gs(R.string.duration)}: ${DecimalFormatter.to2Decimal(duration.toDouble())} min "
            else "${rh.gs(R.string.rate)}: ${DecimalFormatter.to2Decimal(rate)} U/h (${DecimalFormatter.to2Decimal(rate / pump.baseBasalRate * 100)}%) " +
                "${rh.gs(R.string.duration)}: ${DecimalFormatter.to2Decimal(duration.toDouble())} min "
            // smb
            if (smb != 0.0) ret += "SMB: ${DecimalFormatter.toPumpSupportedBolus(smb, activePlugin.activePump, rh)} "
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

    fun toSpanned(): Spanned {
        val pump = activePlugin.activePump
        if (isChangeRequested) {
            // rate
            var ret: String = if (rate == 0.0 && duration == 0) rh.gs(R.string.canceltemp) + "<br>" else if (rate == -1.0) rh.gs(R.string.let_temp_basal_run) + "<br>" else if (usePercent) "<b>" + rh.gs(R.string.rate) + "</b>: " + DecimalFormatter.to2Decimal(percent.toDouble()) + "% " +
                "(" + DecimalFormatter.to2Decimal(percent * pump.baseBasalRate / 100.0) + " U/h)<br>" +
                "<b>" + rh.gs(R.string.duration) + "</b>: " + DecimalFormatter.to2Decimal(duration.toDouble()) + " min<br>" else "<b>" + rh.gs(R.string.rate) + "</b>: " + DecimalFormatter.to2Decimal(rate) + " U/h " +
                "(" + DecimalFormatter.to2Decimal(rate / pump.baseBasalRate * 100.0) + "%) <br>" +
                "<b>" + rh.gs(R.string.duration) + "</b>: " + DecimalFormatter.to2Decimal(duration.toDouble()) + " min<br>"

            // smb
            if (smb != 0.0) ret += "<b>" + "SMB" + "</b>: " + DecimalFormatter.toPumpSupportedBolus(smb, activePlugin.activePump, rh) + "<br>"
            if (isCarbsRequired) {
                ret += "$carbsRequiredText<br>"
            }

            // reason
            ret += "<b>" + rh.gs(R.string.reason) + "</b>: " + reason.replace("<", "&lt;").replace(">", "&gt;")
            return fromHtml(ret)
        }
        return if (isCarbsRequired) {
            fromHtml(carbsRequiredText)
        } else fromHtml(rh.gs(R.string.nochangerequested))
    }

    open fun newAndClone(injector: HasAndroidInjector): APSResult {
        val newResult = APSResult(injector)
        doClone(newResult)
        return newResult
    }

    protected fun doClone(newResult: APSResult) {
        newResult.date = date
        newResult.reason = reason
        newResult.rate = rate
        newResult.duration = duration
        newResult.tempBasalRequested = tempBasalRequested
        newResult.iob = iob
        newResult.json = JSONObject(json.toString())
        newResult.hasPredictions = hasPredictions
        newResult.smb = smb
        newResult.deliverAt = deliverAt
        newResult.rateConstraint = rateConstraint
        newResult.smbConstraint = smbConstraint
        newResult.percent = percent
        newResult.usePercent = usePercent
        newResult.carbsReq = carbsReq
        newResult.carbsReqWithin = carbsReqWithin
        newResult.targetBG = targetBG
    }

    open fun json(): JSONObject? {
        val json = JSONObject()
        if (isChangeRequested) {
            json.put("rate", rate)
            json.put("duration", duration)
            json.put("reason", reason)
        }
        return json
    }

    val predictions: MutableList<GlucoseValue>
        get() {
            val array: MutableList<GlucoseValue> = ArrayList()
            val startTime = date
            json?.let { json ->
                if (json.has("predBGs")) {
                    val predBGs = json.getJSONObject("predBGs")
                    if (predBGs.has("IOB")) {
                        val iob = predBGs.getJSONArray("IOB")
                        for (i in 1 until iob.length()) {
                            val gv = GlucoseValue(
                                raw = 0.0,
                                noise = 0.0,
                                value = iob.getInt(i).toDouble(),
                                smoothed = iob.getInt(i).toDouble(),
                                timestamp = startTime + i * 5 * 60 * 1000L,
                                sourceSensor = GlucoseValue.SourceSensor.IOB_PREDICTION,
                                trendArrow = GlucoseValue.TrendArrow.NONE
                            )
                            array.add(gv)
                        }
                    }
                    if (predBGs.has("aCOB")) {
                        val iob = predBGs.getJSONArray("aCOB")
                        for (i in 1 until iob.length()) {
                            val gv = GlucoseValue(
                                raw = 0.0,
                                noise = 0.0,
                                value = iob.getInt(i).toDouble(),
                                smoothed = iob.getInt(i).toDouble(),
                                timestamp = startTime + i * 5 * 60 * 1000L,
                                sourceSensor = GlucoseValue.SourceSensor.A_COB_PREDICTION,
                                trendArrow = GlucoseValue.TrendArrow.NONE
                            )
                            array.add(gv)
                        }
                    }
                    if (predBGs.has("COB")) {
                        val iob = predBGs.getJSONArray("COB")
                        for (i in 1 until iob.length()) {
                            val gv = GlucoseValue(
                                raw = 0.0,
                                noise = 0.0,
                                value = iob.getInt(i).toDouble(),
                                smoothed = iob.getInt(i).toDouble(),
                                timestamp = startTime + i * 5 * 60 * 1000L,
                                sourceSensor = GlucoseValue.SourceSensor.COB_PREDICTION,
                                trendArrow = GlucoseValue.TrendArrow.NONE
                            )
                            array.add(gv)
                        }
                    }
                    if (predBGs.has("UAM")) {
                        val iob = predBGs.getJSONArray("UAM")
                        for (i in 1 until iob.length()) {
                            val gv = GlucoseValue(
                                raw = 0.0,
                                noise = 0.0,
                                value = iob.getInt(i).toDouble(),
                                smoothed = iob.getInt(i).toDouble(),
                                timestamp = startTime + i * 5 * 60 * 1000L,
                                sourceSensor = GlucoseValue.SourceSensor.UAM_PREDICTION,
                                trendArrow = GlucoseValue.TrendArrow.NONE
                            )
                            array.add(gv)
                        }
                    }
                    if (predBGs.has("ZT")) {
                        val iob = predBGs.getJSONArray("ZT")
                        for (i in 1 until iob.length()) {
                            val gv = GlucoseValue(
                                raw = 0.0,
                                noise = 0.0,
                                value = iob.getInt(i).toDouble(),
                                smoothed = iob.getInt(i).toDouble(),
                                timestamp = startTime + i * 5 * 60 * 1000L,
                                sourceSensor = GlucoseValue.SourceSensor.ZT_PREDICTION,
                                trendArrow = GlucoseValue.TrendArrow.NONE
                            )
                            array.add(gv)
                        }
                    }
                }
            }
            return array
        }
    val latestPredictionsTime: Long
        get() {
            var latest: Long = 0
            try {
                val startTime = date
                if (json != null && json!!.has("predBGs")) {
                    val predBGs = json!!.getJSONObject("predBGs")
                    if (predBGs.has("IOB")) {
                        val iob = predBGs.getJSONArray("IOB")
                        latest = max(latest, startTime + (iob.length() - 1) * 5 * 60 * 1000L)
                    }
                    if (predBGs.has("aCOB")) {
                        val iob = predBGs.getJSONArray("aCOB")
                        latest = max(latest, startTime + (iob.length() - 1) * 5 * 60 * 1000L)
                    }
                    if (predBGs.has("COB")) {
                        val iob = predBGs.getJSONArray("COB")
                        latest = max(latest, startTime + (iob.length() - 1) * 5 * 60 * 1000L)
                    }
                    if (predBGs.has("UAM")) {
                        val iob = predBGs.getJSONArray("UAM")
                        latest = max(latest, startTime + (iob.length() - 1) * 5 * 60 * 1000L)
                    }
                    if (predBGs.has("ZT")) {
                        val iob = predBGs.getJSONArray("ZT")
                        latest = max(latest, startTime + (iob.length() - 1) * 5 * 60 * 1000L)
                    }
                }
            } catch (e: JSONException) {
                aapsLogger.error("Unhandled exception", e)
            }
            return latest
        }
    val isCarbsRequired: Boolean
        get() = carbsReq > 0

    val isChangeRequested: Boolean
        get() {
            val closedLoopEnabled = constraintChecker.isClosedLoopAllowed()
            // closed loop mode: handle change at driver level
            if (closedLoopEnabled.value()) {
                aapsLogger.debug(LTag.APS, "DEFAULT: Closed mode")
                return tempBasalRequested || bolusRequested()
            }

            // open loop mode: try to limit request
            if (!tempBasalRequested && !bolusRequested()) {
                aapsLogger.debug(LTag.APS, "FALSE: No request")
                return false
            }
            val now = System.currentTimeMillis()
            val activeTemp = iobCobCalculator.getTempBasalIncludingConvertedExtended(now)
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
                    val pumpLimit = pump.pumpDescription.pumpType.tbrSettings?.maxDose ?: 0.0
                    if (percent.toDouble() == pumpLimit) {
                        aapsLogger.debug(LTag.APS, "TRUE: Pump limit")
                        return true
                    }
                }
                // report change bigger than 30%
                var percentMinChangeChange = sp.getDouble(R.string.key_loop_openmode_min_change, 30.0)
                percentMinChangeChange /= 100.0
                val lowThreshold = 1 - percentMinChangeChange
                val highThreshold = 1 + percentMinChangeChange
                var change = percent / 100.0
                if (activeTemp != null) change = percent / activeTemp.convertedToPercent(now, profile).toDouble()
                if (change < lowThreshold || change > highThreshold) {
                    aapsLogger.debug(LTag.APS, "TRUE: Outside allowed range " + change * 100.0 + "%")
                    true
                } else {
                    aapsLogger.debug(LTag.APS, "TRUE: Inside allowed range " + change * 100.0 + "%")
                    false
                }
            } else {
                if (activeTemp == null && rate == pump.baseBasalRate) {
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
                    val pumpLimit = pump.pumpDescription.pumpType.tbrSettings?.maxDose ?: 0.0
                    if (rate == pumpLimit) {
                        aapsLogger.debug(LTag.APS, "TRUE: Pump limit")
                        return true
                    }
                }
                // report change bigger than 30%
                var percentMinChangeChange = sp.getDouble(R.string.key_loop_openmode_min_change, 30.0)
                percentMinChangeChange /= 100.0
                val lowThreshold = 1 - percentMinChangeChange
                val highThreshold = 1 + percentMinChangeChange
                var change = rate / profile.getBasal()
                if (activeTemp != null) change = rate / activeTemp.convertedToAbsolute(now, profile)
                if (change < lowThreshold || change > highThreshold) {
                    aapsLogger.debug(LTag.APS, "TRUE: Outside allowed range " + change * 100.0 + "%")
                    true
                } else {
                    aapsLogger.debug(LTag.APS, "TRUE: Inside allowed range " + change * 100.0 + "%")
                    false
                }
            }
        }

    fun bolusRequested(): Boolean = smb > 0.0
}