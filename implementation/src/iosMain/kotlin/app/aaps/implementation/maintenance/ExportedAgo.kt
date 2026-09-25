package app.aaps.implementation.maintenance

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Reading the timestamp an export carries, without any platform formatting.
 *
 * Kept apart from [IosPrefsFileInfo] so the decisions can be tested. The formatting itself cannot
 * be: `NSRelativeDateTimeFormatter` gives different words in every locale, so asserting on its
 * output would only pin the machine the test ran on.
 */
internal object ExportedAgo {

    /**
     * How recent an export has to be to read as "3 days ago" instead of a date.
     *
     * 60 days, the same window Android uses, so both platforms switch over at the same point.
     */
    val relativeWindow: Duration = 60.days

    /**
     * The timestamp as written by `DateUtil.toISOString`, for example `2026-09-01T12:34:56.789Z`.
     *
     * Null when the text is not a timestamp at all. Android lets that throw; here it is a normal
     * answer, because the value comes out of a file the user could have edited or truncated and a
     * broken export should still be listed rather than take the screen down with it.
     */
    fun parse(utcTime: String): Instant? = try {
        Instant.parse(utcTime)
    } catch (_: IllegalArgumentException) {
        null
    }

    /**
     * Whether to say how long ago it was, rather than name the day.
     *
     * False for anything older than [relativeWindow] and also for a timestamp in the future, which
     * means a clock somewhere is wrong: "in 3 days" would read as a bug to the user, while a date
     * just looks odd.
     */
    fun isRelative(exported: Instant, now: Instant): Boolean {
        val elapsed = now - exported
        return elapsed >= Duration.ZERO && elapsed < relativeWindow
    }

    /**
     * The `YYYY-MM-DD` at the front of the timestamp, or the whole text when it is too short.
     *
     * Deliberately not localized. Every other date on the screen comes from the system formatter,
     * but this one is the fallback used when the timestamp could not be understood, so showing it
     * unchanged is what lets a user compare it with the file itself.
     */
    fun datePart(utcTime: String): String = if (utcTime.length >= 10) utcTime.substring(0, 10) else utcTime
}
