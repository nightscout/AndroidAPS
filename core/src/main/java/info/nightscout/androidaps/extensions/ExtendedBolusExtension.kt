package info.nightscout.androidaps.extensions

import info.nightscout.androidaps.data.IobTotal
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.database.entities.ExtendedBolus
import info.nightscout.androidaps.database.entities.TemporaryBasal
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.database.interfaces.end
import info.nightscout.androidaps.interfaces.Insulin
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter.to2Decimal
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.T
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt

fun ExtendedBolus.isInProgress(dateUtil: DateUtil): Boolean =
    dateUtil.now() in timestamp..timestamp + duration

val ExtendedBolus.plannedRemainingMinutes: Int
    get() = max(round((end - System.currentTimeMillis()) / 1000.0 / 60).toInt(), 0)

fun ExtendedBolus.toStringFull(dateUtil: DateUtil): String =
    "E " + to2Decimal(rate) + "U/h @" + dateUtil.timeString(timestamp) +
        " " + getPassedDurationToTimeInMinutes(dateUtil.now()) + "/" + T.msecs(duration).mins() + "min"

fun ExtendedBolus.toStringMedium(dateUtil: DateUtil): String =
    to2Decimal(rate) + "U/h " + getPassedDurationToTimeInMinutes(dateUtil.now()) + "/" + T.msecs(duration).mins() + "'"

fun ExtendedBolus.toStringTotal(): String = "${to2Decimal(amount)}U ( ${to2Decimal(rate)} U/h )"

fun ExtendedBolus.getPassedDurationToTimeInMinutes(time: Long): Int =
    ((min(time, end) - timestamp) / 60.0 / 1000).roundToInt()

fun ExtendedBolus.toTemporaryBasal(profile: Profile): TemporaryBasal =
    TemporaryBasal(
        timestamp = timestamp,
        duration = duration,
        rate = profile.getBasal(timestamp) + rate,
        isAbsolute = true,
        isValid = isValid,
        interfaceIDs_backing = interfaceIDs_backing,
        type = TemporaryBasal.Type.FAKE_EXTENDED
    )

fun ExtendedBolus.toJson(isAdd: Boolean, profile: Profile, dateUtil: DateUtil, useAbsolute: Boolean): JSONObject =
    if (isEmulatingTempBasal)
        toTemporaryBasal(profile)
            .toJson(isAdd, profile, dateUtil, useAbsolute)
            .put("extendedEmulated", toRealJson(isAdd, dateUtil))
    else toRealJson(isAdd, dateUtil)

fun ExtendedBolus.toRealJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject =
    JSONObject()
        .put("created_at", dateUtil.toISOString(timestamp))
        .put("enteredBy", "openaps://" + "AndroidAPS")
        .put("eventType", TherapyEvent.Type.COMBO_BOLUS.text)
        .put("duration", T.msecs(duration).mins())
        .put("splitNow", 0)
        .put("splitExt", 100)
        .put("enteredinsulin", amount)
        .put("relative", rate)
        .put("isValid", isValid)
        .put("isEmulatingTempBasal", isEmulatingTempBasal)
        .also {
            if (interfaceIDs.pumpId != null) it.put("pumpId", interfaceIDs.pumpId)
            if (interfaceIDs.endId != null) it.put("endId", interfaceIDs.endId)
            if (interfaceIDs.pumpType != null) it.put("pumpType", interfaceIDs.pumpType!!.name)
            if (interfaceIDs.pumpSerial != null) it.put("pumpSerial", interfaceIDs.pumpSerial)
            if (isAdd && interfaceIDs.nightscoutId != null) it.put("_id", interfaceIDs.nightscoutId)
        }

/*
        create fake object with nsID and isValid == false
 */
fun extendedBolusFromNsIdForInvalidating(nsId: String): ExtendedBolus =
    extendedBolusFromJson(
        JSONObject()
            .put("mills", 1)
            .put("amount", -1.0)
            .put("enteredinsulin", -1.0)
            .put("duration", -1.0)
            .put("splitNow", 0)
            .put("splitExt", 100)
            .put("_id", nsId)
            .put("isValid", false)
    )!!

