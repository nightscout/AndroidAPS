package app.aaps.shared.impl.utils

import android.content.Context
import androidx.collection.LongSparseArray
import app.aaps.core.interfaces.R
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.SafeParse
import java.security.SecureRandom
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import android.text.format.DateFormat as AndroidDateFormat

@Singleton
class DateUtilImpl @Inject constructor(
    private val context: Context,
    private val clock: Clock = Clock.systemDefaultZone()
) : DateUtil {

    /** The timezone is captured each time systemZone is accessed.*/
    private val systemZone: ZoneId get() = ZoneId.systemDefault()
    /** The locale used for formatting strings (e.g., date formats, AM/PM) is captured each time displayLocale is accessed.*/
    private val displayLocale: Locale get() = Locale.getDefault()

    override fun fromISODateString(isoDateString: String): Long {
        // This custom formatter handles multiple common ISO-like formats.
        val formatter = DateTimeFormatterBuilder()
            // 1. Append the standard date and time part
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            // 2. Optionally append fractional seconds (milliseconds)
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            // 3. Append an offset pattern that can handle "+HHMM" (no colon)
            //    The second argument "Z" tells it to use 'Z' for a zero offset.
            .appendOffset("+HHMM", "Z")
            .toFormatter()
        return ZonedDateTime.parse(isoDateString, formatter).toInstant().toEpochMilli()
    }

    override fun toISOString(date: Long): String {
        /** Formatter for converting an Instant to a standard ISO-8601 UTC string (e.g., "2023-10-27T10:30:00Z"). */
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneId.of("UTC"))
        return formatter.format(Instant.ofEpochMilli(date))
    }

    override fun toISOAsUTC(timestamp: Long): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'0000Z'", Locale.US)
        return formatter.withZone(ZoneId.of("UTC")).format(Instant.ofEpochMilli(timestamp))
    }

    override fun toISONoZone(timestamp: Long): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val zonedDateTime = instant.atZone(systemZone)
        return ISO_LOCAL_FORMATTER.format(zonedDateTime)
    }

    override fun secondsOfTheDayToMillisecondsOfHoursAndMinutes(seconds: Int): Long {
        val startOfToday = LocalDate.now(clock).atStartOfDay(systemZone)
        val totalMinutes = seconds / 60
        val targetTime = startOfToday.plusMinutes(totalMinutes.toLong())
        return targetTime.toInstant().toEpochMilli()
    }

    override fun secondsOfTheDayToMilliseconds(seconds: Int): Long {
        val startOfToday = LocalDate.now(clock).atStartOfDay(systemZone)
        val targetTime = startOfToday.plusSeconds(seconds.toLong())
        return targetTime.toInstant().toEpochMilli()
    }

    override fun toSeconds(hhColonMm: String): Int {
        val p = Pattern.compile("(\\d+):(\\d+)( a.m.| p.m.| AM| PM|AM|PM|)")
        val m = p.matcher(hhColonMm)
        var retVal = 0
        if (m.find()) {
            var hour = SafeParse.stringToInt(m.group(1))
            val minute = SafeParse.stringToInt(m.group(2))
            val amPm = m.group(3)?.trim()?.uppercase(Locale.US) ?: ""
            if (amPm.endsWith("AM") && hour == 12) hour = 0 // Midnight case
            if (amPm.endsWith("PM") && hour != 12) hour += 12 // Afternoon case
            retVal = (hour * 3600) + (minute * 60)
        }
        return retVal
    }

    override fun dateString(mills: Long): String =
        Instant.ofEpochMilli(mills).atZone(systemZone).format(getLocalizedDateFormatter())

    override fun dateStringRelative(mills: Long, rh: ResourceHelper): String {        // Get the current time and the start of today as simple millisecond timestamps.
        val nowMillis = now()
        val startOfTodayMillis = beginOfDay(nowMillis)
        return if (mills < nowMillis) { // Past
            when {
                mills > startOfTodayMillis                                  -> rh.gs(R.string.today)
                mills > startOfTodayMillis - 1.days.inWholeMilliseconds -> rh.gs(R.string.yesterday)
                mills > startOfTodayMillis - 7.days.inWholeMilliseconds -> dayAgo(mills, rh, true)
                else                                                        -> dateString(mills)
            }
        } else { // Future
            when {
                mills < startOfTodayMillis + 1.days.inWholeMilliseconds -> rh.gs(R.string.later_today)
                mills < startOfTodayMillis + 2.days.inWholeMilliseconds -> rh.gs(R.string.tomorrow)
                mills < startOfTodayMillis + 7.days.inWholeMilliseconds -> dayAgo(mills, rh, true)
                else                                                        -> dateString(mills)
            }
        }
    }

    override fun dateStringShort(mills: Long): String {
        val pattern = if (AndroidDateFormat.is24HourFormat(context)) "dd/MM" else "MM/dd"
        val formatter = DateTimeFormatter.ofPattern(pattern)
        return Instant.ofEpochMilli(mills).atZone(systemZone).format(formatter)
    }

    override fun timeString(): String = timeString(now())
    override fun timeString(mills: Long): String {
        val pattern = if (AndroidDateFormat.is24HourFormat(context)) "HH:mm" else "hh:mm a"
        val formatter = DateTimeFormatter.ofPattern(pattern, displayLocale)
        return Instant.ofEpochMilli(mills).atZone(systemZone).format(formatter)
    }

    override fun secondString(): String = secondString(now())
    override fun secondString(mills: Long): String =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(mills), systemZone).format(DateTimeFormatter.ofPattern("ss"))

    override fun minuteString(): String = minuteString(now())
    override fun minuteString(mills: Long): String =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(mills), systemZone).format(DateTimeFormatter.ofPattern("mm"))

    override fun hourString(): String = hourString(now())
    override fun hourString(mills: Long): String {
        val pattern = if (AndroidDateFormat.is24HourFormat(context)) "HH" else "hh"
        val formatter = DateTimeFormatter.ofPattern(pattern)
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(mills), systemZone).format(formatter)
    }

    override fun amPm(): String = amPm(now())
    override fun amPm(mills: Long): String =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(mills), systemZone).format(DateTimeFormatter.ofPattern("a", displayLocale))

    override fun dayNameString(format: String): String = dayNameString(now(), format)
    override fun dayNameString(mills: Long, format: String): String =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(mills), systemZone).format(DateTimeFormatter.ofPattern(format, displayLocale))

    override fun dayString(): String = dayString(now())
    override fun dayString(mills: Long): String =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(mills), systemZone).format(DateTimeFormatter.ofPattern("dd"))

    override fun monthString(format: String): String = monthString(now(), format)
    override fun monthString(mills: Long, format: String): String =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(mills), systemZone).format(DateTimeFormatter.ofPattern(format, displayLocale))

    override fun weekString(): String = weekString(now())
    override fun weekString(mills: Long): String =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(mills), systemZone).format(DateTimeFormatter.ofPattern("ww"))

    override fun timeStringWithSeconds(mills: Long): String {
        val pattern = if (AndroidDateFormat.is24HourFormat(context)) "HH:mm:ss" else "hh:mm:ss a"
        val formatter = DateTimeFormatter.ofPattern(pattern, displayLocale)
        return Instant.ofEpochMilli(mills).atZone(systemZone).format(formatter)
    }

    override fun dateAndTimeRangeString(start: Long, end: Long): String =
        dateAndTimeString(start) + " - " + timeString(end)

    override fun timeRangeString(start: Long, end: Long): String =
        timeString(start) + " - " + timeString(end)

    override fun dateAndTimeString(mills: Long): String =
        if (mills == 0L) "" else dateString(mills) + " " + timeString(mills)

    override fun dateAndTimeStringNullable(mills: Long?): String? =
        if (mills == null || mills == 0L) null else dateString(mills) + " " + timeString(mills)

    override fun dateAndTimeAndSecondsString(mills: Long): String =
        if (mills == 0L) "" else dateString(mills) + " " + timeStringWithSeconds(mills)

    override fun minAgo(rh: ResourceHelper, time: Long?): String {
        if (time == null) return ""
        val duration = (now() - time).milliseconds
        val minutes = duration.inWholeMinutes.toInt()
        return if (abs(minutes) > 9999) "" else rh.gs(R.string.minago, minutes)
    }

    override fun minOrSecAgo(rh: ResourceHelper, time: Long?): String {
        if (time == null) return ""
        val duration = (now() - time).milliseconds
        return when {
            duration.inWholeMinutes >= 2 -> { // If the duration is 2 minutes or more, show minutes
                rh.gs(R.string.minago, duration.inWholeMinutes.toInt())
            }
            else                         -> { // Otherwise, show seconds
                rh.gs(R.string.secago, duration.inWholeSeconds.toInt())
            }
        }
    }

    override fun minAgoShort(time: Long?): String {
        if (time == null) return ""
        val duration = (time - now()).milliseconds
        val minutes = duration.inWholeMinutes.toInt()
        return if (abs(minutes) > 9999) ""
        else "(" + (if (minutes > 0) "+" else "") + minutes + ")"
    }

    override fun minAgoLong(rh: ResourceHelper, time: Long?): String {
        if (time == null) return ""
        val duration = (now() - time).milliseconds
        val minutes = duration.inWholeMinutes.toInt()
        return if (abs(minutes) > 9999) "" else rh.gs(R.string.minago_long, minutes)
    }

    override fun hourAgo(time: Long, rh: ResourceHelper): String {
        val duration = (now() - time).milliseconds
        val hours = duration.inWholeHours
        return rh.gs(R.string.hoursago, hours)
    }

    override fun dayAgo(time: Long, rh: ResourceHelper, round: Boolean): String {
        val duration = (now() - time).milliseconds
        if (round) {
            val daysAsDouble = duration.toDouble(DurationUnit.DAYS)
            return if (duration.isPositive()) {
                val roundedDays = ceil(daysAsDouble)
                rh.gs(R.string.days_ago_round, roundedDays)
            } else {
                val roundedDays = floor(daysAsDouble)
                rh.gs(R.string.in_days_round, roundedDays)
            }
        }
        return if (duration.isPositive()) {
            rh.gs(R.string.days_ago, duration.inWholeDays)
        } else {
            rh.gs(R.string.in_days, abs(duration.inWholeDays))
        }
    }

    override fun beginOfDay(mills: Long): Long =
        Instant.ofEpochMilli(mills).atZone(systemZone)
            .truncatedTo(ChronoUnit.DAYS)
            .toInstant().toEpochMilli()

    override fun timeStringFromSeconds(seconds: Int): String {
        val cached = timeStrings[seconds.toLong()]
        if (cached != null) return cached
        val t = timeString(secondsOfTheDayToMilliseconds(seconds))
        timeStrings.put(seconds.toLong(), t)
        return t
    }

    override fun timeFrameString(timeInMillis: Long, rh: ResourceHelper): String {
        val duration = timeInMillis.milliseconds
        val totalHours = duration.inWholeHours
        val remainingMinutes = (duration - totalHours.hours).inWholeMinutes
        val hoursPart = if (totalHours > 0) "$totalHours${rh.gs(R.string.shorthour)} " else ""
        return "($hoursPart$remainingMinutes')"
    }

    override fun sinceString(timestamp: Long, rh: ResourceHelper): String =
        timeFrameString(now() - timestamp, rh)

    override fun untilString(timestamp: Long, rh: ResourceHelper): String {
        val durationMillis = timestamp - now()
        return timeFrameString(durationMillis, rh)
    }

    override fun now(): Long = clock.millis()

    override fun nowWithoutMilliseconds(): Long =
        clock.instant().truncatedTo(ChronoUnit.SECONDS).toEpochMilli()

    override fun isOlderThan(date: Long, minutes: Long): Boolean =
        Instant.ofEpochMilli(date).isBefore(clock.instant().minus(minutes, ChronoUnit.MINUTES))

    override fun getTimeZoneOffsetMs(): Long {
        val standardOffset = systemZone.rules.getStandardOffset(clock.instant())
        return standardOffset.totalSeconds.seconds.inWholeMilliseconds
    }
