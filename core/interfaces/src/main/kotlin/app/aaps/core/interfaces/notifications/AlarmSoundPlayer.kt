package app.aaps.core.interfaces.notifications

import androidx.annotation.RawRes
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer.Companion.OWNER_FULLSCREEN
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer.Companion.OWNER_INTERNAL

/**
 * Plays AAPS alarm audio (looping, with optional volume ramp) without owning a foreground service
 * or an Activity.
 *
 * AAPS normally holds foreground state via the persistent notification service (`DummyService`),
 * which is what lets a background [android.media.MediaPlayer] start — this avoids the old
 * `AlarmSoundService` `startForegroundService()` race. NOTE: if the user has disabled the
 * persistent notification, a backgrounded internal alarm may be throttled on Android 12+; the
 * full-screen ([OWNER_FULLSCREEN]) path is unaffected because the activity is itself foreground.
 *
 * A single sound plays at a time. Each playback records the [OWNER_FULLSCREEN]/[OWNER_INTERNAL]
 * tag of whoever requested it; [stop] is owner-scoped so one driver tearing down (e.g. the
 * full-screen activity on rotation) cannot silence an alarm started by the other driver.
 * Volume ramp and DND/stream routing follow the user's `AlertIncreaseVolume` /
 * `AlertOverrideDoNotDisturb` preferences.
 */
interface AlarmSoundPlayer {

    /**
     * Start looping playback of [soundRes], recording [ownerTag] as the current owner. Any previous
     * playback (from either owner) is stopped first.
     *
     * @param postedAtElapsedRealtime [android.os.SystemClock.elapsedRealtime] when an accompanying
     *   notification carrying a one-shot channel sound of the same resource was posted. When > 0,
     *   the start is deferred until that one-shot finishes (avoids brief double-audio on full-screen
     *   auto-launch). Pass 0 (the default) when there is no accompanying channel sound — the
     *   duration probe is then skipped entirely.
     */
    fun play(@RawRes soundRes: Int, ownerTag: String, postedAtElapsedRealtime: Long = 0L)

    /** Stop and release playback **only if** [ownerTag] is the current owner. No-op otherwise. */
    fun stop(ownerTag: String)

    companion object {

        /** Owner tag for the full-screen [ErrorActivity] alarm path. */
        const val OWNER_FULLSCREEN = "fullscreen"

        /** Owner tag for the internal-notification (`NotificationManager`) alarm path. */
        const val OWNER_INTERNAL = "internal"
    }
}
