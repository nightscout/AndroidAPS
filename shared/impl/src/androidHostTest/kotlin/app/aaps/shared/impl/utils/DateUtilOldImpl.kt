package app.aaps.shared.impl.utils

import android.content.Context
import androidx.collection.LongSparseArray
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.R
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.utils.pump.ThreadUtil
import org.apache.commons.lang3.time.DateUtils.isSameDay
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.ISODateTimeFormat
import java.security.SecureRandom
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Calendar
import java.util.Date
import java.util.EnumSet
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import java.util.stream.Collectors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The Class DateUtil. A simple wrapper around SimpleDateFormat to ease the handling of iso date string &lt;-&gt; date obj
 * with TZ
 */
@Singleton
class DateUtilOldImpl @Inject constructor(private val context: Context) {

    /**
     * The date format in iso.
     */
    @Suppress("PrivatePropertyName", "SpellCheckingInspection")
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
     * @return the iso-formatted date string
     */
    fun toISOString(date: Long): String {
        val f: DateFormat = SimpleDateFormat(FORMAT_DATE_ISO_OUT, Locale.getDefault())
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(date)
    }

    @Suppress("SpellCheckingInspection")
    fun toISOAsUTC(timestamp: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'0000Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(timestamp)
    }

    @Suppress("SpellCheckingInspection")
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

    fun toSeconds(hhColonMm: String): Int {
        val p = Pattern.compile("(\\d+):(\\d+)( a.m.| p.m.| AM| PM|AM|PM|)")
        val m = p.matcher(hhColonMm)
        var retVal = 0
        if (m.find()) {
            retVal = SafeParse.stringToInt(m.group(1)) * 60 * 60 + SafeParse.stringToInt(m.group(2)) * 60
            if ((m.group(3) == " a.m." || m.group(3) == " AM" || m.group(3) == "AM") && m.group(1) == "12") retVal -= 12 * 60 * 60
            if ((m.group(3) == " p.m." || m.group(3) == " PM" || m.group(3) == "PM") && m.group(1) != "12") retVal += 12 * 60 * 60
        }
        return retVal
    }

