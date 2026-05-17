package app.aaps.core.interfaces.ui

import androidx.annotation.RawRes

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
     * @param soundId sound resource. if == 0 alarm is not started
     */
    fun runAlarm(status: String, title: String, @RawRes soundId: Int = 0)

    /**
     * Starts a repeating alarm sound.
     * @param sound The raw resource ID of the sound to play.
     * @param reason A string describing why the alarm is being started.
     */
    fun startAlarm(@RawRes sound: Int, reason: String)

    /**
     * Stops any currently playing alarm.
     * @param reason A string describing why the alarm is being stopped.
     */
    fun stopAlarm(reason: String)
}
