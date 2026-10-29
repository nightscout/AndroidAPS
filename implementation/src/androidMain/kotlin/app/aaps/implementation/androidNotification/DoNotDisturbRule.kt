package app.aaps.implementation.androidNotification

/**
 * The "Override Do Not Disturb mode" setting, as the rule the alarm sound player follows.
 *
 * A normal app cannot get past Do Not Disturb by choosing a stream: whether the alarm stream is heard
 * is decided by the user's Do Not Disturb profile, and the notification stream is let through by
 * most profiles (any exception for calls or messages keeps it open). So the "off" half of the setting
 * is kept here: the player checks Do Not Disturb itself and stays quiet. The "on" half is the alarm
 * stream, heard only when the profile allows alarms. From Android 15 an app cannot lift Do Not Disturb
 * that the user turned on, also not with Do Not Disturb access: `setInterruptionFilter` only drives the
 * app's own implicit rule. So there is nothing more the player can do for "on".
 *
 * Pure, so it is tested without a MediaPlayer.
 */
internal object DoNotDisturbRule {

    /** True when the alarm sound must not start: Do Not Disturb is on and the override is off. */
    fun staysQuiet(overrideDnd: Boolean, dndOn: Boolean): Boolean = dndOn && !overrideDnd
}
