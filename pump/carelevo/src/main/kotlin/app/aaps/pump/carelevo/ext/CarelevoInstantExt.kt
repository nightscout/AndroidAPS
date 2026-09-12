package app.aaps.pump.carelevo.ext

import app.aaps.core.data.datetime.parseIsoToEpochMillisOrNull
import kotlin.time.Instant

/**
 * Reads an ISO 8601 timestamp that this module wrote, replacing joda's `DateTime.parse`.
 *
 * The stored records were written by joda for a long time, so they carry a local offset
 * (`2026-09-08T13:45:30.123+02:00`), while [Instant.toString] now writes `...Z`.
 * [parseIsoToEpochMillisOrNull] reads both shapes - it exists to match what joda accepted - so old
 * records keep loading and nothing has to rewrite them.
 *
 * This throws `IllegalArgumentException` where the parse fails, the same type joda's `DateTime.parse`
 * threw, so callers that catch it keep working. These strings come from the module's own persistence,
 * so one that does not parse means the record is broken, and a silent 1970 timestamp on a pump record
 * would be worse than a loud failure.
 */
internal fun parseIsoInstant(text: String): Instant =
    parseIsoToEpochMillisOrNull(text)
        ?.let { Instant.fromEpochMilliseconds(it) }
        ?: throw IllegalArgumentException("not an ISO 8601 date-time: $text")
