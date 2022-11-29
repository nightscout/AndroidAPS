package info.nightscout.core.utils

import org.joda.time.DateTime

object MidnightUtils {
    /*
 * Midnight time conversion
 */
    fun secondsFromMidnight(): Int {
        val passed = DateTime().millisOfDay.toLong()
        return (passed / 1000).toInt()
    }

    fun secondsFromMidnight(date: Long): Int {
        val passed = DateTime(date).millisOfDay.toLong()
        return (passed / 1000).toInt()
    }

    fun milliSecFromMidnight(date: Long): Long {
        return DateTime(date).millisOfDay.toLong()
    }
}