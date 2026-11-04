package app.aaps.pump.aaruy.common.util

import java.text.DecimalFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object AaruyTimeUtil {

    /** @param weeks the day names, starting with Sunday */
    fun getWeekOfDate(timestamp: Long, weeks: Array<String>): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        return weeks[calendar.get(Calendar.DAY_OF_WEEK) - 1]
    }

    /** @return the day name of a `yyyy-MM-dd` date, or of today if the date cannot be read */
    fun dateToWeek(datetime: String, week: Array<String>): String {
        val timestamp = try {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(datetime)?.time
        } catch (_: ParseException) {
            null
        }
        return getWeekOfDate(timestamp ?: System.currentTimeMillis(), week)
    }

    /** @param s the date as `yyyy-MM-dd HH:mm:ss` */
    @Throws(ParseException::class)
    fun dateToStamp(s: String): Long =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(s)?.time ?: throw ParseException(s, 0)

    fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

    fun getTime(date: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(date))

    @Suppress("FunctionName")
    fun get_H_m_String(minute: Int): String {
        val format = DecimalFormat("00")
        return format.format(minute / 60) + ":" + format.format(minute % 60)
    }

    /** @return year, month starting with 1, day, hour, minute and second */
    fun getWholeDetail(time: Long): IntArray {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = time
        return intArrayOf(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            calendar.get(Calendar.SECOND)
        )
    }
}