    fun dateString(mills: Long): String {
        val zonedTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(mills), ZoneId.systemDefault())
        val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
        return zonedTime.format(dateFormatter)
    }

    fun dateStringRelative(mills: Long, rh: ResourceHelper): String {
        val df = DateFormat.getDateInstance(DateFormat.SHORT)
        val day = df.format(mills)
        val beginOfToday = beginOfDay(now())
        return if (mills < now()) // Past
            when {
                mills > beginOfToday -> rh.gs(R.string.today)
                mills > beginOfToday - T.days(1).msecs() -> rh.gs(R.string.yesterday)
                mills > beginOfToday - T.days(7).msecs() -> dayAgo(mills, rh, true)
                else -> day
            }
        else // Future
            when {
                mills < beginOfToday + T.days(1).msecs() -> rh.gs(R.string.later_today)
                mills < beginOfToday + T.days(2).msecs() -> rh.gs(R.string.tomorrow)
                mills < beginOfToday + T.days(7).msecs() -> dayAgo(mills, rh, true)
                else                                     -> day
            }
    }

    fun dateStringShort(mills: Long): String {
        var format = "MM/dd"
        if (android.text.format.DateFormat.is24HourFormat(context)) {
            format = "dd/MM"
        }
        return DateTime(mills).toString(DateTimeFormat.forPattern(format))
    }

    fun timeString(): String = timeString(now())
    fun timeString(mills: Long): String {
        var format = "hh:mm a"
        if (android.text.format.DateFormat.is24HourFormat(context)) {
            format = "HH:mm"
        }
        return DateTime(mills).toString(DateTimeFormat.forPattern(format))
    }

    fun secondString(): String = secondString(now())
    fun secondString(mills: Long): String =
        DateTime(mills).toString(DateTimeFormat.forPattern("ss"))

    fun minuteString(): String = minuteString(now())
    fun minuteString(mills: Long): String =
        DateTime(mills).toString(DateTimeFormat.forPattern("mm"))

    fun hourString(): String = hourString(now())
    fun hourString(mills: Long): String {
        var format = "hh"
        if (android.text.format.DateFormat.is24HourFormat(context)) {
            format = "HH"
        }
        return DateTime(mills).toString(DateTimeFormat.forPattern(format))
    }

    fun amPm(): String = amPm(now())
    fun amPm(mills: Long): String =
        DateTime(mills).toString(DateTimeFormat.forPattern("a"))

    fun dayNameString(format: String): String = dayNameString(now(), format)
    fun dayNameString(mills: Long, format: String): String =
        DateTime(mills).toString(DateTimeFormat.forPattern(format))

    fun dayString(): String = dayString(now())
    fun dayString(mills: Long): String =
        DateTime(mills).toString(DateTimeFormat.forPattern("dd"))

    fun monthString(format: String): String = monthString(now(), format)
    fun monthString(mills: Long, format: String): String =
        DateTime(mills).toString(DateTimeFormat.forPattern(format))

    fun weekString(): String = weekString(now())
    fun weekString(mills: Long): String =
        DateTime(mills).toString(DateTimeFormat.forPattern("ww"))

    fun timeStringWithSeconds(mills: Long): String {
        var format = "hh:mm:ss a"
        if (android.text.format.DateFormat.is24HourFormat(context)) {
            format = "HH:mm:ss"
        }
        return DateTime(mills).toString(DateTimeFormat.forPattern(format))
    }

    fun dateAndTimeRangeString(start: Long, end: Long): String {
        return dateAndTimeString(start) + " - " + timeString(end)
    }

    fun timeRangeString(start: Long, end: Long): String {
        return timeString(start) + " - " + timeString(end)
    }

    fun dateAndTimeString(mills: Long): String =
        if (mills == 0L) "" else dateString(mills) + " " + timeString(mills)

    fun dateAndTimeStringNullable(mills: Long?): String? =
        if (mills == null || mills == 0L) null else dateString(mills) + " " + timeString(mills)

    fun dateAndTimeAndSecondsString(mills: Long): String {
        return if (mills == 0L) "" else dateString(mills) + " " + timeStringWithSeconds(mills)
    }

    fun minAgo(rh: ResourceHelper, time: Long?): String {
        if (time == null) return ""
        val minutes = ((now() - time) / 1000 / 60).toInt()
        return if (abs(minutes) > 9999) "" else rh.gs(R.string.minago, minutes)
    }

    fun minOrSecAgo(rh: ResourceHelper, time: Long?): String {
        if (time == null) return ""
        //val minutes = ((now() - time) / 1000 / 60).toInt()
        val seconds = (now() - time) / 1000
        if (seconds > 119) {
            return rh.gs(R.string.minago, (seconds / 60).toInt())
        } else {
            return rh.gs(R.string.secago, seconds.toInt())
        }
    }

    fun minAgoShort(time: Long?): String {
        if (time == null) return ""
        val minutes = ((time - now()) / 1000 / 60).toInt()
        return if (abs(minutes) > 9999) ""
        else "(" + (if (minutes > 0) "+" else "") + minutes + ")"
    }

    fun minAgoLong(rh: ResourceHelper, time: Long?): String {
        if (time == null) return ""
        val minutes = ((now() - time) / 1000 / 60).toInt()
        return if (abs(minutes) > 9999) "" else rh.gs(R.string.minago_long, minutes)
    }

    fun hourAgo(time: Long, rh: ResourceHelper): String {
        val hours = (now() - time) / 1000.0 / 60 / 60
        return rh.gs(R.string.hoursago, hours)
    }

    fun dayAgo(time: Long, rh: ResourceHelper, round: Boolean = false): String {
        var days = (now() - time) / 1000.0 / 60 / 60 / 24
        if (round) {
            return if (now() > time) {
                days = ceil(days)
                rh.gs(R.string.days_ago_round, days)
            } else {
                days = floor(days)
                rh.gs(R.string.in_days_round, days)
            }
        }
        return if (now() > time)
            rh.gs(R.string.days_ago, days)
        else
            rh.gs(R.string.in_days, days)
    }

    fun beginOfDay(mills: Long): Long {
        val givenDate = Calendar.getInstance()
        givenDate.timeInMillis = mills
        givenDate[Calendar.HOUR_OF_DAY] = 0
        givenDate[Calendar.MINUTE] = 0
        givenDate[Calendar.SECOND] = 0
        givenDate[Calendar.MILLISECOND] = 0
        return givenDate.timeInMillis
    }

    fun timeStringFromSeconds(seconds: Int): String {
        val cached = timeStrings[seconds.toLong()]
        if (cached != null) return cached
        val t = timeString(secondsOfTheDayToMilliseconds(seconds))
        timeStrings.put(seconds.toLong(), t)
        return t
    }

    fun timeFrameString(timeInMillis: Long, rh: ResourceHelper): String {
        var remainingTimeMinutes = timeInMillis / (1000 * 60)
        val remainingTimeHours = remainingTimeMinutes / 60
        remainingTimeMinutes %= 60
        return "(" + (if (remainingTimeHours > 0) remainingTimeHours.toString() + rh.gs(R.string.shorthour) + " " else "") + remainingTimeMinutes + "')"
    }

    fun sinceString(timestamp: Long, rh: ResourceHelper): String {
        return timeFrameString(System.currentTimeMillis() - timestamp, rh)
    }

    fun untilString(timestamp: Long, rh: ResourceHelper): String {
        return timeFrameString(timestamp - System.currentTimeMillis(), rh)
    }

    fun now(): Long {
        return System.currentTimeMillis()
    }

    fun nowWithoutMilliseconds(): Long {
        var n = System.currentTimeMillis()
        n -= n % 1000
        return n
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

    fun isSameDay(timestamp1: Long, timestamp2: Long) = isSameDay(Date(timestamp1), Date(timestamp2))

    fun isAfterNoon(): Boolean =
        Instant.now().atZone(ZoneId.systemDefault()).hour >= 12

    fun isSameDayGroup(timestamp1: Long, timestamp2: Long): Boolean {
        val now = now()
        if (now in (timestamp1 + 1) until timestamp2 || now in (timestamp2 + 1) until timestamp1)
            return false
        return isSameDay(Date(timestamp1), Date(timestamp2))
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

    fun age(milliseconds: Long, useShortText: Boolean, rh: ResourceHelper): String {
        val diff = computeDiff(0L, milliseconds)
        var days = " " + rh.gs(R.string.days) + " "
        var hours = " " + rh.gs(R.string.hours) + " "
        var minutes = " " + rh.gs(R.string.unit_minutes) + " "
        if (useShortText) {
            days = " " + rh.gs(R.string.shortday) + " "
            hours = " " + rh.gs(R.string.shorthour) + " "
            minutes = " " + rh.gs(R.string.shortminute) + " "
        }
        if (T.msecs(milliseconds).days() > 1000) return rh.gs(R.string.forever)
        var result = ""
        if (diff.getOrDefault(TimeUnit.DAYS, -1) > 0) result += diff[TimeUnit.DAYS].toString() + days
        if (diff.getOrDefault(TimeUnit.HOURS, -1) > 0) result += diff[TimeUnit.HOURS].toString() + hours
        if (diff[TimeUnit.DAYS] == 0L) result += diff[TimeUnit.MINUTES].toString() + minutes
        return result
    }

    fun niceTimeScalar(time: Long, rh: ResourceHelper): String {
        var t = time
        var unit = rh.gs(R.string.unit_second)
        t /= 1000
        if (t != 1L) unit = rh.gs(R.string.unit_seconds)
        if (t > 59) {
            unit = rh.gs(R.string.unit_minute)
            t /= 60
            if (t != 1L) unit = rh.gs(R.string.unit_minutes)
            if (t > 59) {
                unit = rh.gs(R.string.unit_hour)
                t /= 60
                if (t != 1L) unit = rh.gs(R.string.unit_hours)
                if (t > 24) {
                    unit = rh.gs(R.string.unit_day)
                    t /= 24
                    if (t != 1L) unit = rh.gs(R.string.unit_days)
                    if (t > 28) {
                        unit = rh.gs(R.string.unit_week)
                        t /= 7
                        @Suppress("KotlinConstantConditions")
                        if (t != 1L) unit = rh.gs(R.string.unit_weeks)
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
        if (ThreadUtil.threadId() == 1L) {
            if (df == null) {
                val localDf = DecimalFormat("#", dfs)
                localDf.minimumIntegerDigits = 1
                df = localDf // avoid race condition
            }
            thisDf = df
        } else {
            thisDf = DecimalFormat("#", dfs)
        }
        thisDf?.maximumFractionDigits = digits
        return thisDf?.format(x) ?: ""
    }

    fun formatHHMM(timeAsSeconds: Int): String {
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

    fun timeStampToUtcDateMillis(timestamp: Long): Long {
        val current = Calendar.getInstance().apply { timeInMillis = timestamp }
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, current[Calendar.YEAR])
            set(Calendar.MONTH, current[Calendar.MONTH])
            set(Calendar.DAY_OF_MONTH, current[Calendar.DAY_OF_MONTH])
        }.timeInMillis
    }

    fun mergeUtcDateToTimestamp(timestamp: Long, dateUtcMillis: Long): Long {
        // - TimeZone.getDefault().rawOffset is only workaround for MaterialDatePicket bug
        // Remove after lib fix
        // https://github.com/material-components/material-components-android/issues/4373
        val selected = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = dateUtcMillis }
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.YEAR, selected[Calendar.YEAR])
            set(Calendar.MONTH, selected[Calendar.MONTH])
            set(Calendar.DAY_OF_MONTH, selected[Calendar.DAY_OF_MONTH])
        }.timeInMillis
    }

    fun mergeHourMinuteToTimestamp(timestamp: Long, hour: Int, minute: Int, randomSecond: Boolean = false): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            if (randomSecond) set(Calendar.SECOND, seconds++)
        }.timeInMillis
    }

    companion object {

        private val timeStrings = LongSparseArray<String>()
        private var seconds: Int = (SecureRandom().nextDouble() * 59.0).toInt()

        // singletons to avoid repeated allocation
        private var dfs: DecimalFormatSymbols? = null
        private var df: DecimalFormat? = null
    }
}
