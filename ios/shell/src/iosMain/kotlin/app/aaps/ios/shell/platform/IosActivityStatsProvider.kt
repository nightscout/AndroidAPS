package app.aaps.ios.shell.platform

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.ui.activityMonitor.ActivityStats
import app.aaps.ui.activityMonitor.ActivityStatsProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * No screen-usage statistics, because a client does not collect them.
 *
 * This is an answer, not a gap. The collecting half is an `Application.ActivityLifecycleCallbacks`
 * on Android, and nothing on an iOS client is meant to replace it: the statistics exist to show how
 * long a user spent on each screen of the app that runs their loop, and a follower is not that app.
 *
 * `ActivityStatsProvider` is only the reading half - it was split out so the statistics screen could
 * move to commonMain - so the screen still opens and shows an empty table. Empty is accurate here
 * rather than broken: nothing was measured, so there is nothing to report.
 *
 * Reset is accepted and does nothing, which is the same statement: there is no collection to clear.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosActivityStatsProvider @Inject constructor(
    private val aapsLogger: AAPSLogger
) : ActivityStatsProvider {

    override fun getActivityStats(): List<ActivityStats> {
        aapsLogger.notOnThisPlatform("screen usage statistics - a client does not collect them")
        return emptyList()
    }

    override fun reset() = aapsLogger.notOnThisPlatform("resetting screen usage statistics")
}
