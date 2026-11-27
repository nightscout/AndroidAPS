package app.aaps.wear.watchdirect

/**
 * When the watch asks its sensor link to start again by itself.
 *
 * The driver can end a session on its own, for example after too many failed connections. On the
 * phone the plugin then asks again after a few minutes. The watch app had nothing like it: the link
 * stayed down until the watch app restarted or the phone sent the setting again, and a watch that is
 * away from the phone gets no such message.
 *
 * Only a link that has its key is asked again ([WatchSensorLink.resume]). A new pairing is never
 * started from here: each one can end in a refusal by the sensor, and four refusals in a row lock a
 * G7 / ONE+ for everyone, the phone and the loop too. The driver counts refusals and stops in time,
 * and a resume respects that count. A pairing started again and again would not.
 *
 * Written by olorinmaia (#251).
 */
object WatchDirectRestart {

    /**
     * How long the link must be down before it is asked again: two sensor cycles and one minute.
     * A link that only missed one reading is left alone.
     */
    const val DOWN_BEFORE_ASKING_MS = 11L * 60L * 1000L

    /** Whether a link that is down should be asked to start again now. */
    fun shouldAsk(hasSensor: Boolean, hasKey: Boolean, paused: Boolean, permitted: Boolean, bluetoothOn: Boolean, connected: Boolean): Boolean =
        hasSensor && hasKey && !paused && permitted && bluetoothOn && !connected
}
