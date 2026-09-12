package app.aaps.shared.impl.utils

import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.format.NumberFormatPlatform
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.interfaces.utils.TimeDiff
import app.aaps.core.keys.interfaces.TextRef
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.Instant

/**
 * Dates and times for the whole app.
 *
 * Plain Kotlin apart from three things only a device can answer - the 24 hour setting, text in the
 * user's language, and the standard time zone offset - which arrive through [DateFormatPlatform].
 * See that interface for why they are injected rather than declared `expect`/`actual`.
 *
 * The time zone is read on every call rather than stored, because a phone can cross a zone
 * without restarting. The same is true of the language.
 */
class DateUtilImpl(
    private val platform: DateFormatPlatform,
    private val clock: Clock = Clock.System
) : DateUtil {

    private val systemZone: TimeZone get() = TimeZone.currentSystemDefault()

    private fun Long.at(zone: TimeZone = systemZone): LocalDateTime =
        Instant.fromEpochMilliseconds(this).toLocalDateTime(zone)

    // ---- ISO, the format that goes over the wire ------------------------------------------------
    // These stay in common on purpose. The strings are uploaded to Nightscout and xDrip, so a
    // difference between platforms would be a data bug rather than a cosmetic one.

    override fun fromISODateString(isoDateString: String): Long {
        val cleaned = isoDateString.trim()
        // Accepts what AAPS has always accepted: an optional fraction, and an offset written as
        // `Z`, `+HH:MM` or `+HHMM`.
        val match = ISO_PATTERN.find(cleaned) ?: error("not an ISO date: $isoDateString")
        val (date, time, fraction, offset) = match.destructured
        val millisOfSecond = fraction.trimStart('.').padEnd(3, '0').take(3).toIntOrNull() ?: 0
        val local = LocalDateTime.parse("${date}T$time")
        val zone = when {
            offset.isEmpty() || offset == "Z" -> TimeZone.UTC
            else                              -> TimeZone.of(normaliseOffset(offset))
        }
        return local.toInstant(zone).toEpochMilliseconds() + millisOfSecond
    }

    /** Turns `+0200` into `+02:00`, which is the only spelling [TimeZone.of] accepts. */
    private fun normaliseOffset(offset: String): String =
        if (offset.contains(':')) offset else offset.substring(0, 3) + ":" + offset.substring(3)

    override fun toISOString(date: Long): String {
        val utc = date.at(TimeZone.UTC)
        return "${utc.date}T${two(utc.hour)}:${two(utc.minute)}:${two(utc.second)}.${three(millisPart(date))}Z"
    }

    override fun toISOAsUTC(timestamp: Long): String {
        val utc = timestamp.at(TimeZone.UTC)
        return "${utc.date}T${two(utc.hour)}:${two(utc.minute)}:${two(utc.second)}.${three(millisPart(timestamp))}0000Z"
    }

    override fun toISONoZone(timestamp: Long): String {
        val local = timestamp.at()
        return "${local.date}T${two(local.hour)}:${two(local.minute)}:${two(local.second)}"
    }

    // ---- day arithmetic -------------------------------------------------------------------------

    override fun secondsOfTheDayToMillisecondsOfHoursAndMinutes(seconds: Int): Long {
        val totalMinutes = seconds / 60
        return today().atTime(LocalTime(totalMinutes / 60, totalMinutes % 60))
            .toInstant(systemZone).toEpochMilliseconds()
    }

    override fun secondsOfTheDayToMilliseconds(seconds: Int): Long =
        today().atTime(LocalTime.fromSecondOfDay(seconds))
            .toInstant(systemZone).toEpochMilliseconds()

    private fun today(): LocalDate = now().at().date

    override fun toSeconds(hhColonMm: String): Int {
        val m = HH_MM_PATTERN.find(hhColonMm) ?: return 0
        var hour = SafeParse.stringToInt(m.groupValues[1])
        val minute = SafeParse.stringToInt(m.groupValues[2])
        val amPm = m.groupValues[3].trim().uppercase()
        if (amPm.endsWith("AM") && hour == 12) hour = 0 // Midnight case
        if (amPm.endsWith("PM") && hour != 12) hour += 12 // Afternoon case
        return (hour * 3600) + (minute * 60)
    }

    // ---- text in the user's language ------------------------------------------------------------

    override fun dateString(mills: Long): String = platform.localizedShortDate(mills)

    override fun dateStringRelative(mills: Long, rh: TextResolver): String {
        val nowMillis = now()
        val startOfTodayMillis = beginOfDay(nowMillis)
        return if (mills < nowMillis) { // Past
            when {
                mills > startOfTodayMillis                             -> "${rh.gs(InterfacesStrings.today)} - ${dateString(mills)}"
                mills > startOfTodayMillis - 1.days.inWholeMilliseconds -> "${rh.gs(InterfacesStrings.yesterday)} - ${dateString(mills)}"
                mills > startOfTodayMillis - 7.days.inWholeMilliseconds -> "${dayAgo(mills, rh, true)} - ${dateString(mills)}"
                else                                                   -> dateString(mills)
            }
        } else { // Future
            when {
                mills < startOfTodayMillis + 1.days.inWholeMilliseconds -> rh.gs(InterfacesStrings.later_today)
                mills < startOfTodayMillis + 2.days.inWholeMilliseconds -> rh.gs(InterfacesStrings.tomorrow)
                mills < startOfTodayMillis + 7.days.inWholeMilliseconds -> dayAgo(mills, rh, true)
                else                                                   -> dateString(mills)
            }
        }
    }

    override fun dateStringShort(mills: Long): String =
        platform.format(mills, if (platform.is24Hour()) "dd/MM" else "MM/dd")

    override fun timeString(): String = timeString(now())
    override fun timeString(mills: Long): String =
        platform.format(mills, if (platform.is24Hour()) "HH:mm" else "hh:mm a")

    override fun secondString(): String = secondString(now())
    override fun secondString(mills: Long): String = platform.format(mills, "ss")

    override fun minuteString(): String = minuteString(now())
    override fun minuteString(mills: Long): String = platform.format(mills, "mm")

    override fun hourString(): String = hourString(now())
    override fun hourString(mills: Long): String =
        platform.format(mills, if (platform.is24Hour()) "HH" else "hh")

    override fun amPm(): String = amPm(now())
    override fun amPm(mills: Long): String = platform.format(mills, "a")

    override fun dayNameString(format: String): String = dayNameString(now(), format)
    override fun dayNameString(mills: Long, format: String): String = platform.format(mills, format)

    override fun dayString(): String = dayString(now())
    override fun dayString(mills: Long): String = platform.format(mills, "dd")

    override fun monthString(format: String): String = monthString(now(), format)
    override fun monthString(mills: Long, format: String): String = platform.format(mills, format)

    override fun weekString(): String = weekString(now())
    override fun weekString(mills: Long): String = platform.format(mills, "ww")

    override fun timeStringWithSeconds(mills: Long): String =
        platform.format(mills, if (platform.is24Hour()) "HH:mm:ss" else "hh:mm:ss a")

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

    // ---- durations described in words -----------------------------------------------------------

    override fun minAgo(rh: TextResolver, time: Long?): String {
        if (time == null) return ""
        val minutes = (now() - time).milliseconds.inWholeMinutes.toInt()
        return if (abs(minutes) > 9999) "" else rh.gs(InterfacesStrings.minago, minutes)
    }

    override fun minOrSecAgo(rh: TextResolver, time: Long?): String {
        if (time == null) return ""
        val duration = (now() - time).milliseconds
        return when {
            duration.inWholeMinutes >= 2 -> rh.gs(InterfacesStrings.minago, duration.inWholeMinutes.toInt())
            else                         -> rh.gs(InterfacesStrings.secago, duration.inWholeSeconds.toInt())
        }
    }

    override fun minOrSec(rh: TextResolver, durationMs: Long): String {
        if (durationMs < 0) return ""
        val duration = durationMs.milliseconds
        return when {
            duration.inWholeMinutes >= 2 -> rh.gs(InterfacesStrings.min_plus, duration.inWholeMinutes.toInt())
            else                         -> rh.gs(InterfacesStrings.sec_plus, duration.inWholeSeconds.toInt())
        }
    }

    override fun minAgoShort(time: Long?): String {
        if (time == null) return ""
        val minutes = (time - now()).milliseconds.inWholeMinutes.toInt()
        return if (abs(minutes) > 9999) ""
        else "(" + (if (minutes > 0) "+" else "") + minutes + ")"
    }

    override fun minAgoLong(rh: TextResolver, time: Long?): String {
        if (time == null) return ""
        val minutes = (now() - time).milliseconds.inWholeMinutes.toInt()
        return if (abs(minutes) > 9999) "" else rh.gs(InterfacesStrings.minago_long, minutes)
    }

    override fun hourAgo(time: Long, rh: TextResolver): String =
        rh.gs(InterfacesStrings.hoursago, (now() - time).milliseconds.inWholeHours)

    override fun dayAgo(time: Long, rh: TextResolver, round: Boolean): String {
        val duration = (now() - time).milliseconds
        if (round) {
            val daysAsDouble = duration.toDouble(DurationUnit.DAYS)
            return if (duration.isPositive()) rh.gs(InterfacesStrings.days_ago_round, ceil(daysAsDouble))
            else rh.gs(InterfacesStrings.in_days_round, floor(daysAsDouble))
        }
        return if (duration.isPositive()) rh.gs(InterfacesStrings.days_ago, duration.inWholeDays)
        else rh.gs(InterfacesStrings.in_days, abs(duration.inWholeDays))
    }

    override fun beginOfDay(mills: Long): Long =
        mills.at().date.atStartOfDayIn(systemZone).toEpochMilliseconds()

    override fun timeStringFromSeconds(seconds: Int): String =
        timeStrings.getOrPut(seconds) { timeString(secondsOfTheDayToMilliseconds(seconds)) }

    override fun timeFrameString(timeInMillis: Long, rh: TextResolver, withParentheses: Boolean): String {
        val duration = timeInMillis.milliseconds
        val totalHours = duration.inWholeHours
        val remainingMinutes = (duration - totalHours.hours).inWholeMinutes
        val hoursPart = if (totalHours > 0) "$totalHours${rh.gs(InterfacesStrings.shorthour)} " else ""
        val body = "$hoursPart$remainingMinutes'"
        return if (withParentheses) "($body)" else body
    }

    override fun sinceString(timestamp: Long, rh: TextResolver): String =
        timeFrameString(now() - timestamp, rh)

    override fun untilString(timestamp: Long, rh: TextResolver, withParentheses: Boolean): String =
        timeFrameString(timestamp - now(), rh, withParentheses)

    override fun timeRemainingString(timeInMillis: Long, rh: TextResolver): String {
        val duration = timeInMillis.milliseconds
        val totalHours = duration.inWholeHours.toInt()
        val remainingMinutes = (duration - totalHours.hours).inWholeMinutes.toInt()
        return if (totalHours > 0) rh.gs(InterfacesStrings.time_remaining_h_m, totalHours, remainingMinutes)
        else rh.gs(InterfacesStrings.time_remaining_m, remainingMinutes)
    }

    // ---- the clock ------------------------------------------------------------------------------

    override fun now(): Long = clock.now().toEpochMilliseconds()

    override fun nowWithoutMilliseconds(): Long = (now() / 1000L) * 1000L

    override fun isOlderThan(date: Long, minutes: Long): Boolean =
        date < now() - minutes * 60_000L

    override fun getTimeZoneOffsetMs(): Long = platform.standardUtcOffsetMillis()

    override fun getTimeZoneOffsetMsWithDST(): Long =
        systemZone.offsetAt(clock.now()).totalSeconds.seconds.inWholeMilliseconds

    override fun getTimeZoneOffsetMinutes(timestamp: Long): Int =
        systemZone.offsetAt(Instant.fromEpochMilliseconds(timestamp)).totalSeconds / 60

    override fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean =
        timestamp1.at().date == timestamp2.at().date

    override fun isAfterNoon(): Boolean = now().at().hour >= 12

    override fun isSameDayGroup(timestamp1: Long, timestamp2: Long): Boolean {
        val now = now()
        if (now in (timestamp1 + 1) until timestamp2 || now in (timestamp2 + 1) until timestamp1) return false
        return isSameDay(timestamp1, timestamp2)
    }

    //Map:{DAYS=1, HOURS=3, MINUTES=46, SECONDS=40, MILLISECONDS=0, MICROSECONDS=0, NANOSECONDS=0}
    override fun computeDiff(date1: Long, date2: Long): TimeDiff {
        val duration = (date2 - date1).milliseconds
        return duration.toComponents { days, hours, minutes, seconds, nanoseconds ->
            TimeDiff(
                days = days,
                hours = hours.toLong(),
                minutes = minutes.toLong(),
                seconds = seconds.toLong(),
                // Remaining nanoseconds, split into millis, micros and nanos.
                milliseconds = nanoseconds.toLong() / 1_000_000,
                microseconds = (nanoseconds.toLong() / 1_000) % 1000,
                nanoseconds = nanoseconds.toLong() % 1000
            )
        }
    }

    override fun age(milliseconds: Long, useShortText: Boolean, rh: TextResolver): String {
        val duration = milliseconds.milliseconds
        if (duration.inWholeDays > 1000) return rh.gs(InterfacesStrings.forever)
        val daysUnit = if (useShortText) rh.gs(InterfacesStrings.shortday) else rh.gs(InterfacesStrings.days)
        val hoursUnit = if (useShortText) rh.gs(InterfacesStrings.shorthour) else rh.gs(InterfacesStrings.hours)
        val minutesUnit = if (useShortText) rh.gs(InterfacesStrings.shortminute) else rh.gs(InterfacesStrings.unit_minutes)
        val days = duration.inWholeDays
        val hours = (duration - days.days).inWholeHours
        val minutes = (duration - days.days - hours.hours).inWholeMinutes
        return when {
            days > 0  -> "$days $daysUnit $hours $hoursUnit "
            hours > 0 -> "$hours $hoursUnit $minutes $minutesUnit "
            else      -> "${duration.inWholeMinutes} $minutesUnit"
        }
    }

    override fun niceTimeScalar(time: Long, rh: TextResolver): String {
        val duration = time.milliseconds
        val (value, unit) = when {
            duration.inWholeDays > 6     -> {
                val weeks = duration.inWholeDays / 7
                weeks to if (weeks == 1L) InterfacesStrings.unit_week else InterfacesStrings.unit_weeks
            }
            duration.inWholeHours > 23   -> {
                val days = duration.inWholeDays
                days to if (days == 1L) InterfacesStrings.unit_day else InterfacesStrings.unit_days
            }
            duration.inWholeMinutes > 59 -> {
                val hours = duration.inWholeHours
                hours to if (hours == 1L) InterfacesStrings.unit_hour else InterfacesStrings.unit_hours
            }
            duration.inWholeSeconds > 59 -> {
                val minutes = duration.inWholeMinutes
                minutes to if (minutes == 1L) InterfacesStrings.unit_minute else InterfacesStrings.unit_minutes
            }
            else                         -> {
                val seconds = duration.inWholeSeconds
                seconds to if (seconds == 1L) InterfacesStrings.unit_second else InterfacesStrings.unit_seconds
            }
        }
        return "${qs(value.toDouble(), 0)} ${rh.gs(unit)}"
    }

    override fun qs(x: Double, numDigits: Int): String {
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
        // maxFractionDigits with minFractionDigits = 0 keeps the original behaviour of
        // not showing trailing zeros (12.0 -> "12").
        // The separator is always a dot, as it was before. Grouping is off: the old code used
        // DecimalFormat() which groups by default, and since only the decimal separator was
        // overridden the grouping separator stayed locale dependent. On a German device that
        // turned 1234.5 into "1.234.5", and on a Czech one into "1<nbsp>234.5".
        // max(0, ...) keeps the old behaviour for a numDigits below -1: DecimalFormat clamped
        // a negative maximumFractionDigits to 0, so the value was printed as a whole number.
        // Without it NumberFormat would throw, because maxFractionDigits may not be negative.
        return NumberFormat(minFractionDigits = 0, maxFractionDigits = max(0, digits))
            .format(x, NumberFormatPlatform.SEPARATOR_DOT)
    }

    override fun formatHHMM(timeAsSeconds: Int): String {
        val duration = timeAsSeconds.seconds
        val hours = duration.inWholeHours
        val minutes = (duration - hours.hours).inWholeMinutes
        return "${two(hours.toInt())}:${two(minutes.toInt())}"
    }

    override fun timeZoneByOffset(offsetInMilliseconds: Long): String {
        if (offsetInMilliseconds == 0L) return "UTC"
        val offsetInSeconds = offsetInMilliseconds.milliseconds.inWholeSeconds.toInt()
        val now = clock.now()
        return TimeZone.availableZoneIds
            .firstOrNull { TimeZone.of(it).offsetAt(now).totalSeconds == offsetInSeconds }
            ?: "UTC" // Default to "UTC" if no match is found.
    }

    override fun timeStampToUtcDateMillis(timestamp: Long): Long =
        timestamp.at(TimeZone.UTC).date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

    //TODO: timeStampToUtcDateMillis has a different output than the old function.
    // Since that seems to be desired behaviour in the history browser,
    // the functionality was refactored in getTimestampWithCurrentTimeOfDay()
    override fun getTimestampWithCurrentTimeOfDay(timestamp: Long): Long =
        timestamp.at().date.atTime(now().at().time).toInstant(systemZone).toEpochMilliseconds()

    override fun mergeUtcDateToTimestamp(timestamp: Long, dateUtcMillis: Long): Long =
        dateUtcMillis.at(TimeZone.UTC).date.atTime(timestamp.at().time)
            .toInstant(systemZone).toEpochMilliseconds()

    override fun mergeHourMinuteToTimestamp(timestamp: Long, hour: Int, minute: Int, randomSecond: Boolean): Long {
        val original = timestamp.at()
        val second = if (randomSecond) seconds++ % 60 else original.second
        return LocalDateTime(original.year, original.month, original.day, hour, minute, second)
            .toInstant(systemZone).toEpochMilliseconds()
    }

    private fun millisPart(millis: Long): Int = ((millis % 1000) + 1000).toInt() % 1000

    private fun two(value: Int): String = value.toString().padStart(2, '0')

    private fun three(value: Int): String = value.toString().padStart(3, '0')

    private val timeStrings = mutableMapOf<Int, String>()
    private var seconds: Int = (Random.nextDouble() * 59.0).toInt()

    companion object {

        private val ISO_PATTERN =
            Regex("""(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2}(?::\d{2})?)(\.\d+)?(Z|[+-]\d{2}:?\d{2})?""")
        private val HH_MM_PATTERN = Regex("""(\d+):(\d+)( a\.m\.| p\.m\.| AM| PM|AM|PM|)""")
    }
}
