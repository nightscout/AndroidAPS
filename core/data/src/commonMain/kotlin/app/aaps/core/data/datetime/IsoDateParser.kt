package app.aaps.core.data.datetime

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant

/**
 * Lenient ISO 8601 parsing, matching what joda's `ISODateTimeFormat.dateTimeParser()` accepted.
 *
 * joda is a JVM library so it cannot go to iOS, and kotlinx-datetime is multiplatform but strict:
 * `Instant.parse` rejects `+0200` without a colon, a value with no offset at all, and a bare date -
 * all of which arrive today from Nightscout device status written by other uploaders.
 *
 * The trick is to take any explicit offset off the end **first**, then parse what is left as a plain
 * local value. One small parser then covers every shape rather than needing a format per variant:
 *
 * - `2026-08-06T04:56:19.555Z`, `...+02:00`, `...+0200`, `...-04:00` -> that exact instant
 * - `2026-08-06T04:56:19.555`, `2026-08-06T04:56` -> **local** time, as joda read it
 * - `2026-08-06` -> **local** midnight
 * - lower case `t` / `z` -> accepted
 * - anything else -> null
 *
 * Returning null rather than a sentinel is deliberate: the two callers want different things from a
 * failure. `:core:nssdk` maps it to `0L` so one bad treatment does not abort a whole sync, while
 * `RT` throws, because joda threw there and a silent 1970 timestamp inside an APS result would be
 * worse than a loud failure.
 *
 * `IsoDateParserParityTest` pins every shape above against real joda output.
 *
 * **Knowingly duplicated** in `:core:nssdk` (`RemoteTreatment.parseCreatedAt`). That module has no
 * project dependencies at all, and adding one to share ~30 lines would couple a standalone SDK to
 * the app's data module. If a third caller appears, revisit that trade.
 */
fun parseIsoToEpochMillisOrNull(isoDateString: String): Long? {
    val text = isoDateString.trim().uppercase()
    if (text.isEmpty()) return null

    // Peel off a trailing zone designator, so the rest is a plain local date-time.
    var local = text
    var offset: UtcOffset? = null
    if (text.endsWith("Z")) {
        local = text.dropLast(1)
        offset = UtcOffset.ZERO
    } else {
        offsetAtEnd.find(text)?.let { match ->
            val parsed = runCatching { UtcOffset.parse(withOffsetColon(match.value)) }.getOrNull()
            if (parsed != null) {
                local = text.substring(0, match.range.first)
                offset = parsed
            }
        }
    }

    val zone = TimeZone.currentSystemDefault()

    runCatching { LocalDateTime.parse(local) }.getOrNull()?.let { dateTime ->
        val instant = offset?.let { dateTime.toInstant(it) } ?: dateTime.toInstant(zone)
        return instant.toEpochMilliseconds()
    }
    // Date only. joda gave local midnight, and a date without a time never carries an offset.
    runCatching { LocalDate.parse(local) }.getOrNull()?.let { date ->
        return date.atStartOfDayIn(zone).toEpochMilliseconds()
    }
    return null
}

/** A trailing `+HH:MM` / `+HHMM` offset. Anchored so it cannot match the date's own dashes. */
private val offsetAtEnd = Regex("""[+-]\d{2}:?\d{2}$""")

/** `+0200` -> `+02:00`; already-correct input is returned unchanged. */
private fun withOffsetColon(offset: String): String =
    if (offset.contains(':')) offset else offset.substring(0, 3) + ":" + offset.substring(3)
