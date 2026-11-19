package app.aaps.ui.activities

import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.AlarmIntent
import app.aaps.core.interfaces.pump.alerts.PumpAlerts
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAlarmDismissed
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.SnackbarHostPresence
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.MetroAppCompatActivity
import app.aaps.core.ui.compose.dialogs.GlobalSnackbarHost
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first

/**
 * The alarm screen: one alert, over whatever the user is looking at and over the lock screen.
 *
 * It owns no sound. Every alarm is a card in the notification list now - a pump's alert and the app's own alike -
 * and that list is the only thing that plays alarm audio, so this screen is a view of the ringing card and its
 * buttons act on that one alert. See `_docs/PUMP_DESIGN.md`, section 2 and section 8.
 *
 * It used to own a `MediaPlayer` under `OWNER_FULLSCREEN` for the alarms that came through
 * `UiInteraction.runAlarm`, and carried the title, the message, the sound and the eat-reminder flag in its intent.
 * All of that is gone with the second alarm path: the screen is told which alert to show and reads the rest from
 * [PumpAlerts.active], so an alert whose text changes while the screen is open shows the new text.
 *
 * The screen itself is shared code ([PumpAlertScreen]), so iOS shows the same one.
 */
class ErrorActivity : MetroAppCompatActivity() {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var snackbarHostPresence: SnackbarHostPresence
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var config: Config
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var iconsProvider: IconsProvider
    @Inject lateinit var pumpAlerts: PumpAlerts

    /** Which alert is shown. Mutable because [onNewIntent] replaces it when a second alarm takes the screen. */
    private var alertSource by mutableStateOf<String?>(null)
    private var alertKey by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The hardware volume buttons control whichever stream the alarm plays on while this is up.
        volumeControlStream =
            if (preferences.get(BooleanKey.AlertOverrideDoNotDisturb)) AudioManager.STREAM_ALARM
            else AudioManager.STREAM_NOTIFICATION

        // An alarm woken from the background has to be able to light the screen and show over the lock screen.
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        readAlert(intent)
        val appIcon = iconsProvider.getIcon()

        // The flavour launcher icon, badged over the error icon. It is a drawable id, so the shared screen takes it
        // as a slot rather than a parameter.
        val badge: @Composable () -> Unit = {
            Image(
                painter = painterResource(id = appIcon),
                contentDescription = "App icon",
                modifier = Modifier.size(20.dp)
            )
        }

        setContent {
            val source = alertSource
            val key = alertKey
            // OK on the watch: the alarm is answered there, and this screen closes as its own OK would.
            LaunchedEffect(Unit) {
                rxBus.toFlow(EventAlarmDismissed::class).first()
                finish()
            }
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
                            if (source != null && key != null) PumpAlertScreen(
                                pumpAlerts = pumpAlerts,
                                source = source,
                                key = key,
                                uel = uel,
                                onClose = { finish() },
                                badge = badge
                            )
                        }
                        GlobalSnackbarHost(
                            rxBus = rxBus,
                            snackbarHostPresence = snackbarHostPresence,
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
        readAlert(intent)
    }

    /**
     * Which alert this screen is for. Without it there is nothing to show, so the screen closes rather than
     * standing there empty - it can only happen if something other than the alert platform started it.
     */
    private fun readAlert(intent: Intent) {
        alertSource = intent.getStringExtra(AlarmIntent.EXTRA_PUMP_ALERT_SOURCE)
        alertKey = intent.getStringExtra(AlarmIntent.EXTRA_PUMP_ALERT_KEY)
        if (alertSource == null || alertKey == null) {
            aapsLogger.error(LTag.CORE, "Alarm screen started without an alert, closing")
            finish()
            return
        }
        aapsLogger.debug(LTag.CORE, "Alarm screen for $alertSource/$alertKey")
    }
}
