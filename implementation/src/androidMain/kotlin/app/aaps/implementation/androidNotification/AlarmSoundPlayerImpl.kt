package app.aaps.implementation.androidNotification

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.core.interfaces.notifications.DoNotDisturbRule
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.rawRes
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.math.ln
import kotlin.math.pow

/**
 * Activity-less, foreground-service-less alarm audio player. Extracted from the old
 * `AlarmSoundService` / `ErrorActivity` MediaPlayer path so the full-screen alarm and the
 * internal-notification alarm share one implementation. See [AlarmSoundPlayer].
 *
 * Thread confinement: [play] and [stop] are thin shims that post their work to the **main looper**;
 * ALL access to [player] / [currentSound] / [currentOwner] / [currentVolumeLevel] and every
 * MediaPlayer call + callback runs on that single thread. This makes the player race-free even
 * though [play] may be invoked from any thread (e.g. NotificationManagerImpl's IO dispatcher).
 *
 * Do Not Disturb: with the override off the sound is not started while Do Not Disturb is on, see
 * [DoNotDisturbRule]. With it on the alarm stream is used, and the Do Not Disturb profile decides.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AlarmSoundPlayerImpl(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val rh: ResourceHelper
) : AlarmSoundPlayer {

    private val handler = Handler(Looper.getMainLooper())
    private val notificationManager get() = context.getSystemService(NotificationManager::class.java)

    // --- main-looper-confined state ---
    private var player: MediaPlayer? = null
    private var currentSound: AlarmSound? = null
    private var currentOwner: String? = null
    private var currentVolumeLevel = 0

    /** Whether the sound playing now breaks through Do Not Disturb (urgent low) whatever the override says. */
    private var currentBreaksThrough = false

    /** The phone ringtone the user chose for the sound playing now, or null: the bundled [currentSound]. */
    private var currentRingtone: String? = null

    override fun play(sound: AlarmSound, ownerTag: String, breakThroughDnd: Boolean, ringtoneUri: String?) {
        handler.post { doPlay(sound, ownerTag, breakThroughDnd, ringtoneUri) }
    }

    override fun stop(ownerTag: String) {
        handler.post { if (currentOwner == ownerTag) doStop() }
    }

    private fun doPlay(sound: AlarmSound, ownerTag: String, breakThroughDnd: Boolean, ringtoneUri: String?) {
        // Idempotent: a re-request of the exact same sound by the same owner while it is already playing is a no-op,
        // so the registry offering the same alarm again does not restart the volume ramp.
        if (player != null && currentOwner == ownerTag && currentSound == sound && currentBreaksThrough == breakThroughDnd &&
            currentRingtone == ringtoneUri
        ) return

        doStop()
        currentSound = sound
        currentBreaksThrough = breakThroughDnd
        currentOwner = ownerTag
        currentRingtone = ringtoneUri
        // Started at once. The start used to be deferred past the one-shot sound of the notification channel that
        // the deleted `postFullScreenAlarm` posted alongside it; the card that carries an alarm now is silent, so
        // there is nothing to wait out and no reason for the blocking MediaMetadataRetriever probe.
        startMediaPlayer()
    }

    private fun doStop() {
        handler.removeCallbacks(volumeRamp)
        player?.let { mp ->
            try {
                if (mp.isPlaying) mp.stop()
            } catch (_: IllegalStateException) {
                // already released / not started — ignore
            }
            mp.release()
        }
        player = null
        currentOwner = null
    }

    private fun startMediaPlayer() {
        val soundRes = currentSound?.rawRes ?: return

        // Urgent low rings in Do Not Disturb whatever the user's override says; it plays on the alarm stream for that
        val overrideDnd = currentBreaksThrough || preferences.get(BooleanKey.AlertOverrideDoNotDisturb)
        if (DoNotDisturbRule.staysQuiet(overrideDnd, notificationManager.currentInterruptionFilter)) {
            aapsLogger.debug(LTag.CORE, "AlarmSoundPlayer: Do Not Disturb is on and the override is off, the alarm stays quiet")
            return
        }
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(if (overrideDnd) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        try {
            val mp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                MediaPlayer(context.createAttributionContext("aapsAudio"))
            } else {
                MediaPlayer()
            }
            mp.setAudioAttributes(audioAttrs)
            // The user's ringtone, else the bundled sound. A ringtone that cannot be opened falls back at once: it
            // must never leave the alarm silent
            val ringtone = currentRingtone?.let { uri ->
                try {
                    mp.setDataSource(context, Uri.parse(uri))
                    true
                } catch (e: Exception) {
                    aapsLogger.error(LTag.CORE, "AlarmSoundPlayer: ringtone $uri cannot be played, the bundled sound plays", e)
                    currentRingtone = null
                    mp.reset()
                    mp.setAudioAttributes(audioAttrs)
                    false
                }
            } == true
            if (!ringtone) {
                val afd = context.resources.openRawResourceFd(soundRes) ?: run {
                    aapsLogger.error(LTag.CORE, "AlarmSoundPlayer: unable to open raw resource $soundRes")
                    mp.release()
                    return
                }
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            }
            mp.isLooping = true
            if (preferences.get(BooleanKey.AlertIncreaseVolume)) {
                currentVolumeLevel = 0
                mp.setVolume(0f, 0f)
                handler.postDelayed(volumeRamp, VOLUME_INCREASE_INITIAL_SILENT_TIME_MILLIS)
            } else {
                mp.setVolume(1f, 1f)
            }
            mp.setOnPreparedListener { prepared ->
                // doStop() may have nulled/replaced player before prepare completes (all on main) —
                // only start if we're still the active player, else release the stale one.
                if (player === prepared) prepared.start()
                else prepared.release()
            }
            mp.setOnErrorListener { failed, what, extra ->
                aapsLogger.error(LTag.CORE, "AlarmSoundPlayer: MediaPlayer error what=$what extra=$extra")
                if (player === failed && currentRingtone != null) {
                    // The ringtone failed while preparing or playing: the bundled sound plays instead, same owner
                    aapsLogger.error(LTag.CORE, "AlarmSoundPlayer: ringtone failed, the bundled sound plays")
                    handler.removeCallbacks(volumeRamp)
                    player = null
                    failed.release()
                    currentRingtone = null
                    startMediaPlayer()
                } else doStop()
                true
            }
            // Assign before prepareAsync so doStop() can find it during the Preparing state.
            player = mp
            mp.prepareAsync()
        } catch (ex: Exception) {
            aapsLogger.error(LTag.CORE, "AlarmSoundPlayer: unhandled exception", ex)
        }
    }


    private val volumeRamp = object : Runnable {
        override fun run() {
            currentVolumeLevel++
            val volumePercentage = 100.0.coerceAtMost(currentVolumeLevel / VOLUME_INCREASE_STEPS.toDouble() * 100)
            val volume = (1 - (ln(1.0.coerceAtLeast(100.0 - volumePercentage)) / ln(100.0))).toFloat()
            player?.setVolume(volume, volume)
            if (currentVolumeLevel < VOLUME_INCREASE_STEPS) {
                val delay = VOLUME_INCREASE_MIN_DELAY_MILLIS.coerceAtLeast(
                    VOLUME_INCREASE_BASE_DELAY_MILLIS -
                        ((currentVolumeLevel - 1).toDouble().pow(VOLUME_INCREASE_DELAY_DECREMENT_EXPONENT) * 1000).toLong()
                )
                handler.postDelayed(this, delay)
            }
        }
    }

    private companion object {

        const val VOLUME_INCREASE_STEPS = 40
        const val VOLUME_INCREASE_INITIAL_SILENT_TIME_MILLIS = 3_000L
        const val VOLUME_INCREASE_BASE_DELAY_MILLIS = 15_000L
        const val VOLUME_INCREASE_MIN_DELAY_MILLIS = 2_000L
        const val VOLUME_INCREASE_DELAY_DECREMENT_EXPONENT = 2.0
    }
}
