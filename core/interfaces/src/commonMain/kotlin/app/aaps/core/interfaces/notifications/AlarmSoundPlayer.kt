package app.aaps.core.interfaces.notifications

import app.aaps.core.interfaces.notifications.AlarmSoundPlayer.Companion.OWNER_INTERNAL

/**
 * Plays AAPS alarm audio (looping, with optional volume ramp) without owning a foreground service
 * or an Activity.
 *
 * AAPS normally holds foreground state via the persistent notification service (`DummyService`),
 * which is what lets a background [android.media.MediaPlayer] start — this avoids the old
 * `AlarmSoundService` `startForegroundService()` race. NOTE: if the user has disabled the
 * persistent notification, a backgrounded alarm may be throttled on Android 12+.
 *
 * A single sound plays at a time. Each playback records the [OWNER_INTERNAL] tag of whoever
 * requested it, and [stop] is owner-scoped, so a second owner could never silence the first's alarm.
 * There used to be a second one, `OWNER_FULLSCREEN`, for the alarm the deleted `postFullScreenAlarm`
 * started; every alarm is a card in the notification list now and the registry is the only owner.
 * Volume ramp and DND/stream routing follow the user's `AlertIncreaseVolume` /
 * `AlertOverrideDoNotDisturb` preferences. On Android a stream choice alone cannot keep the override
 * setting, the Do Not Disturb profile decides what a stream may do: with the override off the player
 * checks Do Not Disturb itself and stays quiet, and with it on the alarm stream is heard only when the
 * profile allows alarms. Every other alarm sound on Android follows the same rule, `DoNotDisturbRule`
 * in androidMain.
 *
 * `AlertOverrideDoNotDisturb` takes two levers to honour off Android, because iOS splits what
 * Android's alarm stream does in one. The Ring/Silent switch is answered by the audio session
 * category chosen here - and only by that, since iOS publishes no way to read the switch - while
 * Focus is answered by the notification's interruption level in `SystemNotificationPlatform`. An
 * implementation that sets only one of the two honours half the setting, which is what iOS did.
 *
 * Desktop honours neither, and cannot: AWT offers a single system beep and no notion of silent mode.
 */
interface AlarmSoundPlayer {

    /**
     * Start looping playback of [sound], recording [ownerTag] as the current owner. Any previous
     * playback is stopped first.
     */
    fun play(sound: AlarmSound, ownerTag: String)

    /** Stop and release playback **only if** [ownerTag] is the current owner. No-op otherwise. */
    fun stop(ownerTag: String)

    companion object {

        /** Owner tag for the internal-notification (`NotificationManager`) alarm path. */
        const val OWNER_INTERNAL = "internal"
    }
}
