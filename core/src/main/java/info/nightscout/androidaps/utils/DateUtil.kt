package info.nightscout.androidaps.utils

import android.content.Context
import androidx.collection.LongSparseArray
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.ISODateTimeFormat
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import java.util.stream.Collectors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * The Class DateUtil. A simple wrapper around SimpleDateFormat to ease the handling of iso date string &lt;-&gt; date obj
 * with TZ
 */
@Singleton
open class DateUtil @Inject constructor(private val context: Context) {

    /**
     * The date format in iso.
     */
    @Suppress("PrivatePropertyName")
    private val FORMAT_DATE_ISO_OUT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

    /**
     * Takes in an ISO date string of the following format:
     * yyyy-mm-ddThh:mm:ss.ms+HoMo
     *
     * @param isoDateString the iso date string
     * @return the date
     */
    fun fromISODateString(isoDateString: String): Long {
        val parser = ISODateTimeFormat.dateTimeParser()
        val dateTime = DateTime.parse(isoDateString, parser)
        return dateTime.toDate().time
    }

    /**
     * Render date
     *
     * @param date   the date obj
     * @param format - if not specified, will use FORMAT_DATE_ISO
     * @param tz     - tz to set to, if not specified uses local timezone
     * @return the iso-formatted date string
     */
    @JvmOverloads
    fun toISOString(date: Long, format: String = FORMAT_DATE_ISO_OUT, tz: TimeZone = TimeZone.getTimeZone("UTC")): String {
        val f: DateFormat = SimpleDateFormat(format, Locale.getDefault())
        f.timeZone = tz
        return f.format(date)
    }

    fun toISOAsUTC(timestamp: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'0000Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(timestamp)
    }

    fun toISONoZone(timestamp: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        format.timeZone = TimeZone.getDefault()
        return format.format(timestamp)
    }

    fun secondsOfTheDayToMilliseconds(seconds: Int): Long {
        val calendar: Calendar = GregorianCalendar()
        calendar[Calendar.MONTH] = 0 // Set january to be sure we miss DST changing
        calendar[Calendar.HOUR_OF_DAY] = seconds / 60 / 60
        calendar[Calendar.MINUTE] = seconds / 60 % 60
        calendar[Calendar.SECOND] = 0
        return calendar.timeInMillis
    }

    fun toSeconds(hh_colon_mm: String): Int {
        val p = Pattern.compile("(\\d+):(\\d+)( a.m.| p.m.| AM| PM|AM|PM|)")
        val m = p.matcher(hh_colon_mm)
        var retval = 0
        if (m.find()) {
            retval = SafeParse.stringToInt(m.group(1)) * 60 * 60 + SafeParse.stringToInt(m.group(2)) * 60
            if ((m.group(3) == " a.m." || m.group(3) == " AM" || m.group(3) == "AM") && m.group(1) == "12") retval -= 12 * 60 * 60
            if ((m.group(3) == " p.m." || m.group(3) == " PM" || m.group(3) == "PM") && m.group(1) != "12") retval += 12 * 60 * 60
        }
        return retval
    }

    fun dateString(mills: Long): String {
        val df = DateFormat.getDateInstance(DateFormat.SHORT)
        return df.format(mills)
    }

    fun dateStringShort(mills: Long): String {
        var format = "MM/dd"
        if (android.text.format.DateFormat.is24HourFormat(context)) {
            format = "dd/MM"
        }
        return DateTime(mills).toString(DateTimeFormat.forPattern(format))
    }

    fun timeString(mills: Long): String {
        var format = "hh:mma"
        if (android.text.format.DateFormat.is24HourFormat(context)) {
            format = "HH:mm"
        }
        return DateTime(mills).toString(DateTimeFormat.forPattern(format))
    }

    private fun timeStringWithSeconds(mills: Long): String {
        var format = "hh:mm:ssa"
        if (android.text.format.DateFormat.is24HourFormat(context)) {
            format = "HH:mm:ss"
        }
        return DateTime(mills).toString(DateTimeFormat.forPattern(format))
    }

    fun dateAndTimeRangeString(start: Long, end: Long): String {
        return dateAndTimeString(start) + " - " + timeString(end)
    }

