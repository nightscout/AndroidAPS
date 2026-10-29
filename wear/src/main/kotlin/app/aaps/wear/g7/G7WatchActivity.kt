package app.aaps.wear.g7

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.aaps.core.interfaces.di.injectMetroMembers
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.wear.R
import dev.zacsweers.metro.Inject

/**
 * Direct-to-watch on the watch itself: asks for the Bluetooth permission and for unrestricted battery use,
 * and keeps the screen on while the sensor's pairing requests are due, so they are not missed behind the
 * watch face.
 */
class G7WatchActivity : ComponentActivity() {

    @Inject lateinit var watchDirect: G7WatchDirect

    private val askPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { watchDirect.retry() }

    override fun onCreate(savedInstanceState: Bundle?) {
        injectMetroMembers(this)
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val status by watchDirect.status.collectAsState()
                val state = status.state
                LaunchedEffect(state) {
                    if (state == EventData.G7WatchState.PAIRING) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(stringResource(R.string.g7_watch_title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(text(state)), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    if (state == EventData.G7WatchState.NEEDS_PERMISSION) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { askPermission.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)) }) {
                            Text(stringResource(R.string.g7_watch_allow))
                        }
                    } else if (status.batteryRestricted) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.g7_watch_notification_battery), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { askForUnrestrictedBattery() }) {
                            Text(stringResource(R.string.g7_watch_allow_battery))
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from the settings: battery use may have changed
        watchDirect.refresh()
    }

    /**
     * The system prompt for unrestricted battery use. Not every watch has it, so fall back to the list of
     * apps and their battery use, then to this app's settings page.
     */
    @SuppressLint("BatteryLife")
    private fun askForUnrestrictedBattery() {
        val intents = listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        )
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // try the next one
            }
        }
    }

    private fun text(state: EventData.G7WatchState): Int = when (state) {
        EventData.G7WatchState.OFF              -> R.string.g7_watch_state_off
        EventData.G7WatchState.NOT_SUPPORTED    -> R.string.g7_watch_state_not_supported
        EventData.G7WatchState.NEEDS_PERMISSION -> R.string.g7_watch_notification_permission
        EventData.G7WatchState.BLUETOOTH_OFF    -> R.string.g7_watch_state_bluetooth_off
        EventData.G7WatchState.PAIRING          -> R.string.g7_watch_notification_pairing
        EventData.G7WatchState.WAITING,
        EventData.G7WatchState.CONNECTED        -> R.string.g7_watch_state_running
        EventData.G7WatchState.REFUSED          -> R.string.g7_watch_notification_refused
    }
}
