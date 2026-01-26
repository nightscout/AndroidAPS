package app.aaps.core.objects.extensions

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.TB
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt

fun EB.isInProgress(dateUtil: DateUtil): Boolean =
    dateUtil.now() in timestamp..timestamp + duration

val EB.plannedRemainingMinutes: Int
    get() = max(round((end - System.currentTimeMillis()) / 1000.0 / 60).toInt(), 0)

fun EB.toStringFull(dateUtil: DateUtil, rh:ResourceHelper): String =
    rh.gs(app.aaps.core.ui.R.string.extended_bolus_full, rate, dateUtil.timeString(timestamp), getPassedDurationToTimeInMinutes(dateUtil.now()), T.msecs(duration).mins())

fun EB.toStringMedium(dateUtil: DateUtil, rh:ResourceHelper): String =
    rh.gs(app.aaps.core.ui.R.string.extended_bolus_medium, rate, getPassedDurationToTimeInMinutes(dateUtil.now()), T.msecs(duration).mins())

fun EB.getPassedDurationToTimeInMinutes(time: Long): Int =
    ((min(time, end) - timestamp) / 60.0 / 1000).roundToInt()

fun EB.toTemporaryBasal(profile: Profile): TB =
    TB(
        timestamp = timestamp,
        duration = duration,
        rate = profile.getBasal(timestamp) + rate,
        isAbsolute = true,
        isValid = isValid,
        ids = ids,
        type = TB.Type.FAKE_EXTENDED
    )

fun EB.iobCalc(time: Long, profile: EffectiveProfile): IobTotal {
    if (!isValid) return IobTotal(time)
    val result = IobTotal(time)
    val realDuration = getPassedDurationToTimeInMinutes(time)
    if (realDuration > 0) {
        val insulinEndTime = profile.iCfg.insulinEndTime
        val diaAgo = time - insulinEndTime
        val aboutFiveMinIntervals = ceil(realDuration / 5.0).toInt()
        val spacing = realDuration / aboutFiveMinIntervals.toDouble()
        for (j in 0L until aboutFiveMinIntervals) {
            // find middle of the interval
            val calcDate = (timestamp + j * spacing * 60 * 1000 + 0.5 * spacing * 60 * 1000).toLong()
            if (calcDate > diaAgo && calcDate <= time) {
                val tempBolusSize: Double = rate * spacing / 60.0
                val tempBolusPart = BS(
                    timestamp = calcDate,
                    amount = tempBolusSize,
                    type = BS.Type.NORMAL,
                    iCfg = profile.iCfg
                )
                val aIOB = tempBolusPart.iobCalc(time)
                result.iob += aIOB.iobContrib
                result.activity += aIOB.activityContrib
                result.extendedBolusInsulin += tempBolusPart.amount
            }
        }
    }
    return result
}

fun EB.iobCalc(
    time: Long,
    profile: EffectiveProfile,
    lastAutosensResult: AutosensResult,
    exerciseMode: Boolean,
    halfBasalExerciseTarget: Int,
    isTempTarget: Boolean
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
        val insulinEndTime = profile.iCfg.insulinEndTime
        val diaAgo = time - insulinEndTime
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
                val tempBolusPart = BS(
                    timestamp = calcDate,
                    amount = tempBolusSize,
                    type = BS.Type.NORMAL,
                    iCfg = profile.iCfg
                )
                val aIOB = tempBolusPart.iobCalc(time)
                result.iob += aIOB.iobContrib
                result.activity += aIOB.activityContrib
                result.extendedBolusInsulin += tempBolusPart.amount
            }
        }
    }
    return result
}

