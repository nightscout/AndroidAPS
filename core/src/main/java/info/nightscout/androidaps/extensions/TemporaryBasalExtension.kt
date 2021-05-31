package info.nightscout.androidaps.extensions

import info.nightscout.androidaps.data.IobTotal
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.database.entities.TemporaryBasal
import info.nightscout.androidaps.database.entities.TemporaryBasal.Type.Companion.fromString
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.database.interfaces.end
import info.nightscout.androidaps.interfaces.Insulin
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter.to0Decimal
import info.nightscout.androidaps.utils.DecimalFormatter.to2Decimal
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.T
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt

fun TemporaryBasal.getPassedDurationToTimeInMinutes(time: Long): Int =
    ((min(time, end) - timestamp) / 60.0 / 1000).roundToInt()

val TemporaryBasal.plannedRemainingMinutes: Int
    get() = max(round((end - System.currentTimeMillis()) / 1000.0 / 60).toInt(), 0)

fun TemporaryBasal.convertedToAbsolute(time: Long, profile: Profile): Double =
    if (isAbsolute) rate
    else profile.getBasal(time) * rate / 100

fun TemporaryBasal.convertedToPercent(time: Long, profile: Profile): Int =
    if (!isAbsolute) rate.toInt()
    else (rate / profile.getBasal(time) * 100).toInt()

fun TemporaryBasal.netExtendedRate(profile: Profile) = rate - profile.getBasal(timestamp)
val TemporaryBasal.durationInMinutes
    get() = T.msecs(duration).mins()

fun TemporaryBasal.toStringFull(profile: Profile, dateUtil: DateUtil): String {
    return when {
        type == TemporaryBasal.Type.FAKE_EXTENDED -> {
            to2Decimal(rate) + "U/h (" + to2Decimal(netExtendedRate(profile)) + "E) @" +
                dateUtil.timeString(timestamp) +
                " " + getPassedDurationToTimeInMinutes(dateUtil.now()) + "/" + durationInMinutes + "'"
        }

        isAbsolute                                -> {
            to2Decimal(rate) + "U/h @" +
                dateUtil.timeString(timestamp) +
                " " + getPassedDurationToTimeInMinutes(dateUtil.now()) + "/" + durationInMinutes + "'"
        }

        else                                      -> { // percent
            rate.toString() + "% @" +
                dateUtil.timeString(timestamp) +
                " " + getPassedDurationToTimeInMinutes(dateUtil.now()) + "/" + durationInMinutes + "'"
        }
    }
}

fun TemporaryBasal.toJson(isAdd: Boolean, profile: Profile, dateUtil: DateUtil, useAbsolute: Boolean): JSONObject =
    JSONObject()
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("enteredBy", "openaps://" + "AndroidAPS")
        .put("eventType", TherapyEvent.Type.TEMPORARY_BASAL.text)
        .put("isValid", isValid)
        .put("duration", T.msecs(duration).mins())
        .put("rate", rate)
        .put("type", type.name)
        .also {
            if (useAbsolute) it.put("absolute", convertedToAbsolute(timestamp, profile))
            else it.put("percent", convertedToPercent(timestamp, profile) - 100)
            if (interfaceIDs.pumpId != null) it.put("pumpId", interfaceIDs.pumpId)
            if (interfaceIDs.endId != null) it.put("endId", interfaceIDs.endId)
            if (interfaceIDs.pumpType != null) it.put("pumpType", interfaceIDs.pumpType!!.name)
            if (interfaceIDs.pumpSerial != null) it.put("pumpSerial", interfaceIDs.pumpSerial)
            if (isAdd && interfaceIDs.nightscoutId != null) it.put("_id", interfaceIDs.nightscoutId)
        }

/*
        create fake object with nsID and isValid == false
 */
fun temporaryBasalFromNsIdForInvalidating(nsId: String): TemporaryBasal =
    temporaryBasalFromJson(
        JSONObject()
            .put("mills", 1)
            .put("absolute", 1.0)
            .put("duration", 1.0)
            .put("_id", nsId)
            .put("isValid", false)
    )!!

fun temporaryBasalFromJson(jsonObject: JSONObject): TemporaryBasal? {
    val timestamp = JsonHelper.safeGetLongAllowNull(jsonObject, "mills", null) ?: return null
    val percent = JsonHelper.safeGetDoubleAllowNull(jsonObject, "percent")
    val absolute = JsonHelper.safeGetDoubleAllowNull(jsonObject, "absolute")
    val duration = JsonHelper.safeGetLongAllowNull(jsonObject, "duration") ?: return null
    val type = fromString(JsonHelper.safeGetString(jsonObject, "type"))
    val isValid = JsonHelper.safeGetBoolean(jsonObject, "isValid", true)
    val id = JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null) ?: return null
    val pumpId = JsonHelper.safeGetLongAllowNull(jsonObject, "pumpId", null)
    val endPumpId = JsonHelper.safeGetLongAllowNull(jsonObject, "endId", null)
    val pumpType = InterfaceIDs.PumpType.fromString(JsonHelper.safeGetStringAllowNull(jsonObject, "pumpType", null))
    val pumpSerial = JsonHelper.safeGetStringAllowNull(jsonObject, "pumpSerial", null)

    val rate = if (percent != null) percent + 100 else absolute ?: return null
    if (duration == 0L) return null
    if (timestamp == 0L) return null

    return TemporaryBasal(
        timestamp = timestamp,
        rate = rate,
        duration = T.mins(duration).msecs(),
        type = type,
        isAbsolute = percent == null,
        isValid = isValid
    ).also {
        it.interfaceIDs.nightscoutId = id
        it.interfaceIDs.pumpId = pumpId
        it.interfaceIDs.endId = endPumpId
        it.interfaceIDs.pumpType = pumpType
        it.interfaceIDs.pumpSerial = pumpSerial
    }
}

