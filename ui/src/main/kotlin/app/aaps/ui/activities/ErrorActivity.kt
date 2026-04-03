package app.aaps.ui.activities

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.asAnnouncement
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.ui.services.AlarmSoundService
import dagger.android.support.DaggerAppCompatActivity
import kotlinx.coroutines.launch
import javax.inject.Inject

class ErrorActivity : DaggerAppCompatActivity() {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var config: Config
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var iconsProvider: IconsProvider

    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private var status: String = ""
    private var title: String = ""
    private var sound: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = intent.getStringExtra(AlarmSoundService.STATUS) ?: ""
        title = intent.getStringExtra(AlarmSoundService.TITLE) ?: ""
        sound = intent.getIntExtra(AlarmSoundService.SOUND_ID, app.aaps.core.ui.R.raw.error)
        val appIcon = iconsProvider.getIcon()

        aapsLogger.debug("Error activity displayed: $title - $status")

        setContent {
            CompositionLocalProvider(
                LocalPreferences provides preferences,
                LocalDateUtil provides dateUtil,
                LocalConfig provides config
            ) {
                AapsTheme {
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
                }
            }
        }

        // Create announcement if configured
        if (preferences.get(BooleanKey.NsClientCreateAnnouncementsFromErrors) && config.APS)
            lifecycleScope.launch {
                persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                    therapyEvent = TE.asAnnouncement(status),
                    timestamp = dateUtil.now(),
                    action = Action.CAREPORTAL,
                    source = Sources.Aaps,
                    note = status,
                    listValues = listOf(ValueWithUnit.TEType(TE.Type.ANNOUNCEMENT))
                )
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
    }

    private fun startAlarm() {
        if (sound != 0)
            uiInteraction.startAlarm(sound, "$title:$status")
    }

    private fun stopAlarm(reason: String) =
        uiInteraction.stopAlarm(reason)
}

@Composable
fun ErrorScreen(
    title: String,
    status: String,
    appIcon: Int,
    onOk: () -> Unit,
    onMute: () -> Unit,
    onMute5Min: () -> Unit,
    onStart: () -> Unit
) {
    DisposableEffect(Unit) {
        onStart()
        onDispose { }
    }

    // Semi-transparent background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = false) { }
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        // Dialog-style card
        Card(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Error icon with app icon badge
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    // Main error icon
                    Image(
                        painter = painterResource(id = app.aaps.core.ui.R.drawable.ic_error_red_48dp),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                    // App icon badge in bottom-right corner
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = CircleShape
                            )
                            .padding(2.dp)
                    ) {
                        Image(
                            painter = painterResource(id = appIcon),
                            contentDescription = "App icon",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Status message
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Mute 5 min button
                Button(
                    onClick = onMute5Min,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(app.aaps.core.ui.R.string.mute5min))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mute button
                Button(
                    onClick = onMute,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(app.aaps.core.ui.R.string.mute))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // OK button
                Button(
                    onClick = onOk,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(app.aaps.core.ui.R.string.ok))
                }
            }
        }
    }
}