    fun dateAndTimeString(mills: Long): String {
        return if (mills == 0L) "" else dateString(mills) + " " + timeString(mills)
    }

    fun dateAndTimeAndSecondsString(mills: Long): String {
        return if (mills == 0L) "" else dateString(mills) + " " + timeStringWithSeconds(mills)
    }

    fun minAgo(resourceHelper: ResourceHelper, time: Long?): String {
        if (time == null) return ""
        val mins = ((now() - time) / 1000 / 60).toInt()
        return resourceHelper.gs(R.string.minago, mins)
    }

    fun minAgoShort(time: Long?): String {
        if (time == null) return ""
        val mins = ((time - now()) / 1000 / 60).toInt()
        return (if (mins > 0) "+" else "") + mins
    }

    fun hourAgo(time: Long, resourceHelper: ResourceHelper): String {
        val hours = (now() - time) / 1000.0 / 60 / 60
        return resourceHelper.gs(R.string.hoursago, hours)
    }

    fun timeStringFromSeconds(seconds: Int): String {
        val cached = timeStrings[seconds.toLong()]
        if (cached != null) return cached
        val t = timeString(secondsOfTheDayToMilliseconds(seconds))
        timeStrings.put(seconds.toLong(), t)
        return t
    }

    fun timeFrameString(timeInMillis: Long, resourceHelper: ResourceHelper): String {
        var remainingTimeMinutes = timeInMillis / (1000 * 60)
        val remainingTimeHours = remainingTimeMinutes / 60
        remainingTimeMinutes %= 60
        return "(" + (if (remainingTimeHours > 0) remainingTimeHours.toString() + resourceHelper.gs(R.string.shorthour) + " " else "") + remainingTimeMinutes + "')"
    }

    fun sinceString(timestamp: Long, resourceHelper: ResourceHelper): String {
        return timeFrameString(System.currentTimeMillis() - timestamp, resourceHelper)
    }

    fun untilString(timestamp: Long, resourceHelper: ResourceHelper): String {
        return timeFrameString(timestamp - System.currentTimeMillis(), resourceHelper)
    }

    fun now(): Long {
        return System.currentTimeMillis()
    }

    fun nowWithoutMilliseconds(): Long {
        var n = System.currentTimeMillis()
        n -= n % 1000
        return n
    }

    fun isCloseToNow(date: Long): Boolean {
        val diff = abs(date - now())
        return diff < T.mins(2L).msecs()
    }

    fun isOlderThan(date: Long, minutes: Long): Boolean {
        val diff = now() - date
        return diff > T.mins(minutes).msecs()
    }

    fun getTimeZoneOffsetMs(): Long {
        return GregorianCalendar().timeZone.rawOffset.toLong()
    }

    fun getTimeZoneOffsetMinutes(timestamp: Long): Int {
        return TimeZone.getDefault().getOffset(timestamp) / 60000
    }

    //Map:{DAYS=1, HOURS=3, MINUTES=46, SECONDS=40, MILLISECONDS=0, MICROSECONDS=0, NANOSECONDS=0}
    fun computeDiff(date1: Long, date2: Long): Map<TimeUnit, Long> {
        val units: MutableList<TimeUnit> = ArrayList(EnumSet.allOf(TimeUnit::class.java))
        units.reverse()
        val result: MutableMap<TimeUnit, Long> = LinkedHashMap()
        var millisecondsRest = date2 - date1
        for (unit in units) {
            val diff = unit.convert(millisecondsRest, TimeUnit.MILLISECONDS)
            val diffInMillisecondsForUnit = unit.toMillis(diff)
            millisecondsRest -= diffInMillisecondsForUnit
            result[unit] = diff
        }
        return result
    }

