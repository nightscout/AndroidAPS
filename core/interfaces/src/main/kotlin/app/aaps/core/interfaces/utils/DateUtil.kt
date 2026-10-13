package app.aaps.core.interfaces.utils

import app.aaps.core.interfaces.resources.ResourceHelper
import java.util.concurrent.TimeUnit

/**
 * The Class DateUtil. A modern utility class for handling dates, times, and durations using the `java.time` API.
 * It provides methods for parsing, formatting, and manipulating timestamps with proper timezone and locale handling.
 */
interface DateUtil {

    /**
     * Parses a standard ISO-8601 date string into a Unix timestamp in milliseconds.
     * @param isoDateString The string to parse (e.g., "2023-10-27T10:30:00Z").
     * @return The number of milliseconds since the Unix epoch.
     * Unlike the old Joda-Time, this will throw an exception if the provided
     * string is not in the correct format.
     */
    fun fromISODateString(isoDateString: String): Long

    /**
     * Converts a Unix timestamp in milliseconds into a standard ISO-8601 formatted string in UTC.
     * @param date The timestamp in milliseconds.
     * @return An ISO-formatted date string (e.g., "2023-10-27T10:30:00Z").
     */
    fun toISOString(date: Long): String

    /**
     * Converts a timestamp to an ISO-like string, forced into UTC and with a specific "Z" suffix format.
     * @param timestamp The timestamp in milliseconds.
     * @return A UTC formatted string like "2023-10-27T10:30:00.1230000Z".
     */
    fun toISOAsUTC(timestamp: Long): String

    /**
     * Converts a timestamp to an ISO-like string representing the local date and time in the app's `systemZone`, without any timezone information.
     * @param timestamp The timestamp in milliseconds.
     * @return A local formatted string like "2023-10-27T06:30:00".
     */
    fun toISONoZone(timestamp: Long): String

    /**
     * Converts a number of minutes from the beginning of today into a full Unix timestamp for that time.
     * @param seconds The number of seconds past midnight.
     * @return The full timestamp in milliseconds for that time on the current day.
     */
    fun secondsOfTheDayToMillisecondsOfHoursAndMinutes(seconds: Int): Long

    /**
     * Converts a number of seconds from the beginning of today into a full Unix timestamp for that time.
     * @param seconds The number of seconds past midnight.
     * @return The full timestamp in milliseconds for that time on the current day.
     */
    fun secondsOfTheDayToMilliseconds(seconds: Int): Long

    /**
     * Parses a time string (e.g., "14:30", "2:30 PM") into the total number of seconds from the start of the day.
     * @param hhColonMm The time string to parse.
     * @return The total number of seconds from midnight.
     */
    fun toSeconds(hhColonMm: String): Int

    /**
     * Formats a timestamp into a localized date string (e.g., "10/27/2023").
     * @param mills The timestamp in milliseconds.
     * @return The formatted date string.
     */
    fun dateString(mills: Long): String

    /**
     * Formats a timestamp into a user-friendly, relative date string (e.g., "Today", "Yesterday", "Tomorrow").
     * Falls back to a standard date format for dates further in the past or future.
     * @param mills The timestamp in milliseconds.
     * @param rh A resource helper to get localized strings like "Today".
     * @return The relative date string.
     */
    fun dateStringRelative(mills: Long, rh: ResourceHelper): String

    /**
     * Formats a timestamp into a short date string (e.g., "10/27" or "27/10") based on the user's 12/24 hour preference.
     * @param mills The timestamp in milliseconds.
     * @return A short date string.
     */
    fun dateStringShort(mills: Long): String

    /**
     * Gets the current time formatted as a string (e.g., "10:30 AM"), respecting the device's 12/24 hour setting.
     * @return The formatted time string.
     */
    fun timeString(): String

    /**
     * Formats a timestamp as a string (e.g., "10:30 AM"), respecting the device's 12/24 hour setting.
     * @param mills The timestamp in milliseconds.
     * @return The formatted time string.
     */
    fun timeString(mills: Long): String

    /**
     * Gets the seconds part of the current time as a two-digit string (e.g., "05").
     * @return The formatted seconds string.
     */
    fun secondString(): String

    /**
     * Extracts the seconds part of a timestamp as a two-digit string (e.g., "05").
     * @param mills The timestamp in milliseconds.
     * @return The formatted seconds string.
     */
    fun secondString(mills: Long): String

    /**
     * Gets the minutes part of the current time as a two-digit string (e.g., "30").
     * @return The formatted minutes string.
     */
    fun minuteString(): String

    /**
     * Extracts the minutes part of a timestamp as a two-digit string (e.g., "30").
     * @param mills The timestamp in milliseconds.
     * @return The formatted minutes string.
     */
    fun minuteString(mills: Long): String

