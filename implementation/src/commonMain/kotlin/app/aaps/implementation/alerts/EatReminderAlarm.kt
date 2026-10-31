package app.aaps.implementation.alerts

/**
 * The platform part of the "time to eat" timer: makes the phone ring at a time, and takes that back.
 *
 * There is one alarm. [schedule] replaces the one set before, so it is safe to call again with the same time,
 * which is what `EatReminderManager.restore` does at app start.
 *
 * Like `ReminderScheduler`, an implementation must really ring at that time, also with the app in the
 * background. A timer on screen that then stays silent is worse than no timer.
 */
interface EatReminderAlarm {

    /**
     * Rings at [endsAt]. [startedAt] is for a platform that shows the countdown.
     *
     * @return false when the alarm cannot be set; the implementation has told the user why
     */
    fun schedule(startedAt: Long, endsAt: Long): Boolean

    /** Takes the alarm back, and the countdown with it. Harmless when none is set. */
    fun cancel()
}
