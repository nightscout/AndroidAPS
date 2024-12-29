package app.aaps.pump.eopatch.extension

import java.util.*
import java.util.concurrent.TimeUnit

internal val Long.date: Date
    get() = Calendar.getInstance().also { it.timeInMillis = this }.time

fun Long.getDiffDays(isRelative: Boolean = false): Long {
    val inputTimeMillis = this
    val currentTimeMillis = System.currentTimeMillis()
    val diffTimeMillis = if (inputTimeMillis > currentTimeMillis) inputTimeMillis - currentTimeMillis else isRelative.takeOne(currentTimeMillis - inputTimeMillis, 0)

    return TimeUnit.MILLISECONDS.toDays(diffTimeMillis)
}
