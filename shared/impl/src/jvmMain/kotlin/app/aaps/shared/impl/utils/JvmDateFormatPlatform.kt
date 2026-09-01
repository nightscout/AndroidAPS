package app.aaps.shared.impl.utils

import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Date and time formatting on desktop, using the JVM's own locale data.
 *
 * The counterpart of the Android and Apple implementations. All three answer from the platform's
 * locale rather than from a fixed pattern, so a date reads the way the user expects it to on the
 * machine they are running.
 */
class JvmDateFormatPlatform : DateFormatPlatform {

    /**
     * Read from the short time pattern for the default locale, looking at the hour field rather than
     * for an AM/PM marker.
     *
     * The marker is the obvious signal and it is wrong: Traditional Chinese asks for `Bh:mm`, where
     * `B` is the flexible day period, so a twelve hour pattern can carry no `a` at all. `h` and `K`
     * count to twelve, `H` and `k` to twenty four, and that is fixed by the Unicode standard rather
     * than by any locale.
     */
    override fun is24Hour(): Boolean {
        val pattern = (java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) as? SimpleDateFormat)?.toPattern().orEmpty()
        var inQuote = false
        var index = 0
        while (index < pattern.length) {
            val letter = pattern[index]
            if (letter == '\'') {
                // Two in a row are one literal apostrophe, not the start and end of an empty quote.
                if (index + 1 < pattern.length && pattern[index + 1] == '\'') index++ else inQuote = !inQuote
                index++
                continue
            }
            if (!inQuote) when (letter) {
                'h', 'K' -> return false
                'H', 'k' -> return true
            }
            index++
        }
        // No hour field named at all. The 24 hour clock is the safer default for a medical log, where
        // an ambiguous "7:30" is worse than an unfamiliar "19:30".
        return true
    }

    override fun format(millis: Long, pattern: String): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))

    override fun localizedShortDate(millis: Long): String =
        java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, Locale.getDefault()).format(Date(millis))

    /**
     * The zone's offset ignoring daylight saving, which is what "standard" means here - the same
     * number `TimeZone.getRawOffset` reports on Android.
     */
    override fun standardUtcOffsetMillis(): Long =
        TimeZone.getTimeZone(ZoneId.systemDefault()).rawOffset.toLong()
}
