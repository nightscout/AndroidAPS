package info.nightscout.core.extensions

import info.nightscout.database.entities.Bolus
import info.nightscout.database.entities.TemporaryBasal
import info.nightscout.database.entities.interfaces.end
import info.nightscout.interfaces.aps.AutosensResult
import info.nightscout.interfaces.insulin.Insulin
import info.nightscout.interfaces.iob.IobTotal
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
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

private fun TemporaryBasal.netExtendedRate(profile: Profile) = rate - profile.getBasal(timestamp)
val TemporaryBasal.durationInMinutes
    get() = T.msecs(duration).mins()

fun TemporaryBasal.toStringFull(profile: Profile, dateUtil: DateUtil): String {
    return when {
        type == TemporaryBasal.Type.FAKE_EXTENDED -> {
            DecimalFormatter.to2Decimal(rate) + "U/h (" + DecimalFormatter.to2Decimal(netExtendedRate(profile)) + "E) @" +
                dateUtil.timeString(timestamp) +
                " " + getPassedDurationToTimeInMinutes(dateUtil.now()) + "/" + durationInMinutes + "'"
        }

        isAbsolute                                -> {
            DecimalFormatter.to2Decimal(rate) + "U/h @" +
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

fun TemporaryBasal.toStringShort(): String =
    if (isAbsolute || type == TemporaryBasal.Type.FAKE_EXTENDED) DecimalFormatter.to2Decimal(rate) + "U/h"
    else "${DecimalFormatter.to0Decimal(rate)}%"

fun TemporaryBasal.iobCalc(time: Long, profile: Profile, insulinInterface: Insulin): IobTotal {
    val result = IobTotal(time)
    val realDuration = getPassedDurationToTimeInMinutes(time)
    var netBasalAmount = 0.0
    if (realDuration > 0) {
        var netBasalRate: Double
        val dia = profile.dia
        val diaAgo = time - dia * 60 * 60 * 1000
        val aboutFiveMinIntervals = ceil(realDuration / 5.0).toInt()
        val tempBolusSpacing = realDuration / aboutFiveMinIntervals.toDouble()
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

fun TemporaryBasal.iobCalc(
    time: Long,
    profile: Profile,
    lastAutosensResult: AutosensResult,
    exercise_mode: Boolean,
    half_basal_exercise_target: Int,
    isTempTarget: Boolean,
    insulinInterface: Insulin
): IobTotal {
    val result = IobTotal(time)
    val realDuration = getPassedDurationToTimeInMinutes(time)
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
        val tempBolusSpacing = realDuration / aboutFiveMinIntervals.toDouble()
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
