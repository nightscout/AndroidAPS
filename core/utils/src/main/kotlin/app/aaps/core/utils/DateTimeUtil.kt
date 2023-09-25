package app.aaps.core.utils

import org.joda.time.LocalDateTime
import org.joda.time.Minutes
import org.joda.time.Seconds
import java.util.Calendar
import java.util.GregorianCalendar

/*
* Created by andy on 10/25/18.
*/
/**
 * This is simple version of ATechDate, limited only to one format (yyyymmddHHMIss)
 */
object DateTimeUtil {

    /**
     * DateTime is packed as long: yyyymmddHHMMss
     *
     * @param aTechDateTime
     * @return
     */
    fun toLocalDateTime(aTechDateTime: Long): LocalDateTime {
        var dateTime = aTechDateTime
        val year = (dateTime / 10000000000L).toInt()
        dateTime -= year * 10000000000L
        val month = (dateTime / 100000000L).toInt()
        dateTime -= month * 100000000L
        val dayOfMonth = (dateTime / 1000000L).toInt()
        dateTime -= dayOfMonth * 1000000L
        val hourOfDay = (dateTime / 10000L).toInt()
        dateTime -= hourOfDay * 10000L
        val minute = (dateTime / 100L).toInt()
        dateTime -= minute * 100L
        val second = dateTime.toInt()
        return LocalDateTime(year, month, dayOfMonth, hourOfDay, minute, second)
    }

    /**
     * DateTime is packed as long: yyyymmddHHMMss
     *
     * @param aTechDateTime
     * @return
     */
    private fun toGregorianCalendar(aTechDateTime: Long): GregorianCalendar {
        var dateTime = aTechDateTime
        val year = (dateTime / 10000000000L).toInt()
        dateTime -= year * 10000000000L
        val month = (dateTime / 100000000L).toInt()
        dateTime -= month * 100000000L
        val dayOfMonth = (dateTime / 1000000L).toInt()
        dateTime -= dayOfMonth * 1000000L
        val hourOfDay = (dateTime / 10000L).toInt()
        dateTime -= hourOfDay * 10000L
        val minute = (dateTime / 100L).toInt()
        dateTime -= minute * 100L
        val second = dateTime.toInt()
        return GregorianCalendar(year, month - 1, dayOfMonth, hourOfDay, minute, second)
    }

    fun toATechDate(ldt: LocalDateTime): Long {
        var aTechDateTime = 0L
        aTechDateTime += ldt.year * 10000000000L
        aTechDateTime += ldt.monthOfYear * 100000000L
        aTechDateTime += ldt.dayOfMonth * 1000000L
        aTechDateTime += ldt.hourOfDay * 10000L
        aTechDateTime += ldt.minuteOfHour * 100L
        aTechDateTime += ldt.secondOfMinute.toLong()
        return aTechDateTime
    }

    fun toATechDate(gc: GregorianCalendar): Long {
        var aTechDateTime = 0L
        aTechDateTime += gc[Calendar.YEAR] * 10000000000L
        aTechDateTime += (gc[Calendar.MONTH] + 1) * 100000000L
        aTechDateTime += gc[Calendar.DAY_OF_MONTH] * 1000000L
        aTechDateTime += gc[Calendar.HOUR_OF_DAY] * 10000L
        aTechDateTime += gc[Calendar.MINUTE] * 100L
        aTechDateTime += gc[Calendar.SECOND].toLong()
        return aTechDateTime
    }

    @JvmStatic fun toATechDate(timeInMillis: Long): Long {
        val gc = GregorianCalendar()
        gc.timeInMillis = timeInMillis
        return toATechDate(gc)
    }

    /*
    public static boolean isSameDay(LocalDateTime ldt1, LocalDateTime ldt2) {

        return (ldt1.getYear() == ldt2.getYear() && //
                ldt1.getMonthOfYear() == ldt2.getMonthOfYear() && //
                ldt1.getDayOfMonth() == ldt2.getDayOfMonth());

    }
*/
    fun isSameDay(ldt1: Long, ldt2: Long): Boolean {
        val day1 = ldt1 / 10000L
        val day2 = ldt2 / 10000L
        return day1 == day2
    }

    fun toATechDate(year: Int, month: Int, dayOfMonth: Int, hour: Int, minutes: Int, seconds: Int): Long {
        var aTechDateTime = 0L
        aTechDateTime += year * 10000000000L
        aTechDateTime += month * 100000000L
        aTechDateTime += dayOfMonth * 1000000L
        aTechDateTime += hour * 10000L
        aTechDateTime += minutes * 100L
        aTechDateTime += seconds.toLong()
        return aTechDateTime
    }

