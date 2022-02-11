package info.nightscout.androidaps.plugins.pump.eopatch.extension

import java.util.*
import java.util.concurrent.TimeUnit

internal val Long.date: Date
    get() = Calendar.getInstance().also { it.timeInMillis = this }.time

fun Long.getDiffTime(isRelative: Boolean = false): Triple<Long, Long, Long> {
    val inputTimeMillis = this
    val currentTimeMillis = System.currentTimeMillis()
    val diffTimeMillis = if (inputTimeMillis > currentTimeMillis) inputTimeMillis - currentTimeMillis else isRelative.takeOne(currentTimeMillis - inputTimeMillis, 0)

    val hours = TimeUnit.MILLISECONDS.toHours(diffTimeMillis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffTimeMillis - TimeUnit.HOURS.toMillis(hours))
    val seconds = TimeUnit.MILLISECONDS.toSeconds(diffTimeMillis - TimeUnit.HOURS.toMillis(hours) - TimeUnit.MINUTES.toMillis(minutes))

    return Triple(hours, minutes, seconds)
}

fun Long.getDiffTime(startTimeMillis: Long): Triple<Long, Long, Long> {
    val inputTimeMillis = this
    val diffTimeMillis = if (inputTimeMillis > startTimeMillis) inputTimeMillis - startTimeMillis else 0

    val hours = TimeUnit.MILLISECONDS.toHours(diffTimeMillis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffTimeMillis - TimeUnit.HOURS.toMillis(hours))
    val seconds = TimeUnit.MILLISECONDS.toSeconds(diffTimeMillis - TimeUnit.HOURS.toMillis(hours) - TimeUnit.MINUTES.toMillis(minutes))

    return Triple(hours, minutes, seconds)
}

fun Long.getDiffDays(isRelative: Boolean = false): Long {
    val inputTimeMillis = this
    val currentTimeMillis = System.currentTimeMillis()
    val diffTimeMillis = if (inputTimeMillis > currentTimeMillis) inputTimeMillis - currentTimeMillis else isRelative.takeOne(currentTimeMillis - inputTimeMillis, 0)

    return TimeUnit.MILLISECONDS.toDays(diffTimeMillis)
}

fun Long.getSeconds() : Long {
    return (this/1000)*1000
}