    /**
     * Gets the hour part of the current time as a string, respecting the 12/24 hour format.
     * @return The formatted hour string.
     */
    fun hourString(): String

    /**
     * Extracts the hour part of a timestamp as a string, respecting the 12/24 hour format.
     * @param mills The timestamp in milliseconds.
     * @return The formatted hour string.
     */
    fun hourString(mills: Long): String

    /**
     * Gets the localized AM/PM designator for the current time.
     * @return The AM/PM string (e.g., "AM", "PM").
     */
    fun amPm(): String

    /**
     * Gets the localized AM/PM designator for a given timestamp.
     * @param mills The timestamp in milliseconds.
     * @return The AM/PM string.
     */
    fun amPm(mills: Long): String

    /**
     * Gets the day name for the current date, formatted according to the given pattern.
     * @param format A pattern like "EEE" (short name) or "EEEE" (full name).
     * @return The formatted day name.
     */
    fun dayNameString(format: String = "E"): String

    /**
     * Gets the day name for a given timestamp, formatted according to the given pattern.
     * @param mills The timestamp in milliseconds.
     * @param format A pattern like "EEE" (short name) or "EEEE" (full name).
     * @return The formatted day name.
     */
    fun dayNameString(mills: Long, format: String = "E"): String

    /**
     * Gets the day of the month for the current date as a two-digit string (e.g., "27").
     * @return The formatted day string.
     */
    fun dayString(): String = dayString(now())

    /**
     * Extracts the day of the month from a timestamp as a two-digit string (e.g., "27").
     * @param mills The timestamp in milliseconds.
     * @return The formatted day string.
     */
    fun dayString(mills: Long): String

    /**
     * Gets the month for the current date, formatted according to the given pattern.
     * @param format A pattern like "M" (number), "MMM" (short name), or "MMMM" (full name).
     * @return The formatted month string.
     */
    fun monthString(format: String = "MMM"): String

    /**
     * Extracts the month from a timestamp, formatted according to the given pattern.
     * @param mills The timestamp in milliseconds.
     * @param format A pattern like "M" (number), "MMM" (short name), or "MMMM" (full name).
     * @return The formatted month string.
     */
    fun monthString(mills: Long, format: String = "MMM"): String

    /**
     * Gets the week of the year for the current date as a string.
     * @return The formatted week string.
     */
    fun weekString(): String

    /**
     * Extracts the week of the year from a timestamp as a string.
     * @param mills The timestamp in milliseconds.
     * @return The formatted week string.
     */
    fun weekString(mills: Long): String

    /**
     * Formats a timestamp into a time string that includes seconds (e.g., "10:30:05 AM").
     * Respects the device's 12/24 hour setting.
     * @param mills The timestamp in milliseconds.
     * @return The formatted time string with seconds.
     */
    fun timeStringWithSeconds(mills: Long): String

    /**
     * Creates a string representing a date and time range.
     * @param start The start timestamp in milliseconds.
     * @param end The end timestamp in milliseconds.
     * @return A formatted string like "10/27/2023 10:00 AM - 11:00 AM".
     */
    fun dateAndTimeRangeString(start: Long, end: Long): String

    /**
     * Creates a string representing a time range.
     * @param start The start timestamp in milliseconds.
     * @param end The end timestamp in milliseconds.
     * @return A formatted string like "10:00 AM - 11:00 AM".
     */
    fun timeRangeString(start: Long, end: Long): String

    /**
     * Combines the localized date and time strings for a given timestamp.
     * @param mills The timestamp in milliseconds. Returns empty string if 0.
     * @return A formatted string like "10/27/2023 10:30 AM".
     */
    fun dateAndTimeString(mills: Long): String

    /**
     * Combines the localized date and time strings for a given timestamp, returning null if the timestamp is null or 0.
     * @param mills The nullable timestamp in milliseconds.
     * @return A formatted string like "10/27/2023 10:30 AM", or null.
     */
    fun dateAndTimeStringNullable(mills: Long?): String?

    /**
     * Combines the localized date and time (with seconds) strings for a given timestamp.
     * @param mills The timestamp in milliseconds. Returns empty string if 0.
     * @return A formatted string like "10/27/2023 10:30:05 AM".
     */
    fun dateAndTimeAndSecondsString(mills: Long): String

    /**
     * Returns a string describing how many minutes ago a timestamp was (e.g., "5 min ago").
     * @param rh Resource helper for localized strings.
     * @param time The timestamp in milliseconds. Can be null.
     * @return The relative time string.
     */
    fun minAgo(rh: ResourceHelper, time: Long?): String

    /**
     * Returns a string describing how many minutes or seconds ago a timestamp was.
     * Uses seconds if under 2 minutes, otherwise minutes.
     * @param rh Resource helper for localized strings.
     * @param time The timestamp in milliseconds. Can be null.
     * @return The relative time string (e.g., "30 sec ago" or "3 min ago").
     */
    fun minOrSecAgo(rh: ResourceHelper, time: Long?): String

