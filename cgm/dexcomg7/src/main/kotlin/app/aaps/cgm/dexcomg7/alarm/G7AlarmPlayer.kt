package app.aaps.cgm.dexcomg7.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.math.ceil

/**
 * Plays the alarm sounds and vibrations.
 *
 * The sound goes to the alarm stream ([AudioAttributes.USAGE_ALARM]), the same way the AAPS alarm
 * does with "override Do Not Disturb" on. That stream is not muted by the ringer's silent or vibrate
 * mode. It still has its own volume, which can be 0, so with a minimum volume set the stream is raised
 * to that level while the sound plays and put back afterwards. The vibration is marked as an alarm
 * too, which silent mode does not suppress either.
 *
 * One sound at a time. Every MediaPlayer call runs on the main looper, as in AAPS's AlarmSoundPlayerImpl.
 */
@SingleIn(AppScope::class)
@Inject
class G7AlarmPlayer(
    private val context: Context,
    private val aapsLogger: AAPSLogger
) {

    private val handler = Handler(Looper.getMainLooper())
    private val audioManager get() = context.getSystemService(AudioManager::class.java)

    // --- main looper only ---
    private var player: MediaPlayer? = null
    private var volumeBefore: Int? = null
    private var volumeSet: Int? = null

    /**
     * Play [sound] once, or on a loop until [stop] when [loop] is true. The alarm stream is raised to
     * at least [minimumVolumePercent] of its range while it plays; 0 leaves it alone.
     */
    fun play(sound: G7AlarmSound, loop: Boolean, minimumVolumePercent: Int) {
        handler.post { doPlay(sound, loop, minimumVolumePercent) }
    }

    fun stop() {
        handler.post { doStop() }
    }

    fun vibrate(urgent: Boolean) {
        val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        if (!vibrator.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(if (urgent) URGENT_PATTERN else PATTERN, -1)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, ALARM_ATTRIBUTES)
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.BGSOURCE, "Dexcom G7: vibration failed", e)
        }
    }

    private fun doPlay(sound: G7AlarmSound, loop: Boolean, minimumVolumePercent: Int) {
        doStop()
        raiseVolume(minimumVolumePercent)
        try {
            val mp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) MediaPlayer(context.createAttributionContext("aapsAudio")) else MediaPlayer()
            mp.setAudioAttributes(ALARM_ATTRIBUTES)
            // Keeps the CPU up while the sound plays, even in Doze.
            mp.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            context.resources.openRawResourceFd(sound.rawRes).use { afd -> mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length) }
            mp.isLooping = loop
            mp.setVolume(1f, 1f)
            mp.setOnPreparedListener { if (player === it) it.start() else it.release() }
            mp.setOnCompletionListener { if (player === it) doStop() else it.release() }
            mp.setOnErrorListener { _, what, extra ->
                aapsLogger.error(LTag.BGSOURCE, "Dexcom G7: alarm sound error what=$what extra=$extra")
                doStop()
                true
            }
            player = mp
            mp.prepareAsync()
        } catch (e: Exception) {
            aapsLogger.error(LTag.BGSOURCE, "Dexcom G7: alarm sound could not play", e)
            doStop()
        }
    }

    private fun doStop() {
        player?.let { mp ->
            try {
                if (mp.isPlaying) mp.stop()
            } catch (_: IllegalStateException) {
                // not started yet, or already released
            }
            mp.release()
        }
        player = null
        restoreVolume()
    }

    private fun raiseVolume(minimumVolumePercent: Int) {
        if (minimumVolumePercent <= 0) return
        val am = audioManager ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val wanted = ceil(max * minimumVolumePercent / 100.0).toInt().coerceIn(1, max)
        val current = am.getStreamVolume(AudioManager.STREAM_ALARM)
        if (current >= wanted) return
        try {
            am.setStreamVolume(AudioManager.STREAM_ALARM, wanted, 0)
            if (volumeBefore == null) volumeBefore = current
            volumeSet = wanted
        } catch (e: SecurityException) {
            aapsLogger.error(LTag.BGSOURCE, "Dexcom G7: could not raise the alarm volume", e)
        }
    }

    private fun restoreVolume() {
        val before = volumeBefore ?: return
        val am = audioManager
        // Only if the user has not changed it themselves in the meantime.
        if (am != null && am.getStreamVolume(AudioManager.STREAM_ALARM) == volumeSet) {
            try {
                am.setStreamVolume(AudioManager.STREAM_ALARM, before, 0)
            } catch (e: SecurityException) {
                aapsLogger.error(LTag.BGSOURCE, "Dexcom G7: could not restore the alarm volume", e)
            }
        }
        volumeBefore = null
        volumeSet = null
    }

    private companion object {

        // Lazy: built on first use, not when the class loads (the DI graph tests load it on the JVM).
        val ALARM_ATTRIBUTES: AudioAttributes by lazy {
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        }

        val PATTERN = longArrayOf(0, 400, 200, 400, 200, 400)
        val URGENT_PATTERN = longArrayOf(0, 1000, 400, 1000, 400, 1000, 400, 1000)
    }
}
