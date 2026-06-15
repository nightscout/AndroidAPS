package app.aaps.ui.activities

import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
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
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
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

/**
 * Full-screen alarm UI. Audio playback (looping MediaPlayer + volume ramp) lives in the shared
 * [AlarmSoundPlayer] singleton so the internal-notification alarm path can reuse it; this activity
 * only drives play/stop in response to user actions.
 */
class ErrorActivity : DaggerAppCompatActivity() {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var config: Config
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var iconsProvider: IconsProvider
    @Inject lateinit var alarmSoundPlayer: AlarmSoundPlayer

    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private var status by mutableStateOf("")
    private var title by mutableStateOf("")
    private var sound: Int = 0

    /**
     * `SystemClock.elapsedRealtime()` when the originating notification was posted (from
     * [AlarmIntent.EXTRA_POSTED_AT_ELAPSED_REALTIME]). Forwarded to [AlarmSoundPlayer.play] so it
     * can defer its start until the notification channel's one-shot sound has finished.
     */
    private var postedAtElapsedRealtime: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hardware volume buttons control whichever stream the alarm plays on while activity is up.
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
        // play() stops any previous playback first.
        startAlarm()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
        // Owner-scoped: only stops audio this activity owns, never an internal-path alarm.
        alarmSoundPlayer.stop(AlarmSoundPlayer.OWNER_FULLSCREEN)
    }

    private fun startAlarm() {
        if (sound != 0) alarmSoundPlayer.play(sound, AlarmSoundPlayer.OWNER_FULLSCREEN, postedAtElapsedRealtime)
    }

    private fun stopAlarm(reason: String) {
        aapsLogger.debug(LTag.CORE, "stopAlarm: $reason")
        alarmSoundPlayer.stop(AlarmSoundPlayer.OWNER_FULLSCREEN)
        uiInteraction.stopAlarm(reason)
    }
}
