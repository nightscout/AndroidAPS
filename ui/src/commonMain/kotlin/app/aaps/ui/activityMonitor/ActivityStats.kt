package app.aaps.ui.activityMonitor

/**
 * How long the user has spent on one screen.
 *
 * @property activityName The simplified name of the screen (without "Activity" suffix)
 * @property duration Human-readable duration string representing total time spent there,
 *                    formatted using [app.aaps.core.interfaces.utils.DateUtil.niceTimeScalar]
 * @property days Number of days since the screen was first tracked, calculated from
 *                the difference between now and the start timestamp
 *
 * @see ActivityStatsProvider.getActivityStats
 */
data class ActivityStats(
    val activityName: String,
    val duration: String,
    val days: Double
)
