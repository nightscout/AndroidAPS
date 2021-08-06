package info.nightscout.androidaps.extensions

import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.DecimalFormatter.to2Decimal
import info.nightscout.androidaps.utils.T
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

val PumpSync.PumpState.TemporaryBasal.end: Long
    get() = timestamp + duration

val PumpSync.PumpState.TemporaryBasal.plannedRemainingMinutes: Long
    get() = max(T.msecs(end - System.currentTimeMillis()).mins(), 0L)

val PumpSync.PumpState.TemporaryBasal.plannedRemainingMinutesRoundedUp: Int
    get() = max(ceil((end - System.currentTimeMillis()) / 1000.0 / 60).toInt(), 0)

val PumpSync.PumpState.TemporaryBasal.durationInMinutes: Int
    get() = T.msecs(duration).mins().toInt()

fun PumpSync.PumpState.TemporaryBasal.getPassedDurationToTimeInMinutes(time: Long): Int =
    ((min(time, end) - timestamp) / 60.0 / 1000).roundToInt()

fun PumpSync.PumpState.TemporaryBasal.convertedToAbsolute(time: Long, profile: Profile): Double =
    if (isAbsolute) rate
    else profile.getBasal(time) * rate / 100

fun PumpSync.PumpState.TemporaryBasal.toStringFull(dateUtil: DateUtil): String {
    return when {
        isAbsolute -> {
            DecimalFormatter.to2Decimal(rate) + "U/h @" +
                dateUtil.timeString(timestamp) +
                " " + getPassedDurationToTimeInMinutes(dateUtil.now()) + "/" + durationInMinutes + "'"
        }

        else       -> { // percent
            rate.toString() + "% @" +
                dateUtil.timeString(timestamp) +
                " " + getPassedDurationToTimeInMinutes(dateUtil.now()) + "/" + durationInMinutes + "'"
        }
    }
}

val PumpSync.PumpState.ExtendedBolus.end: Long
    get() = timestamp + duration

val PumpSync.PumpState.ExtendedBolus.plannedRemainingMinutes: Long
    get() = max(T.msecs(end - System.currentTimeMillis()).mins(), 0L)

fun PumpSync.PumpState.ExtendedBolus.getPassedDurationToTimeInMinutes(time: Long): Int =
    ((min(time, end) - timestamp) / 60.0 / 1000).roundToInt()

fun PumpSync.PumpState.ExtendedBolus.toStringFull(dateUtil: DateUtil): String =
    "E " + to2Decimal(rate) + "U/h @" +
        dateUtil.timeString(timestamp) +
        " " + getPassedDurationToTimeInMinutes(dateUtil.now()) + "/" + T.msecs(duration).mins() + "min"

