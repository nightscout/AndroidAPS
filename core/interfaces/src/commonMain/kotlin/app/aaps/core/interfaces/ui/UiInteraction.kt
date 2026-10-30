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
    fun runEatReminderAlarm(status: String, title: String) = runAlarm(status, title, AlarmSound.EAT_REMINDER)

    /**
     * Stops any currently playing alarm (cancels FSI + all sound notifications).
     * Per-AAPS-notification cancellation happens internally inside the implementation module.
     * @param reason A string describing why the alarm is being stopped.
     */
    fun stopAlarm(reason: String)

    /**
     * "Silence and close" from somewhere else than the alarm screen (the watch): [stopAlarm], and the alarm screen
     * closes as its own "Silence and close" would. Where there is no such screen, it is a plain [stopAlarm].
     */
    fun dismissAlarm(reason: String) = stopAlarm(reason)

    /**
     * "Mute for [minutes]": [stopAlarm] now, and the same alarm rings again after [minutes], on the phone and on
     * the watch, unless it was stopped or replaced in between. The alarm screen stays, as with its own
     * "Mute for 5 minutes". Where there is no re-ring, it is a plain [stopAlarm].
     */
    fun muteAlarm(minutes: Int, reason: String) = stopAlarm(reason)

    /**
     * Silences the alarm of the "time to eat" timer ([runEatReminderAlarm]) when it is the alarm shown now, and
     * nothing else: other alarms keep sounding. A newer alarm takes the place of the shown one, so once a pump
     * alarm came in, this leaves that alarm alone (#5408).
     *
     * Only the master's timer calls this. Where the eat alarm is a plain alarm, stopping it is all there is.
     */
    fun stopEatReminderAlarm() = stopAlarm("Eat reminder")

    companion object {

        /** "Mute for 5 minutes" on the alarm screen and on the watch's notification of the alarm. */
        const val MUTE_MINUTES = 5
    }
}
