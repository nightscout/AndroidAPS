package info.nightscout.shared.utils

import android.os.Build
import androidx.annotation.RequiresApi
import info.nightscout.shared.interfaces.ResourceHelper
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * The Class DateUtil. A simple wrapper around SimpleDateFormat to ease the handling of iso date string &lt;-&gt; date obj
 * with TZ
 */
interface DateUtil {

    /**
     * Takes in an ISO date string of the following format:
     * yyyy-mm-ddThh:mm:ss.ms+HoMo
     *
     * @param isoDateString the iso date string
     * @return the date
     */
    fun fromISODateString(isoDateString: String): Long

    /**
     * Render date
     *
     * @param date   the date obj
     * @return the iso-formatted date string
     */
    fun toISOString(date: Long): String
    fun toISOAsUTC(timestamp: Long): String
    fun toISONoZone(timestamp: Long): String
    fun secondsOfTheDayToMilliseconds(seconds: Int): Long
    fun toSeconds(hhColonMm: String): Int
    fun dateString(mills: Long): String
    fun dateStringRelative(mills: Long, rh: ResourceHelper): String
    fun dateStringShort(mills: Long): String
    fun timeString(): String
    fun timeString(mills: Long): String
    fun secondString(): String
    fun secondString(mills: Long): String
    fun minuteString(): String
    fun minuteString(mills: Long): String
    fun hourString(): String
    fun hourString(mills: Long): String
    fun amPm(): String
    fun amPm(mills: Long): String
    fun dayNameString(format: String = "E"): String
    fun dayNameString(mills: Long, format: String = "E"): String
    fun dayString(): String = dayString(now())
    fun dayString(mills: Long): String
    fun monthString(format: String = "MMM"): String
    fun monthString(mills: Long, format: String = "MMM"): String
    fun weekString(): String
    fun weekString(mills: Long): String
    fun timeStringWithSeconds(mills: Long): String
    fun dateAndTimeRangeString(start: Long, end: Long): String
    fun timeRangeString(start: Long, end: Long): String
    fun dateAndTimeString(mills: Long): String
    fun dateAndTimeAndSecondsString(mills: Long): String
    fun minAgo(rh: ResourceHelper, time: Long?): String
    fun minAgoShort(time: Long?): String
    fun minAgoLong(rh: ResourceHelper, time: Long?): String
    fun hourAgo(time: Long, rh: ResourceHelper): String
    fun dayAgo(time: Long, rh: ResourceHelper, round: Boolean = false): String
    fun beginOfDay(mills: Long): Long
    fun timeStringFromSeconds(seconds: Int): String
    fun timeFrameString(timeInMillis: Long, rh: ResourceHelper): String
    fun sinceString(timestamp: Long, rh: ResourceHelper): String
    fun untilString(timestamp: Long, rh: ResourceHelper): String
    fun now(): Long
    fun nowWithoutMilliseconds(): Long
    fun isOlderThan(date: Long, minutes: Long): Boolean
    fun getTimeZoneOffsetMs(): Long
    fun getTimeZoneOffsetMinutes(timestamp: Long): Int
    fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean

    fun isSameDayGroup(timestamp1: Long, timestamp2: Long): Boolean

    //Map:{DAYS=1, HOURS=3, MINUTES=46, SECONDS=40, MILLISECONDS=0, MICROSECONDS=0, NANOSECONDS=0}
    fun computeDiff(date1: Long, date2: Long): Map<TimeUnit, Long>
    fun age(milliseconds: Long, useShortText: Boolean, rh: ResourceHelper): String
    fun niceTimeScalar(time: Long, rh: ResourceHelper): String
    fun qs(x: Double, numDigits: Int): String
    fun formatHHMM(timeAsSeconds: Int): String

    @RequiresApi(Build.VERSION_CODES.O)
    fun timeZoneByOffset(offsetInMilliseconds: Long): TimeZone
    fun timeStampToUtcDateMillis(timestamp: Long): Long
    fun mergeUtcDateToTimestamp(timestamp: Long, dateUtcMillis: Long): Long
    fun mergeHourMinuteToTimestamp(timestamp: Long, hour: Int, minute: Int, randomSecond: Boolean = false): Long
}
