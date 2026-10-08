package app.aaps.shared.impl.utils

import app.aaps.core.interfaces.utils.usesTwelveHourClock
import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970

/**
 * Date text on iOS, from `NSDateFormatter`.
 *
 * The locale and time zone are read on each call rather than held, because both can change while
 * the app is running: the user can switch language in Settings, and a phone crossing a time zone
 * changes zone without restarting.
 */
class IosDateFormatPlatform : DateFormatPlatform {

    /**
     * Reads the device clock setting the way Apple documents.
     *
     * There is no flag to ask. The supported way is to build the locale's own preferred hour format
     * and read the hour field out of it.
     *
     * This looked for an AM/PM letter instead, which is wrong for the reason [usesTwelveHourClock]
     * sets out - and worse, the Compose theme on this same platform already parsed the hour field,
     * so iOS gave two different answers to one question. On a `zh-Hant` phone with "24-Hour Time"
     * switched off, the template is `Bh` - twelve hour, no `a` in it - so `DateUtil` printed `HH:mm`
     * and flipped the short date, while the picker next to it offered a twelve hour dial.
     */
    override fun is24Hour(): Boolean {
        val template = NSDateFormatter.dateFormatFromTemplate("j", 0uL, NSLocale.currentLocale)
        return usesTwelveHourClock(template.orEmpty()) != true
    }

    override fun format(millis: Long, pattern: String): String =
        formatter().apply { dateFormat = pattern }.stringFromDate(millis.toNsDate())

    override fun localizedShortDate(millis: Long): String =
        formatter().apply {
            dateStyle = NSDateFormatterShortStyle
            timeStyle = NSDateFormatterNoStyle
        }.stringFromDate(millis.toNsDate())

    override fun standardUtcOffsetMillis(): Long {
        val now = NSDate()
        val zone = NSCalendar.currentCalendar.timeZone
        // secondsFromGMT already includes the daylight hour when one is in effect, so taking it
        // back off is what leaves the standard offset.
        val daylight = zone.daylightSavingTimeOffsetForDate(now)
        return ((zone.secondsFromGMTForDate(now).toDouble() - daylight) * 1000.0).toLong()
    }

    private fun formatter(): NSDateFormatter = NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        timeZone = NSCalendar.currentCalendar.timeZone
    }

    private fun Long.toNsDate(): NSDate = NSDate.dateWithTimeIntervalSince1970(this / 1000.0)
}
