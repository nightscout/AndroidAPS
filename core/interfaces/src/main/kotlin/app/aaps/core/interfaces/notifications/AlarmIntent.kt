package app.aaps.core.interfaces.notifications

/**
 * Intent extras used to carry alarm payload from notification PendingIntents into ErrorActivity.
 * Lives in core/interfaces so both the activity (ui module) and the notification builder
 * (implementation module) can reference the same keys without a cross-module dependency.
 */
object AlarmIntent {

    /** Raw sound resource id; the activity uses it to play audio with volume ramp. */
    const val EXTRA_SOUND_ID = "soundId"

    /** Alarm status / body text. */
    const val EXTRA_STATUS = "status"

    /** Alarm title. */
    const val EXTRA_TITLE = "title"

    /**
     * SystemClock.elapsedRealtime() when the alarm notification was posted. The activity uses
     * this together with the per-sound MP3 duration (probed via MediaMetadataRetriever) to
     * defer its own MediaPlayer start until the notification channel's one-shot sound is done
     * playing — avoids the brief double-audio on FSI auto-launch where channel sound and
     * activity loop would otherwise overlap.
     */
    const val EXTRA_POSTED_AT_ELAPSED_REALTIME = "postedAtElapsedRealtime"
}