    fun age(milliseconds: Long, useShortText: Boolean, resourceHelper: ResourceHelper): String {
        val diff = computeDiff(0L, milliseconds)
        var days = " " + resourceHelper.gs(R.string.days) + " "
        var hours = " " + resourceHelper.gs(R.string.hours) + " "
        var minutes = " " + resourceHelper.gs(R.string.unit_minutes) + " "
        if (useShortText) {
            days = resourceHelper.gs(R.string.shortday)
            hours = resourceHelper.gs(R.string.shorthour)
            minutes = resourceHelper.gs(R.string.shortminute)
        }
        var result = ""
        if (diff[TimeUnit.DAYS]!! > 0) result += diff[TimeUnit.DAYS].toString() + days
        if (diff[TimeUnit.HOURS]!! > 0) result += diff[TimeUnit.HOURS].toString() + hours
        if (diff[TimeUnit.DAYS] == 0L) result += diff[TimeUnit.MINUTES].toString() + minutes
        return result
    }

    fun niceTimeScalar(time: Long, resourceHelper: ResourceHelper): String {
        var t = time
        var unit = resourceHelper.gs(R.string.unit_second)
        t /= 1000
        if (t != 1L) unit = resourceHelper.gs(R.string.unit_seconds)
        if (t > 59) {
            unit = resourceHelper.gs(R.string.unit_minute)
            t /= 60
            if (t != 1L) unit = resourceHelper.gs(R.string.unit_minutes)
            if (t > 59) {
                unit = resourceHelper.gs(R.string.unit_hour)
                t /= 60
                if (t != 1L) unit = resourceHelper.gs(R.string.unit_hours)
                if (t > 24) {
                    unit = resourceHelper.gs(R.string.unit_day)
                    t /= 24
                    if (t != 1L) unit = resourceHelper.gs(R.string.unit_days)
                    if (t > 28) {
                        unit = resourceHelper.gs(R.string.unit_week)
                        t /= 7
                        if (t != 1L) unit = resourceHelper.gs(R.string.unit_weeks)
                    }
                }
            }
        }
        //if (t != 1) unit = unit + "s"; //implemented plurality in every step, because in other languages plurality of time is not every time adding the same character
        return qs(t.toDouble(), 0) + " " + unit
    }

    fun qs(x: Double, numDigits: Int): String {
        var digits = numDigits
        if (digits == -1) {
            digits = 0
            if ((x.toInt() % x == 0.0)) {
                digits++
                if ((x.toInt() * 10 / 10).toDouble() != x) {
                    digits++
                    if ((x.toInt() * 100 / 100).toDouble() != x) digits++
                }
            }
        }
        if (dfs == null) {
            val localDfs = DecimalFormatSymbols()
            localDfs.decimalSeparator = '.'
            dfs = localDfs // avoid race condition
        }
        val thisDf: DecimalFormat?
        // use singleton if on ui thread otherwise allocate new as DecimalFormat is not thread safe
        if (Thread.currentThread().id == 1L) {
            if (df == null) {
                val localDf = DecimalFormat("#", dfs)
                localDf.minimumIntegerDigits = 1
                df = localDf // avoid race condition
            }
            thisDf = df
        } else {
            thisDf = DecimalFormat("#", dfs)
        }
        thisDf!!.maximumFractionDigits = digits
        return thisDf.format(x)
    }

    fun format_HH_MM(timeAsSeconds: Int): String {
        val hour = timeAsSeconds / 60 / 60
        val minutes = (timeAsSeconds - hour * 60 * 60) / 60
        val df = DecimalFormat("00")
        return df.format(hour.toLong()) + ":" + df.format(minutes.toLong())
    }

    fun timeZoneByOffset(offsetInMilliseconds: Long): TimeZone =
        TimeZone.getTimeZone(
            if (offsetInMilliseconds == 0L) ZoneId.of("UTC")
            else ZoneId.getAvailableZoneIds()
                .stream()
                .map(ZoneId::of)
                .filter { z -> z.rules.getOffset(Instant.now()).totalSeconds == ZoneOffset.ofHours((offsetInMilliseconds / 1000 / 3600).toInt()).totalSeconds }
                .collect(Collectors.toList())
                .firstOrNull() ?: ZoneId.of("UTC")
        )

    companion object {

        private val timeStrings = LongSparseArray<String>()

        // singletons to avoid repeated allocation
        private var dfs: DecimalFormatSymbols? = null
        private var df: DecimalFormat? = null
    }
}