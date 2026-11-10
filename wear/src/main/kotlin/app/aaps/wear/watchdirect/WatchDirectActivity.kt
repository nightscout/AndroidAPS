package app.aaps.wear.watchdirect

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.Text
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.wear.R
import app.aaps.wear.di.WearMetroActivity
import dev.zacsweers.metro.Inject

/**
 * Direct-to-watch on the watch itself: what the watch's own link is doing, the Bluetooth permission
 * and unrestricted battery use. Keeps the screen on while the sensor's pairing request is due, so it
 * is not missed behind the watch face.
 *
 * Opened from the notifications of [WatchDirect] and [WatchDirectService].
 */
class WatchDirectActivity : WearMetroActivity() {

    @Inject lateinit var watchDirect: WatchDirect

    private val askPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { watchDirect.retry() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val status by watchDirect.status.collectAsState()
                val state = status.state
                LaunchedEffect(state) {
                    if (state == EventData.CgmWatchState.PAIRING) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                val listState = rememberScalingLazyListState()
                Box(modifier = Modifier.fillMaxSize()) {
                    // A list and not a plain column: it scrolls with the crown and keeps the text and the
                    // button inside the round screen. A plain column cut off the bottom of the button.
                    ScalingLazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            ListHeader {
                                Text(stringResource(R.string.watch_direct_title), textAlign = TextAlign.Center)
                            }
                        }
                        item {
                            Text(stringResource(stateText(state)), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        }
                        if (state == EventData.CgmWatchState.NEEDS_PERMISSION) {
                            item {
                                Button(
                                    onClick = { askPermission.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.watch_direct_allow))
                                }
                            }
                        } else if (status.batteryRestricted) {
                            item {
                                Text(stringResource(R.string.watch_direct_notification_battery), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                            }
                            item {
                                Button(
                                    onClick = { askForUnrestrictedBattery() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.watch_direct_allow_battery))
                                }
                            }
                        }
                    }
                    // Only when there is something to scroll, as in MenuListActivity
                    if (listState.canScrollForward || listState.canScrollBackward) {
                        ScrollIndicator(
                            state = listState,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
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
     * The system prompt for unrestricted battery use. Not every watch has it, so fall back to the
     * list of apps and their battery use, then to this app's settings page.
     */
    @SuppressLint("BatteryLife")
    private fun askForUnrestrictedBattery() {
        // The same screens WatchDirect looks for before it shows the warning, then app info as a last try.
        val intents = batterySettingsIntents(packageName) +
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // try the next one
            }
        }
    }

    private fun stateText(state: EventData.CgmWatchState): Int = when (state) {
        EventData.CgmWatchState.OFF              -> R.string.watch_direct_state_off
        EventData.CgmWatchState.NOT_SUPPORTED    -> R.string.watch_direct_state_not_supported
        EventData.CgmWatchState.NEEDS_PERMISSION -> R.string.watch_direct_notification_permission
        EventData.CgmWatchState.BLUETOOTH_OFF    -> R.string.watch_direct_state_bluetooth_off
        EventData.CgmWatchState.PAIRING          -> R.string.watch_direct_notification_pairing
        EventData.CgmWatchState.WAITING,
        EventData.CgmWatchState.CONNECTED        -> R.string.watch_direct_state_running
        EventData.CgmWatchState.REFUSED          -> R.string.watch_direct_notification_refused
    }
}
