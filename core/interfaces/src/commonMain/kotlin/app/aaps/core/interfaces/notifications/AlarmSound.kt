package app.aaps.core.interfaces.notifications

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
 * Deliberately a closed set: the sound files live in `:core:ui/res/raw` and there are four of them.
 * Adding one means adding an entry here, which makes the `when` in the Android resolver stop
 * compiling until it is handled.
 */
enum class AlarmSound {

    /** Standard alarm. */
    ALARM,

    /** Urgent alarm - used for the Nightscout urgent announcement path. */
    URGENT_ALARM,

    /** General error. */
    ERROR,

    /** Bolus delivery failure. */
    BOLUS_ERROR
}
