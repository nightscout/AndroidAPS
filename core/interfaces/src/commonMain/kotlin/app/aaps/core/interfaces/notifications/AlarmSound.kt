package app.aaps.core.interfaces.notifications

import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.keys.interfaces.TextRef

/**
 * One of the alarm sounds AAPS ships.
 *
 * These used to travel as bare `R.raw.*` ints. An id is Android only, so it kept every declaring
 * file out of common code, and it carried two further problems that this type removes:
 *
 * - **Two different "no sound".** `runAlarm` took `soundId: Int = 0` while `AapsNotification` took a
 *   nullable id, and the player guarded with `if (soundRes == 0) return` in two places. Absence is
 *   now spelled `null`, once, and the compiler enforces it.
 * - **An id in an Intent.** The chosen sound is passed to the full screen alarm activity as an
 *   extra, and a resource id is a build specific number - the same value means something else in
 *   the next build. The stable [name] travels instead.
 *
 * Deliberately a closed set: the sound files live in `:core:ui/res/raw` and there are five of them.
 * Adding one means adding an entry here, which makes the `when` in the Android resolver stop
 * compiling until it is handled.
 *
 * [title] is the name the user sees: on the Android alarm channels and in the sound choice of an alert kind.
 */
enum class AlarmSound(val title: TextRef) {

    /** Standard alarm. */
    ALARM(InterfacesStrings.alarm_channel_standard),

    /** Urgent alarm - used for the Nightscout urgent announcement path. */
    URGENT_ALARM(InterfacesStrings.alarm_channel_urgent),

    /** General error. */
    ERROR(InterfacesStrings.alarm_channel_error),

    /** Bolus delivery failure. */
    BOLUS_ERROR(InterfacesStrings.alarm_channel_bolus_error),

    /** The "time to eat" timer. A soft kitchen timer, so it is not taken for a warning. */
    EAT_REMINDER(InterfacesStrings.alarm_channel_eat_reminder)
}