fun TemporaryBasal.toStringShort(): String =
    if (isAbsolute || type == TemporaryBasal.Type.FAKE_EXTENDED) to2Decimal(rate) + "U/h"
    else "${to0Decimal(rate)}%"

fun TemporaryBasal.iobCalc(time: Long, profile: Profile, insulinInterface: Insulin): IobTotal {
    val result = IobTotal(time)
    val realDuration: Int = getPassedDurationToTimeInMinutes(time)
    var netBasalAmount = 0.0
    if (realDuration > 0) {
        var netBasalRate: Double
        val dia = profile.dia
        val diaAgo = time - dia * 60 * 60 * 1000
        val aboutFiveMinIntervals = ceil(realDuration / 5.0).toInt()
        val tempBolusSpacing = (realDuration / aboutFiveMinIntervals).toDouble()
        for (j in 0L until aboutFiveMinIntervals) {
            // find middle of the interval
            val calcDate = (timestamp + j * tempBolusSpacing * 60 * 1000 + 0.5 * tempBolusSpacing * 60 * 1000).toLong()
            val basalRate = profile.getBasal(calcDate)
            netBasalRate = if (isAbsolute) {
                rate - basalRate
            } else {
                (rate - 100) / 100.0 * basalRate
            }
            if (calcDate > diaAgo && calcDate <= time) {
                val tempBolusSize = netBasalRate * tempBolusSpacing / 60.0
                netBasalAmount += tempBolusSize
                val tempBolusPart = Bolus(
                    timestamp = calcDate,
                    amount = tempBolusSize,
                    type = Bolus.Type.NORMAL
                )
                val aIOB = insulinInterface.iobCalcForTreatment(tempBolusPart, time, dia)
                result.basaliob += aIOB.iobContrib
                result.activity += aIOB.activityContrib
                result.netbasalinsulin += tempBolusPart.amount
                if (tempBolusPart.amount > 0) {
                    result.hightempinsulin += tempBolusPart.amount
                }
            }
        }
    }
    result.netInsulin = netBasalAmount
    return result
}

fun TemporaryBasal.iobCalc(time: Long, profile: Profile, lastAutosensResult: AutosensResult, exercise_mode: Boolean, half_basal_exercise_target: Int, isTempTarget: Boolean, insulinInterface: Insulin): IobTotal {
    val result = IobTotal(time)
    val realDuration: Double = getPassedDurationToTimeInMinutes(time).toDouble()
    var netBasalAmount = 0.0
    var sensitivityRatio = lastAutosensResult.ratio
    val normalTarget = 100.0
    if (exercise_mode && isTempTarget && profile.getTargetMgdl() >= normalTarget + 5) {
        // w/ target 100, temp target 110 = .89, 120 = 0.8, 140 = 0.67, 160 = .57, and 200 = .44
        // e.g.: Sensitivity ratio set to 0.8 based on temp target of 120; Adjusting basal from 1.65 to 1.35; ISF from 58.9 to 73.6
        val c = half_basal_exercise_target - normalTarget
        sensitivityRatio = c / (c + profile.getTargetMgdl() - normalTarget)
    }
    if (realDuration > 0) {
        var netBasalRate: Double
        val dia = profile.dia
        val diaAgo = time - dia * 60 * 60 * 1000
        val aboutFiveMinIntervals = ceil(realDuration / 5.0).toInt()
        val tempBolusSpacing = realDuration / aboutFiveMinIntervals
        for (j in 0L until aboutFiveMinIntervals) {
            // find middle of the interval
            val calcDate = (timestamp + j * tempBolusSpacing * 60 * 1000 + 0.5 * tempBolusSpacing * 60 * 1000).toLong()
            var basalRate = profile.getBasal(calcDate)
            basalRate *= sensitivityRatio
            netBasalRate = if (isAbsolute) {
                rate - basalRate
            } else {
                val abs: Double = rate / 100.0 * profile.getBasal(calcDate)
                abs - basalRate
            }
            if (calcDate > diaAgo && calcDate <= time) {
                val tempBolusSize = netBasalRate * tempBolusSpacing / 60.0
                netBasalAmount += tempBolusSize
                val tempBolusPart = Bolus(
                    timestamp = calcDate,
                    amount = tempBolusSize,
                    type = Bolus.Type.NORMAL
                )
                val aIOB = insulinInterface.iobCalcForTreatment(tempBolusPart, time, dia)
                result.basaliob += aIOB.iobContrib
                result.activity += aIOB.activityContrib
                result.netbasalinsulin += tempBolusPart.amount
                if (tempBolusPart.amount > 0) {
                    result.hightempinsulin += tempBolusPart.amount
                }
            }
        }
    }
    result.netInsulin = netBasalAmount
    return result
}
