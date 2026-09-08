package app.aaps.pump.eopatch.extension

import kotlin.time.Duration.Companion.milliseconds

fun Long.getDiffDays(isRelative: Boolean = false): Long {
    val inputTimeMillis = this
    val currentTimeMillis = System.currentTimeMillis()
    val diffTimeMillis = if (inputTimeMillis > currentTimeMillis) inputTimeMillis - currentTimeMillis else isRelative.takeOne(currentTimeMillis - inputTimeMillis, 0)

    return diffTimeMillis.milliseconds.inWholeDays
}
