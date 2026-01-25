package app.aaps.core.objects.extensions

import app.aaps.core.data.model.BS
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

fun TB.getPassedDurationToTimeInMinutes(time: Long): Int =
    ((min(time, end) - timestamp) / 60.0 / 1000).roundToInt()

val TB.plannedRemainingMinutes: Int
    get() = max(round((end - System.currentTimeMillis()) / 1000.0 / 60).toInt(), 0)

fun TB.convertedToAbsolute(time: Long, profile: Profile): Double =
    if (isAbsolute) rate
    else profile.getBasal(time) * rate / 100

fun TB.convertedToPercent(time: Long, profile: Profile): Int =
    if (!isAbsolute) rate.toInt()
    else (rate / profile.getBasal(time) * 100).toInt()

private fun TB.netExtendedRate(profile: Profile) = rate - profile.getBasal(timestamp)
val TB.durationInMinutes
    get() = T.msecs(duration).mins()

fun TB.toStringFull(profile: Profile, dateUtil: DateUtil, rh: ResourceHelper): String {
    val timeAndDuration = "${dateUtil.timeString(timestamp)} ${getPassedDurationToTimeInMinutes(dateUtil.now())}/${durationInMinutes}'"

    return when {
        type == TB.Type.FAKE_EXTENDED -> {
            rh.gs(app.aaps.core.ui.R.string.temp_basal_tsf_fake_extended, rate, netExtendedRate(profile), timeAndDuration)
        }

        isAbsolute                    -> {
            rh.gs(app.aaps.core.ui.R.string.temp_basal_tsf_absolute, rate, timeAndDuration)
        }

        else                          -> { // percent
            rh.gs(app.aaps.core.ui.R.string.temp_basal_tsf_percent, rate, timeAndDuration)
        }
    }
}

fun TB.toStringShort(rh: ResourceHelper): String =
    if (isAbsolute || type == TB.Type.FAKE_EXTENDED) rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, rate)
    else rh.gs(app.aaps.core.ui.R.string.formatPercent, rate)

fun TB.iobCalc(time: Long, profile: EffectiveProfile): IobTotal {
    if (!isValid) return IobTotal(time)
    val result = IobTotal(time)
    val realDuration = getPassedDurationToTimeInMinutes(time)
    var netBasalAmount = 0.0
    if (realDuration > 0) {
        var netBasalRate: Double
        val insulinEndTime = profile.iCfg.insulinEndTime
        val diaAgo = time - insulinEndTime
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
                val tempBolusPart = BS(
                    timestamp = calcDate,
                    amount = tempBolusSize,
                    type = BS.Type.NORMAL,
                    iCfg = profile.iCfg
                )
                val aIOB = tempBolusPart.iobCalc(time)
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

fun TB.iobCalc(
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
    var netBasalAmount = 0.0
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
                val tempBolusPart = BS(
                    timestamp = calcDate,
                    amount = tempBolusSize,
                    type = BS.Type.NORMAL,
                    iCfg = profile.iCfg
                )
                val aIOB = tempBolusPart.iobCalc(time)
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