    /**
     * Returns a short string showing the difference in minutes between now and a given time, with a sign.
     * E.g., "(+5)" for 5 minutes in the future, "(-10)" for 10 minutes in the past.
     * @param time The timestamp in milliseconds. Can be null.
     * @return The short formatted difference string.
     */
    fun minAgoShort(time: Long?): String

    /**
     * Returns a verbose string describing how many minutes ago a timestamp was.
     * @param rh Resource helper for localized strings.
     * @param time The timestamp in milliseconds. Can be null.
     * @return The verbose relative time string.
     */
    fun minAgoLong(rh: ResourceHelper, time: Long?): String

    /**
     * Returns a string describing how many hours ago a timestamp was.
     * @param time The timestamp in milliseconds.
     * @param rh Resource helper for localized strings.
     * @return The relative hours string.
     */
    fun hourAgo(time: Long, rh: ResourceHelper): String

    /**
     * Returns a string describing how many days ago (or in how many days) a timestamp is.
     * @param time The timestamp in milliseconds.
     * @param rh Resource helper for localized strings.
     * @param round If true, rounds to the nearest whole day. Otherwise uses fractional days.
     * @return The relative days string.
     */
    fun dayAgo(time: Long, rh: ResourceHelper, round: Boolean = false): String

    /**
     * Calculates the timestamp for the beginning of the day (midnight) for a given timestamp.
     * @param mills The timestamp in milliseconds.
     * @return The timestamp in milliseconds for midnight at the start of that day.
     */
    fun beginOfDay(mills: Long): Long

    /**
     * Converts seconds from the start of the day to a formatted time string, with caching for performance.
     * @param seconds The number of seconds past midnight.
     * @return A formatted time string (e.g., "10:30 AM").
     */
    fun timeStringFromSeconds(seconds: Int): String

    /**
     * Formats a duration in milliseconds into a human-readable string with hours and minutes.
     * @param timeInMillis The duration in milliseconds.
     * @param rh Resource helper for localized units (e.g., "h").
     * @return A formatted duration string like "(1h 30m)".
     */
    fun timeFrameString(timeInMillis: Long, rh: ResourceHelper): String

    /**
     * Calculates the elapsed time since a given timestamp and formats it as a duration.
     * @param timestamp The past timestamp in milliseconds.
     * @param rh Resource helper.
     * @return A formatted duration string of the elapsed time.
     */
    fun sinceString(timestamp: Long, rh: ResourceHelper): String

    /**
     * Calculates the time remaining until a future timestamp and formats it as a duration.
     * @param timestamp The future timestamp in milliseconds.
     * @param rh Resource helper.
     * @return A formatted duration string of the remaining time.
     */
    fun untilString(timestamp: Long, rh: ResourceHelper): String

    /**
     * Gets the current system time in milliseconds.
     * @return The current timestamp.
     */
    fun now(): Long

    /**
     * Gets the current system time in milliseconds, with the millisecond part set to zero.
     * @return The current timestamp, truncated to the second.
     */
    fun nowWithoutMilliseconds(): Long

    /**
     * Checks if a given timestamp is older than a specified number of minutes from now.
     * @param date The timestamp to check, in milliseconds.
     * @param minutes The number of minutes to check against.
     * @return True if the date is older, false otherwise.
     */
    fun isOlderThan(date: Long, minutes: Long): Boolean

    /**
     * Gets the standard (non-DST) timezone offset for the app's `systemZone` in milliseconds.
     * @return The standard offset in milliseconds.
     */
    fun getTimeZoneOffsetMs(): Long

    /**
     * Gets the DST-aware timezone offset for the app's `systemZone` in milliseconds.
     * @return The standard offset in milliseconds.
     */
    fun getTimeZoneOffsetMsWithDST(): Long

    /**
     * Gets the timezone offset in minutes for a specific moment in time.
     * This correctly handles Daylight Saving Time (DST), returning the actual offset for that instant.
     * @param timestamp The timestamp in milliseconds to check.
     * @return The total offset from UTC in minutes (e.g., -240 for EDT, -300 for EST).
     */
    fun getTimeZoneOffsetMinutes(timestamp: Long): Int

    /**
     * Checks if two timestamps occur on the same calendar day in the app's `systemZone`.
     * This is DST-safe and correctly handles timestamps that might be on different UTC dates but the same local date.
     * @param timestamp1 The first timestamp in milliseconds.
     * @param timestamp2 The second timestamp in milliseconds.
     * @return True if they are on the same local calendar day, false otherwise.
     */
    fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean

    /**
     * Checks if the current time in the app's `systemZone` is past noon (12:00 PM).
     * @return True if the hour is 12 or greater, false otherwise.
     */
    fun isAfterNoon(): Boolean