// TODO: getTimeZoneOffsetMs was __AND IS__ not DST aware. Check if intended.
//  If not intended, use the following:
    override fun getTimeZoneOffsetMsWithDST(): Long {
        val dSTAwareOffset = systemZone.rules.getOffset(clock.instant())
        return dSTAwareOffset.totalSeconds.seconds.inWholeMilliseconds
    }

    override fun getTimeZoneOffsetMinutes(timestamp: Long): Int {
        val actualOffset = systemZone.rules.getOffset(Instant.ofEpochMilli(timestamp))
        return actualOffset.totalSeconds / 60
    }

    override fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
        val instant1 = Instant.ofEpochMilli(timestamp1)
        val instant2 = Instant.ofEpochMilli(timestamp2)
        val date1 = instant1.atZone(systemZone).toLocalDate()
        val date2 = instant2.atZone(systemZone).toLocalDate()
        return date1.isEqual(date2)
    }

    override fun isAfterNoon(): Boolean =
        ZonedDateTime.now(clock).hour >= 12

    override fun isSameDayGroup(timestamp1: Long, timestamp2: Long): Boolean {
        val now = now()
        if (now in (timestamp1 + 1) until timestamp2 || now in (timestamp2 + 1) until timestamp1) return false
        return isSameDay(timestamp1, timestamp2)
    }

    //Map:{DAYS=1, HOURS=3, MINUTES=46, SECONDS=40, MILLISECONDS=0, MICROSECONDS=0, NANOSECONDS=0}
    override fun computeDiff(date1: Long, date2: Long): Map<TimeUnit, Long> {
        val duration = (date2 - date1).milliseconds
        return duration.toComponents { days, hours, minutes, seconds, nanoseconds ->
            mapOf(
                TimeUnit.DAYS to days,
                TimeUnit.HOURS to hours.toLong(),
                TimeUnit.MINUTES to minutes.toLong(),
                TimeUnit.SECONDS to seconds.toLong(),
                // Convert remaining nanoseconds into millis, micros, and nanos for the map.
                TimeUnit.MILLISECONDS to nanoseconds.toLong() / 1_000_000,
                TimeUnit.MICROSECONDS to (nanoseconds.toLong() / 1_000) % 1000,
                TimeUnit.NANOSECONDS to nanoseconds.toLong() % 1000
            )
        }
    }

    override fun timeAgoFullString(milliseconds: Long, rh: ResourceHelper): String {
        return when {
            milliseconds <= 0 -> ""
            else -> {
                val duration = milliseconds.milliseconds
                val days = duration.inWholeDays
                val hours = (duration - days.days).inWholeHours
                val minutes = (duration - days.days - hours.hours).inWholeMinutes

                when {
                    days > 0        -> rh.gq(R.plurals.plurals_day_hour_ago, days.toInt(), days.toString(), hours.toString())
                    hours > 0       -> rh.gq(R.plurals.plurals_hour_ago, hours.toInt(), hours.toString())
                    minutes > 0     -> rh.gq(R.plurals.plurals_minute_ago, minutes.toInt(), minutes.toString())
                    else            -> rh.gs(R.string.seconds_ago)
                }
            }
        }
    }

    override fun age(milliseconds: Long, useShortText: Boolean, rh: ResourceHelper): String {
        val duration = milliseconds.milliseconds
        if (duration.inWholeDays > 1000) return rh.gs(R.string.forever)
        val daysUnit = if (useShortText) rh.gs(R.string.shortday) else rh.gs(R.string.days)
        val hoursUnit = if (useShortText) rh.gs(R.string.shorthour) else rh.gs(R.string.hours)
        val minutesUnit = if (useShortText) rh.gs(R.string.shortminute) else rh.gs(R.string.unit_minutes)
        val days = duration.inWholeDays
        val hours = (duration - days.days).inWholeHours
        val minutes = (duration - days.days - hours.hours).inWholeMinutes
        return when {
            days > 0  -> "$days $daysUnit $hours $hoursUnit "
            hours > 0 -> "$hours $hoursUnit $minutes $minutesUnit "
            else      -> "${duration.inWholeMinutes} $minutesUnit"
        }
    }

    override fun niceTimeScalar(time: Long, rh: ResourceHelper): String {
        val duration = time.milliseconds
        val (value, unitId) = when {
            duration.inWholeDays > 6 -> {
                val weeks = duration.inWholeDays / 7
                weeks to if (weeks == 1L) R.string.unit_week else R.string.unit_weeks
            }
            duration.inWholeHours > 23 -> {
                val days = duration.inWholeDays
                days to if (days == 1L) R.string.unit_day else R.string.unit_days
            }
            duration.inWholeMinutes > 59 -> {
                val hours = duration.inWholeHours
                hours to if (hours == 1L) R.string.unit_hour else R.string.unit_hours
            }
            duration.inWholeSeconds > 59 -> {
                val minutes = duration.inWholeMinutes
                minutes to if (minutes == 1L) R.string.unit_minute else R.string.unit_minutes
            }
            else -> {
                val seconds = duration.inWholeSeconds
                seconds to if (seconds == 1L) R.string.unit_second else R.string.unit_seconds
            }
        }
        return "${qs(value.toDouble(), 0)} ${rh.gs(unitId)}"
    }

    override fun qs(x: Double, numDigits: Int): String {
        val formatter = decimalFormatter.get()
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
        // Use maximumFractionDigits to replicate the original behavior
        // of not showing trailing zeros (e.g., 12.0 -> "12").
        formatter!!.maximumFractionDigits = digits
        // We must also set the minimum to 0 to allow for truncation.
        formatter.minimumFractionDigits = 0
        return formatter.format(x)
    }

    override fun formatHHMM(timeAsSeconds: Int): String {
        val duration = timeAsSeconds.seconds
        val hours = duration.inWholeHours
        val minutes = (duration - hours.hours).inWholeMinutes
        return "%02d:%02d".format(hours, minutes)
    }

    override fun timeZoneByOffset(offsetInMilliseconds: Long): String {
        if (offsetInMilliseconds == 0L) return "UTC"
        val offsetInSeconds = offsetInMilliseconds.milliseconds.inWholeSeconds.toInt()
        val now = clock.instant()
        return ZoneId.getAvailableZoneIds()
            .firstOrNull { zoneIdString ->
                val zoneId = ZoneId.of(zoneIdString)
                // Compare the zone's current offset in seconds to the requested offset.
                zoneId.rules.getOffset(now).totalSeconds == offsetInSeconds
            }
            ?: "UTC" // Default to "UTC" if no match is found.
    }

    override fun timeStampToUtcDateMillis(timestamp: Long): Long =
        Instant.ofEpochMilli(timestamp).truncatedTo(ChronoUnit.DAYS).toEpochMilli()

    //TODO: timeStampToUtcDateMillis has a different output than the old function.
    // Since that seems to be desired behaviour in the history browser,
    // the functionality was refactored in getTimestampWithCurrentTimeOfDay()
    override fun getTimestampWithCurrentTimeOfDay(timestamp: Long): Long {
        val inputDate = Instant.ofEpochMilli(timestamp).atZone(systemZone).toLocalDate()
        val timeOfNow = ZonedDateTime.now(clock).toLocalTime()
        return inputDate.atTime(timeOfNow).atZone(systemZone).toInstant().toEpochMilli()
    }

    override fun mergeUtcDateToTimestamp(timestamp: Long, dateUtcMillis: Long): Long {
        val localTime = Instant.ofEpochMilli(timestamp).atZone(systemZone).toLocalTime()
        val utcDate = Instant.ofEpochMilli(dateUtcMillis).atZone(ZoneId.of("UTC")).toLocalDate()
        val finalDateTime = utcDate.atTime(localTime).atZone(systemZone)
        return finalDateTime.toInstant().toEpochMilli()
    }

    override fun mergeHourMinuteToTimestamp(timestamp: Long, hour: Int, minute: Int, randomSecond: Boolean): Long {
        val originalDateTime = Instant.ofEpochMilli(timestamp).atZone(systemZone)
        var updatedDateTime = originalDateTime.withHour(hour).withMinute(minute)
        if (randomSecond) updatedDateTime = updatedDateTime.withSecond(seconds++)
        return updatedDateTime.toInstant().toEpochMilli()
    }

    /**
     * Creates a date formatter based on the user's current device locale (e.g., MM/dd/yyyy for US, dd/MM/yyyy for UK).
     * @return A `DateTimeFormatter` configured for a short, localized date.
     */
    private fun getLocalizedDateFormatter(): DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(displayLocale)

    companion object {

        /** Formatter for creating a local ISO-like string without a timezone (e.g., "2023-10-27T10:30:00"). */
        private val ISO_LOCAL_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        private val timeStrings = LongSparseArray<String>()
        private var seconds: Int = (SecureRandom().nextDouble() * 59.0).toInt()

        val decimalFormatter = object : ThreadLocal<DecimalFormat>() {
            override fun initialValue(): DecimalFormat {
                // Each thread gets its own DecimalFormat instance, configured once.
                return DecimalFormat().apply {
                    decimalFormatSymbols = DecimalFormatSymbols().apply {
                        decimalSeparator = '.'
                    }
                }
            }
        }
    }
}