fun extendedBolusFromJson(jsonObject: JSONObject): ExtendedBolus? {
    val timestamp = JsonHelper.safeGetLongAllowNull(jsonObject, "mills", null) ?: return null
    if (JsonHelper.safeGetIntAllowNull(jsonObject, "splitNow") != 0) return null
    if (JsonHelper.safeGetIntAllowNull(jsonObject, "splitExt") != 100) return null
    val amount = JsonHelper.safeGetDoubleAllowNull(jsonObject, "enteredinsulin") ?: return null
    val duration = JsonHelper.safeGetLongAllowNull(jsonObject, "duration") ?: return null
    val isValid = JsonHelper.safeGetBoolean(jsonObject, "isValid", true)
    val isEmulatingTempBasal = JsonHelper.safeGetBoolean(jsonObject, "isEmulatingTempBasal", false)
    val id = JsonHelper.safeGetStringAllowNull(jsonObject, "_id", null) ?: return null
    val pumpId = JsonHelper.safeGetLongAllowNull(jsonObject, "pumpId", null)
    val endPumpId = JsonHelper.safeGetLongAllowNull(jsonObject, "endId", null)
    val pumpType = InterfaceIDs.PumpType.fromString(JsonHelper.safeGetStringAllowNull(jsonObject, "pumpType", null))
    val pumpSerial = JsonHelper.safeGetStringAllowNull(jsonObject, "pumpSerial", null)

    if (timestamp == 0L) return null
    if (duration == 0L) return null
    if (amount == 0.0) return null

    return ExtendedBolus(
        timestamp = timestamp,
        amount = amount,
        duration = T.mins(duration).msecs(),
        isEmulatingTempBasal = isEmulatingTempBasal,
        isValid = isValid
    ).also {
        it.interfaceIDs.nightscoutId = id
        it.interfaceIDs.pumpId = pumpId
        it.interfaceIDs.endId = endPumpId
        it.interfaceIDs.pumpType = pumpType
        it.interfaceIDs.pumpSerial = pumpSerial
    }
}

fun ExtendedBolus.iobCalc(time: Long, profile: Profile, insulinInterface: Insulin): IobTotal {
    val result = IobTotal(time)
    val realDuration = getPassedDurationToTimeInMinutes(time)
    if (realDuration > 0) {
        val dia = profile.dia
        val diaAgo = time - dia * 60 * 60 * 1000
        val aboutFiveMinIntervals = ceil(realDuration / 5.0).toInt()
        val spacing = realDuration / aboutFiveMinIntervals
        for (j in 0L until aboutFiveMinIntervals) {
            // find middle of the interval
            val calcDate = (timestamp + j * spacing * 60 * 1000 + 0.5 * spacing * 60 * 1000).toLong()
            if (calcDate > diaAgo && calcDate <= time) {
                val tempBolusSize: Double = rate * spacing / 60.0
                val tempBolusPart = Bolus(
                    timestamp = calcDate,
                    amount = tempBolusSize,
                    type = Bolus.Type.NORMAL
                )
                val aIOB = insulinInterface.iobCalcForTreatment(tempBolusPart, time, dia)
                result.iob += aIOB.iobContrib
                result.activity += aIOB.activityContrib
                result.extendedBolusInsulin += tempBolusPart.amount
            }
        }
    }
    return result
}

fun ExtendedBolus.iobCalc(time: Long, profile: Profile, lastAutosensResult: AutosensResult, exercise_mode: Boolean, half_basal_exercise_target: Int, isTempTarget: Boolean, insulinInterface: Insulin): IobTotal {
    val result = IobTotal(time)
    val realDuration = getPassedDurationToTimeInMinutes(time)
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
        val spacing = realDuration / aboutFiveMinIntervals
        for (j in 0L until aboutFiveMinIntervals) {
            // find middle of the interval
            val calcDate = (timestamp + j * spacing * 60 * 1000 + 0.5 * spacing * 60 * 1000).toLong()
            val basalRate = profile.getBasal(calcDate)
            val basalRateCorrection = basalRate * (sensitivityRatio - 1)
            netBasalRate = rate - basalRateCorrection
            if (calcDate > diaAgo && calcDate <= time) {
                val tempBolusSize = netBasalRate * spacing / 60.0
                val tempBolusPart = Bolus(
                    timestamp = calcDate,
                    amount = tempBolusSize,
                    type = Bolus.Type.NORMAL
                )
                val aIOB = insulinInterface.iobCalcForTreatment(tempBolusPart, time, dia)
                result.iob += aIOB.iobContrib
                result.activity += aIOB.activityContrib
                result.extendedBolusInsulin += tempBolusPart.amount
            }
        }
    }
    return result
}

