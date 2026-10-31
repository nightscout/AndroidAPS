package app.aaps.core.interfaces.notifications

import android.app.NotificationManager

/**
 * The "Override Do Not Disturb mode" setting (`AlertOverrideDoNotDisturb`), as the one rule every alarm
 * sound of AAPS follows on Android: on the phone and on the watch.
 *
 * With the override off, an alarm does not make a sound while Do Not Disturb is on, it only vibrates.
 * Android does not do this by itself. An alarm notification is `CATEGORY_ALARM`, and Do Not Disturb lets
 * an alarm notification ring when the profile allows alarms (the Android default), also on a channel that
 * uses the notification stream. So every place that can make an alarm sound asks this rule first: the
 * sound player does not start, and a notification goes to a channel that only vibrates. It stays
 * `CATEGORY_ALARM`, so the vibration still comes through Do Not Disturb when the profile allows alarms.
 *
 * With the override on, the alarm stream is used, and the Do Not Disturb profile decides whether alarms
 * are heard. From Android 15 an app cannot lift Do Not Disturb that the user turned on, also not with Do
 * Not Disturb access: `setInterruptionFilter` only drives the app's own implicit rule. So that is as far
 * as "on" reaches.
 *
 * Pure, so it is tested without a device.
 */
object DoNotDisturbRule {

    /**
     * True when the alarm must not make a sound: Do Not Disturb is on and the override is off.
     * [interruptionFilter] is `NotificationManager.currentInterruptionFilter`. A value it does not know
     * counts as Do Not Disturb off: when in doubt, a medical alarm sounds.
     */
    fun staysQuiet(overrideDnd: Boolean, interruptionFilter: Int): Boolean = !overrideDnd && isDndOn(interruptionFilter)

    private fun isDndOn(interruptionFilter: Int): Boolean =
        interruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
            interruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS ||
            interruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE
}
