package info.nightscout.core.extensions

import info.nightscout.database.entities.Bolus
import info.nightscout.database.entities.ExtendedBolus
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

fun ExtendedBolus.isInProgress(dateUtil: DateUtil): Boolean =
    dateUtil.now() in timestamp..timestamp + duration

val ExtendedBolus.plannedRemainingMinutes: Int
    get() = max(round((end - System.currentTimeMillis()) / 1000.0 / 60).toInt(), 0)

fun ExtendedBolus.toStringFull(dateUtil: DateUtil): String =
    "E " + DecimalFormatter.to2Decimal(rate) + "U/h @" + dateUtil.timeString(timestamp) +
        " " + getPassedDurationToTimeInMinutes(dateUtil.now()) + "/" + T.msecs(duration).mins() + "min"

fun ExtendedBolus.toStringMedium(dateUtil: DateUtil): String =
    DecimalFormatter.to2Decimal(rate) + "U/h " + getPassedDurationToTimeInMinutes(dateUtil.now()) + "/" + T.msecs(duration).mins() + "'"

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
        type = info.nightscout.database.entities.TemporaryBasal.Type.FAKE_EXTENDED
    )

fun ExtendedBolus.iobCalc(time: Long, profile: Profile, insulinInterface: Insulin): IobTotal {
    if (!isValid) return IobTotal(time)
    val result = IobTotal(time)
    val realDuration = getPassedDurationToTimeInMinutes(time)
    if (realDuration > 0) {
        val dia = profile.dia
        val diaAgo = time - dia * 60 * 60 * 1000
        val aboutFiveMinIntervals = ceil(realDuration / 5.0).toInt()
        val spacing = realDuration / aboutFiveMinIntervals.toDouble()
        for (j in 0L until aboutFiveMinIntervals) {
            // find middle of the interval
            val calcDate = (timestamp + j * spacing * 60 * 1000 + 0.5 * spacing * 60 * 1000).toLong()
            if (calcDate > diaAgo && calcDate <= time) {
                val tempBolusSize: Double = rate * spacing / 60.0
                val tempBolusPart = Bolus(
                    timestamp = calcDate,
                    amount = tempBolusSize,
                    type = info.nightscout.database.entities.Bolus.Type.NORMAL
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

fun ExtendedBolus.iobCalc(
    time: Long,
    profile: Profile,
    lastAutosensResult: AutosensResult,
    exerciseMode: Boolean,
    halfBasalExerciseTarget: Int,
    isTempTarget: Boolean,
    insulinInterface: Insulin
): IobTotal {
    if (!isValid) return IobTotal(time)
    val result = IobTotal(time)
    val realDuration = getPassedDurationToTimeInMinutes(time)
    var sensitivityRatio = lastAutosensResult.ratio
    val normalTarget = 100.0
    if (exerciseMode && isTempTarget && profile.getTargetMgdl() >= normalTarget + 5) {
        // w/ target 100, temp target 110 = .89, 120 = 0.8, 140 = 0.67, 160 = .57, and 200 = .44
        // e.g.: Sensitivity ratio set to 0.8 based on temp target of 120; Adjusting basal from 1.65 to 1.35; ISF from 58.9 to 73.6
        val c = halfBasalExerciseTarget - normalTarget
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
                    type = info.nightscout.database.entities.Bolus.Type.NORMAL
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

