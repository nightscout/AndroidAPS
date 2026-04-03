package app.aaps.pump.eopatch.extension

import java.util.concurrent.TimeUnit

fun Long.getDiffDays(isRelative: Boolean = false): Long {
    val inputTimeMillis = this
    val currentTimeMillis = System.currentTimeMillis()
    val diffTimeMillis = if (inputTimeMillis > currentTimeMillis) inputTimeMillis - currentTimeMillis else isRelative.takeOne(currentTimeMillis - inputTimeMillis, 0)

    return TimeUnit.MILLISECONDS.toDays(diffTimeMillis)
}
