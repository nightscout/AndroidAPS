package app.aaps.shared.impl.utils

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import android.text.format.DateFormat as AndroidDateFormat

/**
 * Date text on Android, unchanged from what `DateUtilImpl` used to do itself.
 *
 * The zone and locale are read on each call rather than held, because both can change while the
 * app runs: the user can switch language, and a phone crossing a time zone changes zone without
 * restarting. That was the behaviour before and it is kept.
 */
class AndroidDateFormatPlatform(
    private val context: Context
) : DateFormatPlatform {

    private val systemZone: ZoneId get() = ZoneId.systemDefault()
    private val displayLocale: Locale get() = Locale.getDefault()

    override fun is24Hour(): Boolean = AndroidDateFormat.is24HourFormat(context)

    override fun format(millis: Long, pattern: String): String =
        Instant.ofEpochMilli(millis)
            .atZone(systemZone)
            .format(DateTimeFormatter.ofPattern(pattern, displayLocale))

    override fun localizedShortDate(millis: Long): String =
        Instant.ofEpochMilli(millis)
            .atZone(systemZone)
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(displayLocale))

    override fun standardUtcOffsetMillis(): Long =
        systemZone.rules.getStandardOffset(Instant.now()).totalSeconds * 1000L
}
