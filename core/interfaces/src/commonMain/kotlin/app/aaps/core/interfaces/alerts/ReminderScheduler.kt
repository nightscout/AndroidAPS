package app.aaps.core.interfaces.alerts

/**
 * Schedules a one-shot reminder that rings at a wall-clock time in the future.
 *
 * Exists so automation's alarm action does not have to be Android code. The Android implementation
 * uses `AlarmManager.setAlarmClock` plus a broadcast receiver; an iOS one would use a local
 * notification.
 *
 * Note for whoever writes a second implementation: `setAlarmClock` is exact, fires while the device
 * is idle, and survives the app being backgrounded. That reliability is the whole point of the alarm
 * action, and it is not automatically true elsewhere - a best-effort scheduler would quietly weaken
 * a feature users rely on.
 */
interface ReminderScheduler {

    /**
     * Rings [text] after [seconds].
     *
     * Implementations report their own failure to the user; callers get no result because there is
     * nothing useful they could do with one.
     */
    fun scheduleReminder(seconds: Int, text: String)
}
