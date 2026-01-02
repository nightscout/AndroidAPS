package app.aaps.wear.watchfaces.utils

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Represents a point in time for watch face applications.
 *
 * Replaces the deprecated android.text.format.Time class with modern java.time API
 * while maintaining backward compatibility with existing watchface code.
 *
 * Stores time components (year, month, day, hour, minute, second, timezone) plus
 * millisecond precision and 12-hour format hour tracking.
 */
class WatchFaceTime {

    /** Year (e.g., 2025) */
    var year: Int = 0

    /** Month (0-11, 0-based like deprecated Time class for compatibility) */
    var month: Int = 0

    /** Day of month (1-31) */
    var monthDay: Int = 0

    /** Hour (0-23) */
    var hour: Int = 0

    /** Minute (0-59) */
    var minute: Int = 0

    /** Second (0-59) */
    var second: Int = 0

    /** Timezone ID (e.g., "America/New_York") */
    var timezone: String = ZoneId.systemDefault().id

    /** Milliseconds timestamp */
    var millis: Long = 0

    /** Hour in 12-hour format (0-11) */
    var hour12: Int = 0

    init {
        reset()
    }

    fun reset() {
        year = 0
        month = 0
        monthDay = 0
        hour = 0
        minute = 0
        second = 0
        timezone = ZoneId.systemDefault().id
        millis = 0
        hour12 = 0
    }

    /**
     * Set this time to the current time.
     * Updates all fields from system clock using current timezone.
     */
    fun setToNow() {
        val now = System.currentTimeMillis()
        set(now)
    }

    /**
     * Set time from milliseconds timestamp.
     *
     * Converts epoch milliseconds to date/time components in the current timezone.
     * Also calculates 12-hour format hour.
     *
     * @param millis Epoch milliseconds since 1970-01-01T00:00:00Z
     */
    fun set(millis: Long) {
        this.millis = millis

        // Convert milliseconds to ZonedDateTime in the stored timezone
        val zoneId = try {
            ZoneId.of(timezone)
        } catch (_: Exception) {
            ZoneId.systemDefault()
        }

        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), zoneId)

        year = zdt.year
        month = zdt.monthValue - 1 // Convert to 0-based month for compatibility
        monthDay = zdt.dayOfMonth
        hour = zdt.hour
        minute = zdt.minute
        second = zdt.second
        timezone = zoneId.id

        // Calculate 12-hour format hour
        hour12 = if (hour == 0 || hour == 12) 0 else hour % 12
    }

    /**
     * Copy time from another WatchFaceTime instance.
     *
     * @param other Source time to copy from, or null for no-op
     */
    fun set(other: WatchFaceTime?) {
        if (other != null) {
            year = other.year
            month = other.month
            monthDay = other.monthDay
            hour = other.hour
            minute = other.minute
            second = other.second
            timezone = other.timezone
            millis = other.millis
            hour12 = other.hour12
        }
    }

    /**
     * Check if the hour has changed compared to another time.
     *
     * @param other Previous time to compare against, or null
     * @return true if other is null or hour differs
     */
    fun hasHourChanged(other: WatchFaceTime?): Boolean {
        return other == null || hour != other.hour
    }

    /**
     * Check if the minute has changed compared to another time.
     *
     * @param other Previous time to compare against, or null
     * @return true if other is null or minute differs
     */
    fun hasMinuteChanged(other: WatchFaceTime?): Boolean {
        return other == null || minute != other.minute
    }

    /**
     * Check if the second has changed compared to another time.
     *
     * @param other Previous time to compare against, or null
     * @return true if other is null or second differs
     */
    fun hasSecondChanged(other: WatchFaceTime?): Boolean {
        return other == null || second != other.second
    }
}
