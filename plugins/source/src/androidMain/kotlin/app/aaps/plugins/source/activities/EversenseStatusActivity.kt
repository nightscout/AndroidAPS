package app.aaps.plugins.source.activities

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.MetroAppCompatActivity
import app.aaps.plugins.eversense.EversenseCGMPlugin
import app.aaps.plugins.eversense.callbacks.EversenseScanCallback
import app.aaps.plugins.eversense.callbacks.EversenseWatcher
import app.aaps.plugins.eversense.enums.EversenseType
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.models.ActiveAlarm
import app.aaps.plugins.eversense.models.EversenseScanResult
import app.aaps.plugins.eversense.models.EversenseState
import app.aaps.plugins.source.R
import dev.zacsweers.metro.Inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class EversenseStatusActivity : MetroAppCompatActivity(), EversenseWatcher {
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var eversense: EversenseCGMPlugin

    private enum class Dialog { NONE, DISCONNECT, CHANGE, SCANNING, RESULTS }
    private var dialog by mutableStateOf(Dialog.NONE)
    private val foundDevices = mutableStateListOf<EversenseScanResult>()
    private var labels by mutableStateOf(emptyList<String>())
    private var connected by mutableStateOf(false)
    private var syncDays by mutableIntStateOf(14)
    private var scanSession: AutoCloseable? = null
    private var scanGeneration = 0L
    private var resumed = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        syncDays = eversense.preferences.getInt("eversense_sync_days", 14)
        updateStatus()
        setEversenseContent(R.string.eversense_status_title, preferences) {
            EversenseStatusContent(labels, connected, syncDays,
                onConnect = ::handleConnectTap, onChange = { dialog = Dialog.CHANGE },
                onSync = { if (eversense.isConnected()) {
                    ioScope.launch { eversense.triggerFullSync(force = true) }
                    mainHandler.postDelayed({ updateStatus() }, 5000)
                } },
                onDays = { syncDays = it; eversense.preferences.edit().putInt("eversense_sync_days", it).apply() })
            DeviceDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        eversense.addWatcher(this)
        updateStatus()
    }

    override fun onPause() {
        resumed = false
        dismissDialog()
        mainHandler.removeCallbacksAndMessages(null)
        eversense.removeWatcher(this)
        super.onPause()
    }

    override fun onConnectionChanged(connected: Boolean) { mainHandler.post { if (resumed) updateStatus() } }
    override fun onStateChanged(state: EversenseState) { mainHandler.post { if (resumed) updateStatus() } }

    // isConnected() is connected && transmitterReady. onConnectionChanged(true) fires as soon as
    // the raw BLE link comes up, before the auth handshake finishes - at that point isConnected()
    // is still false, so updateStatus() draws the red X. transmitterReady only flips true later,
    // once auth completes, and that transition notifies watchers via onTransmitterReady() instead
    // of onConnectionChanged() again. Without refreshing here too, the screen never learns the
    // handshake finished and is stuck showing the red X until it's reopened.
    override fun onTransmitterReady() { mainHandler.post { if (resumed) updateStatus() } }
    override fun onTransmitterNotPlaced() {}
    override fun onAlarmReceived(alarm: ActiveAlarm) {}
    override fun onCGMRead(type: EversenseType, readings: List<EversenseCGMResult>) {}


    private fun updateStatus() {
        val state = eversense.getCurrentState()
        val never = getString(R.string.eversense_status_never)
        fun date(value: Long) = if (value > 0) dateFormatter.format(Date(value)) else never
        connected = eversense.isConnected()
        labels = listOf(
            getString(R.string.eversense_status_connected_label, if (connected) "✅" else "❌"),
            getString(R.string.eversense_status_battery_label, state.batteryPercentage),
            getString(R.string.eversense_status_insertion_label, date(state.insertionDate)),
            getString(R.string.eversense_status_last_sync_label, date(state.lastSync)),
            getString(R.string.eversense_status_signal_label, signalToLabel(state.sensorSignalStrength)),
            getString(R.string.eversense_status_phase_label, state.calibrationPhase.name),
            getString(R.string.eversense_status_last_cal_label, date(state.lastCalibrationDate)),
            getString(R.string.eversense_status_next_cal_label, date(state.nextCalibrationDate))
        )
    }

    private fun handleConnectTap() {
        if (eversense.isConnected()) dialog = Dialog.DISCONNECT
        else if (eversense.preferences.getString("eversense_remote_device", null) != null) {
            ioScope.launch { eversense.forceReconnect() }
            mainHandler.postDelayed({ updateStatus() }, 3000)
        } else showDeviceSelectionDialog()
    }

    private fun dismissDialog() {
        scanGeneration++
        scanSession?.close()
        scanSession = null
        dialog = Dialog.NONE
    }

    private fun showDeviceSelectionDialog() {
        dismissDialog()
        foundDevices.clear()
        dialog = Dialog.SCANNING
        val generation = scanGeneration
        val lease = eversense.startScan(object : EversenseScanCallback {
            override fun onResult(item: EversenseScanResult) {
                mainHandler.post {
                    if (resumed && generation == scanGeneration && dialog == Dialog.SCANNING && item.name.matches(Regex("T\\d+.*")) && foundDevices.none { it.name == item.name })
                        foundDevices.add(item)
                }
            }
        })
        scanSession = lease
        mainHandler.postDelayed({
            if (!resumed || dialog != Dialog.SCANNING || scanSession !== lease) return@postDelayed
            lease.close()
            scanSession = null
            dialog = Dialog.RESULTS
        }, 10000)
    }

    @Composable
    private fun DeviceDialog() {
        val current = dialog
        if (current == Dialog.NONE) return
        val message = when (current) {
            Dialog.DISCONNECT -> R.string.eversense_disconnect_confirm
            Dialog.CHANGE -> R.string.eversense_change_transmitter_confirm
            Dialog.SCANNING -> R.string.eversense_scan_in_progress
            else -> R.string.eversense_no_transmitters_found
        }
        AlertDialog(
            onDismissRequest = ::dismissDialog,
            title = { Text(stringResource(if (current == Dialog.CHANGE) R.string.eversense_change_transmitter else R.string.eversense_scan_title)) },
            text = {
                if (current == Dialog.RESULTS && foundDevices.isNotEmpty()) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        foundDevices.forEach { item ->
                            TextButton(onClick = {
                                dismissDialog()
                                ioScope.launch { eversense.connect(item.device) }
                                mainHandler.postDelayed({ updateStatus() }, 3000)
                            }) { Text(item.name) }
                        }
                    }
                } else Text(stringResource(message))
            },
            confirmButton = {
                when (current) {
                    Dialog.DISCONNECT -> TextButton(onClick = {
                        dismissDialog(); eversense.disconnect(); mainHandler.postDelayed({ updateStatus() }, 500)
                    }) { Text(stringResource(R.string.eversense_status_disconnect)) }
                    Dialog.CHANGE -> TextButton(onClick = {
                        dismissDialog(); eversense.clearStoredDevice(); updateStatus(); showDeviceSelectionDialog()
                    }) { Text(stringResource(R.string.eversense_change_transmitter)) }
                    Dialog.RESULTS -> if (foundDevices.isEmpty()) TextButton(onClick = ::dismissDialog) { Text(stringResource(android.R.string.ok)) }
                    else -> Unit
                }
            },
            dismissButton = { TextButton(onClick = ::dismissDialog) { Text(stringResource(R.string.eversense_scan_cancel)) } }
        )
    }

    private fun signalToLabel(strength: Int): String = getString(when {
        strength >= 75 -> R.string.eversense_signal_excellent
        strength >= 48 -> R.string.eversense_signal_good
        strength >= 30 -> R.string.eversense_signal_fair
        strength >= 25 -> R.string.eversense_signal_weak
        strength > 0 -> R.string.eversense_signal_poor
        else -> R.string.eversense_not_connected
    })

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        scanSession?.close()
        ioScope.cancel()
        eversense.removeWatcher(this)
        super.onDestroy()
    }
}
