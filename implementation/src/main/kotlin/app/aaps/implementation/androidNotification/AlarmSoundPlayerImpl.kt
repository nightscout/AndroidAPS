package app.aaps.implementation.androidNotification

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RawRes
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import javax.inject.Inject
import javax.inject.Singleton
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
 */
@Singleton
class AlarmSoundPlayerImpl @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val rh: ResourceHelper
) : AlarmSoundPlayer {

    private val handler = Handler(Looper.getMainLooper())

    // --- main-looper-confined state ---
    private var player: MediaPlayer? = null
    @RawRes private var currentSound: Int = 0
    private var currentOwner: String? = null
    private var currentVolumeLevel = 0

    // Stable reference so a deferred start can be cancelled by doStop() during the channel-sound guard.
    private val startRunnable = Runnable { startMediaPlayer() }

    override fun play(@RawRes soundRes: Int, ownerTag: String, postedAtElapsedRealtime: Long) {
        if (soundRes == 0) return
        handler.post { doPlay(soundRes, ownerTag, postedAtElapsedRealtime) }
    }

    override fun stop(ownerTag: String) {
        handler.post { if (currentOwner == ownerTag) doStop() }
    }

    private fun doPlay(@RawRes soundRes: Int, ownerTag: String, postedAtElapsedRealtime: Long) {
        doStop()
        currentSound = soundRes
        currentOwner = ownerTag

        // Only the full-screen path passes postedAt > 0 (it has an accompanying channel one-shot to
        // wait out). For the internal path postedAt == 0, so skip the blocking MediaMetadataRetriever
        // probe entirely and start immediately.
        val deferralMs =
            if (postedAtElapsedRealtime > 0L) {
                val soundDurationMs = probeSoundDurationMs(soundRes)
                (soundDurationMs - (SystemClock.elapsedRealtime() - postedAtElapsedRealtime)).coerceAtLeast(0L)
            } else 0L

        if (deferralMs > 0) {
            aapsLogger.debug(LTag.CORE, "AlarmSoundPlayer: deferring start by ${deferralMs}ms (channel sound still playing)")
            handler.postDelayed(startRunnable, deferralMs)
        } else {
            startMediaPlayer()
        }
    }

    private fun doStop() {
        handler.removeCallbacks(startRunnable)
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
        val soundRes = currentSound
        if (soundRes == 0) return

        val overrideDnd = preferences.get(BooleanKey.AlertOverrideDoNotDisturb)
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
            val afd = rh.openRawResourceFd(soundRes) ?: run {
                aapsLogger.error(LTag.CORE, "AlarmSoundPlayer: unable to open raw resource $soundRes")
                mp.release()
                return
            }
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
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
            mp.setOnErrorListener { _, what, extra ->
                aapsLogger.error(LTag.CORE, "AlarmSoundPlayer: MediaPlayer error what=$what extra=$extra")
                doStop()
                true
            }
            // Assign before prepareAsync so doStop() can find it during the Preparing state.
            player = mp
            mp.prepareAsync()
        } catch (ex: Exception) {
            aapsLogger.error(LTag.CORE, "AlarmSoundPlayer: unhandled exception", ex)
        }
    }

    /**
     * Reads the duration of a raw audio resource via [MediaMetadataRetriever] without decoding the
     * audio. Returns 0 if missing/unreadable; callers treat 0 as "no deferral".
     */
    private fun probeSoundDurationMs(@RawRes soundId: Int): Long {
        if (soundId == 0) return 0L
        val retriever = MediaMetadataRetriever()
        return try {
            val afd = context.resources.openRawResourceFd(soundId) ?: return 0L
            try {
                retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            } finally {
                afd.close()
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (ex: Exception) {
            aapsLogger.error(LTag.CORE, "AlarmSoundPlayer: probeSoundDurationMs failed for soundId=$soundId", ex)
            0L
        } finally {
            retriever.release()
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
