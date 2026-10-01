package app.aaps.shared.impl.utils

/**
 * The part of date handling that only the platform can answer.
 *
 * `DateUtilImpl` is otherwise plain Kotlin, so it lives in commonMain and takes one of these
 * instead. Three things are genuinely per platform and nothing else is:
 *
 * - whether the user has chosen a 24 hour clock,
 * - turning an instant into text using the device's language and calendar,
 * - the short date layout that language uses, which is `dd/MM/yyyy` in London and `MM/dd/yyyy` in
 *   New York.
 *
 * This is injected rather than declared `expect`/`actual` for a plain reason: the Android answer
 * needs a `Context`, and an `expect object` has no constructor to give one to. Injection also
 * means a test can supply a fixed formatter and get the same string on every machine.
 *
 * ## Patterns
 *
 * [format] takes a Unicode LDML pattern, the same language `java.time` and `NSDateFormatter` both
 * speak, so `"EEEE"`, `"MMM"` and `"HH:mm"` mean the same thing to either implementation. That is
 * what lets call sites keep passing the pattern strings they always have.
 */
interface DateFormatPlatform {

    /** True when the device is set to a 24 hour clock rather than AM/PM. */
    fun is24Hour(): Boolean

    /**
     * Formats [millis] in the system time zone and the display language.
     *
     * @param pattern a Unicode LDML pattern, for example `"HH:mm"` or `"EEEE"`.
     */
    fun format(millis: Long, pattern: String): String

    /** The short date in the display language, the layout that language puts the day and month in. */
    fun localizedShortDate(millis: Long): String

    /**
     * The zone's offset from UTC ignoring daylight saving, in milliseconds.
     *
     * Not formatting, but here for the same reason: kotlinx-datetime answers what the offset is
     * *now*, which in summer includes the daylight hour. There is no multiplatform way to ask for
     * the standard offset, and reimplementing it by guessing at a winter date breaks in the
     * southern hemisphere. Each platform already knows the answer, so each platform is asked.
     */
    fun standardUtcOffsetMillis(): Long
}
