package app.aaps.core.interfaces.ui

import app.aaps.core.interfaces.notifications.AlarmSound

/**
 * The app's own alarms: the ones AAPS raises about itself rather than the ones a pump reports.
 *
 * A bolus that failed, the "time to eat" timer, an automation reminder. They are
 * [app.aaps.core.interfaces.pump.alerts.PumpAlert]s with
 * [app.aaps.core.interfaces.pump.alerts.PumpAlertLifetime.EVENT], raised under the source `aaps`, so they are one
 * card in the notification list with one sound owner, exactly like a pump's alert. See `_docs/PUMP_DESIGN.md`,
 * section 8.
 *
 * This interface used to also carry two Android activity classes. They are
 * [app.aaps.core.interfaces.ui.AndroidActivities] now: every reader of them was Android, and the only other
 * implementation pointed both at a class that is not an activity, which is what made it plain they did not belong in
 * shared code.
 */
interface UiInteraction {

    /**
     * Raise an alarm: a card that rings, and an alarm screen over whatever the user is looking at.
     *
     * @param key what makes two alarms the same alarm. Raising the same key again updates the alarm that is already
     *   there instead of adding a second one; a different key is a second card, silenced on its own. Before this
     *   existed there was one alarm at a time and a new one replaced whatever was shown, so a bolus error and a
     *   pump-unreachable alarm overwrote each other.
     * @param status the message, shown on the card and on the alarm screen
     * @param title the title, shown on the system notification and on the watch
     * @param sound the alarm sound. Null is the default alarm sound, not silence - which is what it has always
     *   meant here, because the Android path resolved it to `ERROR` in both the channel and the player.
     */
    fun runAlarm(key: String, status: String, title: String, sound: AlarmSound? = null)

    /**
     * The alarm of the "time to eat" timer ([app.aaps.core.interfaces.alerts.EatReminder]). The same alarm as
     * [runAlarm], and its screen and notification offer +5 min, which moves the timer rather than delaying the alarm.
     */
    fun runEatReminderAlarm(status: String, title: String)

    /**
     * The user has dealt with the alarm that is ringing: its sound stops.
     *
     * No key, because the surfaces that call this - the watch, the Mute action on the notification, a swipe - give
     * the user no way to pick one; "stop this noise" is all they can say. What the alarm does next follows from what
     * it is: one of the app's own is over and does not come back, while a pump alert goes quiet for its silence
     * window and returns while its cause lasts. The alarm screen, where the user *can* see which alert they are
     * answering, silences that one alone.
     */
    fun stopAlarm(reason: String)

    /**
     * "Silence and close" from somewhere else than the alarm screen (the watch): [stopAlarm], and the alarm screen
     * closes as its own "Silence and close" would.
     */
    fun dismissAlarm(reason: String) = stopAlarm(reason)

    /**
     * "Mute for [minutes]": the alarm that is ringing goes quiet now and rings again after [minutes], on the phone
     * and on the watch, unless it was answered in between. The alarm screen stays, as with its own
     * "Mute for 5 minutes".
     *
     * The wait is stored, so it survives a restart of AAPS. It used to be an in-process timer, which a restart lost -
     * and which could not work at all on iOS, where a suspended app cannot ring itself.
     */
    fun muteAlarm(minutes: Int, reason: String) = stopAlarm(reason)

    /** Stops the alarm of the "time to eat" timer and nothing else. Only the master's timer calls this. */
    fun stopEatReminderAlarm()

    companion object {

        /** "Mute for 5 minutes" on the alarm screen and on the watch's notification of the alarm. */
        const val MUTE_MINUTES = 5

        /** The source every alarm of the app's own is raised under, as a pump driver uses its own name. */
        const val SOURCE = "aaps"

        /** A command the pump did not carry out - a bolus, an insulin change. */
        const val KEY_COMMAND_FAILED = "command-failed"

        /** The "time to eat" timer. */
        const val KEY_EAT_REMINDER = "eat-reminder"
    }
}
