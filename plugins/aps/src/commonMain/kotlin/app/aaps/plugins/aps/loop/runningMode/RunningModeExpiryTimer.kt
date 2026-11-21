package app.aaps.plugins.aps.loop.runningMode

/**
 * Runs [RunningModeExpiryJob] at the end of a temporary running mode, also when the app is not running then.
 *
 * When to run it is the same everywhere and lives in [RunningModeExpiryScheduler]. Only "run the job at that time"
 * is different per platform. Android uses WorkManager, which keeps the work when the app is killed.
 *
 * The job is a safety net: the mode itself ends on its own time, and the job cancels the zero temp basal that was
 * set for it, so that the basal does not run on after the mode. An implementation that cannot keep the time should
 * run the job late, at the next chance, rather than never.
 */
interface RunningModeExpiryTimer {

    /** Run the job in [delayMs]. Replaces a pending run: only one temporary mode is active at a time. */
    fun schedule(delayMs: Long)

    /** Drops a pending run. A no-op when nothing is pending. */
    fun cancel()
}
