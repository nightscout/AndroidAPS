package app.aaps.database.entities

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt

/**
 * Offset of the device time zone at [timestamp], in milliseconds.
 *
 * This is the default of the `utcOffset` column of every entity, so it was written out twenty times
 * before. It uses kotlinx-datetime rather than `java.util.TimeZone` so the entities can compile for
 * every target. Both give the offset in effect at that instant, daylight saving included.
 */
fun defaultUtcOffset(timestamp: Long): Long =
    TimeZone.currentSystemDefault().offsetAt(Instant.fromEpochMilliseconds(timestamp)).totalSeconds * 1000L
