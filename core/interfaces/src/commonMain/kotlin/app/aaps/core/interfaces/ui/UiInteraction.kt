package app.aaps.core.interfaces.ui

import app.aaps.core.interfaces.notifications.AlarmSound
import kotlin.reflect.KClass

/**
 * Interface to use activities located in different modules
 * usage: startActivity(Intent(context, activityNames.xxxx.java))
 */
interface UiInteraction {

    /** The main activity of the application. */
    val mainActivity: KClass<*>

    /** The activity for displaying error information. */
    val errorHelperActivity: KClass<*>

    /**
     * Show ErrorHelperActivity and start alarm.
     * @param status message inside dialog
     * @param title title of dialog
     * @param sound alarm sound, or null for a silent alarm
     */
    fun runAlarm(status: String, title: String, sound: AlarmSound? = null)

    /**
     * The alarm of the "time to eat" timer ([app.aaps.core.interfaces.alerts.EatReminder]). The same alarm as
     * [runAlarm], but its screen and notification offer +5 min. Where a platform has no such button, it is
     * a plain alarm.
     */
    fun runEatReminderAlarm(status: String, title: String) = runAlarm(status, title, AlarmSound.ALARM)

    /**
     * Stops any currently playing alarm (cancels FSI + all sound notifications).
     * Per-AAPS-notification cancellation happens internally inside the implementation module.
     * @param reason A string describing why the alarm is being stopped.
     */
    fun stopAlarm(reason: String)
}
