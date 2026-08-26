package app.aaps.implementation.overview

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.overview.OverviewData
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import javax.inject.Inject
import kotlin.time.Instant

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class OverviewDataImpl @Inject constructor() : OverviewData {

    // Initialize the window anchor so workers reading these fields before the
    // first predictions-prep run see sensible values. Rounded to next
    // full hour + GraphView-era 100ms nudge to avoid axis-label rounding.
    override var toTime: Long = initialToTime()
    override var fromTime: Long = toTime - T.hours(Constants.GRAPH_TIME_RANGE_HOURS.toLong()).msecs()
    override var endTime: Long = toTime

    private fun initialToTime(): Long {
        val tz = TimeZone.currentSystemDefault()
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val local = now.toLocalDateTime(tz)
        val truncatedHour = LocalDateTime(local.year, local.month, local.day, local.hour, 0)
        val nextFullHour = truncatedHour.toInstant(tz).plus(1, DateTimeUnit.HOUR, tz)
        return nextFullHour.toEpochMilliseconds() + 100000
    }
}
