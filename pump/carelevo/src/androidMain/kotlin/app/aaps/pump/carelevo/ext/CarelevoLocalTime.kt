package app.aaps.pump.carelevo.ext

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** The patch boot time as the driver stores it: `yyMMddHHmm` in local time, years 2000 to 2099. */
internal val BOOT_DATE_TIME_FORMAT: DateTimeFormat<LocalDateTime> = LocalDateTime.Format {
    yearTwoDigits(baseYear = 2000)
    monthNumber()
    day()
    hour()
    minute()
}

/** A local time as the overview shows it: `yyyy-MM-dd HH:mm`. */
internal val OVERVIEW_DATE_TIME_FORMAT: DateTimeFormat<LocalDateTime> = LocalDateTime.Format {
    year()
    char('-')
    monthNumber()
    char('-')
    day()
    char(' ')
    hour()
    char(':')
    minute()
}

/** The local time now as ISO 8601 text with no offset (`2026-11-17T09:30:05.123`). Alarms are sorted by this text. */
internal fun localNowIsoText(): String = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()
