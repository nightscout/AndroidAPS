package info.nightscout.plugins.aps

import android.text.Spanned
import dagger.android.HasAndroidInjector
import info.nightscout.core.extensions.convertedToAbsolute
import info.nightscout.core.extensions.convertedToPercent
import info.nightscout.core.ui.R
import info.nightscout.core.utils.HtmlHelper
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.interfaces.aps.APSResult
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.ConstraintsChecker
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.iob.IobTotal
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.pump.defs.PumpDescription
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max

/**
 * Created by mike on 09.06.2016.
 */
@Suppress("LeakingThis")
open class APSResultObject @Inject constructor(val injector: HasAndroidInjector) : APSResult {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var sp: SP
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var decimalFormatter: DecimalFormatter

    override var date: Long = 0
    override var reason: String = ""
    override var rate = 0.0
    override var percent = 0
    override var usePercent = false
    override var duration = 0
    override var isTempBasalRequested = false
    override var iob: IobTotal? = null
    override var json: JSONObject? = JSONObject()
    override var hasPredictions = false
    override var smb = 0.0 // super micro bolus in units
    override var deliverAt: Long = 0
    override var targetBG = 0.0
    override var carbsReq = 0
    override var carbsReqWithin = 0
    override var inputConstraints: Constraint<Double>? = null
    override var rateConstraint: Constraint<Double>? = null
    override var percentConstraint: Constraint<Int>? = null
    override var smbConstraint: Constraint<Double>? = null

    init {
        injector.androidInjector().inject(this)
    }

    override val carbsRequiredText: String
        get() = rh.gs(R.string.carbsreq, carbsReq, carbsReqWithin)

    override fun toString(): String {
        val pump = activePlugin.activePump
        if (isChangeRequested) {
            // rate
            var ret: String = if (rate == 0.0 && duration == 0) "${rh.gs(R.string.cancel_temp)} "
            else if (rate == -1.0) "${rh.gs(R.string.let_temp_basal_run)}\n"
            else if (usePercent) "${rh.gs(R.string.rate)}: ${decimalFormatter.to2Decimal(percent.toDouble())}% (${decimalFormatter.to2Decimal(percent * pump.baseBasalRate / 100.0)} U/h) " +
                "${rh.gs(R.string.duration)}: ${decimalFormatter.to2Decimal(duration.toDouble())} min "
            else "${rh.gs(R.string.rate)}: ${decimalFormatter.to2Decimal(rate)} U/h (${decimalFormatter.to2Decimal(rate / pump.baseBasalRate * 100)}%) " +
                "${rh.gs(R.string.duration)}: ${decimalFormatter.to2Decimal(duration.toDouble())} min "
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

    override fun toSpanned(): Spanned {
        val pump = activePlugin.activePump
        if (isChangeRequested) {
            // rate
            var ret: String =
                if (rate == 0.0 && duration == 0) rh.gs(R.string.cancel_temp) + "<br>" else if (rate == -1.0) rh.gs(R.string.let_temp_basal_run) + "<br>" else if (usePercent) "<b>" + rh.gs(
                    R.string.rate
                ) + "</b>: " + decimalFormatter.to2Decimal(
                    percent.toDouble()
                ) + "% " +
                    "(" + decimalFormatter.to2Decimal(percent * pump.baseBasalRate / 100.0) + " U/h)<br>" +
                    "<b>" + rh.gs(R.string.duration) + "</b>: " + decimalFormatter.to2Decimal(duration.toDouble()) + " min<br>" else "<b>" + rh.gs(R.string.rate) + "</b>: " + decimalFormatter.to2Decimal(
                    rate
                ) + " U/h " +
                    "(" + decimalFormatter.to2Decimal(rate / pump.baseBasalRate * 100.0) + "%) <br>" +
                    "<b>" + rh.gs(R.string.duration) + "</b>: " + decimalFormatter.to2Decimal(duration.toDouble()) + " min<br>"

            // smb
            if (smb != 0.0) ret += "<b>" + "SMB" + "</b>: " + decimalFormatter.toPumpSupportedBolus(smb, activePlugin.activePump.pumpDescription.bolusStep) + "<br>"
            if (isCarbsRequired) {
                ret += "$carbsRequiredText<br>"
            }

            // reason
            ret += "<b>" + rh.gs(R.string.reason) + "</b>: " + reason.replace("<", "&lt;").replace(">", "&gt;")
            return HtmlHelper.fromHtml(ret)
        }
        return if (isCarbsRequired) {
            HtmlHelper.fromHtml(carbsRequiredText)
        } else HtmlHelper.fromHtml(rh.gs(R.string.nochangerequested))
    }

    override fun newAndClone(injector: HasAndroidInjector): APSResult {
        val newResult = APSResultObject(injector)
        doClone(newResult)
        return newResult
    }

    protected fun doClone(newResult: APSResultObject) {
        newResult.date = date
        newResult.reason = reason
        newResult.rate = rate
        newResult.duration = duration
        newResult.isTempBasalRequested = isTempBasalRequested
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

    override fun json(): JSONObject? {
        val json = JSONObject()
        if (isChangeRequested) {
            json.put("rate", rate)
            json.put("duration", duration)
            json.put("reason", reason)
        }
        return json
    }

    override val predictions: MutableList<GlucoseValue>
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
    override val latestPredictionsTime: Long
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
    override val isChangeRequested: Boolean
        get() {
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
                var percentMinChangeChange = sp.getDouble(info.nightscout.core.utils.R.string.key_loop_openmode_min_change, 30.0)
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
                var percentMinChangeChange = sp.getDouble(info.nightscout.core.utils.R.string.key_loop_openmode_min_change, 30.0)
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
}