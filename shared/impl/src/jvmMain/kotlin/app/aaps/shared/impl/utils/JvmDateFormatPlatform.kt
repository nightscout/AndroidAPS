package app.aaps.shared.impl.utils

import app.aaps.core.interfaces.utils.usesTwelveHourClock
import java.text.DateFormat
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
     * Read from the short time pattern for the default locale, through the shared
     * [usesTwelveHourClock] - which looks at the hour field rather than for an AM/PM marker, and
     * explains why that matters.
     *
     * This used to carry its own copy of that parse. Correct, but a second copy: the Compose theme
     * had a third, and `IosDateFormatPlatform` had a fourth that was wrong. A pattern naming no hour
     * at all still counts as 24 hour here, which is only a fallback - no short time style produces
     * one.
     */
    override fun is24Hour(): Boolean {
        val pattern = (DateFormat.getTimeInstance(DateFormat.SHORT) as? SimpleDateFormat)?.toPattern()
        return usesTwelveHourClock(pattern.orEmpty()) != true
    }

    override fun format(millis: Long, pattern: String): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))

    override fun localizedShortDate(millis: Long): String =
        DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(millis))

    /**
     * The zone's offset ignoring daylight saving, which is what "standard" means here - the same
     * number `TimeZone.getRawOffset` reports on Android.
     */
    override fun standardUtcOffsetMillis(): Long =
        TimeZone.getTimeZone(ZoneId.systemDefault()).rawOffset.toLong()
}
