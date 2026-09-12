package app.aaps.ui.activityMonitor

/**
 * Reads back the screen usage figures shown on the statistics screen.
 *
 * Split from the collector, which has to be an `Application.ActivityLifecycleCallbacks` on Android
 * and so cannot be shared. Only the reading side is needed by a screen, and none of it is platform
 * specific.
 */
interface ActivityStatsProvider {

    /** One entry per screen the user has opened, in no particular order. */
    fun getActivityStats(): List<ActivityStats>

    /** Forgets everything collected so far. */
    fun reset()
}