    /**
     * A specialized check to see if two timestamps are on the same day, with an additional
     * condition that the current time (`now`) does not fall between them.
     * @param timestamp1 The first timestamp in milliseconds.
     * @param timestamp2 The second timestamp in milliseconds.
     * @return False if `now` is between the two timestamps, otherwise the result of `isSameDay()`.
     */
    fun isSameDayGroup(timestamp1: Long, timestamp2: Long): Boolean

    /**
     * Computes the difference between two timestamps and breaks it down into whole days, hours, and minutes.
     * @param date1 The start timestamp in milliseconds.
     * @param date2 The end timestamp in milliseconds.
     * @return A map containing the total number of full days, leftover hours, and leftover minutes.
     */
    //Map:{DAYS=1, HOURS=3, MINUTES=46, SECONDS=40, MILLISECONDS=0, MICROSECONDS=0, NANOSECONDS=0}
    fun computeDiff(date1: Long, date2: Long): Map<TimeUnit, Long>

    /**
     * Converts a duration in milliseconds into a human-readable "age" string (e.g., "5 days 3 hours").
     * @param milliseconds The duration to format.
     * @param useShortText If true, uses abbreviated units (e.g., "d", "h"). If false, uses full names (e.g., "days", "hours").
     * @param rh Resource helper to get localized unit strings.
     * @return The formatted age string.
     */
    fun age(milliseconds: Long, useShortText: Boolean, rh: ResourceHelper): String
    fun timeAgoFullString(milliseconds: Long, rh: ResourceHelper): String


    /**
     * Converts a duration in milliseconds into a simplified, human-readable string with the largest appropriate unit.
     * (e.g., 120000ms becomes "2 minutes"). It handles pluralization for different languages.
     * @param time The duration in milliseconds.
     * @param rh Resource helper to get localized unit strings (e.g., "second", "seconds").
     * @return The formatted string with a single unit (e.g., "5 days").
     */
    fun niceTimeScalar(time: Long, rh: ResourceHelper): String
    /**
     * A thread-safe, locale-agnostic utility to format a double into a string with a specific number of decimal digits.
     * It is optimized to use a cached formatter on the UI thread.
     * @param x The double value to format.
     * @param numDigits The number of decimal digits. If -1, it attempts to auto-detect.
     * @return The formatted string.
     */
    fun qs(x: Double, numDigits: Int): String

    /**
     * Formats a total number of seconds into a zero-padded HH:mm string.
     * @param timeAsSeconds The total duration in seconds.
     * @return A formatted string like "08:05".
     */
    fun formatHHMM(timeAsSeconds: Int): String

    /**
     * Attempts to find a representative IANA timezone name (e.g., "America/New_York")
     * that matches a given offset in milliseconds at the present time.
     * @param offsetInMilliseconds The timezone offset from UTC.
     * @return A matching timezone ID string, or "UTC" if no match is found.
     */
    fun timeZoneByOffset(offsetInMilliseconds: Long): String

    /**
     * Calculates the timestamp for midnight UTC at the beginning of the day for a given timestamp.
     * This effectively strips the time-of-day information, keeping the UTC date.
     * @param timestamp The timestamp in milliseconds.
     * @return A timestamp in milliseconds, representing the start of the UTC day (00:00:00Z).
     */
    fun timeStampToUtcDateMillis(timestamp: Long): Long

    /**
     * Only used in HistoryBrowser instead of timeStampToUtcDateMillis. [LEGACY SUPPORT FUNCTION]
     * Creates a new timestamp by combining the DATE from the input
     * with the TIME OF DAY from the current system time, interpreted in the local timezone.
     * @param timestamp The timestamp providing the date.
     * @return A new timestamp mixing the input date with the current time of day.
     */
    fun getTimestampWithCurrentTimeOfDay(timestamp: Long): Long

    /**
     * Merges a UTC date with a local time from another timestamp.
     * It takes the date part from `dateUtcMillis` and the time-of-day part from `timestamp`.
     * @param timestamp The timestamp providing the time-of-day (in the app's `systemZone`).
     * @param dateUtcMillis The timestamp providing the date (in UTC).
     * @return A new timestamp combining the UTC date and the local time.
     */
    fun mergeUtcDateToTimestamp(timestamp: Long, dateUtcMillis: Long): Long

    /**
     * Creates a new timestamp by replacing the hour and minute of an existing timestamp.
     * @param timestamp The base timestamp to modify.
     * @param hour The new hour to set (0-23).
     * @param minute The new minute to set (0-59).
     * @param randomSecond If true, sets the second to a pseudo-random, incrementing value.
     * @return The new, updated timestamp in milliseconds.
     */
    fun mergeHourMinuteToTimestamp(timestamp: Long, hour: Int, minute: Int, randomSecond: Boolean = false): Long
}
