package app.aaps.pump.carelevo.ext

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.format.char
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration

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

/** This time moved by [duration] on a clock with no time zone, as `java.time.LocalDateTime.plusHours` moved it. */
internal fun LocalDateTime.plusOnTheClock(duration: Duration): LocalDateTime =
    toInstant(TimeZone.UTC).plus(duration).toLocalDateTime(TimeZone.UTC)

/** Whole minutes from [from] to [to] on a clock with no time zone, as `ChronoUnit.MINUTES.between` counted them. */
internal fun clockMinutesBetween(from: LocalDateTime, to: LocalDateTime): Long =
    (to.toInstant(TimeZone.UTC) - from.toInstant(TimeZone.UTC)).inWholeMinutes
