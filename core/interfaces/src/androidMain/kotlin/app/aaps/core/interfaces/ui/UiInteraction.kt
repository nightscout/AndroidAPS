package app.aaps.core.interfaces.ui

import app.aaps.core.interfaces.notifications.AlarmSound

/**
 * Interface to use activities located in different modules
 * usage: startActivity(Intent(context, activityNames.xxxx))
 */
interface UiInteraction {

    /** The main activity of the application. */
    val mainActivity: Class<*>

    /** The activity for displaying error information. */
    val errorHelperActivity: Class<*>

    /**
     * Show ErrorHelperActivity and start alarm.
     * @param status message inside dialog
     * @param title title of dialog
     * @param sound alarm sound, or null for a silent alarm
     */
    fun runAlarm(status: String, title: String, sound: AlarmSound? = null)

    /**
     * Stops any currently playing alarm (cancels FSI + all sound notifications).
     * Per-AAPS-notification cancellation happens internally inside the implementation module.
     * @param reason A string describing why the alarm is being stopped.
     */
    fun stopAlarm(reason: String)
}
