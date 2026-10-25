package app.aaps.ui.compose.overview.graphs

import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.time.Instant

private const val HOUR_MS = 3_600_000L

internal data class GraphClockChange(
    val timestamp: Long,
    val before: UtcOffset,
    val after: UtcOffset
) {
    val minutes: Int get() = (after.totalSeconds - before.totalSeconds) / 60
}

/** Finds offset changes in the axis time zone without changing elapsed-time coordinates. */
internal fun graphClockChanges(start: Long, end: Long, timeZone: TimeZone): List<GraphClockChange> {
    if (end <= start) return emptyList()
    val changes = mutableListOf<GraphClockChange>()
    var cursor = start
    var offset = timeZone.offsetAt(Instant.fromEpochMilliseconds(start - 1))
    while (cursor <= end) {
        val next = minOf(cursor + HOUR_MS, end)
        val nextOffset = timeZone.offsetAt(Instant.fromEpochMilliseconds(next))
        if (offset != nextOffset) {
            var low = cursor
            var high = next
            while (low < high) {
                val middle = low + (high - low) / 2
                if (timeZone.offsetAt(Instant.fromEpochMilliseconds(middle)) == offset) low = middle + 1
                else high = middle
            }
            changes += GraphClockChange(low, offset, nextOffset)
        }
        if (next == end) break
        cursor = next
        offset = nextOffset
    }
    return changes
}

internal fun clockUtcOffset(offset: UtcOffset): String = buildString {
    append("UTC")
    if (offset.totalSeconds != 0) {
        val seconds = abs(offset.totalSeconds)
        append(if (offset.totalSeconds > 0) '+' else '-')
        append(seconds / 3600)
        if (seconds % 3600 != 0) {
            append(':')
            append((seconds / 60 % 60).toString().padStart(2, '0'))
        }
        if (seconds % 60 != 0) {
            append(':')
            append((seconds % 60).toString().padStart(2, '0'))
        }
    }
}

internal fun graphTimeLabel(timestamp: Long, timeZone: TimeZone): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val local = instant.toLocalDateTime(timeZone)
    return buildString {
        append(local.hour.toString().padStart(2, '0'))
        if (local.minute != 0) {
            append(':')
            append(local.minute.toString().padStart(2, '0'))
        }
    }
}
