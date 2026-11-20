package app.aaps.implementation.overview

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.time.SystemTimeZone
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.liveGraphWindowEnd
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class OverviewDataImpl() : OverviewData {

    // Initialize the window anchor so workers reading these fields before the
    // first predictions-prep run see sensible values.
    override var toTime: Long = liveGraphWindowEnd(Clock.System.now(), SystemTimeZone.current())
    override var fromTime: Long = toTime - T.hours(Constants.GRAPH_TIME_RANGE_HOURS.toLong()).msecs()
    override var endTime: Long = toTime
}
