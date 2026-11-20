package app.aaps.core.interfaces.overview

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

interface OverviewData {

    var toTime: Long  // current time rounded up to 1 hour
    var fromTime: Long // toTime - range
    var endTime: Long // toTime + predictions
}

/**
 * The [OverviewData.toTime] of the live graph at [now]: the next full hour in [zone], plus 100 s.
 *
 * Local, not UTC: zones like +05:30 do not line up with UTC hours. The 100 s are from GraphView, which
 * needed them for the axis labels; the graph code still reads the window in this shape.
 */
fun liveGraphWindowEnd(now: Instant, zone: TimeZone): Long {
    val local = now.toLocalDateTime(zone)
    val nextHour = LocalDateTime(local.year, local.month, local.day, local.hour, 0).toInstant(zone) + 1.hours
    return nextHour.toEpochMilliseconds() + LIVE_GRAPH_WINDOW_END_NUDGE_MS
}

private const val LIVE_GRAPH_WINDOW_END_NUDGE_MS = 100_000L
