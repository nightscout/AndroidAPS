package app.aaps.core.interfaces.alerts

import app.aaps.core.interfaces.notifications.AlarmSound

/**
 * The sound the user chose for an alert kind on this phone (`_docs/ALARMS.md`, step 2e). Not chosen: the alert plays
 * its own sound.
 */
sealed interface AlertSound {

    /** One of the sounds AAPS ships: every platform. */
    data class Bundled(val sound: AlarmSound) : AlertSound

    /**
     * Android: a ringtone of the phone, by its content [uri], with the [title] it had when the user picked it. iOS can
     * play only the sounds an app ships, so it never has one. When the ringtone cannot be played (deleted, no access),
     * the alert's own sound plays: an alarm must never be silent.
     */
    data class Ringtone(val uri: String, val title: String) : AlertSound
}
