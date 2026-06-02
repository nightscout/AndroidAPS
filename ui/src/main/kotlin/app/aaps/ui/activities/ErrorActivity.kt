package app.aaps.ui.activities

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.AlarmIntent
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.dialogs.GlobalSnackbarHost
import dagger.android.support.DaggerAppCompatActivity
import javax.inject.Inject
import kotlin.math.ln
import kotlin.math.pow

class ErrorActivity : DaggerAppCompatActivity() {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var config: Config
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var iconsProvider: IconsProvider
    @Inject lateinit var rh: ResourceHelper

    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private var status by mutableStateOf("")
    private var title by mutableStateOf("")
    private var sound: Int = 0

    /**
     * `SystemClock.elapsedRealtime()` when the originating notification was posted (from
     * [AlarmIntent.EXTRA_POSTED_AT_ELAPSED_REALTIME]). Used to compute the activity-audio
     * deferral so the notification channel's one-shot sound finishes before MediaPlayer starts.
     */
    private var postedAtElapsedRealtime: Long = 0L

    // Audio playback (moved here from the deleted AlarmSoundService — see git history).
    private var player: MediaPlayer? = null
    private val volumeRampHandler = Handler(Looper.getMainLooper())
    private var currentVolumeLevel = 0

    // Stable Runnable reference for the deferred MediaPlayer start so stopAudio() can
    // cancel it if the user mutes/dismisses during the channel-sound-guard window.
    private val startMediaPlayerRunnable = Runnable { startMediaPlayer() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hardware volume buttons control whichever stream we're playing on while activity is up.
        volumeControlStream =
            if (preferences.get(BooleanKey.AlertOverrideDoNotDisturb)) AudioManager.STREAM_ALARM
            else AudioManager.STREAM_NOTIFICATION

        // FSI-launched alarms must be able to wake the screen and show over the lock screen.
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        status = intent.getStringExtra(AlarmIntent.EXTRA_STATUS) ?: ""
        title = intent.getStringExtra(AlarmIntent.EXTRA_TITLE) ?: ""
        sound = intent.getIntExtra(AlarmIntent.EXTRA_SOUND_ID, app.aaps.core.ui.R.raw.error)
        postedAtElapsedRealtime = intent.getLongExtra(AlarmIntent.EXTRA_POSTED_AT_ELAPSED_REALTIME, 0L)
        val appIcon = iconsProvider.getIcon()

        aapsLogger.debug("Error activity displayed: $title - $status")

        setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            CompositionLocalProvider(
                LocalPreferences provides preferences,
                LocalDateUtil provides dateUtil,
                LocalConfig provides config,
                LocalSnackbarHostState provides snackbarHostState
            ) {
                AapsTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = Color.Transparent
                        ) {
                            ErrorScreen(
                                title = title,
                                status = status,
                                appIcon = appIcon,
                                onOk = {
                                    uel.log(Action.ERROR_DIALOG_OK, Sources.Unknown)
                                    stopAlarm("Dismiss")
                                    finish()
                                },
                                onMute = {
                                    uel.log(Action.ERROR_DIALOG_MUTE, Sources.Unknown)
                                    stopAlarm("Mute")
                                },
                                onMute5Min = {
                                    uel.log(Action.ERROR_DIALOG_MUTE_5MIN, Sources.Unknown)
                                    stopAlarm("Mute 5 min")
                                    handler.postDelayed({ startAlarm() }, T.mins(5).msecs())
                                },
                                onStart = { startAlarm() }
                            )
                        }
                        GlobalSnackbarHost(
                            rxBus = rxBus,
                            hostState = snackbarHostState,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        status = intent.getStringExtra(AlarmIntent.EXTRA_STATUS) ?: ""
        title = intent.getStringExtra(AlarmIntent.EXTRA_TITLE) ?: ""
        sound = intent.getIntExtra(AlarmIntent.EXTRA_SOUND_ID, app.aaps.core.ui.R.raw.error)
        postedAtElapsedRealtime = intent.getLongExtra(AlarmIntent.EXTRA_POSTED_AT_ELAPSED_REALTIME, 0L)
        aapsLogger.debug("Error activity updated: $title - $status")
        handler.removeCallbacksAndMessages(null)
        stopAudio()
        startAudio()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
        stopAudio()
    }

    private fun startAlarm() {
        if (sound != 0) startAudio()
    }

    private fun stopAlarm(reason: String) {
        aapsLogger.debug(LTag.CORE, "stopAlarm: $reason")
        stopAudio()
        uiInteraction.stopAlarm(reason)
    }

    private fun startAudio() {
        if (sound == 0) return
        stopAudio()

        // Defer MediaPlayer start so the notification channel's one-shot sound finishes first —
        // avoids the brief double-audio on FSI auto-launch. The deferral is computed per-sound
        // by probing the actual MP3 duration via MediaMetadataRetriever — different alarm
        // sounds have very different lengths (urgentalarm ~4s, error <1s, etc).
        // When the user taps the heads-up after the channel sound has finished, elapsed is
        // large and the delay is 0 → audio starts immediately.
        val soundDurationMs = probeSoundDurationMs(sound)
        val elapsed = if (postedAtElapsedRealtime > 0L)
            SystemClock.elapsedRealtime() - postedAtElapsedRealtime
        else
            soundDurationMs // no timestamp → assume channel sound is done
        val deferralMs = (soundDurationMs - elapsed).coerceAtLeast(0L)
        if (deferralMs > 0) {
            aapsLogger.debug(LTag.CORE, "startAudio: deferring MediaPlayer start by ${deferralMs}ms (channel sound still playing)")
            volumeRampHandler.postDelayed(startMediaPlayerRunnable, deferralMs)
        } else {
            startMediaPlayer()
        }
    }

    private fun startMediaPlayer() {
        if (sound == 0) return

        val overrideDnd = preferences.get(BooleanKey.AlertOverrideDoNotDisturb)
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(if (overrideDnd) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        try {
            val mp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                MediaPlayer(createAttributionContext("aapsAudio"))
            } else {
                MediaPlayer()
            }
            mp.setAudioAttributes(audioAttrs)
            val afd = rh.openRawResourceFd(sound) ?: run {
                aapsLogger.error(LTag.CORE, "startAudio: unable to open raw resource $sound")
                mp.release()
                return
            }
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mp.isLooping = true
            if (preferences.get(BooleanKey.AlertIncreaseVolume)) {
                currentVolumeLevel = 0
                mp.setVolume(0f, 0f)
                volumeRampHandler.postDelayed(volumeRamp, VOLUME_INCREASE_INITIAL_SILENT_TIME_MILLIS)
            } else {
                mp.setVolume(1f, 1f)
            }
            mp.setOnPreparedListener { prepared ->
                // stopAudio() may have nulled out player before prepare completes —
                // only start if we're still the active player.
                if (player === prepared) prepared.start()
                else prepared.release()
            }
            mp.setOnErrorListener { _, what, extra ->
                aapsLogger.error(LTag.CORE, "MediaPlayer error what=$what extra=$extra")
                // Listener may fire on a background thread; bounce to main to mutate `player` safely.
                volumeRampHandler.post { stopAudio() }
                true
            }
            // Assign before prepareAsync so stopAudio() can find it during the Preparing state.
            player = mp
            mp.prepareAsync()
        } catch (ex: Exception) {
            aapsLogger.error(LTag.CORE, "startAudio: unhandled exception", ex)
        }
    }

    /**
     * Reads the duration of a raw audio resource via [MediaMetadataRetriever] without decoding
     * the audio — fast (~10–50ms typical). Returns 0 if the resource is missing or unreadable;
     * callers treat 0 as "no deferral".
     */
    private fun probeSoundDurationMs(@androidx.annotation.RawRes soundId: Int): Long {
        if (soundId == 0) return 0L
        val retriever = MediaMetadataRetriever()
        return try {
            val afd = resources.openRawResourceFd(soundId) ?: return 0L
            try {
                retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            } finally {
                afd.close()
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (ex: Exception) {
            aapsLogger.error(LTag.CORE, "probeSoundDurationMs: failed for soundId=$soundId", ex)
            0L
        } finally {
            retriever.release()
        }
    }

    private fun stopAudio() {
        volumeRampHandler.removeCallbacks(volumeRamp)
        volumeRampHandler.removeCallbacks(startMediaPlayerRunnable)
        player?.let { mp ->
            try {
                if (mp.isPlaying) mp.stop()
            } catch (_: IllegalStateException) {
                // already released / not started — ignore
            }
            mp.release()
        }
        player = null
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
                volumeRampHandler.postDelayed(this, delay)
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
