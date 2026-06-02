package app.aaps.plugins.source.activities

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import app.aaps.plugins.source.R
import app.aaps.plugins.eversense.EversenseCGMPlugin
import app.aaps.plugins.eversense.callbacks.EversenseScanCallback
import app.aaps.plugins.eversense.callbacks.EversenseWatcher
import app.aaps.plugins.eversense.models.ActiveAlarm
import app.aaps.plugins.eversense.models.EversenseState
import app.aaps.plugins.eversense.models.EversenseScanResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class EversenseStatusActivity : AppCompatActivity(), EversenseWatcher {

    @Inject lateinit var eversense: EversenseCGMPlugin

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onResume() {
        super.onResume()
        eversense.addWatcher(this)
        updateStatus()
    }

    override fun onPause() {
        super.onPause()
        eversense.removeWatcher(this)
    }

    // EversenseWatcher: update button instantly on connection/state change
    override fun onConnectionChanged(connected: Boolean) { mainHandler.post { updateStatus() } }
    override fun onStateChanged(state: EversenseState) { mainHandler.post { updateStatus() } }
    override fun onTransmitterReady() { /* No-op: status refreshes via onStateChanged */ }
    override fun onTransmitterNotPlaced() { /* No-op: status refreshes via onStateChanged */ }
    override fun onAlarmReceived(alarm: ActiveAlarm) { /* No-op: status refreshes via onStateChanged */ }
    override fun onCGMRead(type: app.aaps.plugins.eversense.enums.EversenseType, readings: List<app.aaps.plugins.eversense.models.EversenseCGMResult>) { /* No-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_eversense_status)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.eversense_status_title)

        updateStatus()

        findViewById<Button>(R.id.eversense_btn_connect).setOnClickListener {
            handleConnectTap()
        }

        findViewById<Button>(R.id.eversense_btn_sync).setOnClickListener {
            if (eversense.isConnected()) {
                ioScope.launch { eversense.triggerFullSync(force = true) }
                mainHandler.postDelayed({ updateStatus() }, 5000)
            }
        }
    }

    private fun updateStatus() {
        val state = eversense.getCurrentState()
        val notConnected = getString(R.string.eversense_not_connected)

        findViewById<TextView>(R.id.eversense_status_connected).text =
            getString(R.string.eversense_status_connected_label, if (eversense.isConnected()) "✅" else "❌")
        findViewById<TextView>(R.id.eversense_status_battery).text =
            getString(R.string.eversense_status_battery_label, state.batteryPercentage)
        findViewById<TextView>(R.id.eversense_status_insertion).text =
            getString(R.string.eversense_status_insertion_label, dateFormatter.format(Date(state.insertionDate)))
        findViewById<TextView>(R.id.eversense_status_last_sync).text =
            getString(R.string.eversense_status_last_sync_label, if (state.lastSync > 0) dateFormatter.format(Date(state.lastSync)) else getString(R.string.eversense_status_never))
        findViewById<TextView>(R.id.eversense_status_signal).text =
            getString(R.string.eversense_status_signal_label, signalToLabel(state.sensorSignalStrength))

        findViewById<TextView>(R.id.eversense_status_phase).text =
            getString(R.string.eversense_status_phase_label, state.calibrationPhase.name)

        findViewById<TextView>(R.id.eversense_status_last_cal).text =
            getString(R.string.eversense_status_last_cal_label, if (state.lastCalibrationDate > 0) dateFormatter.format(Date(state.lastCalibrationDate)) else notConnected)
        findViewById<TextView>(R.id.eversense_status_next_cal).text =
            getString(R.string.eversense_status_next_cal_label, if (state.nextCalibrationDate > 0) dateFormatter.format(Date(state.nextCalibrationDate)) else notConnected)
        findViewById<Button>(R.id.eversense_btn_connect).text =
            if (eversense.isConnected()) getString(R.string.eversense_status_disconnect) else getString(R.string.eversense_status_connect)
        findViewById<Button>(R.id.eversense_btn_sync).isEnabled = eversense.isConnected()
    }

    private fun handleConnectTap() {
        if (eversense.isConnected()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.eversense_scan_title))
                .setMessage("Disconnect from transmitter?")
                .setPositiveButton("Disconnect") { _, _ ->
                    eversense.clearStoredDevice()
                    eversense.disconnect()
                    mainHandler.postDelayed({ updateStatus() }, 500)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            val prefs = getSharedPreferences("EversenseCGMManager", Context.MODE_PRIVATE)
            val hasStoredDevice = prefs.getString("eversense_remote_device", null) != null
            if (hasStoredDevice) {
                ioScope.launch { eversense.connect(null) }
                mainHandler.postDelayed({ updateStatus() }, 3000)
            } else {
                showDeviceSelectionDialog()
            }
        }
    }

    private fun showDeviceSelectionDialog() {
        val foundDevices = mutableListOf<EversenseScanResult>()
        var isCancelled = false
        var dialog: AlertDialog? = null

        val scanCallback = object : EversenseScanCallback {
            override fun onResult(item: EversenseScanResult) {
                if (!isCancelled && item.name.matches(Regex("T\\d+.*")) && foundDevices.none { it.name == item.name })
                    foundDevices.add(item)
            }
        }

        eversense.startScan(scanCallback)

        mainHandler.postDelayed({
            if (isCancelled) return@postDelayed
            eversense.stopScan()
            dialog?.dismiss()
            if (foundDevices.isEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.eversense_scan_title))
                    .setMessage("No Eversense transmitters found. Make sure the transmitter is nearby and try again.")
                    .setPositiveButton("OK", null)
                    .show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.eversense_scan_title))
                    .setItems(foundDevices.map { it.name }.toTypedArray()) { _, position ->
                        ioScope.launch { eversense.connect(foundDevices[position].device) }
                        mainHandler.postDelayed({ updateStatus() }, 3000)
                    }
                    .setNegativeButton(getString(R.string.eversense_scan_cancel), null)
                    .show()
            }
        }, 10000)

        dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.eversense_scan_title))
            .setMessage("Scanning for Eversense devices (10 seconds)...")
            .setNegativeButton(getString(R.string.eversense_scan_cancel)) { _, _ ->
                isCancelled = true
                eversense.stopScan()
            }
            .setCancelable(false)
            .show()
    }

    private fun signalToLabel(strength: Int): String = when {
        strength >= 75 -> "Excellent"
        strength >= 48 -> "Good"
        strength >= 30 -> "Low"
        strength >= 25 -> "Poor"
        strength > 0   -> "Very Poor"
        else           -> getString(R.string.eversense_not_connected)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}