    fun toString(aTechDateTime: Long): String {
        var dateTime = aTechDateTime
        val year = (dateTime / 10000000000L).toInt()
        dateTime -= year * 10000000000L
        val month = (dateTime / 100000000L).toInt()
        dateTime -= month * 100000000L
        val dayOfMonth = (dateTime / 1000000L).toInt()
        dateTime -= dayOfMonth * 1000000L
        val hourOfDay = (dateTime / 10000L).toInt()
        dateTime -= hourOfDay * 10000L
        val minute = (dateTime / 100L).toInt()
        dateTime -= minute * 100L
        val second = dateTime.toInt()
        return getZeroPrefixed(dayOfMonth) + "." + getZeroPrefixed(month) + "." + year + " " +  //
            getZeroPrefixed(hourOfDay) + ":" + getZeroPrefixed(minute) + ":" + getZeroPrefixed(second)
    }

    fun toString(gc: GregorianCalendar): String {
        return (getZeroPrefixed(gc[Calendar.DAY_OF_MONTH]) + "." + getZeroPrefixed(gc[Calendar.MONTH] + 1) + "."
            + gc[Calendar.YEAR] + " "
            +  //
            getZeroPrefixed(gc[Calendar.HOUR_OF_DAY]) + ":" + getZeroPrefixed(gc[Calendar.MINUTE]) + ":"
            + getZeroPrefixed(gc[Calendar.SECOND]))
    }

    @JvmStatic fun toStringFromTimeInMillis(timeInMillis: Long): String {
        val gc = GregorianCalendar()
        gc.timeInMillis = timeInMillis
        return toString(gc)
    }

    private fun getZeroPrefixed(number: Int): String {
        return if (number < 10) "0$number" else "" + number
    }

    fun getYear(aTechDateTime: Long?): Int {
        return if (aTechDateTime == null || aTechDateTime == 0L) {
            2000
        } else (aTechDateTime / 10000000000L).toInt()
    }

    fun toMillisFromATD(aTechDateTime: Long): Long {
        val gc = toGregorianCalendar(aTechDateTime)
        return gc.timeInMillis
    }

    fun getATechDateDifferenceAsMinutes(date1: Long, date2: Long): Int {
        val minutes = Minutes.minutesBetween(toLocalDateTime(date1), toLocalDateTime(date2))
        return minutes.minutes
    }

    fun getATechDateDifferenceAsSeconds(date1: Long, date2: Long): Int {
        val seconds = Seconds.secondsBetween(toLocalDateTime(date1), toLocalDateTime(date2))
        return seconds.seconds
    }

    fun getMillisFromATDWithAddedMinutes(atd: Long, minutesDiff: Int): Long {
        val oldestEntryTime = toGregorianCalendar(atd)
        oldestEntryTime.add(Calendar.MINUTE, minutesDiff)
        return oldestEntryTime.timeInMillis
    }

    fun getATDWithAddedSeconds(atd: Long, addedSeconds: Int): Long {
        val oldestEntryTime = toGregorianCalendar(atd)
        oldestEntryTime.add(Calendar.SECOND, addedSeconds)
        return toATechDate(oldestEntryTime.timeInMillis)
    }

    /*
    public static long getATDWithAddedMinutes(Long atd, int minutesDiff) {
        GregorianCalendar oldestEntryTime = DateTimeUtil.toGregorianCalendar(atd);
        oldestEntryTime.add(Calendar.MINUTE, minutesDiff);

        return toATechDate(oldestEntryTime);
    }
*/
    fun getATDWithAddedMinutes(oldestEntryTime: GregorianCalendar, minutesDiff: Int): Long {
        oldestEntryTime.add(Calendar.MINUTE, minutesDiff)
        return toATechDate(oldestEntryTime)
    }

    /*
    public static long getTimeInFutureFromMinutes(long startTime, int minutes) {
        return startTime + getTimeInMs(minutes);
    }
*/
    @JvmStatic fun getTimeInFutureFromMinutes(minutes: Int): Long {
        return System.currentTimeMillis() + getTimeInMs(minutes)
    }

    private fun getTimeInMs(minutes: Int): Long {
        return getTimeInS(minutes) * 1000L
    }

    private fun getTimeInS(minutes: Int): Int {
        return minutes * 60
    }
}
